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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .widthIn(min = 760.dp, max = 980.dp)
                        .fillMaxHeight(0.84f)
                        .shadow(28.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .background(Color(0xFF171914))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
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
                                    .padding(18.dp)
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .padding(horizontal = 22.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "MENU PREVIEW",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.2.sp,
                                    color = Color.White.copy(alpha = 0.68f)
                                )
                                Text(
                                    text = item.category,
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.White)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            ItemDetailSectionLabel(text = "Recipe / Ingredients")
                            if (item.ingredients.isEmpty()) {
                                Text(
                                    text = "No ingredients added.",
                                    fontFamily = Inter(),
                                    fontSize = 13.sp,
                                    color = ItemDetailEmptyText
                                )
                            } else {
                                item.ingredients.forEach { ingredient ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ItemDetailSurface)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource(Res.drawable.auth_login_img),
                                            contentDescription = ingredient.name,
                                            modifier = Modifier
                                                .size(width = 44.dp, height = 36.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = ingredient.name,
                                            modifier = Modifier.weight(1f),
                                            fontFamily = Inter(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = TextInk
                                        )
                                        Text(
                                            text = "${ingredient.quantity} ${ingredient.unit}".trim(),
                                            fontFamily = Inter(),
                                            fontSize = 11.sp,
                                            color = MutedInk
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .background(Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "ITEM DETAILS",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp,
                                    color = ActiveOlive
                                )
                                Text(
                                    text = "Menu item overview",
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = MutedInk
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ItemDetailSurface)
                                    .border(1.dp, Border, RoundedCornerShape(50))
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp),
                                    tint = TextInk
                                )
                            }
                        }

                        HorizontalDivider(color = Border)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 25.sp,
                                        lineHeight = 29.sp,
                                        color = TextInk
                                    )
                                    Text(
                                        text = item.category + (item.sku?.let { "  •  SKU $it" } ?: ""),
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MutedInk
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF1F4EC))
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                ) {
                                    Text(
                                        text = item.price,
                                        fontFamily = Inter(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = TextInk
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ItemDetailSurface)
                                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                                    .padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ItemDetailSectionLabel(text = "Description")
                                Text(
                                    text = item.description.ifBlank { "No description added." },
                                    fontFamily = Inter(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = if (item.description.isNotBlank()) MutedInk else ItemDetailEmptyText
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ItemDetailSurface)
                                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                ItemDetailSectionLabel(text = "Variants")
                                if (item.variants.isEmpty()) {
                                    Text(
                                        text = "No variants added.",
                                        fontFamily = Inter(),
                                        fontSize = 13.sp,
                                        color = ItemDetailEmptyText
                                    )
                                } else {
                                    item.variants.forEach { variant ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = variant.name,
                                                modifier = Modifier.weight(1f),
                                                fontFamily = Inter(),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = TextInk
                                            )
                                            if (variant.priceDeltaLabel.isNotBlank()) {
                                                Text(
                                                    text = variant.priceDeltaLabel,
                                                    fontFamily = Inter(),
                                                    fontSize = 11.sp,
                                                    color = MutedInk
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ItemDetailSurface)
                                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                ItemDetailSectionLabel(text = "Options")
                                if (item.optionGroups.isEmpty()) {
                                    Text(
                                        text = "No options added.",
                                        fontFamily = Inter(),
                                        fontSize = 13.sp,
                                        color = ItemDetailEmptyText
                                    )
                                } else {
                                    item.optionGroups.forEach { group ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .padding(horizontal = 11.dp, vertical = 9.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                                    fontSize = 12.sp,
                                                    color = TextInk
                                                )
                                                Text(
                                                    text = if (group.required) "Required" else "Optional",
                                                    fontFamily = Inter(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 10.sp,
                                                    color = if (group.required) ActiveOlive else MutedInk
                                                )
                                            }
                                            Text(
                                                text = group.choices.joinToString(separator = "  •  ") { choice ->
                                                    if (choice.priceDeltaLabel.isNotBlank()) {
                                                        "${choice.name} (${choice.priceDeltaLabel})"
                                                    } else {
                                                        choice.name
                                                    }
                                                },
                                                fontFamily = Inter(),
                                                fontSize = 11.sp,
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
            Box(Modifier.fillMaxWidth().height(if (isWidePhone) 144.dp else 190.dp).clip(RoundedCornerShape(12.dp))) {
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
