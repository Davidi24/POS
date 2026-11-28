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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.automirrored.filled.Reply
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
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
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
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.saporini.mobile_desktop.core.components.UnsavedChangesBar
import com.saporini.mobile_desktop.core.components.SettingsSwitch
import com.saporini.mobile_desktop.core.components.SettingsRow
import com.saporini.mobile_desktop.core.components.SettingsPageBg
import com.saporini.mobile_desktop.core.components.SettingsInfoBanner
import com.saporini.mobile_desktop.core.components.SettingsCard
import com.saporini.mobile_desktop.core.components.SettingsBorder
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.ScrollbarGap
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.people.MAX_ROLE_DESCRIPTION
import com.saporini.mobile_desktop.admin.people.MAX_ROLE_NAME
import com.saporini.mobile_desktop.admin.people.PermissionChoice
import com.saporini.mobile_desktop.admin.people.PermissionTree
import com.saporini.mobile_desktop.admin.people.PermissionDto
import com.saporini.mobile_desktop.admin.people.PersonPermissionsEditor
import com.saporini.mobile_desktop.admin.people.StaffScreenModel
import com.saporini.mobile_desktop.admin.people.permissionGroups
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
import mobile_desktop.shared.generated.resources.workspace_pos
import mobile_desktop.shared.generated.resources.shifts_on_duty
import mobile_desktop.shared.generated.resources.workspace_kds
import mobile_desktop.shared.generated.resources.workspace_inventory
import mobile_desktop.shared.generated.resources.workspace_statistics
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.workspace_admin
import mobile_desktop.shared.generated.resources.workspace_fraud
import org.koin.compose.koinInject

/**
 * A role's menu: for your own roles edit (name, picture, pay), switch off or on, and remove; for a built-in role that
 * can be changed here, its pay and switching it off or on in this restaurant (user, 2026-10-31).
 */
internal fun roleActions(role: RoleDto, state: RolesState, model: RolesScreenModel): List<RowAction> = buildList {
    val editable = state.editable(role)
    if (!editable && state.canUpdate && state.permissionsEditable(role)) {
        add(RowAction("Pay", Icons.Outlined.Payments) { model.startRename(role.id) })
        // A built-in role can be switched off here too (user, 2026-10-31): it then gives nothing in this restaurant.
        add(if (role.isActive) RowAction("Switch off", Icons.Outlined.ToggleOff) { model.setRoleActive(role.id, false) }
            else RowAction("Switch on", Icons.Outlined.ToggleOn) { model.setRoleActive(role.id, true) })
    }
    if (editable && state.canUpdate) {
        add(RowAction("Edit", Icons.Outlined.DriveFileRenameOutline) { model.startRename(role.id) })
        add(if (role.isActive) RowAction("Switch off", Icons.Outlined.ToggleOff) { model.setRoleActive(role.id, false) }
            else RowAction("Switch on", Icons.Outlined.ToggleOn) { model.setRoleActive(role.id, true) })
    }
    if (editable && state.canDelete) add(RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askDelete(role.id) })
}

/** The role dialogs: new / rename / copy, and removing a role. */
@Composable
internal fun RoleDialogs(state: RolesState, model: RolesScreenModel) {
    state.form?.let { RoleFormDialog(it, state, model) }
    state.confirmDelete?.let { id ->
        val role = state.roles.firstOrNull { it.id == id }
        ConfirmDialog("Remove the role “${role?.label()}”?",
            "People who have it lose what it allows. If someone still has it, switch it off instead.",
            "Remove role", onConfirm = model::confirmDelete, onDismiss = model::dismissDelete, danger = true, busy = state.saving)
    }
}

// The permissions are shown by the workspace they're used in (user, 2026-10-30: "categorize them in POS, Inventory…"),
// in the picker's order: POS, Kitchen display, Inventory, Admin Hub, Statistics, Fraud Detection.
// My shift is its own card since 2026-11-19 (the POS and the kitchen display both show it).
private val AreaNames = linkedMapOf(
    "POS" to "POS", "KDS" to "Kitchen display", "MY_SHIFT" to "My shift", "INVENTORY" to "Inventory", "ADMIN" to "Admin Hub",
    "STATISTICS" to "Statistics", "FRAUD" to "Fraud Detection", "OTHER" to "Other"
)

/** The workspace a permission is shown in: the server says (PermissionTree), older servers by its code. */
private fun PermissionChoice.area(): String = permission.workspace ?: workspaceOf(permission.code)

/** Its place in that workspace's bar (Tables, Orders…), null for the workspace's "Open …". */
private fun PermissionChoice.place(): String? = if (permission.workspace != null) permission.part else sectionOf(permission.code)

