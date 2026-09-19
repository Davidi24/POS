package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.CormorantGaramond
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.menu.domain.model.Menu
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource

private val PhoneCoverAccent = Color(0xFF94A27F)
private val PhoneCoverInk = Color(0xFF222426)

internal fun phoneMenuCoverHeight(availableHeight: Dp): Dp =
    (availableHeight - 24.dp).coerceAtLeast(360.dp) * 0.9f

internal fun phoneMenuCoverWidth(availableWidth: Dp, coverHeight: Dp): Dp =
    if (availableWidth >= 600.dp) minOf(availableWidth * 0.4f, coverHeight * 0.62f) else availableWidth * 0.74f

@Composable
internal fun PhoneMenuBookCover(
    menu: Menu,
    theme: MenuCoverTheme,
    isReordering: Boolean,
    isDragging: Boolean,
    canManageMenus: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDescription by remember(menu.id) { mutableStateOf(false) }
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        label = "phone-cover-drag-${menu.id}"
    )
    val shape = RoundedCornerShape(12.dp)

    BoxWithConstraints(modifier) {
        val scale = minOf(maxWidth.value / 292f, maxHeight.value / 580f).coerceIn(0.4f, 1.35f)
        val spineWidth = maxWidth * 0.136f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .shadow(if (isDragging) 14.dp else 6.dp, shape)
                .clip(shape)
                .then(
                    if (isReordering) Modifier.border(2.dp, PhoneCoverAccent, shape) else Modifier
                )
                .clickable(enabled = !isReordering, onClick = onClick)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(theme.background)
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = theme.texture.copy(alpha = 0.13f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 0.5.dp.toPx()
                    )
                    x += 4.dp.toPx()
                }
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.09f), Color.Transparent, Color.Black.copy(alpha = 0.025f))
                    )
                )
            }
            Box(
                modifier = Modifier
                    .width(spineWidth)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF151616), Color(0xFF353735), Color(0xFF121313))
                        )
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stitchX = size.width - 5.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(stitchX, 0f),
                        end = Offset(stitchX, size.height),
                        strokeWidth = 0.7.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = spineWidth + 20.dp * scale, end = 24.dp * scale, bottom = 32.dp * scale),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(76.dp * scale))
                Image(
                    painter = painterResource(Res.drawable.brand_logo),
                    contentDescription = "Saporini Italiano",
                    modifier = Modifier.fillMaxWidth(0.82f).height(88.dp * scale),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(42.dp * scale))
                Text(
                    text = coverTitle(menu.name),
                    fontFamily = CormorantGaramond(),
                    fontWeight = FontWeight.Bold,
                    fontSize = (46f * scale).sp,
                    lineHeight = (46f * scale).sp,
                    letterSpacing = 0.4.sp,
                    color = theme.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp * scale)
                ) {
                    PhoneCoverMetadata(Icons.Outlined.AccessTime, coverSchedule(menu), theme.ink, scale)
                    val itemCount = menu.itemCount ?: 0
                    PhoneCoverMetadata(
                        Icons.AutoMirrored.Outlined.FormatListBulleted,
                        "$itemCount ${if (itemCount == 1) "item" else "items"}",
                        theme.ink,
                        scale
                    )
                }
                Spacer(Modifier.height(26.dp * scale))
                HorizontalDivider(color = theme.ink.copy(alpha = 0.7f))
                Spacer(Modifier.height(24.dp * scale))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OPEN MENU",
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Bold,
                        fontSize = (14f * scale).sp,
                        lineHeight = (22f * scale).sp,
                        color = theme.ink
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp * scale),
                        tint = theme.ink
                    )
                }
            }

            if (!isReordering) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canManageMenus) {
                        PhoneCoverAction(Icons.Outlined.Edit, "Edit ${menu.name}", theme.ink, onEdit)
                    }
                    if (!menu.description.isNullOrBlank()) {
                        PhoneCoverAction(Icons.Outlined.Info, "About ${menu.name}", theme.ink) {
                            showDescription = true
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.DragIndicator, null, Modifier.size(18.dp), tint = PhoneCoverInk)
                    Text(
                        text = if (isDragging) "Moving..." else "Hold & drag",
                        fontFamily = Inter(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PhoneCoverAccent
                    )
                }
            }
        }
    }
    if (showDescription) {
        MenuDescriptionDialog(menu = menu, onDismiss = { showDescription = false })
    }
}

@Composable
private fun PhoneCoverAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = color)
    }
}

@Composable
private fun PhoneCoverMetadata(icon: ImageVector, text: String, color: Color, scale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp * scale)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp * scale), tint = color)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = (14f * scale).sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PhoneMenuCoverSkeletons(availableWidth: Dp, availableHeight: Dp) {
    val coverHeight = phoneMenuCoverHeight(availableHeight)
    val coverWidth = phoneMenuCoverWidth(availableWidth, coverHeight)
    val verticalPadding = if (availableHeight > coverHeight) {
        ((availableHeight - coverHeight) / 2f).coerceAtLeast(4.dp)
    } else {
        4.dp
    }
    val scale = (coverHeight.value / 580f).coerceIn(0.4f, 1.35f)
    val transition = rememberInfiniteTransition(label = "phone-menu-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "phone-menu-skeleton-alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState(), enabled = false)
            .padding(start = 20.dp, end = 20.dp, top = verticalPadding, bottom = verticalPadding)
            .alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(coverWidth)
                    .height(coverHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F1EE))
            ) {
                Box(Modifier.fillMaxWidth(0.136f).fillMaxHeight().background(Color(0xFFD1D3CF)))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 60.dp * scale,
                            end = 24.dp * scale,
                            top = 64.dp * scale,
                            bottom = 32.dp * scale
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth(0.75f).height(62.dp * scale).background(Color(0xFFE0E2DC), RoundedCornerShape(8.dp)))
                    Spacer(Modifier.height(40.dp * scale))
                    repeat(2) {
                        Box(Modifier.fillMaxWidth().height(28.dp * scale).background(Color(0xFFE0E2DC), RoundedCornerShape(5.dp)))
                        Spacer(Modifier.height(8.dp * scale))
                    }
                    Spacer(Modifier.weight(1f))
                    repeat(2) {
                        Box(Modifier.fillMaxWidth().height(16.dp * scale).background(Color(0xFFE0E2DC), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(18.dp * scale))
                    }
                    HorizontalDivider(color = Color(0xFFDADDD5))
                    Spacer(Modifier.height(24.dp * scale))
                    Box(Modifier.fillMaxWidth(0.65f).height(18.dp * scale).background(Color(0xFFE0E2DC), RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
