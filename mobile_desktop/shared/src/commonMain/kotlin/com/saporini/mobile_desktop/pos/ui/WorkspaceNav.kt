package com.saporini.mobile_desktop.pos.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.pos_simple_logo
import org.jetbrains.compose.resources.painterResource

// One navigation bar for every workspace (user rule: "use the POS one for all, just with different names, icons and
// screens"). POS, KDS, the Admin Hub, Inventory, Statistics, Fraud Detection and Restaurant all pass their own entries.

/** One place a workspace's bar opens: its name, its icon, and the filled icon shown when it's chosen on a phone. */
internal data class NavEntry<K>(val key: K, val label: String, val icon: ImageVector, val filledIcon: ImageVector = icon)

private val NavActive = Color(0xFF4F7942)
private val NavInk = Color(0xFF202426)

@Composable
private fun WorkspaceLogo() {
    Image(painterResource(Res.drawable.pos_simple_logo), "Saporini", Modifier.size(82.dp), contentScale = ContentScale.Fit)
}

/**
 * The top bar (desktop and tablet), the way POS works: the first [fixed] entries always show; the next place shows the
 * entry picked last from More, so the page that's open is always in the bar; More lists the rest. With no more entries
 * than [fixed] + 1, everything sits in the bar and there's no More.
 */
@Composable
internal fun <K> WorkspaceNavTopBar(
    entries: List<NavEntry<K>>,
    fixed: Int,
    selected: K,
    onSelect: (K) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
    logo: @Composable () -> Unit = { WorkspaceLogo() }
) {
    val head = entries.take(fixed)
    val rotating = entries.drop(fixed)
    // The place after the fixed ones: the first of the rest, until something else is picked from More.
    var slot by remember(rotating.map { it.key }) { mutableStateOf(rotating.firstOrNull()?.key) }
    // A page opened some other way (a link, a button on a page) also takes the place, so it shows as chosen.
    LaunchedEffect(selected) { if (rotating.any { it.key == selected }) slot = selected }
    val slotEntry = rotating.firstOrNull { it.key == slot } ?: rotating.firstOrNull()
    // More keeps its order; the first entry steps into the spot of the one now in the bar.
    val more = if (slotEntry == null) emptyList() else rotating.drop(1).map { if (it.key == slotEntry.key) rotating.first() else it }

    WorkspaceTopBar(onLogout = onLogout, logo = logo, onBackToWorkspaces = onBackToWorkspaces) {
        head.forEach { entry -> TopBarNavItem(entry.label, entry.icon, selected == entry.key, { onSelect(entry.key) }) }
        slotEntry?.let { entry -> TopBarNavItem(entry.label, entry.icon, selected == entry.key, { onSelect(entry.key) }) }
        if (more.isNotEmpty()) {
            MoreNavMenu(more.map { it.label }, more.indexOfFirst { it.key == selected }) { index ->
                val picked = more[index].key
                slot = picked
                onSelect(picked)
            }
        }
    }
}

/**
 * The bottom bar on phones: the first [phoneFixed] entries, then More with the rest, Switch workspace and Sign out.
 * [withMore] false leaves More out (Inventory: its two places only; back and sign out are in its top bar).
 */
@Composable
internal fun <K> WorkspaceNavBottomBar(
    entries: List<NavEntry<K>>,
    selected: K,
    onSelect: (K) -> Unit,
    onLogout: () -> Unit,
    onBackToWorkspaces: (() -> Unit)? = null,
    phoneFixed: Int = 4,
    withMore: Boolean = true,
    // A lower bar (56 instead of 68).
    compact: Boolean = false
) {
    val shown = entries.take(phoneFixed)
    val more = entries.drop(phoneFixed)
    val barHeight = if (compact) 56.dp else 68.dp
    WorkspaceBottomBar(
        withMore = withMore,
        barHeight = barHeight,
        moreSelected = more.any { it.key == selected },
        onLogout = onLogout,
        onBackToWorkspaces = onBackToWorkspaces,
        moreItems = { closeMenu ->
            more.forEach { entry ->
                val chosen = entry.key == selected
                DropdownMenuItem(
                    text = {
                        Text(entry.label, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp,
                            letterSpacing = 0.sp, color = if (chosen) NavActive else NavInk)
                    },
                    leadingIcon = { Icon(if (chosen) entry.filledIcon else entry.icon, null, Modifier.size(20.dp), tint = if (chosen) NavActive else NavInk) },
                    onClick = { closeMenu(); onSelect(entry.key) }
                )
            }
        }
    ) {
        shown.forEach { entry ->
            PhoneNavItem(
                modifier = Modifier.weight(1f),
                height = barHeight,
                label = entry.label,
                icon = if (selected == entry.key) entry.filledIcon else entry.icon,
                selected = selected == entry.key,
                onClick = { onSelect(entry.key) }
            )
        }
    }
}
