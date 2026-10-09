package com.saporini.mobile_desktop.admin.people.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.people.StaffScreenModel
import com.saporini.mobile_desktop.admin.people.StaffState
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.CardDivider
import com.saporini.mobile_desktop.core.components.ChartPalette
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.FilterRow
import com.saporini.mobile_desktop.core.components.InitialsAvatar
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewCompactEmpty
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewPanel
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageControlHeight
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.PagedFooter
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.SideColumn
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.StripCard
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.reservations.HeaderDropdown
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.workspace_admin
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.settings_notifications
import org.koin.compose.koinInject

private val StatusChoices = listOf("Active" to true, "Switched off" to false, "Everyone" to null)
private const val ALL_ROLES = "All roles"

/** Admin Hub → Users: the restaurant's staff accounts. */
@Composable
internal fun UsersScreen(modifier: Modifier = Modifier) {
    val model = koinInject<StaffScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    // Team totals: on opening, once the roles are known, and after every change.
    LaunchedEffect(state.canRead, state.assignableRoles.size, state.notice) { model.loadCounts() }
    UsersContent(state, model, modifier)
}

@Composable
internal fun UsersContent(state: StaffState, model: StaffScreenModel, modifier: Modifier = Modifier) {
    val roleNames = state.assignableRoles.associate { it.code to it.label() }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Users", "Staff accounts, their roles and who can sign in") {
                SearchField(state.filter.search, model::search, Modifier.width(240.dp), placeholder = "Search name, email or username", height = PageControlHeight)
                if (!size.isDesktop) {
                    HeaderDropdown(StatusChoices.first { it.second == state.filter.active }.first, Icons.Outlined.HowToReg, Modifier.width(150.dp),
                        StatusChoices.map { it.first }, { label -> model.showActive(StatusChoices.first { it.first == label }.second) })
                    if (state.assignableRoles.isNotEmpty()) {
                        HeaderDropdown(state.filter.roleCode?.let { roleNames[it] ?: humanize(it) } ?: ALL_ROLES, Icons.Outlined.Badge, Modifier.width(150.dp),
                            listOf(ALL_ROLES) + state.assignableRoles.map { it.label() },
                            { label -> model.role(state.assignableRoles.firstOrNull { it.label() == label }?.code) })
                    }
                }
                RefreshButton(state.loading, { model.refresh(); model.loadCounts() })
                if (state.canCreate) ToolbarPrimaryButton("Add person", Icons.Outlined.PersonAdd, !state.saving, model::startNew)
            }
            ScreenMessages(state.error.takeIf { state.draft == null }, state.notice, model::clearMessages,
                warning = "This list may be out of date; it refreshes when the connection is back."
                    .takeIf { state.stale && state.people.isNotEmpty() && state.error == null && !state.loading })

            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to users", "Your role needs “View users” to see staff accounts.")
                state.people.isEmpty() && state.loading -> OverviewPageSkeleton(size)
                else -> {
                    SummaryCardRow(size, summaryCards(state, roleNames))
                    if (size.isDesktop) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PeoplePanel(state, model, roleNames, size, Modifier.weight(1f).fillMaxHeight())
                            SideColumn(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                StatusFilterPanel(state, model)
                                RoleFilterPanel(state, model)
                            }
                        }
                    } else {
                        PeoplePanel(state, model, roleNames, size, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }

    state.draft?.let { UserEditorDialog(state, model) }
    state.confirmRemove?.let { id ->
        val person = state.people.firstOrNull { it.id == id }
        ConfirmDialog(
            "Remove ${person?.displayName ?: "this person"}?",
            "They won't be able to sign in any more, and they leave the staff list. Their past orders and shifts stay in the records.",
            "Remove", onConfirm = model::confirmRemove, onDismiss = model::dismissRemove, danger = true
        )
    }
}

