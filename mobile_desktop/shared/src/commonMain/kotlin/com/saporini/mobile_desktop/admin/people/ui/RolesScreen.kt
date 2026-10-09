package com.saporini.mobile_desktop.admin.people.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.people.MAX_ROLE_DESCRIPTION
import com.saporini.mobile_desktop.admin.people.MAX_ROLE_NAME
import com.saporini.mobile_desktop.admin.people.PermissionChoice
import com.saporini.mobile_desktop.admin.people.RoleDto
import com.saporini.mobile_desktop.admin.people.RoleForm
import com.saporini.mobile_desktop.admin.people.RolesScreenModel
import com.saporini.mobile_desktop.admin.people.RolesState
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.ChartPalette
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.CountPill
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitDivider
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.ListPanel
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.OverviewStatCard
import com.saporini.mobile_desktop.core.components.PageHeader
import com.saporini.mobile_desktop.core.components.PageState
import com.saporini.mobile_desktop.core.components.PageStateKind
import com.saporini.mobile_desktop.core.components.RefreshButton
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.RingChart
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.components.percentText
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.workspace_admin
import mobile_desktop.shared.generated.resources.workspace_fraud
import org.koin.compose.koinInject

/** Admin Hub → Permissions: roles and what each one may do. */
@Composable
internal fun RolesScreen(modifier: Modifier = Modifier) {
    val model = koinInject<RolesScreenModel>()
    BindToLifecycle(model::setActive, model::onDispose)
    val state by model.state.collectAsState()
    RolesContent(state, model, modifier)
}

@Composable
internal fun RolesContent(state: RolesState, model: RolesScreenModel, modifier: Modifier = Modifier) {
    // On phones the list and the permissions are two steps.
    var phoneShowsRole by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (size.isPhone) 14.dp else 22.dp, vertical = if (size.isPhone) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PageHeader("Permissions", "Roles decide what each person can see and do") {
                RefreshButton(state.loading, model::refresh)
                if (state.canCreate) ToolbarPrimaryButton("New role", Icons.AutoMirrored.Outlined.PlaylistAdd, !state.saving, model::startNew)
            }
            ScreenMessages(state.error.takeIf { state.form == null }, state.notice, model::clearMessages)
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to roles", "Your role needs “View roles” to see permissions.")
                state.roles.isEmpty() && state.loading -> OverviewPageSkeleton(size)
                state.roles.isEmpty() -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                else -> {
                    if (!size.isPhone || !phoneShowsRole) SummaryCardRow(size, roleCards(state))
                    when {
                        !size.isPhone -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            RoleList(state, model, Modifier.width(if (size.isDesktop) 330.dp else 270.dp).fillMaxHeight()) { model.select(it) }
                            RolePermissions(state, model, size, Modifier.weight(1f).fillMaxHeight(), onBack = null)
                        }
                        phoneShowsRole -> RolePermissions(state, model, size, Modifier.fillMaxWidth().weight(1f), onBack = { phoneShowsRole = false })
                        else -> RoleList(state, model, Modifier.fillMaxWidth().weight(1f)) { model.select(it); phoneShowsRole = true }
                    }
                }
            }
        }
    }

    state.form?.let { RoleFormDialog(it, state, model) }
    state.confirmDelete?.let { id ->
        val role = state.roles.firstOrNull { it.id == id }
        ConfirmDialog("Remove the role “${role?.label()}”?",
            "People who have it lose what it allows. If someone still has it, switch it off instead.",
            "Remove role", onConfirm = model::confirmDelete, onDismiss = model::dismissDelete, danger = true, busy = state.saving)
    }
}