/** Which workspace a permission is used in. */
private fun workspaceOf(code: String): String = when {
    code == "POS_ACCESS" || code.startsWith("ORDER_") || code.startsWith("PAYMENT_") || code.startsWith("RESERVATION_") ||
        code.startsWith("MENUS_") || code == "SHIFT_SELF" -> "POS"
    code.startsWith("KDS_") -> "KDS"
    code.startsWith("INVENTORY_") || code.startsWith("SUPPLIERS_") -> "INVENTORY"
    code == "ADMIN_ACCESS" || code.startsWith("USERS_") || code.startsWith("ROLES_") || code.startsWith("SETTINGS_") ||
        code.startsWith("SHIFT_") || code == "SESSIONS_MANAGE" || code.startsWith("RESTAURANTS_") || code == "SALARY_MANAGE" ||
        code.startsWith("DEVICES_") -> "ADMIN"
    code == "STATISTICS_ACCESS" || code.startsWith("REPORTS_") -> "STATISTICS"
    code.startsWith("FRAUD_") -> "FRAUD"
    else -> "OTHER"
}

/**
 * Where inside its workspace a permission is used, as the workspace itself is laid out (user, 2026-10-30: "even POS has
 * its categories, Tables, Orders… as it is"): POS → Tables, Orders, Payments, Reservations, Menu, My shift, History; Admin Hub →
 * Shifts & salary, Users, Roles, Settings, Sign-in sessions, Restaurant info. Null for the "Open …" one and workspaces
 * with a single part.
 */
private fun sectionOf(code: String): String? = when {
    code.endsWith("_ACCESS") -> null
    // As the POS bar has them (user, 2026-10-30: "I separated them, check the nav bar"): moving and joining tables is
    // Tables, looking back at closed orders is History, the rest is Orders.
    code == "ORDER_TRANSFER" -> "Tables"
    code == "ORDER_REOPEN" || code == "ORDER_AUDIT" -> "History"
    code.startsWith("ORDER_") -> "Orders"
    code.startsWith("PAYMENT_") -> "Payments"
    code.startsWith("RESERVATION_") -> "Reservations"
    code.startsWith("MENUS_") -> "Menu"
    code == "SHIFT_SELF" -> "My shift"
    code == "SHIFT_READ" || code == "SHIFT_MANAGE" -> "Shifts"
    code == "SALARY_MANAGE" -> "Salary"
    code.startsWith("DEVICES_") -> "Devices"
    code.startsWith("INVENTORY_") -> "Stock"
    code.startsWith("SUPPLIERS_") -> "Suppliers"
    code.startsWith("USERS_") -> "Users"
    code.startsWith("ROLES_") -> "Roles"
    code.startsWith("SETTINGS_") -> "Settings"
    code == "SESSIONS_MANAGE" -> "Sign-in sessions"
    code.startsWith("RESTAURANTS_") -> "Restaurant info"
    else -> null
}

/** A workspace card's picture: the same 3D pictures as the workspace picker. */
private fun workspacePicture(area: String): org.jetbrains.compose.resources.DrawableResource? = when (area) {
    "POS" -> Res.drawable.workspace_pos
    "KDS" -> Res.drawable.workspace_kds
    "INVENTORY" -> Res.drawable.workspace_inventory
    "ADMIN" -> Res.drawable.workspace_admin
    "STATISTICS" -> Res.drawable.workspace_statistics
    "MY_SHIFT" -> Res.drawable.shifts_on_duty
    "FRAUD" -> Res.drawable.workspace_fraud
    else -> null
}

private val SectionOrder = listOf("Tables", "Orders", "Payments", "Reservations", "Menu", "Kitchen Status", "My shift", "My Sales", "History",
    "Tickets", "Upcoming", "Overview", "Stock", "Items", "Counts", "Suppliers", "Shifts", "Salary", "Users", "Roles", "Settings", "Audit log",
    "Devices", "Sign-in sessions", "Restaurant info")

/**
 * The permissions by workspace, as the app is laid out: the workspace's "Open …" first, then place by place in its bar's
 * order. The Super Admin's own (creating restaurants, settings templates…) never show inside a restaurant.
 */
internal fun byWorkspace(choices: List<PermissionChoice>): List<Pair<String, List<PermissionChoice>>> =
    choices.filterNot { it.permission.platformOnly || it.area() == "PLATFORM" || it.permission.bundledInto != null }
        .groupBy { it.area() }
        .toList()
        .sortedBy { (key, _) -> AreaNames.keys.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it } }
        .map { (key, list) ->
            key to list.sortedWith(compareBy<PermissionChoice> { it.permission.position ?: Int.MAX_VALUE }
                .thenBy { it.place()?.let { section -> SectionOrder.indexOf(section) + 1 } ?: 0 }
                .thenBy { it.permission.name })
        }

