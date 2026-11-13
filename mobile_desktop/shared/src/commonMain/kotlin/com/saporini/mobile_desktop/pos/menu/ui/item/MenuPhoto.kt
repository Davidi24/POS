package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.ui.RemoteImage

private val PhotoPlaceholderBackground = Color(0xFFF1EFEA)
private val PhotoPlaceholderIcon = Color(0xFFB9B4A8)

/** A dish's photo from its stored URL; a calm "no photo" tile while it loads or when the dish has none. */
@Composable
internal fun MenuPhoto(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: Dp = 22.dp
) {
    RemoteImage(url = url, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale) {
        DishPhotoPlaceholder(iconSize)
    }
}

@Composable
internal fun DishPhotoPlaceholder(iconSize: Dp = 22.dp, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(PhotoPlaceholderBackground), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.RestaurantMenu, null, Modifier.size(iconSize), tint = PhotoPlaceholderIcon)
    }
}
