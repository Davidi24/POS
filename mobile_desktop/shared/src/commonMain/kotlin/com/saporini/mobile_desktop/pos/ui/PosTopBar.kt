package com.saporini.mobile_desktop.pos.ui.shell

import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.pos.ui.PosSection
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import org.jetbrains.compose.resources.painterResource

private val ActiveOlive = Color(0xFF4F7942)
private val Ink = Color(0xFF202426)
private val MutedInk = Color(0xFF3D4342)
private val OnlineGreen = Color(0xFF18C637)
private val AlertRed = Color(0xFFFF1414)
private val MenuSelectedBackground = Color(0xFFF3F5EF)

@Composable
fun PosPhoneTopBar(initials: String) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(Res.drawable.pos_simple_logo),
                contentDescription = "Saporini",
                modifier = Modifier.size(width = 24.dp, height = 40.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnlineStatus(textColor = ActiveOlive)
                LanguageSelector()
                com.saporini.mobile_desktop.notifications.NotificationBell(tint = MutedInk)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ActiveOlive),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFFEDEEEB))
    }
}

// How much room the top bar has. FULL is the desktop look and is never changed by the smaller tiers.
private enum class TopBarTier { FULL, MEDIUM, COMPACT }

private val LocalTopBarTier = androidx.compose.runtime.staticCompositionLocalOf { TopBarTier.FULL }

@Composable
fun PosTopBar(
    selected: PosSection,
    onSelect: (PosSection) -> Unit,
    onLogout: () -> Unit,
    logo: @Composable () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
) {
    // Tables, Orders and Reservations always show; the fourth place is Menu or whatever was picked last from More.
    WorkspaceNavTopBar(posNavEntries(), fixed = 3, selected = selected, onSelect = onSelect, onLogout = onLogout,
        onBackToWorkspaces = onBackToWorkspaces, logo = logo)
}

/** POS's places, in bar order: the first four on a phone, and Menu sits in the slot More rotates through. */
internal fun posNavEntries(): List<NavEntry<PosSection>> = PosSection.entries.map { NavEntry(it, it.label, it.icon(), it.filledPhoneIcon()) }

// Desktop top bar frame shared by the POS and KDS workspaces; each workspace passes in its own tabs.
@Composable
internal fun WorkspaceTopBar(
    onLogout: () -> Unit,
    logo: @Composable () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
    navItems: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val tier = when {
        maxWidth >= 1100.dp -> TopBarTier.FULL
        maxWidth >= 900.dp -> TopBarTier.MEDIUM
        else -> TopBarTier.COMPACT
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTopBarTier provides tier) {
    Surface(
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = if (tier == TopBarTier.FULL) 18.dp else 10.dp, end = if (tier == TopBarTier.FULL) 18.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Only for users with more than one workspace: back to "Choose your workspace".
                onBackToWorkspaces?.let { back ->
                    IconButton(onClick = back, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, "Back to workspaces", Modifier.size(26.dp), tint = Ink)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                Box(
                    modifier = Modifier.size(width = if (tier == TopBarTier.FULL) 52.dp else 40.dp, height = 42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    logo()
                }

                Spacer(Modifier.width(4.dp))
            }

            // The places take the room that's left after the controls on the right, which always keep their size; on a
            // window too narrow for all of them, the places slide sideways instead of squeezing the profile.
            Row(
                Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (tier == TopBarTier.COMPACT) 4.dp else 8.dp)
            ) {
                OnlineStatus(showLabel = tier == TopBarTier.FULL)
                // A small tablet (600–900 wide) needs the room for the places, so the language moves out of the bar there.
                if (tier != TopBarTier.COMPACT) LanguageSelector()
                com.saporini.mobile_desktop.notifications.NotificationBell(tint = MutedInk)
            }

            Spacer(Modifier.width(when (tier) { TopBarTier.FULL -> 14.dp; TopBarTier.MEDIUM -> 6.dp; TopBarTier.COMPACT -> 4.dp }))
            TopBarSeparator()
            Spacer(Modifier.width(when (tier) { TopBarTier.FULL -> 14.dp; TopBarTier.MEDIUM -> 6.dp; TopBarTier.COMPACT -> 4.dp }))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (tier == TopBarTier.COMPACT) 6.dp else 10.dp)
            ) {
                LogoutButton(onClick = onLogout)
                // The person's own menu (name, role, restaurant, Profile, Sign out), the same in every workspace (user, 2026-11-18).
                com.saporini.mobile_desktop.account.ui.AccountMenu(onLogout)
            }
        }
    }
    }
    }
}

