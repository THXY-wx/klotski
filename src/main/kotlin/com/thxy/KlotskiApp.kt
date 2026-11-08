package com.thxy

import javafx.application.Application
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import javafx.stage.Stage
import netscape.javascript.JSObject

/**
 * 数字华容道桌面应用入口。
 *
 * 使用 JavaFX WebView 承载 HTML/CSS/JavaScript 界面，并通过
 * [GameBridge] 与 Kotlin 游戏核心通信，通过 [WindowBridge] 控制窗口。
 */
class KlotskiApp : Application() {

    private val bridge = GameBridge()
    private lateinit var windowBridge: WindowBridge

    override fun start(stage: Stage) {
        val webView = WebView().apply {
            isContextMenuEnabled = false
        }
        val engine = webView.engine
        engine.isJavaScriptEnabled = true

        val scene = Scene(webView, 1180.0, 780.0).apply {
            fill = Color.web("#06101d")
        }
        windowBridge = WindowBridge(stage, scene) { json ->
            Platform.runLater {
                runCatching {
                    engine.executeScript(
                        "window.klotskiApp && window.klotskiApp.updateWindowState($json)"
                    )
                }
            }
        }

        stage.title = "数字华容道 · Klotski"
        stage.minWidth = 960.0
        stage.minHeight = 640.0
        stage.fullScreenExitHint = ""
        stage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
        stage.scene = scene
        stage.show()

        scene.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            when {
                event.code == KeyCode.F11 -> {
                    windowBridge.toggleFullscreen()
                    event.consume()
                }

                event.code == KeyCode.ESCAPE && windowBridge.isSpecialMode() -> {
                    windowBridge.exitDisplayMode()
                    event.consume()
                }
            }
        }

        var pageFailed = false
        engine.loadWorker.stateProperty().addListener { _, _, state ->
            when (state) {
                Worker.State.SUCCEEDED -> {
                    if (pageFailed) return@addListener
                    try {
                        val window = engine.executeScript("window") as JSObject
                        window.setMember("bridge", bridge)
                        window.setMember("windowBridge", windowBridge)
                        engine.executeScript("window.klotskiApp.boot()")
                    } catch (error: Throwable) {
                        pageFailed = true
                        engine.loadContent(errorPage("界面初始化失败：${error.message}"))
                    }
                }

                Worker.State.FAILED -> {
                    pageFailed = true
                    val message = engine.loadWorker.exception?.message ?: "未知错误"
                    engine.loadContent(errorPage("页面加载失败：$message"))
                }

                else -> Unit
            }
        }

        val page = KlotskiApp::class.java.getResource("/com/thxy/game.html")
        if (page == null) {
            pageFailed = true
            engine.loadContent(errorPage("找不到界面资源：/com/thxy/game.html"))
        } else {
            engine.load(page.toExternalForm())
        }
    }

    /**
     * 从独立模板生成加载失败页面。
     *
     * @param message 错误说明。
     * @return 替换了错误信息的 HTML。
     */
    private fun errorPage(message: String): String {
        val template = runCatching {
            KlotskiApp::class.java
                .getResource("/com/thxy/error.html")
                ?.readText(Charsets.UTF_8)
        }.getOrNull() ?: "<!doctype html><html><body><p>{{MESSAGE}}</p></body></html>"
        return template.replace("{{MESSAGE}}", escapeHtml(message))
    }

    /**
     * 转义插入 HTML 的文本。
     *
     * @param value 原始文本。
     * @return HTML 安全文本。
     */
    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
