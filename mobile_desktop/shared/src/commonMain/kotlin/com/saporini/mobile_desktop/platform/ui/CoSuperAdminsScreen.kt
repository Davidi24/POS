package com.saporini.mobile_desktop.platform.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.saporini.mobile_desktop.core.components.KitDivider
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import com.saporini.mobile_desktop.core.components.KitSwitch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.animation.core.tween
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.saporini.mobile_desktop.admin.people.MAX_PASSWORD
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.CellText
import com.saporini.mobile_desktop.core.components.CheckRow
import com.saporini.mobile_desktop.core.components.CheckState
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ListPanel
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.TableColumn
import com.saporini.mobile_desktop.core.components.TableHeader
import com.saporini.mobile_desktop.core.components.TableRow
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToolbarIconButton
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.CoSuperAdminsScreenModel
import com.saporini.mobile_desktop.platform.CoSuperAdminsState
import com.saporini.mobile_desktop.platform.ASSIGNED_EVERY
import com.saporini.mobile_desktop.platform.ASSIGNED_NONE
import com.saporini.mobile_desktop.platform.AdminStatus
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.NewCoSuperAdminDraft
import com.saporini.mobile_desktop.platform.PERMISSION_AREAS
import com.saporini.mobile_desktop.platform.PermissionTicks
import com.saporini.mobile_desktop.platform.coSuperAdminProblems
import com.saporini.mobile_desktop.platform.permissionArea
import org.koin.compose.koinInject
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.users_admin

/** Super Admin only: the people who help run every restaurant, and what each of them may do. */
object CoSuperAdminsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val model = koinInject<CoSuperAdminsScreenModel>()
        BindToLifecycle(model::setActive, model::onDispose)
        val state by model.state.collectAsState()
        val session = koinInject<com.saporini.mobile_desktop.core.session.SessionManager>()
        val user by session.currentUser.collectAsState()
        val workspaceModel = koinInject<com.saporini.mobile_desktop.workspace.ui.WorkspaceScreenModel>()
        val workspaceState by workspaceModel.state.collectAsState()
        androidx.compose.runtime.LaunchedEffect(workspaceState.loggedOut) {
            if (workspaceState.loggedOut) navigator?.replaceAll(com.saporini.mobile_desktop.auth.ui.login.LoginScreen)
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB))) {
            val narrow = maxWidth < 760.dp
            com.saporini.mobile_desktop.workspace.ui.PageDecorations(Modifier.fillMaxSize(), narrow)
            val hideBar = com.saporini.mobile_desktop.core.components.rememberHideOnScroll(narrow)
            Column(Modifier.fillMaxSize()) {
                com.saporini.mobile_desktop.core.components.HideOnScrollBar(hideBar) {
                    com.saporini.mobile_desktop.workspace.ui.PickerTopBar(user, workspaceState.isLoggingOut, workspaceModel::logout, compact = narrow)
                }
                CoSuperAdminsContent(state, model, onBack = { navigator?.pop() }, modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(hideBar.connection))
            }
        }
    }
}