@Composable
internal fun TopBarNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) ActiveOlive else Ink
    val tier = LocalTopBarTier.current

    if (tier == TopBarTier.COMPACT) {
        // Narrow window: icon with a small label underneath, like the phone bar.
        Box(Modifier.height(72.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = onClick,
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = color)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color)
                    Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = color, maxLines = 1)
                }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).width(64.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) ActiveOlive else Color.Transparent)
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .height(72.dp)
            .padding(horizontal = if (tier == TopBarTier.FULL) 6.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = onClick,
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = color)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (tier == TopBarTier.FULL) 10.dp else 7.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (tier == TopBarTier.FULL) 23.dp else 21.dp),
                    tint = color
                )
                Text(
                    text = label,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (tier == TopBarTier.FULL) 16.sp else 14.sp,
                    letterSpacing = 0.sp,
                    color = color
                )
            }
        }
        // The line under the open place sits a little above the bar's edge (user, 2026-11-01).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .width(if (tier == TopBarTier.FULL) 118.dp else 96.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) ActiveOlive else Color.Transparent)
        )
    }
}

/** "More ⌄" in the top bar and its menu: [labels] in order, the open one highlighted; [onPick] gets the index. */
@Composable
internal fun MoreNavMenu(labels: List<String>, selectedIndex: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val compact = LocalTopBarTier.current == TopBarTier.COMPACT
    Box(
        modifier = Modifier
            .height(72.dp)
            .padding(horizontal = if (LocalTopBarTier.current == TopBarTier.FULL) 6.dp else 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (compact) TextButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = null, modifier = Modifier.size(22.dp), tint = Ink)
                Text("More", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Ink, maxLines = 1)
            }
        } else TextButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "More",
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = when (LocalTopBarTier.current) { TopBarTier.FULL -> 16.sp; TopBarTier.MEDIUM -> 14.sp; TopBarTier.COMPACT -> 12.sp },
                    letterSpacing = 0.sp,
                    color = Ink
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Ink
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White),
            shape = RoundedCornerShape(10.dp),
            containerColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { MenuText(text = label, selected = index == selectedIndex, fontSize = 16) },
                    onClick = {
                        expanded = false
                        onPick(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationButton(count: Int) {
    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(
            onClick = {},
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = "Notifications",
                modifier = Modifier.size(30.dp),
                tint = MutedInk
            )
        }
        Badge(
            modifier = Modifier.offset(x = (-1).dp, y = 2.dp),
            containerColor = AlertRed,
            contentColor = Color.White
        ) {
            Text(
                text = count.toString(),
                fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun OnlineStatus(textColor: Color = Ink, showLabel: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(OnlineGreen)
        )
        if (showLabel) Text(
            text = "Online",
            fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.sp,
            color = textColor
        )
    }
}

@Composable
private fun TopBarSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(Color(0xFFE3E5E1))
    )
}


