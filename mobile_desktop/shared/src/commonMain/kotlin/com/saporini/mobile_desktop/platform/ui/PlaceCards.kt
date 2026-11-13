package com.saporini.mobile_desktop.platform.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.HoverTooltip
import com.saporini.mobile_desktop.core.ui.RemoteImage
import com.saporini.mobile_desktop.platform.PickedPicture
import com.saporini.mobile_desktop.pos.menu.ui.item.MaxPhotoBytes
import com.saporini.mobile_desktop.pos.menu.ui.item.photoContentType
import com.saporini.mobile_desktop.pos.menu.ui.justCreated
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

// The cards of the super admins' restaurant and branch pages: a cover picture (or a soft colour), a logo square on its
// edge, a status badge, a strip of figures, a footer and an "Open" pill. Hovering lifts the card.

/** A soft colour per restaurant or branch, so cards without a picture are still easy to tell apart. */
internal data class Accent(val strong: Color, val soft: Color, val softer: Color)

private val Accents = listOf(
    Accent(Color(0xFF4F7942), Color(0xFFD9E8D1), Color(0xFFF2F7EF)),
    Accent(Color(0xFF24748A), Color(0xFFD2E7EE), Color(0xFFEFF6F8)),
    Accent(Color(0xFFC8790B), Color(0xFFF5E2C2), Color(0xFFFCF6EC)),
    Accent(Color(0xFF6E5A9E), Color(0xFFE1DAF0), Color(0xFFF5F2FA)),
    Accent(Color(0xFFB0505E), Color(0xFFF1D7DB), Color(0xFFFBF2F3)),
    Accent(Color(0xFF2E8B7A), Color(0xFFD1ECE6), Color(0xFFEFF8F6))
)

internal fun accentOf(key: String): Accent = Accents[(key.hashCode() and 0x7fffffff) % Accents.size]

/** "Local Demo Bistro" → "LB": the first and last word, so similar names still differ. */
internal fun initialsOf(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

@Composable
internal fun PlaceCard(
    accentKey: String,
    title: String,
    place: String,
    coverUrl: String?,
    logoUrl: String?,
    status: String,
    statusColor: Color,
    stats: List<Pair<String, String>>,
    actionText: String,
    entering: Boolean,
    highlighted: Boolean,
    enabled: Boolean,
    canChangeCover: Boolean,
    coverBusy: Boolean,
    onCover: (PickedPicture) -> Unit,
    onRemoveCover: () -> Unit,
    onCoverProblem: (String) -> Unit,
    onOpen: () -> Unit,
    // A small button just before the "Open" pill (e.g. "+ Add branch").
    extraAction: (@Composable () -> Unit)? = null,
    // The pen in the cover's corner; the picture is then changed in the edit dialog.
    onEdit: (() -> Unit)? = null,
    footer: @Composable RowScope.(Accent) -> Unit
) {
    val accent = accentOf(accentKey)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered) 14.dp else 3.dp, label = "card-lift")
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onOpen,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.justCreated(highlighted, shape).fillMaxWidth()
            .shadow(lift, shape, ambientColor = Color(0x16000000), spotColor = Color(0x1C000000)),
        shape = shape, color = Color.White,
        border = BorderStroke(1.dp, if (hovered) accent.strong.copy(alpha = 0.45f) else Kit.Border)
    ) {
        Box {
            Column {
                Box(Modifier.fillMaxWidth().height(112.dp)) {
                    CoverBackground(coverUrl, accent)
                    if (onEdit != null) {
                        EditPill(onEdit, Modifier.align(Alignment.TopEnd).padding(10.dp))
                    } else if (canChangeCover) {
                        CoverButton(coverUrl != null, coverBusy, onCover, onRemoveCover, onCoverProblem, Modifier.align(Alignment.TopEnd).padding(10.dp))
                    }
                    StatusBadge(status, statusColor, Modifier.align(Alignment.TopStart).padding(12.dp))
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Place, null, Modifier.size(14.dp), tint = Kit.Muted)
                            Text(place, fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        stats.forEachIndexed { index, (value, label) ->
                            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(Kit.Border))
                            Column(Modifier.weight(if (index == stats.lastIndex && stats.size > 2) 1.2f else 1f).padding(horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, maxLines = 1)
                                Text(label, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    KitDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        footer(accent)
                        extraAction?.invoke()
                        OpenPill(actionText, entering, hovered)
                    }
                }
            }
            // The logo sits on the edge of the cover.
            Box(
                Modifier.padding(start = 20.dp, top = 78.dp).size(68.dp).shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(Color.White).padding(3.dp)
            ) {
                LogoTile(logoUrl, title, accent, Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)), 22)
            }
        }
    }
}

/** The cover picture, or the card's soft colour with light rings and a faint shop front. */
@Composable
internal fun CoverBackground(coverUrl: String?, accent: Accent) {
    val plain: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.soft, accent.softer)))) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.35f), radius = 70.dp.toPx(), center = Offset(size.width - 40.dp.toPx(), -10.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.25f), radius = 46.dp.toPx(), center = Offset(size.width * 0.45f, size.height + 18.dp.toPx()))
            }
            Icon(Icons.Outlined.Storefront, null, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 6.dp).size(58.dp),
                tint = accent.strong.copy(alpha = 0.16f))
        }
    }
    if (coverUrl == null) {
        plain()
    } else {
        Box(Modifier.fillMaxSize()) {
            RemoteImage(coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) { plain() }
            // A light shade at the top keeps the badge and the picture button readable on any picture.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent))))
        }
    }
}

