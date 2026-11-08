package com.thxy.session

import com.thxy.gamestate.GameState
import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.util.Duration

/**
 * 游戏会话控制器。
 *
 * 负责在不可变 [GameState] 之上管理游戏阶段和计时器。棋盘规则仍然由
 * `GameState` 的不可变转换函数完成；本类只保存当前状态引用，并在移动
 * 成功时启动计时、在完成时结束计时。
 *
 * @param initialSize 初始棋盘边长。
 * @param onTimerChanged 计时变化的回调，参数为当前累计毫秒数。
 */
internal class GameSession(
    initialSize: Int = 4,
    private val onTimerChanged: (Long) -> Unit = {}
) {

    private var state: GameState = GameState.newGame(initialSize)
    private var phase: GamePhase = GamePhase.PLAYING
    private var elapsedMillis = 0L
    private var timerRunning = false
    private var startedAtNanos = 0L

    private val timeline = Timeline(
        KeyFrame(Duration.millis(TIMER_INTERVAL_MILLIS), { tick() })
    ).apply {
        cycleCount = Animation.INDEFINITE
    }

    /**
     * 以指定边长开始新游戏，并重置计时。
     *
     * @param size 棋盘边长。
     * @return 新状态 JSON。
     */
    fun newGame(size: Int): String {
        state = GameState.newGame(size)
        phase = GamePhase.PLAYING
        resetTimer()
        return toJson()
    }

    /**
     * 移动指定坐标上的数字块。
     *
     * @param x 列坐标。
     * @param y 行坐标。
     * @return 新状态 JSON。
     */
    fun move(x: Int, y: Int): String {
        if (phase == GamePhase.WON) return toJson()
        return apply(GameState.move(state, x, y))
    }

    /**
     * 按数字块方向移动。
     *
     * @param value 方向名称。
     * @return 新状态 JSON。
     */
    fun moveDirection(value: String): String {
        if (phase == GamePhase.WON) return toJson()
        return apply(GameState.moveDirection(state, value))
    }

    /**
     * 撤销一步。
     *
     * @return 新状态 JSON。
     */
    fun undo(): String {
        if (phase == GamePhase.WON) return toJson()
        state = GameState.undo(state)
        return toJson()
    }

    /**
     * 重做一步。
     *
     * @return 新状态 JSON。
     */
    fun redo(): String {
        if (phase == GamePhase.WON) return toJson()
        state = GameState.redo(state)
        return toJson()
    }

    /**
     * 恢复到本局初始棋盘。
     *
     * @return 新状态 JSON。
     */
    fun restart(): String {
        state = GameState.restart(state)
        phase = GamePhase.PLAYING
        resetTimer()
        return toJson()
    }

    /**
     * 重新生成随机棋盘。
     *
     * @return 新状态 JSON。
     */
    fun shuffle(): String {
        state = GameState.shuffle(state)
        phase = GamePhase.PLAYING
        resetTimer()
        return toJson()
    }

    /**
     * 返回当前状态 JSON。
     *
     * @return 当前状态 JSON。
     */
    fun toJson(): String {
        val cells = state.board.snapshot()
        return buildString(cells.size * 4 + 192) {
            append("{\"size\":").append(state.size)
            append(",\"cells\":[")
            cells.forEachIndexed { index, value ->
                if (index > 0) append(',')
                append(value)
            }
            append("],\"blank\":").append(cells.indexOf(0))
            append(",\"moves\":").append(state.moves)
            append(",\"finished\":").append(phase == GamePhase.WON)
            append(",\"phase\":\"").append(if (phase == GamePhase.WON) "won" else "playing").append('"')
            append(",\"elapsedMillis\":").append(elapsedMillis)
            append(",\"timerRunning\":").append(timerRunning)
            append(",\"undo\":").append(state.history.size)
            append(",\"redo\":").append(state.redo.size)
            append('}')
        }
    }

    /**
     * 停止内部计时器。
     */
    fun dispose() {
        timeline.stop()
    }

    /**
     * 应用一次移动转换，并更新计时与阶段。
     *
     * @param next 转换后的状态。
     * @return 新状态 JSON。
     */
    private fun apply(next: GameState): String {
        if (next === state) return toJson()
        state = next

        if (!timerRunning) startTimer()
        if (state.board.isFinish()) {
            phase = GamePhase.WON
            stopTimer()
        }
        return toJson()
    }

    /**
     * 启动计时器，从当前累计时间继续。
     */
    private fun startTimer() {
        if (phase != GamePhase.PLAYING || timerRunning) return
        timerRunning = true
        startedAtNanos = System.nanoTime() - elapsedMillis * NANOS_PER_MILLI
        timeline.play()
        notifyTimer()
    }

    /**
     * 停止计时器并保留最终时间。
     */
    private fun stopTimer() {
        if (!timerRunning) return
        elapsedMillis = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI
        timerRunning = false
        timeline.stop()
        notifyTimer()
    }

    /**
     * 重置计时器。
     */
    private fun resetTimer() {
        timeline.stop()
        timerRunning = false
        elapsedMillis = 0L
        startedAtNanos = 0L
        notifyTimer()
    }

    /**
     * 定时刷新累计时间。
     */
    private fun tick() {
        if (phase != GamePhase.PLAYING) {
            stopTimer()
            return
        }
        elapsedMillis = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI
        notifyTimer()
    }

    /**
     * 通知页面计时变化。
     */
    private fun notifyTimer() {
        onTimerChanged(elapsedMillis)
    }

    private companion object {
        private const val TIMER_INTERVAL_MILLIS = 250.0
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