private fun areaIcon(area: String): ImageVector = when (area) {
    "POS" -> Icons.Outlined.PointOfSale
    "ADMIN" -> Icons.Outlined.AdminPanelSettings
    "RESTAURANT" -> Icons.Outlined.Storefront
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

/**
 * What the chosen role may do, set like a Settings page: how much it allows, then a card per area with a switch for the
 * whole area and one per permission, and the floating bar while changes aren't saved. The role page shows the role.
 */
@Composable
internal fun RolePermissionsPanel(state: RolesState, model: RolesScreenModel, modifier: Modifier, compact: Boolean,
                                  // In the role card's Permissions dialog, whose own buttons save (no floating bar).
                                  inDialog: Boolean = false) {
    val role = state.selected
    if (role == null || (state.loadingRole && state.permissionIds.isEmpty())) {
        Box(modifier, Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), color = Kit.Green, strokeWidth = 3.dp) }
        return
    }
    val canEdit = state.canEditPermissions()
    // Only the workspaces the role works in (user, 2026-10-30: "the waiter should have only POS, not KDS or others"): the
    // cards it has something in (as saved, or switched on now). A role with nothing yet shows them all.
    val areas = byWorkspace(state.groups.flatMap { it.second }).let { all ->
        all.filter { (_, choices) -> choices.any { it.permission.id in state.savedPermissionIds || it.granted } }.ifEmpty { all }
    }
    val allowed = areas.sumOf { (_, choices) -> choices.count { it.granted } }
    val total = areas.sumOf { (_, choices) -> choices.size }
    Box(modifier) {
        ScrollColumn(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(bottom = if (state.dirty && !inDialog) 96.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // How much it allows, as a slim bar with the count (on a phone the bar goes under the title, which keeps the whole line).
                val bar: @Composable (Modifier) -> Unit = { m ->
                    Box(m.height(5.dp).clip(RoundedCornerShape(50)).background(Kit.Green.copy(alpha = 0.13f))) {
                        Box(Modifier.fillMaxWidth(if (total == 0) 0f else allowed.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(50)).background(Kit.Green))
                    }
                }
                val count: @Composable () -> Unit = {
                    Text("$allowed of $total allowed", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, SettingsBorder)) {
                    if (compact) Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("What ${role.label()} can do", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            count()
                        }
                        bar(Modifier.fillMaxWidth())
                    } else Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("What ${role.label()} can do", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        bar(Modifier.weight(1f))
                        count()
                    }
                }
                if (!canEdit) {
                    ReadOnlyNote(
                        when {
                            !state.canAssign -> "You can look at what this role allows, but not change it."
                            !state.permissionsEditable(role) -> "Only someone above this role can change it."
                            else -> "This role has permissions you don't have, so only someone above you can change it."
                        },
                        onCopy = null,
                        compact = compact
                    )
                }
                androidx.compose.runtime.CompositionLocalProvider(LocalScopeHooks provides ScopeHooks(state.scopes, state.scopeChoices, canEdit, model::setScope)) {
                    WorkspaceCards(areas, PermissionTree(state.catalog), canEdit, onToggle = { model.tickAll(listOf(it.permission.id), !it.granted) },
                        onToggleAll = { choices, on -> model.tickAll(choices.map { it.permission.id }, on) })
                }
            }
        }
        if (state.dirty && !inDialog) {
            UnsavedChangesBar(
                saving = state.saving, problem = null, error = null,
                onDiscard = model::discardChanges, onSave = model::savePermissions,
                // A phone has room for the short version only.
                message = if (compact) "Unsaved changes" else "Unsaved changes: they apply to everyone with ${role.label()}",
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp, end = ScrollbarGap),
                maxWidth = Dp.Unspecified
            )
        }
    }
}

/**
 * One person's own permissions, as a page inside Users with a back arrow (user, 2026-10-30: "every person has the button
 * Permissions and I can modify their permissions"; "it opens in there with a back button", not a dialog). The same area
 * cards as a role, starting from what their role gives them; what differs is counted on top, "Same as the role" takes it
 * all back, and the floating bar saves.
 */