private fun summaryCards(state: StaffState, roleNames: Map<String, String>): List<@Composable (Modifier) -> Unit> {
    val counts = state.counts
    val biggest = counts?.byRole?.maxByOrNull { it.value }?.takeIf { it.value > 0 }
    return listOf(
        { m ->
            OverviewStatCard("Team", counts?.active?.toString() ?: "–", counts?.let { "${it.everyone} accounts · ${it.inactive} switched off" } ?: "Counting the team…",
                Kit.Green, m, image = Res.drawable.overview_guests, suffix = "can sign in",
                progress = counts?.takeIf { it.everyone > 0 }?.let { it.active.toFloat() / it.everyone })
        },
        { m ->
            OverviewStatCard("Roles you can give", "${state.assignableRoles.size}", "Change what they allow in Permissions",
                Kit.Blue, m, image = Res.drawable.workspace_admin)
        },
        { m ->
            OverviewStatCard("Biggest team", biggest?.let { roleNames[it.key] ?: humanize(it.key) } ?: "–",
                biggest?.let { "${it.value} active ${if (it.value == 1L) "person" else "people"}" } ?: "No roles counted yet",
                Kit.Amber, m, image = Res.drawable.overview_reservations)
        },
        { m ->
            OverviewStatCard("Switched off", counts?.inactive?.toString() ?: "–",
                if ((counts?.inactive ?: 0) == 0L) "Everyone can sign in" else "Can't sign in until switched on",
                if ((counts?.inactive ?: 0) > 0) Kit.Grey else Kit.Purple, m, image = Res.drawable.settings_notifications)
        }
    )
}

@Composable
private fun PeoplePanel(state: StaffState, model: StaffScreenModel, roleNames: Map<String, String>, size: ScreenSize, modifier: Modifier) {
    val filterText = listOfNotNull(
        StatusChoices.first { it.second == state.filter.active }.first.takeIf { state.filter.active != null },
        state.filter.roleCode?.let { roleNames[it] ?: humanize(it) }
    ).joinToString(" · ")
    OverviewPanel(Icons.Outlined.People, if (filterText.isBlank()) "People" else "People · $filterText", modifier, count = state.total.toInt()) {
        when {
            state.people.isEmpty() && state.stale -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty("Couldn't load the team", "Check the connection, then try again.", Icons.Outlined.PersonSearch,
                    action = { RetryText(onClick = model::refresh) })
            }
            state.people.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                OverviewEmpty(
                    if (state.filter.search.isNotBlank()) "Nobody matches “${state.filter.search}”" else "Nobody here",
                    when {
                        state.filter.search.isNotBlank() -> "Try part of the name, the email or the username."
                        state.filter.active == false -> "Nobody is switched off."
                        else -> "Add a person to give them a sign-in for the tills."
                    },
                    Icons.Outlined.PersonSearch, image = Res.drawable.overview_guests
                )
            }
            else -> {
                val listState = rememberLazyListState()
                LoadMoreWhenNearEnd(listState, state.hasNext, state.loadingMore || state.loading, onLoadMore = model::loadMore)
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)) {
                    items(state.people, key = { it.id }) { person ->
                        PersonCard(person, state, roleNames, personActions(person, state, model), compact = !size.isDesktop) {
                            if (state.canEdit(person)) model.startEdit(person.id)
                        }
                    }
                    item(key = "footer") { PagedFooter(state.people.size, state.total, state.hasNext, state.loadingMore, state.error != null, model::loadMore) }
                }
            }
        }
    }
}

