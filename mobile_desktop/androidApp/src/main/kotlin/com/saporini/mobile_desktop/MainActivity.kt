package com.saporini.mobile_desktop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.saporini.mobile_desktop.core.session.TokenPersistence

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        TokenPersistence.init(this)
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }

    // Keys no text field took: a barcode scanner's quick burst ending with Enter is a scan (user, 2026-11-27).
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val scanner = com.saporini.mobile_desktop.core.scan.ScanCatcher
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (scanner.enter()) return true
        } else {
            event.unicodeChar.takeIf { it > 0 }?.let { scanner.char(it.toChar()) }
        }
        return super.onKeyDown(keyCode, event)
    }

    // Every touch or key on the app's screen keeps a connected till from locking.
    override fun onUserInteraction() {
        super.onUserInteraction()
        com.saporini.mobile_desktop.core.session.IdleLock.touch()
    }
}

@Preview(name = "Pixel", device = Devices.PIXEL, showSystemUi = true)
@Preview(name = "Pixel Tablet", device = Devices.PIXEL_TABLET, showSystemUi = true)
@Composable
fun AppAndroidPreview() {
    App()
}