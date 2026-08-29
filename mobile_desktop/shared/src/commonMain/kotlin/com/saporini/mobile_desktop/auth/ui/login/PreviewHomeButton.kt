package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

@Composable
internal fun BoxScope.PreviewHomeButton() {
    val navigator = LocalNavigator.currentOrThrow

    IconButton(
        onClick = { navigator.replaceAll(UiPreviewScreen) },
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(12.dp)
            .background(Color.White.copy(alpha = 0.92f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Outlined.Home,
            contentDescription = "UI screens",
            tint = Color.Black
        )
    }
}