/** The logo, or the initials on the card's colour. */
@Composable
internal fun LogoTile(logoUrl: String?, name: String, accent: Accent, modifier: Modifier, fontSize: Int) {
    RemoteImage(logoUrl, "$name logo", modifier, contentScale = ContentScale.Crop) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.strong, accent.strong.copy(alpha = 0.78f)))),
            contentAlignment = Alignment.Center) {
            Text(initialsOf(name), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = fontSize.sp, color = Color.White)
        }
    }
}

/** The restaurant's logo in a rounded square, or its initials on its colour when it has none. */
@Composable
internal fun RestaurantLogo(url: String?, name: String, size: Dp) {
    LogoTile(url, name, accentOf(name), Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)), (size.value * 0.34f).toInt())
}

@Composable
private fun StatusBadge(label: String, color: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.94f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color)
    }
}

/** Opens the file picker for a picture, checked like dish photos (PNG, JPEG or WebP under 5 MB). */
@Composable
internal fun rememberPicturePicker(onPicked: (PickedPicture) -> Unit, onProblem: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch {
            val contentType = photoContentType(file.name)
            val bytes = runCatching { file.readBytes() }.getOrNull()
            when {
                contentType == null -> onProblem("Use a PNG, JPEG or WebP picture.")
                bytes == null -> onProblem("This picture couldn't be read. Try another one.")
                bytes.size > MaxPhotoBytes -> onProblem("The picture is too big. Use one under 5 MB.")
                runCatching { bytes.decodeToImageBitmap() }.isFailure -> onProblem("This file can't be opened as a picture.")
                else -> onPicked(PickedPicture(bytes, file.name, contentType))
            }
        }
    }
    return { launcher.launch() }
}

/** The round button on the cover: add a picture, or change/remove it. */
@Composable
private fun CoverButton(
    hasCover: Boolean,
    busy: Boolean,
    onCover: (PickedPicture) -> Unit,
    onRemove: () -> Unit,
    onProblem: (String) -> Unit,
    modifier: Modifier
) {
    var open by remember { mutableStateOf(false) }
    val picker = rememberPicturePicker(onCover, onProblem)
    Box(modifier) {
        HoverTooltip(if (hasCover) "Change the picture" else "Add a picture") {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                    .clickable(enabled = !busy) { if (hasCover) open = true else picker() },
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Kit.Green, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.AddPhotoAlternate, if (hasCover) "Change the picture" else "Add a picture", Modifier.size(18.dp), tint = Kit.Ink)
            }
        }
        DropdownMenu(open, { open = false }, Modifier.background(Color.White)) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.Upload, null, Modifier.size(18.dp), tint = Kit.Ink) },
                text = { Text("Choose another picture", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink) },
                onClick = { open = false; picker() }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = Kit.Danger) },
                text = { Text("Remove the picture", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Danger) },
                onClick = { open = false; onRemove() }
            )
        }
    }
}

@Composable
private fun OpenPill(text: String, entering: Boolean, hovered: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (hovered) Kit.Green else Kit.GreenSoft).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (entering) {
            CircularProgressIndicator(Modifier.size(16.dp), color = if (hovered) Color.White else Kit.Green, strokeWidth = 2.dp)
        } else {
            Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (hovered) Color.White else Kit.Green)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = if (hovered) Color.White else Kit.Green)
        }
    }
}

/** The pen on a card's cover: "Edit". */
@Composable
private fun EditPill(onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.94f)).clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp), tint = Kit.Ink)
        Text("Edit", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
    }
}

/** A small white pill with a plus, for adding something from a card ("Add branch"). */
@Composable
internal fun AddPill(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (hovered) Kit.GreenSoft else Color.White)
            .border(1.dp, if (hovered) Kit.Green.copy(alpha = 0.5f) else Kit.Border, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Outlined.Add, null, Modifier.size(15.dp), tint = Kit.Green)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
    }
}

/** A dashed tile at the end of a grid for adding one more. */
@Composable
internal fun AddTile(title: String, detail: String, onClick: () -> Unit, height: Dp = 140.dp, modifier: Modifier = Modifier.fillMaxWidth()) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).background(if (hovered) Kit.GreenSoft else Color.White.copy(alpha = 0.6f))
            .drawBehind {
                drawRoundRect(
                    color = if (hovered) Kit.Green else Color(0xFFC9D3C5),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                )
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (hovered) Kit.Green else Kit.GreenSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = if (hovered) Color.White else Kit.Green)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
        Spacer(Modifier.height(4.dp))
        Text(detail, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
    }
}

/** The curved back arrow (like the POS's), on the same line as a page's title. */
@Composable
internal fun BackArrow(description: String, onClick: () -> Unit) {
    HoverTooltip(description) {
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Reply, description, Modifier.size(24.dp), tint = Kit.Ink)
        }
    }
}

/** A small icon + text line for a card's footer. */
@Composable
internal fun FooterLine(icon: ImageVector, text: String, tint: Color = Kit.Muted) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(13.dp), tint = tint)
        Text(text, fontFamily = Inter(), fontSize = 11.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
