package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.core.components.IconTile
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.admin.inventory.StockItemDto
import mobile_desktop.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

// Sample pictures remain available only for name-only choices and House-made examples. Stock item thumbnails must use
// the image URL returned by the backend, or show the item's kind icon when no server photo has been saved.
internal object MockItemPhotos {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    /** "Parmesan, 24 months" → "parmesan-24-months", the picture's file name. */
    fun slug(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** The sample picture for any name (a stock item or a house-made item), when one ships with the app. */
    fun cachedNamed(name: String): ImageBitmap? = cache[slug(name)]

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadNamed(name: String): ImageBitmap? = cache.getOrPut(slug(name)) {
        runCatching { Res.readBytes("files/mock-food/${slug(name)}.png").decodeToImageBitmap() }.getOrNull()
    }
}

/**
 * Shows only a photo URL saved by the backend; otherwise shows the item's kind icon.
 */
@Composable
internal fun StockItemThumb(item: StockItemDto, size: Dp = 38.dp) = ItemThumb(item.imageUrl, item.itemType, size)

/**
 * A picture for anything known only by its name and maybe a photo (an ingredient choice from the menu, say): the photo,
 * else its sample picture (MOCK), else [icon] on a soft tile in [tone].
 */
@Composable
internal fun NamedThumb(name: String, imageUrl: String?, icon: ImageVector, tone: Color, size: Dp) {
    if (!imageUrl.isNullOrBlank()) {
        RemoteImage(imageUrl, name, Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(Color.White)) { IconTile(icon, tone, size) }
        return
    }
    var sample by remember(name) { mutableStateOf(MockItemPhotos.cachedNamed(name)) }
    LaunchedEffect(name) { if (sample == null) sample = MockItemPhotos.loadNamed(name) }
    val picture = sample
    if (picture == null) {
        IconTile(icon, tone, size)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(Color.White)) {
            Image(picture, name, Modifier.fillMaxSize().padding(size * 0.08f), contentScale = ContentScale.Fit)
        }
    }
}