@Composable
internal fun CoSuperAdminsContent(state: CoSuperAdminsState, model: CoSuperAdminsScreenModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val narrow = maxWidth < 760.dp
        val side = if (narrow) 16.dp else 36.dp
        ScreenMessages(state.error.takeIf { state.draft == null && state.ticks == null && state.loaded }, state.notice, model::clearMessages)
        // Whether the "Add support admin" tile at the end of the cards is in view; the top button shows only while it isn't.
        var addTileSeen by remember { mutableStateOf(false) }
        // Shapes like the admin cards until the first answer, held a moment so a quick load doesn't flash them, then a fade.
        val shapes = com.saporini.mobile_desktop.core.components.steadyLoading(
            state.allowed && ((!state.loaded && state.error == null) || (state.loading && state.people.isEmpty())))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.widthIn(max = 1180.dp).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = side, end = side, top = 4.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "title") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlainBackArrow("Back to the restaurants", onBack)
                        Column(Modifier.weight(1f)) {
                            Text("Admins", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 18.sp else 21.sp, color = Kit.Ink)
                            Text("Support admins take care of the restaurants you give them", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                        }
                        if (state.allowed && state.people.isNotEmpty() && !shapes) AdminFiltersButton(state, model)
                        RefreshButton(state.loading, model::refresh)
                        // Only while the "Add support admin" tile at the end of the cards is out of sight (user, 2026-11-21: "this button
                        // appears only when the big one goes down and I need to scroll to see it").
                        androidx.compose.animation.AnimatedVisibility(state.allowed && state.people.isNotEmpty() && !addTileSeen,
                            enter = androidx.compose.animation.fadeIn(tween(160)), exit = androidx.compose.animation.fadeOut(tween(120))) {
                            KitButton(if (narrow) "Add" else "Add support admin", model::startAdd, icon = Icons.Outlined.PersonAdd, enabled = !state.saving)
                        }
                    }
                }
                // With a filter on: how many it leaves and a way back to everyone.
                if (state.allowed && state.people.isNotEmpty() && !shapes && activeFilters(state) > 0) item(key = "filter") {
                    Row(Modifier.animateItem(fadeInSpec = CardFadeIn, placementSpec = null, fadeOutSpec = tween(90)),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(filterText(state), Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${shownPeople(state).size} of ${state.people.size}", fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
                        Text("Clear filters", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = model::clearFilters).padding(horizontal = 6.dp, vertical = 3.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Green)
                    }
                }
                when {
                    !state.allowed -> item(key = "denied") {
                        PageState(PageStateKind.NO_ACCESS, "Only the Super Admin", "Only the Super Admin adds Co-Super-Admins and chooses what they can do.")
                    }
                    shapes -> item(key = "shapes") {
                        AdminGridShape(Modifier.animateItem(fadeInSpec = tween(160, delayMillis = 80), placementSpec = null, fadeOutSpec = tween(80)))
                    }
                    !state.loaded && state.error != null -> item(key = "failed") {
                        PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                    }
                    state.people.isEmpty() -> item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            OverviewEmpty(
                                "No support admins yet",
                                "Add someone to take care of restaurants for you. They work only in the restaurants you give them.",
                                Icons.Outlined.AdminPanelSettings,
                                action = { KitButton("Add support admin", model::startAdd, icon = Icons.Outlined.PersonAdd) }
                            )
                        }
                    }
                    shownPeople(state).isEmpty() -> item(key = "none-here") {
                        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            OverviewEmpty("No support admin matches", "Try other filters, or give the restaurant to someone from their Restaurants.",
                                Icons.Outlined.AdminPanelSettings, action = { RetryText("Clear filters", onClick = model::clearFilters) })
                        }
                    }
                    else -> {
                        // A card per support admin with the Users' admin figure (user, 2026-11-21: "put the icons of users"), three a row on a
                        // wide window, two on a tablet, one on a phone; a dashed tile at the end adds one more.
                        item(key = "people") {
                            BoxWithConstraints(Modifier.animateItem(fadeInSpec = CardFadeIn, placementSpec = null, fadeOutSpec = tween(90)).fillMaxWidth()) {
                                val columns = when {
                                    maxWidth >= 900.dp -> 3
                                    maxWidth >= 560.dp -> 2
                                    else -> 1
                                }
                                val gap = 18.dp
                                val tiles: List<StaffUserDto?> = shownPeople(state) + listOf(null)
                                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                    tiles.chunked(columns).forEach { row ->
                                        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                            row.forEach { person ->
                                                val cell = Modifier.weight(1f).fillMaxHeight()
                                                if (person != null) AdminCard(person, state, model, cell)
                                                else {
                                                    // Whether at least half of it is in view (the list clips it as it scrolls away).
                                                    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { addTileSeen = false } }
                                                    AddTile("Add support admin", "They take care of the restaurants you give them", model::startAdd,
                                                        height = 120.dp, modifier = cell.onGloballyPositioned { tile ->
                                                            val full = tile.size.height
                                                            addTileSeen = full > 0 && tile.boundsInWindow().height >= full * 0.5f
                                                        })
                                                }
                                            }
                                            repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "rules") {
                            Text(
                                "They work only in the restaurants you give them and do only what you allow. They never see or change you, remove owners only when you allow it, and remove only people they added themselves.",
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted
                            )
                        }
                    }
                }
            }
        }
    }
    state.draft?.let { AddDialog(it, state, model) }
    state.editing?.let { id -> state.people.firstOrNull { it.id == id }?.let { AdminEditDialog(it, state, model) } }
    state.ticks?.let { PermissionsDialog(it, model) }
    state.access?.let { AccessDialog(it, model) }
    state.confirmRemove?.let { person ->
        ConfirmDialog("Remove ${person.displayName}?",
            "They can no longer sign in or take care of any restaurant. People and changes they made stay. To stop them for a while, switch them off instead.",
            "Remove", onConfirm = model::confirmRemove, onDismiss = model::dismissRemove, danger = true)
    }
    state.confirmSwitchOff?.let { person ->
        ConfirmDialog("Switch off ${person.displayName}?", "They can't sign in until you switch them on again. Nothing they did is lost.",
            "Switch off", onConfirm = model::confirmSwitchOff, onDismiss = model::dismissConfirm, danger = true)
    }
    state.confirmPassword?.let { person ->
        ConfirmDialog("Send a password link?", "${person.displayName} gets an email at ${person.email} to choose a new password.",
            "Send", onConfirm = model::confirmPassword, onDismiss = model::dismissConfirm)
    }
}

