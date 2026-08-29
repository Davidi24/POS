package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuthImageScaffold(
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val isWide = maxWidth >= 1000.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxSize()
                        .background(Color.White),
                    content = content
                )
                Box(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.auth_login_img),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                content = content
            )
        }
    }
}
