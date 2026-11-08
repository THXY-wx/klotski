package com.thxy

/**
 * 普通模式下的窗口位置和大小。
 *
 * @property x 窗口左上角横坐标。
 * @property y 窗口左上角纵坐标。
 * @property width 窗口宽度。
 * @property height 窗口高度。
 * @property maximized 退出无边框全屏后是否恢复最大化。
 */
internal data class WindowBounds(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val maximized: Boolean
)