@Composable
internal fun PersonPermissionsPage(editor: PersonPermissionsEditor, catalog: List<PermissionDto>, myPermissions: Set<String>, superAdmin: Boolean,
                                   model: StaffScreenModel, compact: Boolean, modifier: Modifier) {
    val tree = remember(catalog) { PermissionTree(catalog) }
    // What counts: a permission only with what it needs (PermissionTree).
    val shown = tree.shown(editor.ticked)
    val choices = catalog.map { permission ->
        val on = permission.code in shown
        PermissionChoice(permission, on, superAdmin || permission.code in myPermissions || permission.code in editor.saved)
    }
    // Only the workspaces the person works in (what their role gives, what they had, what's on now).
    val areas = byWorkspace(choices).let { all ->
        all.filter { (_, list) -> list.any { it.permission.code in editor.fromRoles || it.permission.code in editor.saved || it.granted } }.ifEmpty { all }
    }
    val extra = editor.ticked - editor.fromRoles
    val missing = editor.fromRoles - editor.ticked
    val allowedCount = areas.sumOf { (_, list) -> list.count { it.granted } }
    val totalCount = areas.sumOf { (_, list) -> list.size }
    val same = extra.isEmpty() && missing.isEmpty() && editor.scopes.isEmpty()
    // Leaving with changes not saved asks first (user, 2026-11-20).
    var leaving by remember { mutableStateOf(false) }
    val leave = { if (editor.dirty) leaving = true else model.closePermissions() }
    com.saporini.mobile_desktop.core.ui.PlatformBackHandler(enabled = true) { leave() }
    if (leaving) com.saporini.mobile_desktop.core.components.UnsavedLeaveDialog(
        onDiscard = { leaving = false; model.closePermissions() },
        onSave = { leaving = false; model.savePersonPermissions(thenClose = true) },
        onStay = { leaving = false }, saving = editor.saving,
        message = "You changed ${editor.name}'s permissions and didn't save them.")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Who, from which role, and how far from it (user, 2026-11-20: "make the page look good").
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, SettingsBorder)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // One step back inside the page: the plain arrow (user rule).
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { leave() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to ${editor.roleName}", Modifier.size(22.dp), tint = Kit.Ink)
                    }
                    com.saporini.mobile_desktop.pos.shifts.ShiftAvatar(editor.name, if (compact) 40.dp else 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${editor.name}'s permissions", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (compact) 17.sp else 19.sp,
                            color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(editor.roleName, Modifier.clip(RoundedCornerShape(50)).background(Kit.Green.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                                color = Kit.Green, maxLines = 1)
                            if (!compact) Text("Starts from what ${editor.roleName} can do", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!editor.loading && !same) {
                        KitButton(if (compact) "Reset" else "Same as the role", model::resetPersonPermissions, style = ButtonStyle.SECONDARY,
                            icon = Icons.Outlined.Restore, enabled = !editor.saving)
                    }
                }
                if (!editor.loading) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsBorder))
                    Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAF8)).padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (same) Kit.Muted else Kit.Amber))
                        Text(
                            if (same) "Same as ${editor.roleName}"
                            else listOfNotNull(
                                extra.size.takeIf { it > 0 }?.let { "$it more than ${editor.roleName}" },
                                missing.size.takeIf { it > 0 }?.let { "$it taken away" },
                                editor.scopes.size.takeIf { it > 0 }?.let { "their own floors or roles" }
                            ).joinToString(" · "),
                            Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (same) Kit.Ink else Kit.Amber, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("$allowedCount of $totalCount allowed", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted,
                            maxLines = 1)
                    }
                }
            }
        }
        if (editor.loading) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), color = Kit.Green, strokeWidth = 3.dp) }
            return@Column
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ScrollColumn(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(bottom = if (editor.dirty) 96.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalScopeHooks provides ScopeHooks(editor.scopes, editor.scopeChoices, !editor.saving, model::setPersonScope)) {
                        WorkspaceCards(areas, tree, canEdit = !editor.saving,
                            onToggle = { model.tickPersonPermissions(listOf(it.permission.code), !it.granted, tree) },
                            onToggleAll = { list, on -> model.tickPersonPermissions(list.map { it.permission.code }, on, tree) })
                    }
                }
            }
            if (editor.dirty || editor.error != null) {
                UnsavedChangesBar(
                    saving = editor.saving, problem = null, error = editor.error,
                    onDiscard = model::discardPersonPermissions, onSave = model::savePersonPermissions,
                    message = if (compact) "Unsaved changes" else "Unsaved changes: they apply to ${editor.name} only",
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp, end = ScrollbarGap),
                    maxWidth = Dp.Unspecified
                )
            }
            // Saving keeps the page and plays "Saved" (user, 2026-11-20).
            com.saporini.mobile_desktop.core.components.DoneBurst(
                editor.savedTick.takeIf { it > 0 }?.let { com.saporini.mobile_desktop.core.components.DoneMark(it, "Permissions saved") },
                Modifier.align(Alignment.Center))
        }
    }
}

/** Why the permissions can't be changed here, with "Make a copy" for a built-in role (under the words on a phone, [compact]). */
@Composable
private fun ReadOnlyNote(text: String, onCopy: (() -> Unit)?, compact: Boolean) {
    val box = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, SettingsBorder, RoundedCornerShape(12.dp))
        .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
    val words: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Kit.Tint), Alignment.Center) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(17.dp), tint = Kit.Muted)
            }
            Text(text, Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Ink)
        }
    }
    if (compact && onCopy != null) {
        Column(box, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            words(Modifier.fillMaxWidth())
            KitButton("Make a copy", onCopy, Modifier.fillMaxWidth(), icon = Icons.Outlined.ContentCopy, style = ButtonStyle.SECONDARY)
        }
    } else Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        words(Modifier.weight(1f))
        onCopy?.let { KitButton("Make a copy", it, icon = Icons.Outlined.ContentCopy, style = ButtonStyle.SECONDARY) }
    }
}

/**
 * The workspace cards, POS first (user, 2026-11-19: "POS completely at top, then go down with the others"); small cards of
 * about the same size (Inventory, Kitchen display…) sit side by side on a wide window (user: "if it hasn't so many
 * permissions, make it left and right"). Each card's switch is its "Open …"; with it off the card only says so. Inside,
 * each part starts with its "View …" and what needs it sits under it. Giving an owner-level permission asks first ("be
 * careful").
 */
