package com.thxy

import javafx.application.Platform
import javafx.scene.Scene
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle

/**
 * 窗口控制桥接层。
 *
 * JavaScript 通过该对象切换全屏、无边框全屏，或退出应用。
 *
 * @param stage 主窗口。
 * @param contentScene 承载 WebView 的场景。
 * @param onStateChanged 窗口状态变化时的回调，参数为状态 JSON。
 */
class WindowBridge(
    private val stage: Stage,
    private val contentScene: Scene,
    private val onStateChanged: (String) -> Unit = {}
) {

    init {
        stage.fullScreenProperty().addListener { _, _, _ ->
            notifyState()
        }
    }

    private var borderlessStage: Stage? = null
    private var savedBounds: WindowBounds? = null

    /**
     * 切换普通全屏模式。
     *
     * @return 当前窗口状态 JSON。
     */
    fun toggleFullscreen(): String {
        if (borderlessStage != null) exitBorderless()
        stage.isFullScreen = !stage.isFullScreen
        return stateJson()
    }

    /**
     * 进入或退出无边框全屏模式。
     *
     * @return 当前窗口状态 JSON。
     */
    fun toggleBorderless(): String {
        if (borderlessStage != null) {
            exitBorderless()
        } else {
            enterBorderless()
        }
        return stateJson()
    }

    /**
     * 退出所有特殊显示模式，回到普通窗口。
     *
     * @return 当前窗口状态 JSON。
     */
    fun exitDisplayMode(): String {
        if (borderlessStage != null) {
            exitBorderless()
        } else if (stage.isFullScreen) {
            stage.isFullScreen = false
        }
        return stateJson()
    }

    /**
     * 返回当前窗口状态。
     *
     * @return 当前窗口状态 JSON。
     */
    fun state(): String = stateJson()

    /**
     * 判断当前是否处于全屏或无边框全屏模式。
     *
     * @return 处于特殊显示模式时返回 `true`。
     */
    fun isSpecialMode(): Boolean = stage.isFullScreen || borderlessStage != null

    /**
     * 退出应用。
     */
    fun exit() {
        Platform.exit()
    }

    /**
     * 进入无边框全屏模式。
     */
    private fun enterBorderless() {
        if (borderlessStage != null) return
        if (stage.isFullScreen) stage.isFullScreen = false

        val bounds = Screen.getScreensForRectangle(stage.x, stage.y, stage.width, stage.height)
            .firstOrNull()
            ?.bounds
            ?: Screen.getPrimary().bounds

        savedBounds = WindowBounds(stage.x, stage.y, stage.width, stage.height, stage.isMaximized)
        val borderless = Stage(StageStyle.UNDECORATED)
        borderless.scene = null
        stage.hide()
        stage.scene = null
        borderless.scene = contentScene
        borderless.x = bounds.minX
        borderless.y = bounds.minY
        borderless.width = bounds.width
        borderless.height = bounds.height
        // 不置顶，保证其他应用切到前台时可以正常显示在无边框窗口上方。
        borderless.isAlwaysOnTop = false
        borderless.isResizable = false
        borderless.setOnCloseRequest { event ->
            event.consume()
            exitBorderless()
        }
        borderlessStage = borderless
        borderless.show()
        notifyState()
    }

    /**
     * 退出无边框全屏并恢复普通窗口。
     */
    private fun exitBorderless() {
        val borderless = borderlessStage ?: return
        borderlessStage = null
        borderless.setOnCloseRequest(null)
        borderless.scene = null

        stage.scene = contentScene
        val saved = savedBounds
        if (saved != null) {
            if (!saved.maximized) {
                stage.isMaximized = false
                stage.x = saved.x
                stage.y = saved.y
                stage.width = saved.width
                stage.height = saved.height
            }
        }
        stage.show()
        if (saved?.maximized == true) {
            stage.isMaximized = true
        }
        borderless.close()
        savedBounds = null
        stage.toFront()
        notifyState()
    }

    /**
     * 生成窗口状态 JSON。
     *
     * @return 窗口状态 JSON。
     */
    private fun stateJson(): String =
        "{\"fullscreen\":${stage.isFullScreen},\"borderless\":${borderlessStage != null}}"

    /**
     * 将最新窗口状态通知给页面。
     */
    private fun notifyState() {
        onStateChanged(stateJson())
    }
}
