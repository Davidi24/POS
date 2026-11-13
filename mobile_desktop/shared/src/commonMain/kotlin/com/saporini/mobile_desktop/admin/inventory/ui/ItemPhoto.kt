package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.IconTile
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.pos.menu.ui.item.MaxPhotoBytes
import com.saporini.mobile_desktop.pos.menu.ui.item.PhotoChange
import com.saporini.mobile_desktop.pos.menu.ui.item.photoContentType
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

/** A stock item's photo in a list row; its kind's icon when it has none (or while the photo loads). */
@Composable
internal fun ItemThumb(imageUrl: String?, type: String?, size: Dp = 38.dp) {
    val fallback: @Composable () -> Unit = { IconTile(itemTypeIcon(type), itemTypeColor(type), size) }
    if (imageUrl.isNullOrBlank()) fallback()
    else RemoteImage(imageUrl, null, Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(Kit.Canvas)) { fallback() }
}

/**
 * The photo part of the item editor: a tile to click (add or change; × removes), with [beside] (the name field) and a
 * one-line photo hint next to it. The picked file is checked here (type, size, really a picture); it's uploaded once
 * the item is saved.
 */
@Composable
internal fun ItemPhotoField(
    imageUrl: String?,
    photo: PhotoChange,
    onChange: (PhotoChange) -> Unit,
    enabled: Boolean,
    beside: @Composable ColumnScope.() -> Unit
) {
    var problem by remember { mutableStateOf<String?>(null) }
    val picked = photo as? PhotoChange.Replace
    val pickedBitmap = remember(picked) { picked?.bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    val keptUrl = imageUrl.takeIf { photo == PhotoChange.Keep }
    val hasPhoto = picked != null || keptUrl != null
    val pick = rememberPhotoPicker({ problem = it }, onChange)

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        // The tile is the only photo control: click it to add or change the photo, the × takes it off.
        Surface(
            onClick = pick, enabled = enabled, modifier = Modifier.size(112.dp), shape = RoundedCornerShape(14.dp),
            color = Kit.Canvas, border = BorderStroke(1.dp, if (problem != null) Kit.Danger else Kit.Border)
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    pickedBitmap != null -> Image(pickedBitmap, "New photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    keptUrl != null -> RemoteImage(keptUrl, "Item photo", Modifier.fillMaxSize()) { EmptyPhoto() }
                    else -> EmptyPhoto()
                }
                if (hasPhoto) {
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(13.dp), tint = Color.White)
                        Text("Change", Modifier.padding(start = 5.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                            color = Color.White)
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                        .background(Color.White).clickable(enabled = enabled) {
                            problem = null
                            onChange(if (imageUrl != null) PhotoChange.Remove else PhotoChange.Keep)
                        }, Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Remove photo", Modifier.size(15.dp), tint = Kit.Danger)
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            beside()
            Text(
                problem ?: when {
                    picked != null -> "Photo: ${picked.fileName} · uploads when you save"
                    photo == PhotoChange.Remove -> "The photo is taken off when you save"
                    keptUrl != null -> "Click the photo to change it"
                    else -> "Photo is optional: PNG, JPEG or WebP, up to 5 MB"
                },
                fontFamily = Inter(), fontSize = 11.sp, color = if (problem != null) Kit.Danger else Kit.Muted, maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyPhoto() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), Alignment.Center) {
            Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(22.dp), tint = Kit.Green)
        }
        Text("Add photo", Modifier.padding(top = 8.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
    }
}

/**
 * Opens the file picker for a photo and checks the file (PNG, JPEG or WebP, up to 5 MB, really a picture). [onPicked]
 * gets a good one; [onProblem] gets why a file can't be used, or null once one could.
 */
@Composable
internal fun rememberPhotoPicker(onProblem: (String?) -> Unit, onPicked: (PhotoChange.Replace) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val problemTo by rememberUpdatedState(onProblem)
    val pickedTo by rememberUpdatedState(onPicked)
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file == null) return@rememberFilePickerLauncher
        scope.launch {
            val contentType = photoContentType(file.name)
            if (contentType == null) {
                problemTo("Use a PNG, JPEG or WebP photo.")
                return@launch
            }
            val bytes = runCatching { file.readBytes() }.getOrNull()
            val problem = when {
                bytes == null -> "This photo couldn't be read. Try another one."
                bytes.size > MaxPhotoBytes -> "This photo is too big. Use one under 5 MB."
                runCatching { bytes.decodeToImageBitmap() }.isFailure -> "This file can't be opened as a photo."
                else -> null
            }
            problemTo(problem)
            if (problem == null && bytes != null) pickedTo(PhotoChange.Replace(bytes, file.name, contentType))
        }
    }
    return { picker.launch() }
}

/**
 * The first tile of a place's icons (user, 2026-11-11: "when creating a place I can upload my own image if I want"):
 * "Your picture" to upload one; once picked (or saved) it shows the picture, chosen, with an × to go back to an icon.
 * Click it again to pick another picture. It's uploaded when the place is saved.
 */
@Composable
internal fun OwnPlacePictureTile(imageUrl: String?, photo: PhotoChange, enabled: Boolean, modifier: Modifier,
                                 onChange: (PhotoChange) -> Unit, onProblem: (String?) -> Unit) {
    val picked = photo as? PhotoChange.Replace
    val pickedBitmap = remember(picked) { picked?.bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    val keptUrl = imageUrl.takeIf { photo == PhotoChange.Keep }
    val chosen = pickedBitmap != null || keptUrl != null
    val pick = rememberPhotoPicker(onProblem, onChange)
    Surface(onClick = pick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = if (chosen) Kit.GreenSoft else Color.White,
        border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) Kit.Green else Kit.Green.copy(alpha = 0.45f))) {
        Box(Modifier.fillMaxSize()) {
            if (chosen) {
                if (pickedBitmap != null) Image(pickedBitmap, "Your picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else RemoteImage(keptUrl, "Your picture", Modifier.fillMaxSize()) { EmptyPlacePicture() }
                Text("Yours", Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(vertical = 3.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = Color.White, maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                    .background(Color.White).clickable(enabled = enabled) {
                        onProblem(null)
                        onChange(if (imageUrl != null) PhotoChange.Remove else PhotoChange.Keep)
                    }, Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Remove your picture", Modifier.size(12.dp), tint = Kit.Danger)
                }
            } else EmptyPlacePicture()
        }
    }
}

@Composable
private fun EmptyPlacePicture() {
    Column(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(28.dp), tint = Kit.Green)
        Text("Your picture", Modifier.padding(top = 5.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, color = Kit.Green,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