@Composable
internal fun WorkspaceCards(areas: List<Pair<String, List<PermissionChoice>>>, tree: PermissionTree, canEdit: Boolean,
                            onToggle: (PermissionChoice) -> Unit, onToggleAll: (List<PermissionChoice>, Boolean) -> Unit) {
    // Switching something owner-level on waits for "Give it" (the list to switch, and what then happens).
    var careful by remember { mutableStateOf<Pair<List<PermissionChoice>, () -> Unit>?>(null) }
    fun guarded(list: List<PermissionChoice>, on: Boolean, action: () -> Unit) {
        // Everything that would come on: the ones switched and what they need.
        val byCode = list.firstOrNull()?.let { areas.flatMap { (_, c) -> c }.associateBy { c -> c.permission.code } } ?: emptyMap()
        val coming = if (!on) emptyList() else list.flatMap { choice -> listOf(choice) + tree.ancestors(choice.permission.code).mapNotNull { byCode[it] } }
            .filter { !it.granted && it.permission.careful }.distinctBy { it.permission.code }
        if (coming.isEmpty()) action() else careful = coming to action
    }
    val toggle: (PermissionChoice) -> Unit = { choice -> guarded(listOf(choice), !choice.granted) { onToggle(choice) } }
    val toggleAll: (List<PermissionChoice>, Boolean) -> Unit = { list, on -> guarded(list, on) { onToggleAll(list, on) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pairUp = maxWidth >= 900.dp
        // Rows of cards: a big card alone, two small ones together.
        val rows = mutableListOf<List<Pair<String, List<PermissionChoice>>>>()
        areas.forEach { area ->
            val small = isSmallCard(area.second)
            val last = rows.lastOrNull()
            if (pairUp && small && last != null && last.size == 1 && isSmallCard(last[0].second)) rows[rows.lastIndex] = last + area
            else rows += listOf(area)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                // A small card keeps half the width even alone, the other half left empty (user, 2026-11-19).
                val small = pairUp && isSmallCard(row[0].second)
                if (row.size == 1 && !small) WorkspaceCard(row[0].first, row[0].second, tree, canEdit, toggle, onToggleAll = { on -> toggleAll(row[0].second, on) })
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (area, choices) ->
                        Box(Modifier.weight(1f)) { WorkspaceCard(area, choices, tree, canEdit, toggle, onToggleAll = { on -> toggleAll(choices, on) }) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    careful?.let { (coming, action) ->
        ConfirmDialog(
            "Be careful",
            if (coming.size == 1) "“${coming[0].permission.name.ifBlank { humanize(coming[0].permission.code) }}” is usually only for owners. Give it only to someone you trust."
            else "These are usually only for owners. Give them only to someone you trust.",
            "Give it", onConfirm = { action(); careful = null }, onDismiss = { careful = null }, danger = true,
            extra = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    coming.forEach { choice ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Shield, null, Modifier.padding(top = 2.dp).size(15.dp), tint = Kit.Amber)
                            Column {
                                Text(choice.permission.name.ifBlank { humanize(choice.permission.code) }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, color = Kit.Ink)
                                choice.permission.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

/** A card with only a few permissions (Kitchen display, My shift, Statistics, Fraud Detection): half the width on a wide window. */
private fun isSmallCard(choices: List<PermissionChoice>): Boolean = choices.size <= 9

@Composable
private fun WorkspaceCard(area: String, choices: List<PermissionChoice>, tree: PermissionTree, canEdit: Boolean,
                          onToggle: (PermissionChoice) -> Unit, onToggleAll: (Boolean) -> Unit) {
    val tone = ChartPalette[(area.hashCode() and 0x7fffffff) % 5]
    val code = AreaNames.entries.firstOrNull { it.value == area }?.key ?: area
    val title = AreaNames[area] ?: area.takeIf { it.any(Char::isLowerCase) } ?: humanize(area)
    // The workspace's own "Open …": the card's switch. Workspaces without one switch everything at once.
    val access = choices.firstOrNull { it.permission.code.endsWith("_ACCESS") && tree.parentOf(it.permission.code) == null }
    val rest = choices.filter { it !== access }
    val open = access?.granted ?: true
    val on = choices.count { it.granted }
    val all = on == choices.size
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, SettingsBorder)) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(tone.copy(alpha = if (open) 0.07f else 0.025f))
                    .clickable(enabled = canEdit && (access?.grantable ?: true)) { if (access != null) onToggle(access) else onToggleAll(!all) }
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val picture = workspacePicture(code)
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(Color.White).alpha(if (open) 1f else 0.5f), Alignment.Center) {
                    if (picture != null) Image(painterResource(picture), null, Modifier.padding(3.dp).fillMaxSize(), contentScale = ContentScale.Fit)
                    else Icon(areaIcon(code), null, Modifier.size(19.dp), tint = tone)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (open) Kit.Ink else Kit.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            // A card of one (My shift) says what it is.
                            access == null && choices.size == 1 -> choices[0].permission.description?.takeIf { it.isNotBlank() } ?: (if (on == 0) "Off" else "On")
                            access == null -> if (on == 0) "Nothing here yet" else "$on of ${choices.size} allowed"
                            !open -> "Can't open $title"
                            else -> "$on of ${choices.size} allowed"
                        },
                        fontFamily = Inter(), fontSize = 12.sp, color = if (open && access != null) tone else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (canEdit && open && !all && rest.any { it.grantable }) {
                    Text("Turn all on", Modifier.clip(RoundedCornerShape(8.dp)).clickable { onToggleAll(true) }.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
                }
                if (canEdit && access != null && !access.grantable) Icon(Icons.Outlined.Lock, "Locked", Modifier.size(14.dp), tint = Kit.Faint)
                KitSwitch(if (access != null) access.granted else all,
                    if (canEdit) ({ _ -> if (access != null) onToggle(access) else onToggleAll(!all) }) else null,
                    enabled = !canEdit || (access?.grantable ?: rest.any { it.grantable }))
            }
            if (!open) {
                Text("Switch it on to choose what they can do in $title.", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    fontFamily = Inter(), fontSize = 12.5.sp, color = Kit.Muted)
            } else if (rest.isNotEmpty() && !(access == null && choices.size == 1)) {
                // Its parts (POS → Tables, Orders, Payments…) as soft blocks in columns, kept about even.
                val parts = mutableListOf<Pair<String?, MutableList<PermissionChoice>>>()
                rest.forEach { choice ->
                    val section = choice.place()
                    val existing = parts.firstOrNull { it.first == section }
                    if (existing == null) parts += section to mutableListOf(choice) else existing.second += choice
                }
                // Each place of the workspace's bar is a card of its own (user, 2026-11-19: "a card for each, Tables, Orders,
                // Menu"), its "See …" as the card's switch.
                BoxWithConstraints(Modifier.fillMaxWidth().background(Color(0xFFF7F7F5)).padding(10.dp)) {
                    val columns = when { maxWidth >= 860.dp -> 3; maxWidth >= 420.dp -> 2; else -> 1 }.coerceAtMost(parts.size)
                    val stacks = List(columns) { mutableListOf<Pair<String?, List<PermissionChoice>>>() }
                    val heights = IntArray(columns)
                    parts.forEach { part ->
                        val shortest = heights.indices.minBy { heights[it] }
                        stacks[shortest] += part
                        heights[shortest] += part.second.size + 1
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        stacks.forEach { stack ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                stack.forEach { (section, list) -> PermissionPart(section, list, choices, tree, tone, canEdit, onToggle) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One place of a workspace (Tables, Orders, Stock…) as a card: its name and its "See …" switch on top (user, 2026-11-19:
 * "maybe I don't let someone see the tables at all"), then what changes things there, each under the one it needs. With
 * the place off, the rest stays folded away.
 */
@Composable
private fun PermissionPart(section: String?, list: List<PermissionChoice>, all: List<PermissionChoice>, tree: PermissionTree, tone: Color,
                           canEdit: Boolean, onToggle: (PermissionChoice) -> Unit) {
    val inPart = list.map { it.permission.code }.toSet()
    val byCode = all.associateBy { it.permission.code }
    val accessCode = all.firstOrNull { p -> p.permission.code.endsWith("_ACCESS") && tree.parentOf(p.permission.code) == null }?.permission?.code
    // The card's own switch: the first that needs nothing in this place (its "See …"); a place of one has only that.
    val head = list.firstOrNull { tree.parentOf(it.permission.code) !in inPart } ?: return
    val ordered = mutableListOf<Pair<PermissionChoice, Int>>()
    fun place(choice: PermissionChoice, depth: Int) {
        ordered += choice to depth
        list.filter { tree.parentOf(it.permission.code) == choice.permission.code }.forEach { place(it, depth + 1) }
    }
    list.filter { it !== head && tree.parentOf(it.permission.code) == head.permission.code }.forEach { place(it, 0) }
    list.filter { it !== head && tree.parentOf(it.permission.code) !in inPart }.forEach { place(it, 0) }
    // The place needs something from another one that's off (Payments needs "See orders").
    val parent = tree.parentOf(head.permission.code)?.let { byCode[it] }
    val waiting = parent != null && !parent.granted && parent.permission.code != accessCode
    val editable = canEdit && head.grantable
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, SettingsBorder, RoundedCornerShape(12.dp))
            .alpha(if (waiting) 0.5f else 1f)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = editable) { onToggle(head) }
                .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(width = 3.dp, height = 30.dp).clip(RoundedCornerShape(50)).background(if (head.granted) tone else Kit.Border))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(section ?: head.permission.name.ifBlank { humanize(head.permission.code) }, Modifier.weight(1f, fill = false),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = if (head.granted) Kit.Ink else Kit.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (head.permission.careful) Icon(Icons.Outlined.Shield, "Owner-level", Modifier.size(13.dp), tint = Kit.Amber)
                }
                val line = when {
                    canEdit && !head.grantable -> "You don't have this yourself"
                    waiting -> "Needs “${parent!!.permission.name.ifBlank { humanize(parent.permission.code) }}”"
                    section != null && list.size > 1 -> head.permission.name.ifBlank { humanize(head.permission.code) }
                    else -> head.permission.description?.takeIf { it.isNotBlank() }
                }
                line?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 14.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (canEdit && !head.grantable) Icon(Icons.Outlined.Lock, "Locked", Modifier.size(13.dp), tint = Kit.Faint)
            KitSwitch(head.granted, if (canEdit) ({ _ -> onToggle(head) }) else null, enabled = !canEdit || editable, compact = true)
        }
        // Where it counts: branches, floors and places as subgroups (user, 2026-11-20).
        if (head.granted && !waiting) ScopeSection(head, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp))
        if (ordered.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsBorder))
            if (!head.granted) {
                Text(if (ordered.size == 1) "1 more when it's on" else "${ordered.size} more when it's on",
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 8.dp), fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Faint)
            } else Column(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                ordered.forEach { (choice, depth) ->
                    val needs = tree.parentOf(choice.permission.code)?.let { byCode[it] }
                    // Needs something that's off: faded; when that's in another place, the line says what.
                    val off = needs != null && !needs.granted && needs.permission.code != accessCode
                    val note = if (off && needs!!.permission.code !in inPart) "Needs “${needs.permission.name.ifBlank { humanize(needs.permission.code) }}”" else null
                    PermissionLine(choice, depth, off, note, canEdit, onToggle)
                }
            }
        }
    }
}

/** One permission: indented under what it needs, its name (a part's first line also says what it does), and its switch. */
@Composable
private fun PermissionLine(choice: PermissionChoice, depth: Int, waiting: Boolean, note: String?, canEdit: Boolean, onToggle: (PermissionChoice) -> Unit) {
    val editable = canEdit && choice.grantable
    Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = editable) { onToggle(choice) }
            .padding(start = 12.dp + 18.dp * depth, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .alpha(if (waiting) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (depth > 0) Box(Modifier.size(width = 2.dp, height = 18.dp).clip(RoundedCornerShape(50)).background(Kit.Border))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(choice.permission.name.ifBlank { humanize(choice.permission.code) }, Modifier.weight(1f, fill = false), fontFamily = Inter(),
                    fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp,
                    color = if (canEdit || choice.granted) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Owner-level: a small shield, and giving it asks first.
                if (choice.permission.careful) Icon(Icons.Outlined.Shield, "Owner-level", Modifier.size(13.dp), tint = Kit.Amber)
            }
            (when {
                canEdit && !choice.grantable -> "You don't have this yourself"
                note != null -> note
                depth == 0 -> choice.permission.description?.takeIf { it.isNotBlank() }
                else -> null
            })?.let {
                Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 14.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canEdit && !choice.grantable) Icon(Icons.Outlined.Lock, "Locked", Modifier.size(13.dp), tint = Kit.Faint)
        KitSwitch(choice.granted, if (canEdit) ({ _ -> onToggle(choice) }) else null, enabled = !canEdit || editable, compact = true)
    }
    if (choice.granted && !waiting) ScopeSection(choice, Modifier.padding(start = 12.dp + 18.dp * depth, end = 12.dp, bottom = 8.dp))
    }
}

/**
 * Some floors / roles only (user, 2026-11-20): what the open editor has chosen ([chosen]: permission → targets, missing =
 * all), what can be chosen, and how to change it.
 */
internal class ScopeHooks(
    val chosen: Map<String, List<String>>,
    val choices: com.saporini.mobile_desktop.admin.people.ScopeChoicesDto,
    val canEdit: Boolean,
    val onChange: (String, List<String>) -> Unit
)

internal val LocalScopeHooks = androidx.compose.runtime.staticCompositionLocalOf<ScopeHooks?> { null }

/** One thing to choose in a scope: a branch, a floor, a place or a role, with what's under it. */
private class ScopeNode(val target: String, val label: String, val children: List<ScopeNode> = emptyList(), val detail: String? = null) {
    // The places (or floors without places) it stands for: what's actually chosen.
    val leaves: List<String> get() = if (children.isEmpty()) listOf(target) else children.flatMap { it.leaves }
}

/** The tree to choose from: branches → floors → places (a branch level only with several branches), or the roles. */
private fun scopeTree(kind: String, choices: com.saporini.mobile_desktop.admin.people.ScopeChoicesDto): List<ScopeNode> {
    if (kind != "FLOORS") return choices.roles.map { ScopeNode(it.target, it.name) }
    fun floorsOf(branchId: String?) = choices.floors.filter { it.branchId == branchId }.map { floor ->
        val places = choices.places.filter { it.branchId == branchId && it.floor == floor.name }.map { ScopeNode(it.target, it.name) }
        ScopeNode(floor.target, floor.name, places, detail = if (places.isEmpty()) null else if (places.size == 1) "1 place" else "${places.size} places")
    }
    val branchIds = choices.floors.map { it.branchId }.distinct()
    if (branchIds.size <= 1) return floorsOf(branchIds.firstOrNull())
    return branchIds.map { id ->
        val name = choices.branches.firstOrNull { it.branchId == id }?.name ?: choices.floors.firstOrNull { it.branchId == id }?.branchName ?: "Branch"
        val floors = floorsOf(id)
        ScopeNode("branch:$id", name, floors, detail = if (floors.size == 1) "1 floor" else "${floors.size} floors")
    }
}

/** What's chosen, as places (and floors without places): everything when nothing is stored. */
private fun chosenLeaves(tree: List<ScopeNode>, stored: List<String>?): Set<String> {
    val all = tree.flatMap { it.leaves }.toSet()
    if (stored == null) return all
    val out = LinkedHashSet<String>()
    fun walk(nodes: List<ScopeNode>) {
        nodes.forEach { node -> if (node.target in stored) out.addAll(node.leaves) else walk(node.children) }
    }
    walk(tree)
    return out
}

/** The shortest way to store [chosen]: a whole branch or floor when all of it is chosen; nothing = all of them. */
private fun storedFor(tree: List<ScopeNode>, chosen: Set<String>): List<String> {
    if (tree.flatMap { it.leaves }.all { it in chosen }) return emptyList()
    val out = mutableListOf<String>()
    fun walk(nodes: List<ScopeNode>) {
        nodes.forEach { node -> if (node.leaves.all { it in chosen }) out += node.target else walk(node.children) }
    }
    walk(tree)
    return out
}

/**
 * Under "See tables" / "See others' shifts" (user, 2026-11-20: "hard to understand that you can edit it; branches, floors
 * or places as subgroups"): a line saying where it counts with a clear Choose button; open, the branches, floors and places
 * (or roles) as a tree to tick, each level under the one it belongs to.
 */
@Composable
private fun ScopeSection(choice: PermissionChoice, modifier: Modifier = Modifier) {
    val kind = choice.permission.scope ?: return
    val hooks = LocalScopeHooks.current ?: return
    val floors = kind == "FLOORS"
    val tree = remember(hooks.choices, kind) { scopeTree(kind, hooks.choices) }
    if (tree.isEmpty()) return
    val stored = hooks.chosen[choice.permission.code]
    val chosen = chosenLeaves(tree, stored)
    val all = tree.flatMap { it.leaves }
    var open by androidx.compose.runtime.saveable.rememberSaveable(choice.permission.code) { mutableStateOf(false) }
    val summary = when {
        stored == null -> if (floors) "Everywhere: every floor and place" else "Every role"
        else -> {
            val names = mutableListOf<String>()
            fun walk(nodes: List<ScopeNode>) { nodes.forEach { n -> if (n.leaves.all { it in chosen }) names += n.label else walk(n.children) } }
            walk(tree)
            if (names.size <= 3) names.joinToString(", ") else "${names.take(2).joinToString(", ")} and ${names.size - 2} more"
        }
    }
    fun toggle(node: ScopeNode) {
        if (!hooks.canEdit) return
        val next = if (node.leaves.all { it in chosen }) chosen - node.leaves.toSet() else chosen + node.leaves
        if (next.isEmpty()) return
        hooks.onChange(choice.permission.code, storedFor(tree, next))
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Green.copy(alpha = 0.06f))
        .border(1.dp, Kit.Green.copy(alpha = 0.22f), RoundedCornerShape(10.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(if (floors) Icons.Outlined.Layers else Icons.Outlined.Group, null, Modifier.size(15.dp), tint = Kit.Green)
            Column(Modifier.weight(1f)) {
                Text(if (floors) "Where" else "Whose shifts", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp,
                    letterSpacing = 0.4.sp, color = Kit.Muted, maxLines = 1)
                Text(summary, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White).border(1.dp, Kit.Green.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (open) "Done" else if (hooks.canEdit) "Choose" else "See", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                    color = Kit.Green, maxLines = 1)
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(15.dp), tint = Kit.Green)
            }
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Green.copy(alpha = 0.18f)))
            Column(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 4.dp)) {
                ScopeRow(if (floors) "Everywhere" else "Every role", if (chosen.size == all.size) TriState.ON else TriState.SOME, 0, bold = true,
                    enabled = hooks.canEdit) { if (chosen.size != all.size) hooks.onChange(choice.permission.code, emptyList()) }
                ScopeRows(tree, 1, chosen, hooks.canEdit, ::toggle)
                if (hooks.canEdit) Text("At least one stays chosen.", Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), fontFamily = Inter(),
                    fontSize = 11.sp, color = Kit.Faint)
            }
        }
    }
}

