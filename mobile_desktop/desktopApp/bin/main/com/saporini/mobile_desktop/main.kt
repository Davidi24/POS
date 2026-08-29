package com.saporini.mobile_desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.saporini.mobile_desktop.core.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "mobile_desktop",
            state = rememberWindowState(
                width = 1280.dp,
                height = 800.dp
            )
        ) {
            App()
        }
    }
}
