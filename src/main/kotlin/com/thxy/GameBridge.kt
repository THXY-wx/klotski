package com.thxy

import com.thxy.gamestate.GameState

/**
 * JavaFX WebView 与华容道核心之间的桥接层。
 *
 * 游戏规则位于不可变的 [GameState] 中，本类只负责持有当前状态引用
 * 并把状态转换成 WebView 可解析的 JSON。
 */
class GameBridge {

    private var state: GameState = GameState.newGame(4)

    /**
     * 返回允许选择的最大棋盘边长。
     *
     * @return 最大棋盘边长，即 215。
     */
    fun maxSize(): Int = GameState.MAX_BOARD_SIZE

    /**
     * 以指定边长开始一局新的随机棋盘。
     *
     * @param size 棋盘边长，范围为 `2..maxSize()`。
     * @return 新棋盘的 JSON 状态。
     * @throws IllegalArgumentException 当边长超出范围时抛出。
     */
    fun start(size: Int): String {
        state = GameState.newGame(size)
        return state.toJson()
    }

    /**
     * 移动指定坐标上的数字块。
     *
     * @param x 数字块所在列，从 1 开始。
     * @param y 数字块所在行，从 1 开始。
     * @return 移动后的 JSON 状态。
     */
    fun move(x: Int, y: Int): String {
        state = GameState.move(state, x, y)
        return state.toJson()
    }

    /**
     * 按数字块的移动方向执行一步。
     *
     * @param value `up`、`down`、`left` 或 `right`，不区分大小写。
     * @return 移动后的 JSON 状态。
     */
    fun moveDirection(value: String): String {
        state = GameState.moveDirection(state, value)
        return state.toJson()
    }

    /**
     * 撤销上一步移动。
     *
     * @return 撤销后的 JSON 状态。
     */
    fun undo(): String {
        state = GameState.undo(state)
        return state.toJson()
    }

    /**
     * 重做上一步被撤销的移动。
     *
     * @return 重做后的 JSON 状态。
     */
    fun redo(): String {
        state = GameState.redo(state)
        return state.toJson()
    }

    /**
     * 将当前棋盘恢复到本局开始时的状态。
     *
     * @return 恢复后的 JSON 状态。
     */
    fun restart(): String {
        state = GameState.restart(state)
        return state.toJson()
    }

    /**
     * 以当前边长重新生成一个可解的随机棋盘。
     *
     * @return 新棋盘的 JSON 状态。
     */
    fun shuffle(): String {
        state = GameState.shuffle(state)
        return state.toJson()
    }

    /**
     * 返回当前棋盘状态。
     *
     * @return 当前棋盘的 JSON 状态。
     */
    fun state(): String = state.toJson()
}
