package com.saporini.mobile_desktop.workspace.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.session.BranchScope
import com.saporini.mobile_desktop.core.session.ScopeBranch
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.Workspace
import com.saporini.mobile_desktop.core.session.accessibleWorkspaces
import com.saporini.mobile_desktop.core.session.globalLevel
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.hours.OpenNowDto
import com.saporini.mobile_desktop.hours.OpeningHoursApi
import com.saporini.mobile_desktop.hours.zoneOf
import com.saporini.mobile_desktop.platform.BranchCardDto
import com.saporini.mobile_desktop.platform.BranchListScreenModel
import com.saporini.mobile_desktop.platform.PlatformRepository
import com.saporini.mobile_desktop.platform.ui.CoverBackground
import com.saporini.mobile_desktop.platform.ui.RestaurantLogo
import com.saporini.mobile_desktop.platform.ui.accentOf
import com.saporini.mobile_desktop.platform.ui.nowLine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.compose.koinInject

/**
 * Where an owner, co-owner or admin lands after signing in when the restaurant has several branches (user, 2026-11-23:
 * "make this page better; two sections: the branches, and what can be had for all branches, like statistics"): the
 * restaurant on its picture, then "Work in a branch" (a card per branch, Work here opens the workspaces there) and "The whole
 * restaurant" (each workspace that has a level for every branch, opened straight at it). One branch: straight on to the
 * workspaces.
 */
