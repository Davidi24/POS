package com.saporini.mobile_desktop.pos.menu.ui.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.pos.menu.ui.menu.isPhoneMenuWindow
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.auth_login_img
import org.jetbrains.compose.resources.painterResource

private val ActiveOlive = Color(0xFF94A27F)
private val TextInk = Color(0xFF222426)
private val MutedInk = Color(0xFF747572)
private val Border = Color(0xFFE8E5E1)
private val ItemDetailSurface = Color(0xFFF7F7F5)
private val ItemDetailEmptyText = Color(0xFFC7C8C2)

@Composable
internal fun ItemDetailDialog(
    item: MenuItem,
    onDismiss: () -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        if (isPhone) {
            PhoneItemDetails(item = item, onDismiss = onDismiss)
        } else {
            DesktopItemDetails(item = item, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun DesktopItemDetails(item: MenuItem, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .widthIn(min = 760.dp, max = 920.dp)
                .fillMaxHeight(0.82f)
                .shadow(24.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, Border, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(242.dp)
                        .height(162.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Image(
                        painter = painterResource(Res.drawable.auth_login_img),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    AvailabilityButton(
                        available = item.available,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = item.category.uppercase(),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            color = Color.White.copy(alpha = 0.74f)
                        )
                        Text(
                            text = item.name,
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            lineHeight = 20.sp,
                            color = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "ITEM DETAILS",
                                fontFamily = Inter(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                                color = ActiveOlive
                            )
                            Text(
                                text = item.category + (item.sku?.let { "  •  SKU $it" } ?: ""),
                                fontFamily = Inter(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MutedInk
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ItemDetailSurface)
                                .border(1.dp, Border, RoundedCornerShape(50))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(20.dp),
                                tint = TextInk
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.name,
                            modifier = Modifier.weight(1f),
                            fontFamily = Inter(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            color = TextInk
                        )
                        Spacer(Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF1F4EC))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = item.price,
                                fontFamily = Inter(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = TextInk
                            )
                        }
                    }

                    Text(
                        text = item.description.ifBlank { "No description added." },
                        fontFamily = Inter(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = if (item.description.isNotBlank()) MutedInk else ItemDetailEmptyText
                    )
                }
            }

            HorizontalDivider(color = Border)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                DesktopDetailPanel(
                    title = "Recipe / Ingredients",
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                ) {
                    if (item.ingredients.isEmpty()) {
                        DesktopDetailEmptyText("No ingredients added.")
                    } else {
                        item.ingredients.forEach { ingredient ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.auth_login_img),
                                    contentDescription = ingredient.name,
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 34.dp)
                                        .clip(RoundedCornerShape(7.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = ingredient.name,
                                    modifier = Modifier.weight(1f),
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = TextInk
                                )
                                val amount = "${ingredient.quantity} ${ingredient.unit}".trim()
                                if (amount.isNotBlank()) {
                                    Text(
                                        text = amount,
                                        fontFamily = Inter(),
                                        fontSize = 12.sp,
                                        color = MutedInk
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1.35f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DesktopDetailPanel(title = "Variants") {
                        if (item.variants.isEmpty()) {
                            DesktopDetailEmptyText("No variants added.")
                        } else {
                            item.variants.forEach { variant ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color.White)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = variant.name,
                                        modifier = Modifier.weight(1f),
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = TextInk
                                    )
                                    if (variant.priceDeltaLabel.isNotBlank()) {
                                        Text(
                                            text = variant.priceDeltaLabel,
                                            fontFamily = Inter(),
                                            fontSize = 12.sp,
                                            color = MutedInk
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DesktopDetailPanel(title = "Options") {
                        if (item.optionGroups.isEmpty()) {
                            DesktopDetailEmptyText("No options added.")
                        } else {
                            item.optionGroups.forEach { group ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color.White)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = group.name,
                                            modifier = Modifier.weight(1f),
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = TextInk
                                        )
                                        Text(
                                            text = if (group.required) "Required" else "Optional",
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = if (group.required) ActiveOlive else MutedInk
                                        )
                                    }
                                    if (group.choices.isEmpty()) {
                                        DesktopDetailEmptyText("No choices shown.")
                                    } else {
                                        Text(
                                            text = group.choices.joinToString(separator = "  •  ") { choice ->
                                                if (choice.priceDeltaLabel.isNotBlank()) {
                                                    "${choice.name} (${choice.priceDeltaLabel})"
                                                } else {
                                                    choice.name
                                                }
                                            },
                                            fontFamily = Inter(),
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = MutedInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopDetailPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ItemDetailSurface)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ItemDetailSectionLabel(title)
        content()
    }
}

@Composable
private fun DesktopDetailEmptyText(text: String) {
    Text(
        text = text,
        fontFamily = Inter(),
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = ItemDetailEmptyText
    )
}

@Composable
private fun PhoneItemDetails(item: MenuItem, onDismiss: () -> Unit) {
    val isWidePhone = isWidePhoneWindow()
    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (isWidePhone) 48.dp else 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to menu items", tint = TextInk)
            }
            Text("Item details", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextInk)
        }
        HorizontalDivider(color = Border)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(if (isWidePhone) 116.dp else 150.dp).clip(RoundedCornerShape(12.dp))) {
                Image(
                    painter = painterResource(Res.drawable.auth_login_img),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                AvailabilityButton(
                    available = item.available,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.name, fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = 24.sp, lineHeight = 29.sp, color = TextInk
                )
                Text(
                    item.category + (item.sku?.let { "  •  SKU $it" } ?: ""),
                    fontFamily = Inter(), fontSize = 12.sp, color = MutedInk
                )
                Text(item.price, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 21.sp, color = TextInk)
            }
            PhoneItemDetailSection("Description") {
                PhoneItemDetailText(item.description.ifBlank { "No description added." })
            }
            PhoneItemDetailSection("Recipe / Ingredients") {
                if (item.ingredients.isEmpty()) {
                    PhoneItemDetailText("No ingredients added.")
                } else {
                    item.ingredients.forEach { ingredient ->
                        val amount = "${ingredient.quantity} ${ingredient.unit}".trim()
                        PhoneItemDetailText(if (amount.isBlank()) ingredient.name else "${ingredient.name} • $amount")
                    }
                }
            }
            PhoneItemDetailSection("Variants") {
                if (item.variants.isEmpty()) {
                    PhoneItemDetailText("No variants added.")
                } else {
                    item.variants.forEach { variant ->
                        PhoneItemDetailText(
                            variant.name + variant.priceDeltaLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        )
                    }
                }
            }
            PhoneItemDetailSection("Options") {
                if (item.optionGroups.isEmpty()) {
                    PhoneItemDetailText("No options added.")
                } else {
                    item.optionGroups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                group.name + if (group.required) " • Required" else " • Optional",
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk
                            )
                            group.choices.forEach { choice ->
                                PhoneItemDetailText(
                                    choice.name + choice.priceDeltaLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneItemDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ItemDetailSurface, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ItemDetailSectionLabel(title)
        content()
    }
}

@Composable
private fun PhoneItemDetailText(text: String) {
    Text(text, fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = MutedInk)
}

@Composable
private fun ItemDetailSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = Inter(),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = MutedInk
    )
}