@Composable
internal fun LanguageSelector(inBar: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    // The language in use on this device (user, 2026-11-18: "a default in profile, then change it any time in the nav bar").
    val selectedLanguage by com.saporini.mobile_desktop.core.i18n.I18n.language.collectAsState()
    // With another currency chosen for amounts, its own sign shows beside the language (user, 2026-11-19: "make it look
    // better and show the special char when it has one"; no ≈ here, it takes room): [globe EN | RD$ v].
    val money = com.saporini.mobile_desktop.core.money.ShownMoney
    // Always the currency amounts show in (user, 2026-11-19: "it should always show"): the restaurant's own in grey, another
    // one chosen for amounts in green.
    val shownCode = money.restaurantCurrency?.let { home -> money.chosen ?: home }
    val converting = shownCode != null && shownCode != money.restaurantCurrency

    Box {
        if (inBar) {
            Row(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (expanded) MenuSelectedBackground else Color.White)
                    .border(1.dp, BarPillBorder, RoundedCornerShape(17.dp))
                    .clickable { expanded = true }
                    .padding(start = 10.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Language, null, Modifier.size(17.dp), tint = MutedInk)
                Spacer(Modifier.width(6.dp))
                androidx.compose.material3.Text(selectedLanguage.short, fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, letterSpacing = 0.sp, color = Ink)
                if (shownCode != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(1.dp).height(16.dp).background(BarPillBorder))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (converting) CurrencyTint else Color(0xFFF1F2EF))
                        .padding(horizontal = 7.dp, vertical = 2.dp)) {
                        androidx.compose.material3.Text(com.saporini.mobile_desktop.core.money.shownSymbol(shownCode), fontFamily = Inter(),
                            fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = if (converting) ActiveOlive else Ink, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(17.dp), tint = Ink)
            }
        } else TextButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MutedInk
                )
                androidx.compose.material3.Text(
                    text = selectedLanguage.short,
                    fontFamily = Inter(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    color = Ink
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = Ink
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White).widthIn(min = if (money.restaurantCurrency != null) 260.dp else 0.dp),
            shape = RoundedCornerShape(10.dp),
            containerColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            val money = com.saporini.mobile_desktop.core.money.ShownMoney
            val home = money.restaurantCurrency
            if (home != null) MenuCaption("Language")
            com.saporini.mobile_desktop.core.i18n.AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        MenuText(
                            text = language.nativeName,
                            selected = language == selectedLanguage,
                            translate = false
                        )
                    },
                    onClick = {
                        com.saporini.mobile_desktop.core.i18n.I18n.use(language)
                        expanded = false
                    }
                )
            }
            // Amounts also in a currency the person knows, only to see (user, 2026-11-19: "put the currency together
            // with the language"); signed in to a restaurant only. Payments always stay in the restaurant's currency.
            if (home != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFE3E5E1))
                MenuCaption("Show amounts in")
                DropdownMenuItem(
                    text = { CurrencyLine(home, "Restaurant's currency", money.chosen == null) },
                    onClick = { money.choose(null); expanded = false }
                )
                com.saporini.mobile_desktop.core.money.shownCurrencyChoices().forEach { code ->
                    DropdownMenuItem(
                        text = { CurrencyLine(code, com.saporini.mobile_desktop.platform.currencyName(code).ifBlank { code }, money.chosen == code) },
                        onClick = { money.choose(code); expanded = false }
                    )
                }
                if (money.chosen != null) {
                    val rate = com.saporini.mobile_desktop.core.money.shownRateLine()
                    MenuNote(when {
                        money.unavailable -> "No rate right now, so amounts show in $home."
                        rate != null -> "Approximate amounts, at $rate. Payments stay in $home."
                        else -> "Payments stay in $home."
                    })
                }
            }
        }
    }
}

private val BarPillBorder = Color(0xFFE3E5E1)
private val CurrencyTint = Color(0xFFE6EFE2)

/** A currency in the globe's menu: its sign in a small tile, its name, its code; the chosen one green with a tick. */
@Composable
private fun CurrencyLine(code: String, name: String, selected: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (selected) MenuSelectedBackground else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = 36.dp).height(26.dp).clip(RoundedCornerShape(7.dp))
                .background(if (selected) CurrencyTint else Color(0xFFF1F2EF)).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(com.saporini.mobile_desktop.core.money.shownSymbol(code), fontFamily = Inter(),
                fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = if (selected) ActiveOlive else Ink, maxLines = 1)
        }
        Text(name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp,
            color = if (selected) ActiveOlive else Ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        androidx.compose.material3.Text(code, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Color(0xFF8A8D89))
        Box(Modifier.size(16.dp)) {
            if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = ActiveOlive)
        }
    }
}

@Composable
private fun MenuCaption(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp), fontFamily = Inter(),
        fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.sp, color = Color(0xFF8A8D89))
}

@Composable
private fun MenuNote(text: String) {
    Text(text, Modifier.widthIn(max = 260.dp).padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 8.dp), fontFamily = Inter(),
        fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.sp, color = Color(0xFF747572))
}

@Composable
private fun MenuText(
    text: String,
    selected: Boolean,
    fontSize: Int = 14,
    // A language by its own name, never translated ("Italiano" stays "Italiano").
    translate: Boolean = true
) {
    Text(
        text = text,
        translate = translate,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MenuSelectedBackground else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        fontFamily = Inter(),
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize.sp,
        color = if (selected) ActiveOlive else Ink
    )
}

internal fun PosSection.icon(): ImageVector = when (this) {
    PosSection.TABLES -> Icons.Outlined.TableRestaurant
    PosSection.ORDERS -> Icons.AutoMirrored.Outlined.ReceiptLong
    PosSection.RESERVATIONS -> Icons.AutoMirrored.Outlined.EventNote
    PosSection.MENU -> Icons.Outlined.RestaurantMenu
    PosSection.KITCHEN_STATUS -> Icons.Outlined.RestaurantMenu
    PosSection.SHIFT -> Icons.AutoMirrored.Outlined.EventNote
    PosSection.MY_SALES -> Icons.AutoMirrored.Outlined.ReceiptLong
    PosSection.HISTORY -> Icons.AutoMirrored.Outlined.EventNote
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Logout,
            contentDescription = "Log out",
            modifier = Modifier.size(22.dp),
            tint = MutedInk
        )
    }
}

