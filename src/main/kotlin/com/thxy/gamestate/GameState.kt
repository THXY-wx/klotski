package com.thxy.gamestate

import com.thxy.Klotski
import kotlin.math.sqrt

/**
 * 一局华容道的不可变状态。
 *
 * 所有状态转换函数都接收旧状态并返回新状态，不修改传入对象；只有
 * `GameSession` 持有一个可变的当前状态引用。
 *
 * @property size 棋盘边长。
 * @property board 当前棋盘。
 * @property initialBoard 本局开始时的棋盘，用于“重新开始”。
 * @property moves 当前步数。
 * @property history 已执行移动的历史。
 * @property redo 可重做的移动。
 */
internal data class GameState(
    val size: Int,
    val board: Klotski,
    val initialBoard: Klotski,
    val moves: Int,
    val history: List<Move>,
    val redo: List<Move>
) {

    companion object {

        /**
         * `Int.MAX_VALUE` 的四次方根向下取整，即 215。
         */
        val MAX_BOARD_SIZE = sqrt(sqrt(Int.MAX_VALUE.toDouble())).toInt()

        /**
         * 创建一局新的随机棋局。
         *
         * @param size 棋盘边长，范围为 `2..MAX_BOARD_SIZE`。
         * @return 新棋局状态。
         * @throws IllegalArgumentException 当边长超出范围时抛出。
         */
        fun newGame(size: Int): GameState {
            require(size in 2..MAX_BOARD_SIZE) {
                "棋盘边长必须在 2..$MAX_BOARD_SIZE 之间：$size"
            }
            val board = Klotski(size).random()
            return GameState(
                size = size,
                board = board,
                initialBoard = board,
                moves = 0,
                history = emptyList(),
                redo = emptyList()
            )
        }

        /**
         * 点击指定坐标上的数字块并返回新状态。
         *
         * @param state 旧状态。
         * @param x 数字块所在列，从 1 开始。
         * @param y 数字块所在行，从 1 开始。
         * @return 新状态；坐标无效或数字块不与空白格相邻时返回旧状态。
         */
        fun move(state: GameState, x: Int, y: Int): GameState {
            val direction = directionForClick(state, x, y) ?: return state
            return apply(state, direction)
        }

        /**
         * 按数字块的移动方向执行一步。
         *
         * @param state 旧状态。
         * @param value `up`、`down`、`left` 或 `right`，不区分大小写。
         * @return 新状态；方向无效或无法移动时返回旧状态。
         */
        fun moveDirection(state: GameState, value: String): GameState {
            val direction = when (value.lowercase()) {
                "up" -> MoveDirection.UP
                "down" -> MoveDirection.DOWN
                "left" -> MoveDirection.LEFT
                "right" -> MoveDirection.RIGHT
                else -> return state
            }
            return apply(state, direction)
        }

        /**
         * 撤销上一步移动。
         *
         * @param state 旧状态。
         * @return 新状态；没有可撤销步骤时返回旧状态。
         */
        fun undo(state: GameState): GameState {
            val move = state.history.lastOrNull() ?: return state
            return state.copy(
                board = applyDirection(state.board, move.inverse),
                moves = (state.moves - 1).coerceAtLeast(0),
                history = state.history.dropLast(1),
                redo = state.redo + move
            )
        }

        /**
         * 重做上一步被撤销的移动。
         *
         * @param state 旧状态。
         * @return 新状态；没有可重做步骤时返回旧状态。
         */
        fun redo(state: GameState): GameState {
            val move = state.redo.lastOrNull() ?: return state
            return state.copy(
                board = applyDirection(state.board, move.forward),
                moves = state.moves + 1,
                history = state.history + move,
                redo = state.redo.dropLast(1)
            )
        }

        /**
         * 返回本局开始时的棋盘。
         *
         * @param state 旧状态。
         * @return 重置后的新状态。
         */
        fun restart(state: GameState): GameState = state.copy(
            board = state.initialBoard,
            moves = 0,
            history = emptyList(),
            redo = emptyList()
        )

        /**
         * 为当前棋盘生成一个新的随机可解棋局。
         *
         * @param state 旧状态。
         * @return 新棋局状态。
         */
        fun shuffle(state: GameState): GameState {
            val board = Klotski(state.size).random()
            return state.copy(
                board = board,
                initialBoard = board,
                moves = 0,
                history = emptyList(),
                redo = emptyList()
            )
        }

        /**
         * 判断点击位置是否可以移动，并返回数字块的移动方向。
         *
         * @param state 当前状态。
         * @param x 列坐标，从 1 开始。
         * @param y 行坐标，从 1 开始。
         * @return 数字块的移动方向；无效或不相邻时返回 `null`。
         */
        private fun directionForClick(state: GameState, x: Int, y: Int): MoveDirection? {
            if (x !in 1..state.size || y !in 1..state.size) return null

            val cells = state.board.snapshot()
            val blankIndex = cells.indexOf(0)
            if (blankIndex < 0) return null

            val blankX = blankIndex % state.size + 1
            val blankY = blankIndex / state.size + 1
            return when {
                x == blankX && y == blankY - 1 -> MoveDirection.DOWN
                x == blankX && y == blankY + 1 -> MoveDirection.UP
                x == blankX - 1 && y == blankY -> MoveDirection.RIGHT
                x == blankX + 1 && y == blankY -> MoveDirection.LEFT
                else -> null
            }
        }

        /**
         * 执行一次有效移动，并返回不可变的新状态。
         *
         * @param state 旧状态。
         * @param direction 数字块的移动方向。
         * @return 新状态；无法移动时返回旧状态。
         */
        private fun apply(state: GameState, direction: MoveDirection): GameState {
            val next = applyDirection(state.board, direction)
            if (next === state.board) return state

            return state.copy(
                board = next,
                moves = state.moves + 1,
                history = state.history + Move(direction, direction.opposite()),
                redo = emptyList()
            )
        }

        /**
         * 在不可变棋盘上执行一个方向。
         *
         * @param board 旧棋盘。
         * @param direction 数字块的移动方向。
         * @return 新棋盘；无法移动时返回旧棋盘。
         */
        private fun applyDirection(board: Klotski, direction: MoveDirection): Klotski = when (direction) {
            MoveDirection.UP -> board.up()
            MoveDirection.DOWN -> board.down()
            MoveDirection.LEFT -> board.left()
            MoveDirection.RIGHT -> board.right()
        }

        /**
         * 返回指定方向的反方向。
         *
         * @return 反方向。
         */
        private fun MoveDirection.opposite(): MoveDirection = when (this) {
            MoveDirection.UP -> MoveDirection.DOWN
            MoveDirection.DOWN -> MoveDirection.UP
            MoveDirection.LEFT -> MoveDirection.RIGHT
            MoveDirection.RIGHT -> MoveDirection.LEFT
        }
    }
}