private fun roleCards(state: RolesState): List<@Composable (Modifier) -> Unit> {
    val builtIn = state.roles.count { it.isSystem }
    val custom = state.roles.size - builtIn
    val off = state.roles.count { !it.isActive }
    val selected = state.selected
    val fraction = if (state.catalog.isEmpty()) 0f else state.permissionIds.size.toFloat() / state.catalog.size
    return listOf(
        { m -> OverviewStatCard("Roles", "${state.roles.size}", "$builtIn built-in · $custom made by you", Kit.Green, m, image = Res.drawable.workspace_admin) },
        { m -> OverviewStatCard("Custom roles", "$custom", if (custom == 0) "Copy a built-in role to make one" else "You can rename, change and remove them",
            Kit.Blue, m, image = Res.drawable.overview_reservations) },
        { m -> OverviewStatCard(selected?.let { "${it.label()} can do" } ?: "Open a role", if (selected == null) "–" else percentText(fraction),
            if (selected == null) "Choose a role to see it" else "${state.permissionIds.size} of ${state.catalog.size} permissions",
            Kit.Amber, m, image = Res.drawable.workspace_fraud, progress = if (selected == null) null else fraction) },
        { m -> OverviewStatCard("Switched off", "$off", if (off == 0) "All roles are in use" else "Give nothing until switched on",
            if (off > 0) Kit.Grey else Kit.Purple, m, image = Res.drawable.settings_notifications) }
    )
}

@Composable
private fun RoleList(state: RolesState, model: RolesScreenModel, modifier: Modifier, onOpen: (String) -> Unit) {
    ListPanel(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = Kit.Ink)
            Spacer(Modifier.width(8.dp))
            Text("Roles", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
            CountPill(state.roles.size)
        }
        KitDivider()
        val (builtIn, custom) = state.roles.partition { it.isSystem }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (custom.isNotEmpty()) {
                item(key = "custom") { GroupLabel("Your roles", "${custom.size}") }
                items(custom, key = { it.id }) { role -> RoleListItem(role, state, model, role.id == state.selectedId) { onOpen(role.id) } }
            }
            if (builtIn.isNotEmpty()) {
                item(key = "builtin") { GroupLabel("Built-in", "${builtIn.size}") }
                items(builtIn, key = { it.id }) { role -> RoleListItem(role, state, model, role.id == state.selectedId) { onOpen(role.id) } }
            }
        }
    }
}

