package com.saporini.mobile_desktop.account.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.theme.Inter

/** Who is signed in, as the menu and Profile show them: the person and the restaurant and branch they work in now. */
data class AccountInfo(val user: CurrentUserResponse?, val restaurantName: String? = null, val branchName: String? = null)

/** Provided by the app; empty in look checks. */
internal val LocalAccount = staticCompositionLocalOf { AccountInfo(null) }

internal fun displayName(user: CurrentUserResponse?): String =
    user?.let { "${it.firstName} ${it.lastName}".trim().ifBlank { it.username } } ?: "Signed in"

internal fun initialsOf(user: CurrentUserResponse?): String =
    user?.let { listOf(it.firstName, it.lastName).mapNotNull { part -> part.firstOrNull()?.uppercaseChar() }.joinToString("") }
        ?.ifBlank { user.username.firstOrNull()?.uppercase() } ?: "?"

/** "Owner", "Co owner", "Kitchen" from the first role code. */
internal fun roleLabel(user: CurrentUserResponse?): String? =
    user?.roles?.firstOrNull()?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }

internal fun placeLine(account: AccountInfo): String? =
    listOfNotNull(account.restaurantName, account.branchName).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** The person's initials on a green circle. */
@Composable
internal fun AccountAvatar(user: CurrentUserResponse?, size: Dp, fontSize: TextUnit, photoUrl: String? = user?.photoUrl) {
    val initials: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color(0xFF5E8C50), Color(0xFF3E6534)))), contentAlignment = Alignment.Center) {
            Text(initialsOf(user), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = fontSize, letterSpacing = 0.sp, color = Color.White)
        }
    }
    // The person's photo when they added one (Profile), their initials while it loads or without one.
    com.saporini.mobile_desktop.core.ui.RemoteImage(photoUrl, null, Modifier.size(size).clip(CircleShape), placeholder = initials)
}

/**
 * The menu behind the person's name in every top bar (user, 2026-11-18: "the menu has profile, log out, the restaurant's
 * name"): who they are, their role and restaurant, then Profile and Sign out. [compact] = only the picture.
 */
@Composable
internal fun AccountMenu(onLogout: () -> Unit, compact: Boolean, small: Boolean = false, loggingOut: Boolean = false) {
    val account = LocalAccount.current
    val user = account.user
    val navigator = LocalNavigator.current
    var open by remember { mutableStateOf(false) }
    val name = displayName(user)
    val role = roleLabel(user)
    val till by com.saporini.mobile_desktop.core.session.TokenStore.till.collectAsState()
    val session = org.koin.compose.koinInject<com.saporini.mobile_desktop.core.session.SessionManager>()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 8.dp)) {
    // On a till: lock it for the next person (back to the PIN pad; user, 2026-11-18).
    if (till != null) {
        Surface(
            onClick = { if (!loggingOut) session.signOut("LOCKED") },
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, Kit.Border)
        ) {
            Box(Modifier.size(if (small) 34.dp else 44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Lock, "Lock", Modifier.size(if (small) 17.dp else 20.dp), tint = Kit.Ink)
            }
        }
    }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = Color.White,
            border = BorderStroke(1.dp, Kit.Border)
        ) {
            Row(
                Modifier.padding(start = if (small) 3.dp else 5.dp, end = if (compact) 8.dp else 12.dp, top = if (small) 3.dp else 5.dp,
                    bottom = if (small) 3.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 10.dp)
            ) {
                AccountAvatar(user, if (small) 28.dp else 34.dp, if (small) 11.sp else 13.sp)
                if (!compact) {
                    Column {
                        Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        role?.let { Text(it, fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1) }
                    }
                }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Kit.Ink)
            }
        }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false }, offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(14.dp), containerColor = Color.White, shadowElevation = 10.dp
        ) {
            Column(Modifier.width(284.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountAvatar(user, 44.dp, 15.sp)
                    Column(Modifier.weight(1f)) {
                        Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.sp, color = Kit.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        user?.email?.let {
                            Text(it, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    role?.let {
                        Text(it, Modifier.clip(RoundedCornerShape(50)).background(Kit.GreenSoft).padding(horizontal = 9.dp, vertical = 3.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.sp, color = Kit.Green)
                    }
                }
                placeLine(account)?.let { place ->
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Storefront, null, Modifier.size(15.dp), tint = Kit.Muted)
                        Text(place, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider(color = Kit.Border)
            DropdownMenuItem(
                text = { Text("Profile", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink) },
                leadingIcon = { Icon(Icons.Outlined.PersonOutline, null, Modifier.size(19.dp), tint = Kit.Ink) },
                enabled = navigator != null && user != null,
                onClick = {
                    open = false
                    // The app's own navigator, so Profile opens as a page of its own whatever workspace it's opened from.
                    navigator?.let { generateSequence(it) { n -> n.parent }.last().push(ProfileScreen) }
                }
            )
            HorizontalDivider(color = Kit.Border)
            DropdownMenuItem(
                text = { Text(if (loggingOut) "Signing out…" else "Sign out", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    letterSpacing = 0.sp, color = Kit.Danger) },
                leadingIcon = {
                    if (loggingOut) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Kit.Danger)
                    else Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp), tint = Kit.Danger)
                },
                enabled = !loggingOut,
                onClick = { open = false; onLogout() }
            )
        }
    }
    }
}