private enum class TriState { ON, SOME, OFF }

/** Each node and, indented under it, what it holds. */
@Composable
private fun ScopeRows(nodes: List<ScopeNode>, depth: Int, chosen: Set<String>, enabled: Boolean, onToggle: (ScopeNode) -> Unit) {
    nodes.forEach { node ->
        val on = node.leaves.count { it in chosen }
        ScopeRow(node.label, when (on) { 0 -> TriState.OFF; node.leaves.size -> TriState.ON; else -> TriState.SOME }, depth,
            detail = node.detail, enabled = enabled) { onToggle(node) }
        ScopeRows(node.children, depth + 1, chosen, enabled, onToggle)
    }
}

@Composable
private fun ScopeRow(name: String, state: TriState, depth: Int, bold: Boolean = false, detail: String? = null, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(start = 10.dp + 20.dp * depth, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        val filled = state != TriState.OFF
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(if (filled) Kit.Green else Color.White)
            .border(1.5.dp, if (filled) Kit.Green else Kit.Border, RoundedCornerShape(5.dp)), Alignment.Center) {
            when (state) {
                TriState.ON -> Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
                TriState.SOME -> Box(Modifier.size(width = 9.dp, height = 2.dp).clip(RoundedCornerShape(50)).background(Color.White))
                TriState.OFF -> Unit
            }
        }
        Text(name, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = if (bold || depth <= 1) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1) }
    }
}