/**
 * One support admin as a card: the admin figure on a band of their colour with their status, their face, name and email,
 * the restaurants they take care of, what they may do, and Restaurants / What they can do. Clicking opens "What they can do".
 */
@Composable
private fun AdminCard(person: StaffUserDto, state: CoSuperAdminsState, model: CoSuperAdminsScreenModel, modifier: Modifier) {
    val accent = accentOf(person.id)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (statusText, statusColor) = when {
        !person.isActive -> "Switched off" to Kit.Grey
        !person.emailVerified -> "Email to confirm" to Kit.Amber
        else -> "Active" to Kit.Green
    }
    val counts = state.allowedCounts[person.id]
    val card = state.cards.firstOrNull { it.id == person.id }
    Surface(
        onClick = { model.openPermissions(person) }, modifier = modifier, interactionSource = interaction,
        shape = RoundedCornerShape(18.dp), color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) accent.strong.copy(alpha = 0.55f) else Kit.Border),
        shadowElevation = if (hovered) 8.dp else 1.dp
    ) {
        Column(Modifier.fillMaxHeight().graphicsLayer { alpha = if (person.isActive) 1f else 0.62f }) {
            Box(Modifier.fillMaxWidth().height(118.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.softer, accent.soft)))) {
                androidx.compose.foundation.Image(org.jetbrains.compose.resources.painterResource(Res.drawable.users_admin), null,
                    Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, top = 8.dp).height(108.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                Row(
                    Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.94f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                    Text(statusText, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = statusColor, maxLines = 1)
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(50)).background(accent.strong)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(12.dp), tint = Color.White)
                    Text("SUPPORT ADMIN", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.6.sp, color = Color.White, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(accent.soft), contentAlignment = Alignment.Center) {
                        com.saporini.mobile_desktop.core.ui.RemoteImage(card?.photoUrl, null, Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(initialsOf(person.displayName), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent.strong,
                                    translate = false)
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(person.displayName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                        Text(person.email, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
                    }
                    // Edit opens their window with every change (user, 2026-11-21: "an Edit button where you go inside", not a ⋮ menu).
                    Surface(onClick = { model.openEdit(person) }, enabled = person.id !in state.busy, shape = RoundedCornerShape(50), color = Color.White,
                        border = BorderStroke(1.dp, Kit.Border)) {
                        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp), tint = Kit.Ink)
                            Text("Edit", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1)
                        }
                    }
                }
                // The restaurants they take care of.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TAKES CARE OF", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Kit.Faint)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            card == null -> Text("…", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, translate = false)
                            card.allRestaurants -> PlaceChip("All restaurants", Kit.Green, Icons.Outlined.Apps)
                            card.restaurants.isEmpty() -> PlaceChip("No restaurants yet", Kit.Amber, Icons.Outlined.WarningAmber)
                            else -> {
                                card.restaurants.take(3).forEach { PlaceChip(it.name, Kit.Blue, Icons.Outlined.Storefront, translate = false) }
                                if (card.restaurants.size > 3) PlaceChip("+${card.restaurants.size - 3}", Kit.Blue, null, translate = false)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FooterLine(Icons.Outlined.Key, when {
                        counts == null -> "…"
                        counts.first == counts.second -> "Can do everything"
                        else -> "${counts.second - counts.first} switched off"
                    })
                    if (card?.mayRemoveOwners == true) FooterLine(Icons.Outlined.VerifiedUser, "May remove owners", Kit.Danger)
                }
                Spacer(Modifier.weight(1f))
                KitDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitButton("Restaurants", { model.openAccess(person) }, icon = Icons.Outlined.Storefront, style = ButtonStyle.SECONDARY)
                    KitButton("What they can do", { model.openPermissions(person) }, icon = Icons.Outlined.Tune, style = ButtonStyle.SECONDARY)
                }
            }
        }
    }
}

