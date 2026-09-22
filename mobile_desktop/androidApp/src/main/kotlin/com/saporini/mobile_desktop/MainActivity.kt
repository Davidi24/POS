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
}

@Preview(name = "Pixel", device = Devices.PIXEL, showSystemUi = true)
@Preview(name = "Pixel Tablet", device = Devices.PIXEL_TABLET, showSystemUi = true)
@Composable
fun AppAndroidPreview() {
    App()
}