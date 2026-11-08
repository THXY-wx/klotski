package com.thxy

import com.thxy.gamestate.GameState
import com.thxy.session.GameSession

/**
 * JavaFX WebView 与华容道核心之间的桥接层。
 *
 * 游戏规则位于不可变的 [GameState] 中，会话阶段和计时位于
 * [GameSession] 中；本类负责把操作转发给会话并返回 JSON 状态。
 *
 * @param onTimerChanged 计时变化的回调，用于主动更新页面上的计时显示。
 */
class GameBridge(
    onTimerChanged: (Long) -> Unit = {}
) {

    private val session = GameSession(4, onTimerChanged)

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
     */
    fun start(size: Int): String = session.newGame(size)

    /**
     * 移动指定坐标上的数字块。
     *
     * @param x 数字块所在列，从 1 开始。
     * @param y 数字块所在行，从 1 开始。
     * @return 移动后的 JSON 状态。
     */
    fun move(x: Int, y: Int): String = session.move(x, y)

    /**
     * 按数字块的移动方向执行一步。
     *
     * @param value `up`、`down`、`left` 或 `right`，不区分大小写。
     * @return 移动后的 JSON 状态。
     */
    fun moveDirection(value: String): String = session.moveDirection(value)

    /**
     * 撤销上一步移动。
     *
     * @return 撤销后的 JSON 状态。
     */
    fun undo(): String = session.undo()

    /**
     * 重做上一步被撤销的移动。
     *
     * @return 重做后的 JSON 状态。
     */
    fun redo(): String = session.redo()

    /**
     * 将当前棋盘恢复到本局开始时的状态。
     *
     * @return 恢复后的 JSON 状态。
     */
    fun restart(): String = session.restart()

    /**
     * 以当前边长重新生成一个可解的随机棋盘。
     *
     * @return 新棋盘的 JSON 状态。
     */
    fun shuffle(): String = session.shuffle()

    /**
     * 返回当前棋盘状态。
     *
     * @return 当前棋盘的 JSON 状态。
     */
    fun state(): String = session.toJson()

    /**
     * 停止会话计时器。
     */
    fun dispose() {
        session.dispose()
    }
}