/**
 * A support admin's Edit window (user, 2026-11-21): who they are, then a line per change with its button: their restaurants,
 * what they can do, a password link, switching them off or on, and removing them last. Each one that can't be undone asks
 * "Are you sure?" first.
 */
@Composable
private fun AdminEditDialog(person: StaffUserDto, state: CoSuperAdminsState, model: CoSuperAdminsScreenModel) {
    val card = state.cards.firstOrNull { it.id == person.id }
    val counts = state.allowedCounts[person.id]
    val busy = person.id in state.busy
    val accent = accentOf(person.id)
    val (statusText, statusColor) = when (AdminStatus.of(person)) {
        AdminStatus.OFF -> "Switched off" to Kit.Grey
        AdminStatus.TO_CONFIRM -> "Email to confirm" to Kit.Amber
        AdminStatus.ACTIVE -> "Active" to Kit.Green
    }
    AppDialog("Edit support admin", model::closeEdit, busy = busy, maxWidth = 600.dp,
        buttons = { Box(Modifier.weight(1f)); KitButton("Done", model::closeEdit) }) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.softer).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(accent.soft), contentAlignment = Alignment.Center) {
                com.saporini.mobile_desktop.core.ui.RemoteImage(card?.photoUrl, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(initialsOf(person.displayName), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent.strong, translate = false)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(person.displayName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, translate = false)
                Text(person.email, fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, translate = false)
            }
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                Text(statusText, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = statusColor, maxLines = 1)
            }
        }
        EditLine(Icons.Outlined.Storefront, "Restaurants", when {
            card == null -> "…"
            card.allRestaurants -> "All restaurants"
            card.restaurants.isEmpty() -> "No restaurants yet"
            else -> card.restaurants.joinToString(", ") { it.name }
        }) { KitButton("Change", { model.openAccess(person) }, style = ButtonStyle.SECONDARY, enabled = !busy) }
        EditLine(Icons.Outlined.Tune, "What they can do", when {
            counts == null -> "…"
            counts.first == counts.second -> "Can do everything"
            else -> "${counts.second - counts.first} switched off"
        }) { KitButton("Change", { model.openPermissions(person) }, style = ButtonStyle.SECONDARY, enabled = !busy) }
        EditLine(Icons.Outlined.LockReset, "Password", "Email them a link to choose a new password") {
            KitButton("Send link", { model.askPassword(person) }, style = ButtonStyle.SECONDARY, enabled = !busy)
        }
        if (person.isActive) {
            EditLine(Icons.Outlined.PersonOff, "Sign-in", "They can sign in. Switch them off to stop them for a while.") {
                KitButton("Switch off", { model.askSwitchOff(person) }, style = ButtonStyle.DANGER_OUTLINE, enabled = !busy)
            }
        } else {
            EditLine(Icons.Outlined.Person, "Sign-in", "Switched off: they can't sign in.") {
                KitButton("Switch on", { model.setPersonActive(person, true) }, style = ButtonStyle.SECONDARY, enabled = !busy)
            }
        }
        KitDivider()
        EditLine(Icons.Outlined.Delete, "Remove", "They can no longer sign in. What they did stays.", danger = true) {
            KitButton("Remove", { model.askRemove(person) }, style = ButtonStyle.DANGER_OUTLINE, enabled = !busy)
        }
    }
}