@Composable
private fun RoleListItem(role: RoleDto, state: RolesState, model: RolesScreenModel, selected: Boolean, onClick: () -> Unit) {
    val editable = state.editable(role)
    val tone = if (role.isSystem) Kit.Blue else Kit.Green
    val actions = buildList {
        if (state.canCreate) add(RowAction("Copy as a new role", Icons.Outlined.ContentCopy) { model.startCopy(role.id) })
        if (editable && state.canUpdate) {
            add(RowAction("Rename", Icons.Outlined.DriveFileRenameOutline) { model.startRename(role.id) })
            add(if (role.isActive) RowAction("Switch off", Icons.Outlined.ToggleOff) { model.setRoleActive(role.id, false) }
                else RowAction("Switch on", Icons.Outlined.ToggleOn) { model.setRoleActive(role.id, true) })
        }
        if (editable && state.canDelete) add(RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askDelete(role.id) })
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Kit.GreenSoft else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Kit.Green else Kit.RowBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(start = 10.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tone.copy(alpha = 0.12f)), Alignment.Center) {
            Icon(if (role.isProtected) Icons.Outlined.Lock else Icons.Outlined.AdminPanelSettings, null, Modifier.size(19.dp), tint = tone)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(role.label(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (role.isActive) Kit.Ink else Kit.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(role.description?.takeIf { it.isNotBlank() } ?: if (role.isSystem) "Comes with the app" else "Made for your restaurant",
                fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!role.isActive) StatusChip("Off", Kit.Grey)
        RowActionsMenu(actions)
    }
}

private val AreaNames = mapOf(
    "ORDER" to "Orders", "PAYMENT" to "Payments", "USERS" to "Users", "ROLES" to "Roles", "SETTINGS" to "Settings & admin",
    "MENUS" to "Menu", "MENU" to "Menu", "KDS" to "Kitchen display", "RESERVATIONS" to "Bookings", "RESERVATION" to "Bookings",
    "SHIFT" to "Shifts", "REPORTS" to "Statistics", "FRAUD" to "Fraud detection", "POS" to "Workspaces", "ADMIN" to "Workspaces",
    "STATISTICS" to "Workspaces", "TABLES" to "Tables", "TABLE" to "Tables", "SESSIONS" to "Sign-in sessions", "RESTAURANTS" to "Restaurants",
    "BRANCHES" to "Branches", "CUSTOMERS" to "Customers", "INVENTORY" to "Inventory", "DEVICES" to "Devices"
)

private fun areaIcon(area: String): ImageVector = when (area) {
    "ORDER" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "PAYMENT" -> Icons.Outlined.CreditCard
    "USERS" -> Icons.Outlined.Group
    "ROLES" -> Icons.Outlined.Key
    "SETTINGS" -> Icons.Outlined.Settings
    "MENUS", "MENU" -> Icons.AutoMirrored.Outlined.MenuBook
    "KDS" -> Icons.Outlined.Restaurant
    "RESERVATIONS", "RESERVATION" -> Icons.Outlined.EventSeat
    "SHIFT" -> Icons.Outlined.Schedule
    "REPORTS", "STATISTICS" -> Icons.Outlined.Analytics
    "FRAUD" -> Icons.Outlined.GppMaybe
    "POS", "ADMIN" -> Icons.Outlined.Workspaces
    "TABLES", "TABLE" -> Icons.Outlined.TableRestaurant
    "INVENTORY" -> Icons.Outlined.Inventory2
    "DEVICES" -> Icons.Outlined.Devices
    "RESTAURANTS", "BRANCHES" -> Icons.Outlined.Storefront
    else -> Icons.Outlined.Tune
}

@Composable
private fun RolePermissions(state: RolesState, model: RolesScreenModel, size: ScreenSize, modifier: Modifier, onBack: (() -> Unit)?) {
    val role = state.selected
    ListPanel(modifier) {
        if (role == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                OverviewEmpty("Choose a role", "Pick a role to see and change what it allows.", Icons.Outlined.Shield, image = Res.drawable.workspace_admin)
            }
            return@ListPanel
        }
        val canEdit = state.canEditPermissions()
        val fraction = if (state.catalog.isEmpty()) 0f else state.permissionIds.size.toFloat() / state.catalog.size
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            onBack?.let {
                androidx.compose.material3.IconButton(it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to roles", tint = Kit.Ink) }
            }
            RingChart(listOf(state.permissionIds.size.toFloat(), (state.catalog.size - state.permissionIds.size).coerceAtLeast(0).toFloat()),
                percentText(fraction), "allowed", size = 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(role.label(), Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusPill(if (role.isSystem) "Built-in" else "Custom", if (role.isSystem) Kit.Blue else Kit.Green)
                    if (!role.isActive) StatusPill("Switched off", Kit.Grey)
                }
                Text(role.description?.takeIf { it.isNotBlank() } ?: "${state.permissionIds.size} of ${state.catalog.size} permissions allowed",
                    fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (state.loadingRole) CircularProgressIndicator(Modifier.size(18.dp), color = Kit.Green, strokeWidth = 2.dp)
        }
        KitDivider()
        if (!canEdit) {
            MessageBar(
                when {
                    !state.canAssign -> "You can look at permissions but not change them."
                    !state.editable(role) -> "Built-in roles can't be changed. Copy it (⋮ in the list) to make your own version."
                    else -> "This role has permissions you don't have, so only someone above you can change it."
                },
                MessageKind.INFO, Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
            )
        }
        LazyVerticalStaggeredGrid(
            StaggeredGridCells.Fixed(if (size.isDesktop) 2 else 1), Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), verticalItemSpacing = 12.dp, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.groups.mergeAreas(), key = { it.first }) { (area, choices) -> PermissionGroupCard(area, choices, canEdit, model) }
        }
        if (state.dirty) {
            KitDivider()
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFF8EC)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Unsaved changes", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF8A5A0B))
                    Text("They apply to everyone with this role once saved.", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted)
                }
                KitButton("Discard", model::discardChanges, icon = Icons.AutoMirrored.Outlined.Undo, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                Spacer(Modifier.width(8.dp))
                KitButton("Save permissions", model::savePermissions, icon = Icons.Outlined.Check, loading = state.saving)
            }
        }
    }
}