data class RestaurantHomeScreen(val restaurantId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val session = koinInject<SessionManager>()
        val model = koinInject<BranchListScreenModel>()
        BindToLifecycle(model::setActive, model::onDispose)
        LaunchedEffect(restaurantId) { model.show(restaurantId, "") }
        val state by model.state.collectAsState()
        val user by session.currentUser.collectAsState()
        val authenticated by session.isAuthenticated.collectAsState()
        val workspaceModel = koinInject<WorkspaceScreenModel>()
        val workspaceState by workspaceModel.state.collectAsState()
        LaunchedEffect(authenticated, workspaceState.loggedOut) { if (!authenticated || workspaceState.loggedOut) navigator.replaceAll(LoginScreen) }
        // One branch (or no right to see them): nothing to choose.
        LaunchedEffect(state.loaded, state.allBranches, state.canRead, user?.id) {
            val person = user ?: return@LaunchedEffect
            if (!state.canRead || (state.loaded && state.allBranches <= 1)) navigator.replaceAll(workspaceStart(person))
        }
        // The bar's branch switcher knows the branches from here on.
        LaunchedEffect(state.cards) {
            if (state.cards.isNotEmpty()) BranchScope.loaded(restaurantId, state.cards.sortedWith(compareByDescending<BranchCardDto> { it.open }
                .thenBy { it.name.lowercase() }).map { ScopeBranch(it.id, it.name, it.code, it.open) })
        }
        val repository = koinInject<PlatformRepository>()
        val logo by produceState<String?>(null, restaurantId) { value = runCatching { repository.branding(restaurantId)?.logoUrl }.getOrNull() }
        val hours = remember { org.koin.mp.KoinPlatform.getKoinOrNull()?.getOrNull<OpeningHoursApi>() }
        val openNow by produceState(emptyMap<String, OpenNowDto>(), state.cards.map { it.id }) {
            if (hours == null) return@produceState
            value = coroutineScope {
                state.cards.map { card -> async { card.id to runCatching { hours.now(restaurantId, card.id) }.getOrNull() } }.awaitAll()
            }.mapNotNull { (id, now) -> now?.let { id to it } }.toMap()
        }
        val restaurantZone = produceState<String?>(null, restaurantId) { value = runCatching { repository.restaurant(restaurantId).timezone }.getOrNull() }.value
        var comingSoon by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        val everywhere = Workspace.entries.filter { it.globalLevel != null && user?.let { u -> it in accessibleWorkspaces(u) } == true }

        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB))) {
            val narrow = maxWidth < 760.dp
            val columns = when {
                maxWidth >= 1080.dp -> 3
                maxWidth >= 640.dp -> 2
                else -> 1
            }
            PageDecorations(Modifier.fillMaxSize(), narrow)
            val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(narrow)
            Column(Modifier.fillMaxSize()) {
                com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                    PickerTopBar(user, workspaceState.isLoggingOut, onLogout = workspaceModel::logout, compact = narrow)
                }
                Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection), contentAlignment = Alignment.TopCenter) {
                    ScrollColumn(Modifier.widthIn(max = 1240.dp).fillMaxSize().padding(horizontal = if (narrow) 16.dp else 36.dp),
                        verticalArrangement = Arrangement.spacedBy(if (narrow) 18.dp else 22.dp)) {
                        Spacer(Modifier.height(if (narrow) 4.dp else 10.dp))
                        HomeHero(state.restaurantName, logo, state.restaurantCoverUrl, user?.firstName?.trim()?.ifBlank { null } ?: user?.username.orEmpty(),
                            state.allBranches, state.openBranches, narrow)
                        // Work in a branch.
                        SectionTitle(Icons.Outlined.Place, "Work in a branch", "Its orders, kitchen, stock, staff and shifts", narrow) {
                            RefreshButton(state.loading, model::refresh)
                        }
                        if (!state.loaded) {
                            Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                        } else {
                            state.cards.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    row.forEach { card ->
                                        BranchHomeCard(card, openNow[card.id], zoneOf(restaurantZone), home = card.id == session.homeBranchId,
                                            entering = state.entering == card.id, enabled = state.entering == null && state.restaurantOpen,
                                            modifier = Modifier.weight(1f).fillMaxHeight()) {
                                            model.choose(card) { navigator.push(WorkspacePickerScreen) }
                                        }
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        // The whole restaurant (the user's table: each workspace's level for every branch).
                        if (everywhere.isNotEmpty()) {
                            SectionTitle(Icons.Outlined.Public, "The whole restaurant",
                                if (com.saporini.mobile_desktop.core.session.ALL_BRANCHES_ON) {
                                    if (state.allBranches > 0) "All ${state.allBranches} branches together" else "Every branch together"
                                } else "Every branch together · coming soon", narrow) {}
                            // Laid out exactly as on "Choose your workspace" (user, 2026-11-23: "in the centre, exactly as inside a
                            // branch"): at most three a row, each row centred, the picker's small cards; one under the other on a phone.
                            val soon = !com.saporini.mobile_desktop.core.session.ALL_BRANCHES_ON
                            val card: @Composable (Workspace, Modifier) -> Unit = { workspace, m ->
                                Box(m) {
                                    WorkspaceCard(workspace, Modifier.fillMaxSize().then(if (soon) Modifier.alpha(0.55f) else Modifier),
                                        compact = true, dense = true, smallFive = !narrow, line = workspace.globalLevel) {
                                        if (soon) comingSoon = "Every branch together comes soon. For now, work in one branch at a time."
                                        else {
                                            BranchScope.showAll(workspace, true)
                                            navigator.push(screenFor(workspace))
                                        }
                                    }
                                    if (soon) Text("Coming soon", Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50))
                                        .background(Kit.Amber).padding(horizontal = 10.dp, vertical = 4.dp), fontFamily = Inter(),
                                        fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                                }
                            }
                            if (narrow) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    everywhere.forEach { card(it, Modifier.widthIn(max = 440.dp).fillMaxWidth()) }
                                }
                            } else {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    everywhere.chunked(3).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            row.forEach { card(it, Modifier.width(236.dp).height(212.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(36.dp))
                    }
                }
            }
        }
        ScreenMessages(state.error.takeIf { state.loaded }, state.notice, model::clearMessages)
        com.saporini.mobile_desktop.core.components.BottomToast(comingSoon, isTip = true) { comingSoon = null }
    }
}

