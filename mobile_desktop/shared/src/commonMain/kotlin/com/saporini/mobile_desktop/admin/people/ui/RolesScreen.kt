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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
private val AreaNames = linkedMapOf(
    "POS" to "POS", "KDS" to "Kitchen display", "INVENTORY" to "Inventory", "ADMIN" to "Admin Hub", "STATISTICS" to "Statistics",
    "FRAUD" to "Fraud Detection", "OTHER" to "Other"
)

/** Which workspace a permission is used in. */
private fun workspaceOf(code: String): String = when {
    code == "POS_ACCESS" || code.startsWith("ORDER_") || code.startsWith("PAYMENT_") || code.startsWith("RESERVATION_") ||
        code.startsWith("MENUS_") || code == "SHIFT_SELF" -> "POS"
    code.startsWith("KDS_") -> "KDS"
    code.startsWith("INVENTORY_") -> "INVENTORY"
    code == "ADMIN_ACCESS" || code.startsWith("USERS_") || code.startsWith("ROLES_") || code.startsWith("SETTINGS_") ||
        code.startsWith("SHIFT_") || code == "SESSIONS_MANAGE" || code.startsWith("RESTAURANTS_") -> "ADMIN"
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
    code == "SHIFT_READ" || code == "SHIFT_MANAGE" -> "Shifts & salary"
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
    "FRAUD" -> Res.drawable.workspace_fraud
    else -> null
}

private val SectionOrder = listOf("Tables", "Orders", "Payments", "Reservations", "Menu", "My shift", "History", "Shifts & salary", "Users", "Roles",
    "Settings", "Sign-in sessions", "Restaurant info")

/** The permissions by workspace: the workspace's "Open …" first, then part by part, by name inside a part. */
private fun byWorkspace(choices: List<PermissionChoice>): List<Pair<String, List<PermissionChoice>>> =
    choices.groupBy { workspaceOf(it.permission.code) }
        .toList()
        .sortedBy { (key, _) -> AreaNames.keys.indexOf(key) }
        .map { (key, list) ->
            key to list.sortedWith(compareBy<PermissionChoice> { sectionOf(it.permission.code)?.let { section -> SectionOrder.indexOf(section) + 1 } ?: 0 }
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
                WorkspaceCards(areas, canEdit, onToggle = { model.toggle(it.permission.id) },
                    onToggleAll = { choices, on -> model.tickAll(choices.map { it.permission.id }, on) })
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
    val choices = catalog.map { permission ->
        val on = permission.code in editor.ticked
        PermissionChoice(permission, on, superAdmin || permission.code in myPermissions || (on && permission.code in editor.saved))
    }
    // Only the workspaces the person works in (what their role gives, what they had, what's on now).
    val areas = byWorkspace(choices).let { all ->
        all.filter { (_, list) -> list.any { it.permission.code in editor.fromRoles || it.permission.code in editor.saved || it.granted } }.ifEmpty { all }
    }
    val extra = editor.ticked - editor.fromRoles
    val missing = editor.fromRoles - editor.ticked
    com.saporini.mobile_desktop.core.ui.PlatformBackHandler(enabled = true) { model.closePermissions() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // One step back inside the page: the plain arrow (user rule).
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = model::closePermissions), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to ${editor.roleName}", Modifier.size(22.dp), tint = Kit.Ink)
            }
            com.saporini.mobile_desktop.pos.shifts.ShiftAvatar(editor.name, if (compact) 40.dp else 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${editor.name}'s permissions", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (compact) 17.sp else 19.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Starts from what ${editor.roleName} can do", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            if (!editor.loading && (extra.isNotEmpty() || missing.isNotEmpty())) {
                KitButton(if (compact) "Reset" else "Same as the role", model::resetPersonPermissions, style = ButtonStyle.SECONDARY,
                    icon = Icons.Outlined.Restore, enabled = !editor.saving)
            }
        }
        if (editor.loading) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), color = Kit.Green, strokeWidth = 3.dp) }
            return@Column
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ScrollColumn(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(bottom = if (editor.dirty) 96.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // What's theirs alone, in one line.
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, SettingsBorder)) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                if (extra.isEmpty() && missing.isEmpty()) "Same as ${editor.roleName}"
                                else listOfNotNull(
                                    extra.size.takeIf { it > 0 }?.let { "$it more than ${editor.roleName}" },
                                    missing.size.takeIf { it > 0 }?.let { "$it taken away" }
                                ).joinToString(" · "),
                                Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = if (extra.isEmpty() && missing.isEmpty()) Kit.Ink else Kit.Green, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("${areas.sumOf { (_, list) -> list.count { it.granted } }} of ${areas.sumOf { (_, list) -> list.size }} allowed", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                color = Kit.Muted, maxLines = 1)
                        }
                    }
                    WorkspaceCards(areas, canEdit = !editor.saving,
                        onToggle = { model.tickPersonPermissions(listOf(it.permission.code), !it.granted) },
                        onToggleAll = { list, on -> model.tickPersonPermissions(list.map { it.permission.code }, on) })
                }
            }
            if (editor.dirty || editor.error != null) {
                UnsavedChangesBar(
                    saving = editor.saving, problem = null, error = editor.error,
                    onDiscard = model::closePermissions, onSave = model::savePersonPermissions,
                    message = if (compact) "Unsaved changes" else "Unsaved changes: they apply to ${editor.name} only",
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp, end = ScrollbarGap),
                    maxWidth = Dp.Unspecified
                )
            }
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
 * One area (Orders, Payments…) as a small card: a tinted header with its icon, name and how much is allowed (with a
 * switch for all of it when it can be changed), then a line per permission. A role that can be changed has small
 * switches; one that can't shows the same switches in their real colours, just not clickable (user: the toggles stay,
 * never greyed out).
 */