/** POS, Admin and Statistics access live in one "Workspaces" card. */
private fun List<Pair<String, List<PermissionChoice>>>.mergeAreas(): List<Pair<String, List<PermissionChoice>>> =
    groupBy { (area, _) -> AreaNames[area] ?: humanize(area) }
        .map { (name, groups) -> (groups.first().first.takeIf { groups.size == 1 } ?: name) to groups.flatMap { it.second } }

@Composable
private fun PermissionGroupCard(area: String, choices: List<PermissionChoice>, canEdit: Boolean, model: RolesScreenModel) {
    val on = choices.count { it.granted }
    val all = on == choices.size
    val anyGrantable = choices.any { it.grantable }
    val tone = ChartPalette[(area.hashCode() and 0x7fffffff) % 5]
    val code = AreaNames.entries.firstOrNull { it.value == area }?.key ?: area
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Kit.RowBorder, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().background(Kit.Canvas).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tone.copy(alpha = 0.14f)), Alignment.Center) {
                Icon(areaIcon(code), null, Modifier.size(17.dp), tint = tone)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(AreaNames[area] ?: area.takeIf { it.any(Char::isLowerCase) } ?: humanize(area), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = Kit.Ink)
                Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(RoundedCornerShape(50)).background(tone.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(if (choices.isEmpty()) 0f else on.toFloat() / choices.size).fillMaxHeight().clip(RoundedCornerShape(50)).background(tone))
                }
            }
            Text("$on/${choices.size}", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Muted)
            Spacer(Modifier.width(4.dp))
            KitSwitch(all, compact = true, onChange = { choices.map { it.permission.code.substringBefore('_') }.distinct().forEach { prefix -> model.toggleGroup(prefix, !all) } },
                enabled = canEdit && anyGrantable)
        }
        choices.forEach { choice ->
            KitDivider()
            Row(Modifier.fillMaxWidth().clickable(enabled = canEdit && choice.grantable) { model.toggle(choice.permission.id) }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(choice.permission.name.ifBlank { humanize(choice.permission.code) }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp, color = if (choice.grantable) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = choice.permission.description?.takeIf { it.isNotBlank() } ?: if (!choice.grantable) "You don't have this yourself" else null
                    detail?.let { Text(it, fontFamily = Inter(), fontSize = 10.sp, color = Kit.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                if (!choice.grantable) Icon(Icons.Outlined.Lock, "Locked", Modifier.size(14.dp), tint = Kit.Faint)
                KitSwitch(choice.granted, { model.toggle(choice.permission.id) }, enabled = canEdit && choice.grantable, compact = true)
            }
        }
    }
}

@Composable
private fun RoleFormDialog(form: RoleForm, state: RolesState, model: RolesScreenModel) {
    val title = when (form.mode) {
        RoleForm.Mode.NEW -> "New role"
        RoleForm.Mode.RENAME -> "Rename role"
        RoleForm.Mode.COPY -> "Copy role"
    }
    AppDialog(
        title, model::cancelForm,
        subtitle = when (form.mode) {
            RoleForm.Mode.NEW -> "A new role starts with no permissions. Switch them on after saving."
            RoleForm.Mode.COPY -> "The copy starts with the same permissions; change them afterwards."
            RoleForm.Mode.RENAME -> null
        },
        busy = state.saving, maxWidth = 520.dp,
        buttons = {
            KitButton("Cancel", model::cancelForm, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (form.mode == RoleForm.Mode.RENAME) "Save" else "Create role", model::saveForm, icon = Icons.Outlined.Check, loading = state.saving)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        TextInput(form.name, model::formName, label = "Name", required = true, icon = Icons.Outlined.Badge, placeholder = "Head waiter", maxLength = MAX_ROLE_NAME,
            problem = if (form.name.trim().length > MAX_ROLE_NAME) "At most $MAX_ROLE_NAME characters" else null)
        TextInput(form.description, model::formDescription, label = "Description", optional = true, singleLine = false, minLines = 3,
            placeholder = "What this role is for", maxLength = MAX_ROLE_DESCRIPTION,
            problem = if (form.description.trim().length > MAX_ROLE_DESCRIPTION) "At most $MAX_ROLE_DESCRIPTION characters" else null)
    }
}