/** One change in the Edit window: an icon, its name and what it is now, and its button at the end. */
@Composable
private fun EditLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, danger: Boolean = false,
                     action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (danger) Kit.Danger.copy(alpha = 0.08f) else Kit.Tint),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (danger) Kit.Danger else Kit.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (danger) Kit.Danger else Kit.Ink)
            Text(detail, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        action()
    }
}

@Composable
private fun PlaceChip(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector?, translate: Boolean = true) {
    // Its full width also when the row of cards measures itself (IntrinsicSize.Min): otherwise the chips' row counts too few lines,
    // the card comes out too short and its buttons get squeezed (user, 2026-11-21, a card with three restaurants).
    Row(
        Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.10f))
            .padding(start = if (icon != null) 7.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        icon?.let { Icon(it, null, Modifier.size(13.dp), tint = color) }
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            translate = translate)
    }
}

@Composable
private fun AddDialog(draft: NewCoSuperAdminDraft, state: CoSuperAdminsState, model: CoSuperAdminsScreenModel) {
    val problems = if (draft.showProblems) coSuperAdminProblems(draft) else emptyMap()
    AppDialog(
        "Add a support admin", model::cancelAdd,
        subtitle = "They confirm their email, then sign in with this temporary password and choose their own.",
        busy = state.saving, maxWidth = 640.dp,
        buttons = {
            KitButton("Cancel", model::cancelAdd, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton("Add", model::saveAdd, icon = Icons.Outlined.PersonAdd, loading = state.saving)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        FieldPair({ m -> TextInput(draft.firstName, { v -> model.editDraft { it.copy(firstName = v) } }, m, label = "First name", required = true,
            icon = Icons.Outlined.Person, problem = problems["firstName"], maxLength = 50) },
            { m -> TextInput(draft.lastName, { v -> model.editDraft { it.copy(lastName = v) } }, m, label = "Last name", required = true,
                problem = problems["lastName"], maxLength = 50) })
        FieldPair({ m -> TextInput(draft.email, { v -> model.editDraft { it.copy(email = v) } }, m, label = "Email", required = true,
            icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT) },
            { m -> TextInput(draft.username, { v -> model.editDraft { it.copy(username = v, usernameTouched = true) } }, m, label = "Username",
                required = true, problem = problems["username"], maxLength = 50) })
        TextInput(draft.password, { v -> model.editDraft { it.copy(password = v) } }, Modifier.fillMaxWidth(), label = "Temporary password", required = true,
            icon = Icons.Outlined.Key, password = true, problem = problems["password"], maxLength = MAX_PASSWORD,
            hint = "Share it with them yourself; it isn't emailed")
        NoteBox(Icons.Outlined.Tune, "After adding them you choose what they can do. They start with everything.")
    }
}

@Composable
private fun PermissionsDialog(ticks: PermissionTicks, model: CoSuperAdminsScreenModel) {
    val person = ticks.person
    val first = person.firstName.ifBlank { person.displayName }
    var query by remember(person.id) { mutableStateOf("") }
    var area by remember(person.id) { mutableStateOf(PERMISSION_AREAS.first()) }
    AppDialog(
        "What $first can do", model::closePermissions,
        subtitle = "Switch off what $first shouldn't do. It applies from their next click.",
        busy = ticks.saving, maxWidth = 1000.dp,
        buttons = {
            KitButton("Allow everything", { model.setAll(true) }, style = ButtonStyle.SECONDARY, enabled = !ticks.loading && !ticks.saving)
            KitButton("Allow nothing", { model.setAll(false) }, style = ButtonStyle.SECONDARY, enabled = !ticks.loading && !ticks.saving)
            Box(Modifier.weight(1f))
            KitButton("Cancel", model::closePermissions, style = ButtonStyle.SECONDARY, enabled = !ticks.saving)
            KitButton("Save", model::savePermissions, icon = Icons.Outlined.Check, loading = ticks.saving, enabled = !ticks.loading && ticks.changed)
        }
    ) {
        ticks.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (ticks.loading) {
            PermissionCardsShape()
            return@AppDialog
        }
        val byArea = ticks.permissions.groupBy { permissionArea(it.code) }
        val areas = PERMISSION_AREAS.filter { it in byArea }
        // Who, and how much they may do.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            InitialsAvatar(person.displayName, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(person.displayName, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                Text(person.email, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${ticks.granted.size}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink)
                    Text(" of ${ticks.permissions.size} allowed", Modifier.padding(bottom = 2.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                }
                val fraction = if (ticks.permissions.isEmpty()) 0f else ticks.granted.size.toFloat() / ticks.permissions.size
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Kit.Border)) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(50)).background(Kit.Green))
                }
            }
        }
        com.saporini.mobile_desktop.core.components.SearchField(query, { query = it }, Modifier.fillMaxWidth(),
            placeholder = "Find a permission (e.g. refund, menus, delete)", height = 44.dp)
        Row(Modifier.fillMaxWidth().height(440.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Areas on the left, with how many are on in each.
            Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                areas.forEach { name ->
                    val items = byArea.getValue(name)
                    val on = items.count { it.code in ticks.granted }
                    val selected = query.isBlank() && name == area
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Kit.GreenSoft else Color.Transparent)
                            .clickable { area = name; query = "" }.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Color.White else Kit.Tint), contentAlignment = Alignment.Center) {
                            Icon(areaIcon(name), null, Modifier.size(17.dp), tint = if (on == 0) Kit.Faint else Kit.Green)
                        }
                        Text(name, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 13.sp, color = if (selected) Kit.Green else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$on/${items.size}", Modifier.clip(RoundedCornerShape(50))
                            .background(when { on == items.size -> Kit.Green.copy(alpha = 0.12f); on == 0 -> Kit.Danger.copy(alpha = 0.10f); else -> Kit.Amber.copy(alpha = 0.14f) })
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                            fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            color = when { on == items.size -> Kit.Green; on == 0 -> Kit.Danger; else -> Kit.Amber })
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Kit.Border))
            // The permissions of the chosen area (or every match while searching), each with a switch.
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val shown = if (words.isEmpty()) listOf(area to byArea[area].orEmpty())
                    else areas.map { name -> name to byArea.getValue(name).filter { p -> words.all { w -> "${p.name} ${p.description.orEmpty()} $name".lowercase().contains(w) } } }
                        .filter { it.second.isNotEmpty() }
                if (shown.isEmpty()) {
                    Text("Nothing matches “${query.trim()}”.", Modifier.padding(16.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
                }
                shown.forEach { (name, items) ->
                    val on = items.count { it.code in ticks.granted }
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(areaIcon(name), null, Modifier.size(20.dp), tint = Kit.Green)
                        Column(Modifier.weight(1f)) {
                            Text(name, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
                            Text("$on of ${items.size} allowed", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                        }
                        if (words.isEmpty()) {
                            Text(if (on == items.size) "All on" else "All off", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
                            KitSwitch(on == items.size, { model.toggleArea(name) })
                        }
                    }
                    items.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { permission ->
                                val allowed = permission.code in ticks.granted
                                Row(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (allowed) Color.White else Kit.Canvas)
                                        .border(1.dp, if (allowed) Kit.Green.copy(alpha = 0.35f) else Kit.Border, RoundedCornerShape(12.dp))
                                        .clickable { model.toggle(permission.code) }.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(permission.name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                            color = if (allowed) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        permission.description?.let {
                                            Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    KitSwitch(allowed, { model.toggle(permission.code) }, compact = true)
                                }
                            }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if ("RESTAURANTS_READ" !in ticks.granted) {
            NoteBox(Icons.Outlined.WarningAmber, "Without “View Restaurants” $first can't see the restaurant list, so they can't open any restaurant.",
                tone = Kit.Amber)
        }
    }
}

/** An icon for each area of the permission list. */
private fun areaIcon(area: String): androidx.compose.ui.graphics.vector.ImageVector = when (area) {
    "Restaurants" -> Icons.Outlined.Storefront
    "Apps they can open" -> Icons.Outlined.Apps
    "Staff and roles" -> Icons.Outlined.Groups
    "Settings" -> Icons.Outlined.Settings
    "Menus" -> Icons.AutoMirrored.Outlined.MenuBook
    "Orders" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "Payments and refunds" -> Icons.Outlined.Payments
    "Bookings" -> Icons.AutoMirrored.Outlined.EventNote
    "Kitchen display" -> Icons.Outlined.Kitchen
    "Shifts and pay" -> Icons.Outlined.Schedule
    "Statistics and fraud" -> Icons.Outlined.Insights
    else -> Icons.Outlined.MoreHoriz
}


/** The restaurants a support admin takes care of: all of them, or the ones ticked; and whether they may remove owners. */
@Composable
private fun AccessDialog(editor: com.saporini.mobile_desktop.platform.AdminAccessEditor, model: CoSuperAdminsScreenModel) {
    AppDialog(
        "${editor.name}'s restaurants", model::closeAccess,
        subtitle = "They see and work only in these restaurants.",
        busy = editor.saving, maxWidth = 560.dp,
        buttons = {
            KitButton("Cancel", model::closeAccess, style = ButtonStyle.SECONDARY, enabled = !editor.saving)
            KitButton("Save", model::saveAccess, icon = Icons.Outlined.Check, loading = editor.saving, enabled = !editor.loading)
        }
    ) {
        editor.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (editor.loading) {
            SwitchLinesShape(6)
            return@AppDialog
        }
        com.saporini.mobile_desktop.core.components.SettingsRow("All restaurants", "Every restaurant, also the ones added later.", divider = false,
            onClick = { model.editAccess { it.copy(allRestaurants = !it.allRestaurants) } }) {
            com.saporini.mobile_desktop.core.components.SettingsSwitch(editor.allRestaurants, { v -> model.editAccess { it.copy(allRestaurants = v) } })
        }
        com.saporini.mobile_desktop.core.components.SettingsRow("May remove owners and co-owners", "Off: they can't remove a restaurant's owners or co-owners.",
            onClick = { model.editAccess { it.copy(mayRemoveOwners = !it.mayRemoveOwners) } }) {
            com.saporini.mobile_desktop.core.components.SettingsSwitch(editor.mayRemoveOwners, { v -> model.editAccess { it.copy(mayRemoveOwners = v) } })
        }
        if (!editor.allRestaurants) {
            Text("${editor.chosen.size} of ${editor.restaurants.size} restaurants", Modifier.padding(top = 8.dp), fontFamily = Inter(),
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
            editor.restaurants.forEach { restaurant ->
                val on = restaurant.id in editor.chosen
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) Kit.GreenSoft else Color.Transparent)
                    .clickable { model.editAccess { it.copy(chosen = if (on) it.chosen - restaurant.id else it.chosen + restaurant.id) } }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (on) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(19.dp), tint = if (on) Kit.Green else Kit.Faint)
                    Text(restaurant.name, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = Kit.Ink)
                }
            }
        }
    }
}

/** The support admins shown: all, or those who take care of the restaurant picked (given it, or every restaurant). */
private fun shownPeople(state: CoSuperAdminsState): List<StaffUserDto> =
    state.people.filter { person -> assignedTo(state, person, state.restaurantFilter) && (state.statusFilter == null || AdminStatus.of(person) == state.statusFilter) }

/** Whether [person] fits the "Assigned to" choice: a restaurant (given it, or every restaurant), every one, or none yet. */
private fun assignedTo(state: CoSuperAdminsState, person: StaffUserDto, choice: String?): Boolean {
    if (choice == null) return true
    val card = state.cards.firstOrNull { it.id == person.id } ?: return false
    return when (choice) {
        ASSIGNED_EVERY -> card.allRestaurants
        ASSIGNED_NONE -> !card.allRestaurants && card.restaurants.isEmpty()
        else -> card.allRestaurants || card.restaurants.any { it.id == choice }
    }
}

private fun activeFilters(state: CoSuperAdminsState) = listOfNotNull(state.restaurantFilter, state.statusFilter).size

/** What the filters keep, in words: "Assigned to Local Demo Bistroo · Active". */
private fun filterText(state: CoSuperAdminsState): String = listOfNotNull(
    when (val choice = state.restaurantFilter) {
        null -> null
        ASSIGNED_EVERY -> "Assigned to every restaurant"
        ASSIGNED_NONE -> "No restaurant yet"
        else -> "Assigned to: ${state.restaurants.firstOrNull { it.id == choice }?.name ?: "a restaurant"}"
    },
    state.statusFilter?.label
).joinToString(" · ")

/**
 * One Filters button (user, 2026-11-21: the restaurant list made admins look as if they belonged to a restaurant; "only a
 * filter should make it: are assigned to a restaurant"): Assigned to (every restaurant, one restaurant, none yet) and Status,
 * each with how many.
 */
@Composable
private fun AdminFiltersButton(state: CoSuperAdminsState, model: CoSuperAdminsScreenModel) {
    com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton(Icons.Outlined.FilterList, "Filters", activeFilters(state), 44.dp) { close ->
        val count: (String?) -> Int = { choice -> state.people.count { assignedTo(state, it, choice) } }
        com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup("Assigned to")
        com.saporini.mobile_desktop.admin.inventory.ui.OptionLine("Any restaurant", state.restaurantFilter == null,
            { model.filterRestaurant(null); close() }, icon = Icons.Outlined.AdminPanelSettings, count = state.people.size)
        com.saporini.mobile_desktop.admin.inventory.ui.OptionLine("Every restaurant", state.restaurantFilter == ASSIGNED_EVERY,
            { model.filterRestaurant(ASSIGNED_EVERY); close() }, icon = Icons.Outlined.Public, count = count(ASSIGNED_EVERY))
        state.restaurants.forEach { restaurant ->
            com.saporini.mobile_desktop.admin.inventory.ui.OptionLine(restaurant.name, state.restaurantFilter == restaurant.id,
                { model.filterRestaurant(restaurant.id); close() }, icon = Icons.Outlined.Storefront, count = count(restaurant.id))
        }
        val none = count(ASSIGNED_NONE)
        if (none > 0 || state.restaurantFilter == ASSIGNED_NONE) {
            com.saporini.mobile_desktop.admin.inventory.ui.OptionLine("No restaurant yet", state.restaurantFilter == ASSIGNED_NONE,
                { model.filterRestaurant(ASSIGNED_NONE); close() }, icon = Icons.Outlined.Block, color = Kit.Muted, count = none)
        }
        com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup("Status")
        com.saporini.mobile_desktop.admin.inventory.ui.OptionLine("Any status", state.statusFilter == null, { model.filterStatus(null); close() },
            icon = Icons.Outlined.Person, count = state.people.size)
        AdminStatus.entries.forEach { status ->
            com.saporini.mobile_desktop.admin.inventory.ui.OptionLine(status.label, state.statusFilter == status, { model.filterStatus(status); close() },
                icon = when (status) {
                    AdminStatus.ACTIVE -> Icons.Outlined.VerifiedUser
                    AdminStatus.TO_CONFIRM -> Icons.Outlined.MarkEmailUnread
                    AdminStatus.OFF -> Icons.Outlined.PersonOff
                },
                color = when (status) {
                    AdminStatus.ACTIVE -> Kit.Green
                    AdminStatus.TO_CONFIRM -> Kit.Amber
                    AdminStatus.OFF -> Kit.Muted
                },
                count = state.people.count { AdminStatus.of(it) == status })
        }
        if (activeFilters(state) > 0) {
            Text(
                "Clear all filters",
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { model.clearFilters(); close() }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Danger
            )
        }
    }
}
