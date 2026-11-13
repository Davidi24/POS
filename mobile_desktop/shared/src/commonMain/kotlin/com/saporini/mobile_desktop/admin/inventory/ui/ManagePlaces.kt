package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.InventoryScreenModel
import com.saporini.mobile_desktop.admin.inventory.InventoryState
import com.saporini.mobile_desktop.admin.inventory.StockLocationDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.RemoteImage
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToLong

/**
 * Stock → Places → "Manage places" (user, 2026-11-11: "only show places here, no three dots; a Manage places button
 * instead of New place, and there you manage the places: a dialog with place cards like the counts"). Every place as a
 * card, three a row when they fit; a card opens its editor, and has Edit and Delete. New place adds one. Places not in use
 * come last, faded.
 */
@Composable
internal fun ManagePlacesDialog(state: InventoryState, model: InventoryScreenModel) {
    val close = { model.managePlaces(false) }
    val places = state.locations.sortedWith(compareBy<StockLocationDto> { !it.active }.thenBy { it.name.lowercase() })
    AppDialog("Manage places", close, subtitle = "Where you keep stock. Add a place, change it or delete it.", maxWidth = 1080.dp,
        buttons = {
            KitButton("Done", close, style = ButtonStyle.SECONDARY)
            if (state.canEdit) KitButton("New place", model::newLocation, icon = Icons.Outlined.Add)
        }) {
        if (places.isEmpty()) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                OverviewEmpty("No places yet", "Add where you keep stock: the kitchen, the bar, the walk-in fridge, the dry store.", Icons.Outlined.Storefront)
            }
            return@AppDialog
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                // Three only when each card has room for its three figures side by side.
                maxWidth >= 880.dp -> 3
                maxWidth >= 460.dp -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                places.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { place -> PlaceCard(place, state, model, Modifier.weight(1f).fillMaxHeight()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** One place as a card: its picture big with its kind, the name and code; what's kept there; Edit and Delete. */
@Composable
private fun PlaceCard(place: StockLocationDto, state: InventoryState, model: InventoryScreenModel, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val zoom by animateFloatAsState(if (hovered) 1.06f else 1f, tween(320))
    val color = if (place.locationType == "CUSTOM") kindKeyColor("CUSTOM:${place.kindId}") else placeTypeColor(place.locationType)
    val here = state.levels.filter { it.locationId == place.id }
    val items = here.count { it.onHandQuantity.asDouble() > 0 }
    val low = here.count { it.lowStock }
    val worth = here.sumOf { level ->
        (level.onHandQuantity.asDouble().coerceAtLeast(0.0) * (state.item(level.inventoryItemId)?.costPerUnit?.asDouble() ?: 0.0) * 100).roundToLong()
    }
    val kind = place.kindLabel()
    val edit = { model.editLocation(place.id) }
    Surface(onClick = edit, enabled = state.canEdit,
        modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand).alpha(if (place.active) 1f else 0.62f),
        shape = RoundedCornerShape(18.dp), color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) color.copy(alpha = 0.5f) else Kit.RowBorder),
        shadowElevation = if (hovered) 8.dp else 2.dp, interactionSource = interaction) {
        Column {
            PlaceBanner(place, place.name, listOfNotNull(kind.takeUnless { it.equals(place.name, ignoreCase = true) }, place.code?.takeIf { it.isNotBlank() })
                .joinToString(" · "), zoom, grey = !place.active, fallback = color) {
                BannerPill(placeTypeIcon(place.locationType), kind, color, Modifier.align(Alignment.TopStart))
                if (!place.active) BannerPill(Icons.Outlined.PauseCircle, "Not in use", Kit.Grey, Modifier.align(Alignment.TopEnd))
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // What's kept there.
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceFigure("Items", "$items", Kit.Ink, Modifier.weight(1f))
                    PlaceFigure("Running low", "$low", if (low > 0) Kit.Danger else Kit.Ink, Modifier.weight(1f))
                    PlaceFigure("Worth", moneyText(worth, null), Kit.Ink, Modifier.weight(1.2f))
                }
                place.notes?.takeIf { it.isNotBlank() }?.let {
                    Text("“$it”", fontFamily = Inter(), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 12.sp, letterSpacing = 0.sp,
                        color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                if (state.canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitButton("Edit", edit, Modifier.weight(1f), icon = Icons.Outlined.Edit, style = ButtonStyle.SECONDARY)
                        KitButton("Delete", { model.askDeleteLocation(place.id) }, Modifier.weight(1f), icon = Icons.Outlined.Delete, style = ButtonStyle.DANGER_OUTLINE)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceFigure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontFamily = Inter(), fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.sp, color = color, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A place's picture across the top of a card (count cards and Manage places): its own picture, else its illustration
 * (cropped toward the top so a sign stays in view), else its icon on a soft band; its name and [detail] in white over a
 * dark fade at the foot. [zoom] grows the picture a little on hover; [grey] takes the colour out. [overlay] adds the pills.
 */
@Composable
internal fun PlaceBanner(
    place: StockLocationDto?,
    name: String,
    detail: String,
    zoom: Float,
    grey: Boolean,
    fallback: Color,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val picture = place?.let { p ->
        placeIconChoices.firstOrNull { it.code == (p.iconCode ?: defaultPlaceIconCode(p.locationType)) }
            ?: placeIconChoices.firstOrNull { it.code == defaultPlaceIconCode(p.locationType) }
    }
    val image = picture?.image
    val ownPicture = place?.imageUrl?.takeIf { it.isNotBlank() }
    val onDark = image != null || ownPicture != null
    val placeColor = place?.let { placeTypeColor(it.locationType) } ?: fallback
    Box(Modifier.fillMaxWidth().aspectRatio(2.3f).clipToBounds().background(if (onDark) PlacePictureNight else placeColor.copy(alpha = 0.12f))) {
        val grown = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
        when {
            ownPicture != null -> RemoteImage(ownPicture, name, grown) {}
            image != null -> Image(painterResource(image), null, grown, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.6f),
                colorFilter = if (grey) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
            else -> Icon(picture?.vector ?: Icons.AutoMirrored.Outlined.FactCheck, null,
                Modifier.align(Alignment.Center).padding(bottom = 20.dp).size(52.dp).graphicsLayer { scaleX = zoom; scaleY = zoom }, tint = placeColor)
        }
        if (onDark) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))))
        overlay()
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp,
                color = if (onDark) Color.White else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.sp,
                color = if (onDark) Color.White.copy(alpha = 0.82f) else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A white pill on a card's picture: an icon and a word in [color]. */
@Composable
internal fun BannerPill(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(10.dp).shadow(2.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(Color.White).padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = color, maxLines = 1)
    }
}

/** Behind a place's picture while it loads, and where it doesn't reach: the pictures' own dark background. */
private val PlacePictureNight = Color(0xFF0D1015)