/** The restaurant on its own picture: the logo, "Welcome back, <name>", its name and how many branches are open. */
@Composable
private fun HomeHero(name: String, logo: String?, cover: String?, person: String, branches: Long, open: Long, narrow: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().height(if (narrow) 170.dp else 220.dp).shadow(4.dp, shape).clip(shape)) {
        CoverBackground(cover, accentOf(name.ifBlank { "restaurant" }))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6101410), Color(0x99101410), Color(0x22101410)))))
        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = if (narrow) 18.dp else 32.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 14.dp else 22.dp)) {
            Box(Modifier.shadow(10.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(4.dp)) {
                RestaurantLogo(logo, name.ifBlank { "Restaurant" }, if (narrow) 64.dp else 92.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 6.dp)) {
                Text(if (person.isBlank()) "Welcome back" else "Welcome back, $person", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                    fontSize = if (narrow) 13.sp else 15.sp, color = Color.White.copy(alpha = 0.85f))
                Text(name.ifBlank { " " }, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 24.sp else 34.sp,
                    letterSpacing = (-0.4).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                if (branches > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip(Icons.Outlined.Storefront, if (branches == 1L) "1 branch" else "$branches branches")
                    HeroChip(null, if (open == branches) "All open" else "$open open", dot = if (open > 0) Color(0xFF7BD389) else Color(0xFFE57373))
                }
            }
        }
    }
}

@Composable
private fun HeroChip(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, dot: Color? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = Color.White) }
        dot?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White)
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, note: String, narrow: Boolean, end: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(if (narrow) 36.dp else 42.dp).clip(RoundedCornerShape(12.dp)).background(Kit.GreenSoft), Alignment.Center) {
            Icon(icon, null, Modifier.size(if (narrow) 19.dp else 22.dp), tint = Kit.Green)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 18.sp else 21.sp, color = Kit.Ink)
            Text(note, fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        }
        end()
    }
}

/** A branch: its picture with whether it's open, the name, where it is, whether it's open now, its people and devices, Work here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BranchHomeCard(card: BranchCardDto, now: OpenNowDto?, zone: kotlinx.datetime.TimeZone, home: Boolean, entering: Boolean, enabled: Boolean,
                           modifier: Modifier, onWork: () -> Unit) {
    val usable = card.open && enabled
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered && usable) 14.dp else 2.dp, label = "home-branch-lift")
    val shape = RoundedCornerShape(20.dp)
    Surface(onClick = onWork, enabled = usable, interactionSource = interaction,
        modifier = modifier.shadow(lift, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000)),
        shape = shape, color = Color.White, border = BorderStroke(if (hovered && usable) 1.5.dp else 1.dp, if (hovered && usable) Kit.Green.copy(alpha = 0.5f) else Kit.Border)) {
        Column {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                CoverBackground(card.coverUrl, accentOf(card.id))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))))
                Pill(if (card.open) "Open" else "Closed", if (card.open) Kit.Green else Kit.Grey, Modifier.align(Alignment.TopStart).padding(12.dp))
                if (home) Pill("Your branch", Kit.Blue, Modifier.align(Alignment.TopEnd).padding(12.dp))
                Text(card.name, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), fontFamily = Inter(),
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Place, null, Modifier.size(15.dp), tint = Kit.Muted)
                    Text(listOfNotNull(card.street, card.city).joinToString(", ").ifBlank { "No address yet" }, fontFamily = Inter(), fontSize = 13.sp,
                        color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                }
                nowLine(now, zone)?.let { (text, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = color, maxLines = 1)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Fact(Icons.Outlined.Groups, if (card.staff == 1L) "1 person" else "${card.staff} staff")
                    Fact(Icons.Outlined.Devices, if (card.devices == 1L) "1 device" else "${card.devices} devices")
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (!usable) Kit.Tint else if (hovered) Kit.Green else Kit.GreenSoft),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (entering) CircularProgressIndicator(Modifier.size(18.dp), color = if (hovered) Color.White else Kit.Green, strokeWidth = 2.dp)
                    else {
                        Text(if (card.open) "Work here" else "Closed", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (!usable) Kit.Muted else if (hovered) Color.White else Kit.Green)
                        if (usable) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = if (hovered) Color.White else Kit.Green)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.95f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = color, maxLines = 1)
    }
}

@Composable
private fun Fact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(15.dp), tint = Kit.Muted)
        Text(text, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
    }
}