private fun personActions(person: StaffUserDto, state: StaffState, model: StaffScreenModel): List<RowAction> = buildList {
    if (state.canEdit(person)) {
        add(RowAction("Edit details and roles", Icons.Outlined.Edit) { model.startEdit(person.id) })
        add(if (person.isActive) RowAction("Switch off", Icons.Outlined.PersonOff) { model.setActive(person.id, false) }
            else RowAction("Switch on", Icons.Outlined.ToggleOn) { model.setActive(person.id, true) })
        add(RowAction("Send password reset email", Icons.Outlined.LockReset, enabled = person.isActive) { model.sendPasswordReset(person.id) })
    }
    if (state.canRemove(person)) add(RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askRemove(person.id) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(person: StaffUserDto, state: StaffState, roleNames: Map<String, String>, actions: List<RowAction>, compact: Boolean, onClick: () -> Unit) {
    val me = person.id == state.myId
    val editable = state.canEdit(person)
    StripCard(if (person.isActive) Kit.Green else Kit.Grey, minHeight = 70.dp, chevron = !compact, faded = !person.isActive,
        onClick = if (editable) onClick else null) {
        InitialsAvatar(person.displayName, color = if (person.isActive) avatarColor(person.id) else Kit.Grey, size = 40.dp)
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(person.displayName, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (me) StatusChip("You", Kit.Blue)
            }
            Text("@${person.username}" + (person.phone?.let { " · $it" } ?: ""), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (compact) RoleChips(person.roles, roleNames)
        }
        if (!compact) {
            CardDivider()
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MailOutline, null, Modifier.size(15.dp), tint = Kit.Muted)
                    Spacer(Modifier.width(6.dp))
                    Text(person.email, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (person.emailVerified) Icons.Outlined.Verified else Icons.Outlined.MarkEmailUnread, null, Modifier.size(14.dp),
                        tint = if (person.emailVerified) Kit.Green else Kit.Amber)
                    Spacer(Modifier.width(6.dp))
                    Text(if (person.emailVerified) "Email confirmed" else "Email not confirmed yet", fontFamily = Inter(), fontSize = 11.sp,
                        color = if (person.emailVerified) Kit.Green else Kit.Amber)
                }
            }
            CardDivider()
            Box(Modifier.weight(1.3f)) { RoleChips(person.roles, roleNames) }
        }
        StatusPill(if (person.isActive) "Active" else "Switched off", if (person.isActive) Kit.Green else Kit.Grey)
        RowActionsMenu(actions, busy = person.id in state.busy)
    }
}

private fun avatarColor(id: String): Color = ChartPalette[(id.hashCode() and 0x7fffffff) % 4]

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleChips(roles: List<String>, names: Map<String, String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (roles.isEmpty()) StatusChip("No role", Kit.Amber)
        roles.take(3).forEach { StatusChip(names[it] ?: humanize(it), Kit.Blue, Icons.Outlined.AdminPanelSettings) }
        if (roles.size > 3) StatusChip("+${roles.size - 3}", Kit.Grey)
    }
}

@Composable
private fun StatusFilterPanel(state: StaffState, model: StaffScreenModel) {
    val counts = state.counts
    OverviewPanel(Icons.Outlined.HowToReg, "By status", Modifier.fillMaxWidth()) {
        FilterRow(Icons.Outlined.HowToReg, "Active", counts?.active?.toInt(), Kit.Green, state.filter.active == true) { model.showActive(true) }
        FilterRow(Icons.Outlined.PersonOff, "Switched off", counts?.inactive?.toInt(), Kit.Grey, state.filter.active == false) { model.showActive(false) }
        FilterRow(Icons.Outlined.Groups, "Everyone", counts?.everyone?.toInt(), Kit.Blue, state.filter.active == null) { model.showActive(null) }
    }
}

@Composable
private fun RoleFilterPanel(state: StaffState, model: StaffScreenModel) {
    OverviewPanel(Icons.Outlined.Badge, "By role", Modifier.fillMaxWidth(), titleExtra = "active people") {
        if (state.assignableRoles.isEmpty()) {
            OverviewCompactEmpty("No roles to show", "Roles you can give appear here.", Icons.Outlined.Badge)
        } else {
            FilterRow(Icons.Outlined.Groups, ALL_ROLES, null, Kit.Green, state.filter.roleCode == null) { model.role(null) }
            state.assignableRoles.forEachIndexed { index, role ->
                FilterRow(Icons.Outlined.AdminPanelSettings, role.label(), state.counts?.byRole?.get(role.code)?.toInt(),
                    ChartPalette[index % ChartPalette.size], state.filter.roleCode == role.code,
                    detail = role.description?.takeIf { it.isNotBlank() }) { model.role(if (state.filter.roleCode == role.code) null else role.code) }
            }
        }
    }
}