@Composable
private fun PermissionAreaCard(area: String, choices: List<PermissionChoice>, canEdit: Boolean, onToggle: (PermissionChoice) -> Unit,
                               onToggleAll: (Boolean) -> Unit, innerColumns: Int = 1) {
    val on = choices.count { it.granted }
    val all = on == choices.size
    val anyGrantable = choices.any { it.grantable }
    val tone = ChartPalette[(area.hashCode() and 0x7fffffff) % 5]
    val code = AreaNames.entries.firstOrNull { it.value == area }?.key ?: area
    val title = AreaNames[area] ?: area.takeIf { it.any(Char::isLowerCase) } ?: humanize(area)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, SettingsBorder)) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(tone.copy(alpha = 0.06f)).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The workspace's own picture (the ones on the workspace picker; user, 2026-10-30), else an icon.
                val picture = workspacePicture(code)
                if (picture != null) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), Alignment.Center) {
                        Image(painterResource(picture), null, Modifier.padding(3.dp).fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                } else Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Color.White), Alignment.Center) {
                    Icon(areaIcon(code), null, Modifier.size(17.dp), tint = tone)
                }
                Text(title, Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                // How much is allowed, as a small pill: green when everything, grey when nothing.
                Text(
                    when (on) { 0 -> "None"; choices.size -> "All ${choices.size}"; else -> "$on of ${choices.size}" },
                    Modifier.clip(RoundedCornerShape(50)).background(if (on == 0) Kit.Tint else Kit.GreenSoft).padding(horizontal = 9.dp, vertical = 3.dp),
                    fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (on == 0) Kit.Muted else Kit.Green, maxLines = 1
                )
                KitSwitch(all, if (canEdit) ({ _ -> onToggleAll(!all) }) else null, enabled = !canEdit || anyGrantable, compact = true)
            }
            // Its parts in order (POS → Tables, Orders, Payments…), each under a small heading; inside a part the switches go
            // in [innerColumns] columns (one card alone on a wide window).
            val parts = mutableListOf<Pair<String?, MutableList<PermissionChoice>>>()
            choices.forEach { choice ->
                val section = sectionOf(choice.permission.code)
                if (parts.isEmpty() || parts.last().first != section) parts += section to mutableListOf(choice) else parts.last().second += choice
            }
            parts.forEachIndexed { partIndex, (section, list) ->
                if (section != null) {
                    Text(section.uppercase(), Modifier.fillMaxWidth().background(tone.copy(alpha = 0.03f))
                        .padding(start = if (innerColumns > 1) 20.dp else 14.dp, end = 12.dp, top = if (innerColumns > 1) 12.dp else 9.dp, bottom = if (innerColumns > 1) 7.dp else 5.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp, color = tone, maxLines = 1)
                } else if (partIndex > 0) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Kit.RowBorder))
                list.chunked(innerColumns.coerceAtLeast(1)).forEachIndexed { rowIndex, chunk ->
                    if (rowIndex > 0) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Kit.RowBorder))
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        chunk.forEachIndexed { column, choice ->
                            if (column > 0) Box(Modifier.fillMaxHeight().padding(vertical = 10.dp).width(1.dp).background(Kit.RowBorder))
                            PermissionLine(choice, canEdit, onToggle, Modifier.weight(1f), roomy = innerColumns > 1)
                        }
                        repeat(innerColumns.coerceAtLeast(1) - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** One permission: its name and what it does, and its switch (a lock when you can't give it yourself). */
@Composable
private fun PermissionLine(choice: PermissionChoice, canEdit: Boolean, onToggle: (PermissionChoice) -> Unit, modifier: Modifier,
                           // Side by side in a wide card: more room around each line.
                           roomy: Boolean = false) {
                val editable = canEdit && choice.grantable
                Row(
                    modifier.clickable(enabled = editable) { onToggle(choice) }
                        .padding(start = if (roomy) 20.dp else 14.dp, end = if (roomy) 20.dp else 12.dp, top = if (roomy) 11.dp else 8.dp, bottom = if (roomy) 11.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(choice.permission.name.ifBlank { humanize(choice.permission.code) }, fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = if (canEdit || choice.granted) Kit.Ink else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        (if (canEdit && !choice.grantable) "You don't have this yourself, so you can't give it" else choice.permission.description?.takeIf { it.isNotBlank() })?.let {
                            Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 14.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (canEdit && !choice.grantable) Icon(Icons.Outlined.Lock, "Locked", Modifier.size(13.dp), tint = Kit.Faint)
                    KitSwitch(choice.granted, if (canEdit) ({ _ -> onToggle(choice) }) else null, enabled = !canEdit || editable, compact = true)
                }
}

/**
 * The workspace cards side by side, the columns kept about even in height. Fewer cards than columns (a waiter: just
 * POS) take the whole width together, and their switches go in columns inside (user, 2026-10-30: "why is it only on the
 * left, make it look good").
 */
@Composable
private fun WorkspaceCards(areas: List<Pair<String, List<PermissionChoice>>>, canEdit: Boolean, onToggle: (PermissionChoice) -> Unit,
                           onToggleAll: (List<PermissionChoice>, Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when { maxWidth >= 1180.dp -> 3; maxWidth >= 640.dp -> 2; else -> 1 }
        if (areas.isNotEmpty() && areas.size < columns) {
            val inner = (columns / areas.size).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                areas.forEach { (area, choices) ->
                    Box(Modifier.weight(1f)) {
                        PermissionAreaCard(area, choices, canEdit, onToggle, onToggleAll = { on -> onToggleAll(choices, on) }, innerColumns = inner)
                    }
                }
            }
        } else {
            val stacks = List(columns) { mutableListOf<Pair<String, List<PermissionChoice>>>() }
            val heights = IntArray(columns)
            areas.forEach { area ->
                val shortest = heights.indices.minBy { heights[it] }
                stacks[shortest] += area
                heights[shortest] += area.second.size + 2
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                stacks.forEach { stack ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        stack.forEach { (area, choices) ->
                            PermissionAreaCard(area, choices, canEdit, onToggle, onToggleAll = { on -> onToggleAll(choices, on) })
                        }
                    }
                }
            }
        }
    }
}

