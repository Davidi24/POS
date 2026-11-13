package com.saporini.mobile_desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.saporini.mobile_desktop.core.di.initKoin
import java.awt.GraphicsEnvironment

fun main() {
    initKoin()
    // Opens at the size of the tablet the POS runs on (user, 2026-11-01), never bigger than the screen. It can still be resized.
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "mobile_desktop",
            state = rememberWindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition(Alignment.Center),
                width = minOf(1280, screen.width).dp,
                height = minOf(800, screen.height).dp
            )
        ) {
            App()
        }
    }
}
