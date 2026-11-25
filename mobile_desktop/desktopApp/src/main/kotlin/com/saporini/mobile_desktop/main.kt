package com.saporini.mobile_desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.saporini.mobile_desktop.core.di.initKoin
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import javax.swing.SwingUtilities

/**
 * How big the app draws on a Linux desktop (2026-11-25, user: "when I plug or unplug the monitor it shows gigantic, I have
 * to start it again"). GNOME on Wayland gives X apps the real pixels and the screen's scale only as Xft/DPI (192 = 2x), and
 * that changes when a monitor is plugged in or out; Java reads a scale only once, at start. So on Linux Java draws 1:1 and
 * the app scales itself, following Xft/DPI as it changes (the desktop's XSETTINGS, with a check of `xrdb` when the screen
 * changes in case they don't say). Elsewhere (Windows, macOS) Java follows each screen by itself and nothing changes.
 */
private object ScreenScale {
    val linux = System.getProperty("os.name", "").lowercase().startsWith("linux")

    /** A scale given at launch (`-Dsun.java2d.uiScale=2`, older launchers): used only when the desktop says nothing. */
    private val launchScale = System.getProperty("sun.java2d.uiScale")?.toFloatOrNull()?.takeIf { it > 0f }

    val value = mutableFloatStateOf(1f)

    /** Before anything of AWT starts: Java draws 1:1 on Linux; the app does the scaling. */
    fun prepare() {
        if (linux) System.setProperty("sun.java2d.uiScale", "1")
    }

    fun start() {
        if (!linux) return
        val toolkit = Toolkit.getDefaultToolkit()
        value.floatValue = fromDesktop(toolkit) ?: fromXrdb() ?: launchScale ?: 1f
        toolkit.addPropertyChangeListener("gnome.Xft/DPI") {
            SwingUtilities.invokeLater { fromDesktop(toolkit)?.let { value.floatValue = it } }
        }
        // A monitor plugged in or out changes the screen's size: look again, in case the desktop property didn't move.
        Thread({
            var last = screenSize()
            while (true) {
                Thread.sleep(2_000)
                val now = screenSize()
                if (now != last) {
                    last = now
                    val scale = fromDesktop(toolkit)?.takeIf { it != value.floatValue } ?: fromXrdb()
                    if (scale != null) SwingUtilities.invokeLater { value.floatValue = scale }
                }
            }
        }, "screen-scale").apply { isDaemon = true }.start()
    }

    private fun screenSize(): String = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.joinToString { it.defaultConfiguration.bounds.toString() }
    }.getOrDefault("")

    private fun fromDesktop(toolkit: Toolkit): Float? =
        (toolkit.getDesktopProperty("gnome.Xft/DPI") as? Int)?.takeIf { it > 0 }?.let { it / 1024f / 96f }?.coerceIn(0.5f, 4f)

    private fun fromXrdb(): Float? = runCatching {
        val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Regex("""Xft\.dpi:\s*([0-9.]+)""").find(text)?.groupValues?.get(1)?.toFloatOrNull()?.let { it / 96f }?.coerceIn(0.5f, 4f)
    }.getOrNull()
}

fun main() {
    ScreenScale.prepare()
    initKoin()
    ScreenScale.start()
    val startScale = ScreenScale.value.floatValue
    // Opens at the size of the tablet the POS runs on (user, 2026-11-01), never bigger than the screen. It can still be resized.
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "mobile_desktop",
            state = rememberWindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition(Alignment.Center),
                width = minOf(1280f * startScale, screen.width.toFloat()).dp,
                height = minOf(800f * startScale, screen.height.toFloat()).dp
            )
        ) {
            val scale by ScreenScale.value
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
                App()
            }
        }
    }
}
