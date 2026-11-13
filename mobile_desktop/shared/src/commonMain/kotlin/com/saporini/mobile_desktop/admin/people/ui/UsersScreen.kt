package com.saporini.mobile_desktop.admin.people.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import com.saporini.mobile_desktop.admin.people.PEOPLE_PAGE_SIZE
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.inventory.ui.OptionGroup
import com.saporini.mobile_desktop.admin.inventory.ui.OptionLine
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneControlHeight
import com.saporini.mobile_desktop.admin.inventory.ui.PhoneOptionsButton
import com.saporini.mobile_desktop.admin.people.PlatformRoleCodes
import com.saporini.mobile_desktop.admin.people.RoleDto
import com.saporini.mobile_desktop.admin.people.RoleStats
import com.saporini.mobile_desktop.admin.people.RolesScreenModel
import com.saporini.mobile_desktop.admin.people.RolesState
import com.saporini.mobile_desktop.admin.people.StaffScreenModel
import com.saporini.mobile_desktop.admin.people.StaffState
import com.saporini.mobile_desktop.admin.people.StaffUserDto
import com.saporini.mobile_desktop.core.components.BindToLifecycle
import com.saporini.mobile_desktop.core.components.ConfirmDialog
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.LocalNarrowDialog
import com.saporini.mobile_desktop.core.components.LoadMoreWhenNearEnd
import com.saporini.mobile_desktop.core.components.OverviewEmpty
import com.saporini.mobile_desktop.core.components.OverviewPageSkeleton
import com.saporini.mobile_desktop.core.components.SkeletonBox
import com.saporini.mobile_desktop.core.components.SkeletonLight
import com.saporini.mobile_desktop.core.components.rememberSkeletonAlpha
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
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
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.ScrollList
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.StatusChip
import com.saporini.mobile_desktop.core.components.StatusPill
import com.saporini.mobile_desktop.core.components.SummaryCardRow
import com.saporini.mobile_desktop.core.components.ToolbarButton
import com.saporini.mobile_desktop.core.components.ToolbarPrimaryButton
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.core.ui.HoverTooltip
import com.saporini.mobile_desktop.core.ui.ScreenSize
import com.saporini.mobile_desktop.core.ui.screenSizeFor
import com.saporini.mobile_desktop.pos.shifts.AvatarStack
import com.saporini.mobile_desktop.pos.shifts.ShiftAvatar
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_guests
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.salary_cleaning
import mobile_desktop.shared.generated.resources.salary_extra
import mobile_desktop.shared.generated.resources.salary_kitchen
import mobile_desktop.shared.generated.resources.salary_managers
import mobile_desktop.shared.generated.resources.salary_waiters
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.workspace_admin
import mobile_desktop.shared.generated.resources.workspace_fraud
import mobile_desktop.shared.generated.resources.users_admin
import mobile_desktop.shared.generated.resources.users_owner
import mobile_desktop.shared.generated.resources.users_viewer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

// Admin Hub → Users, in the Salary look (user rule): a card per role (Owners, Admin,
// Manager, Kitchen, Waiter, Cleaner…), three a row. A role opens its own page: its people (add, edit, switch off,
// password reset, remove) and what the role may do (its permissions). Lines aren't clickable, only their buttons.

/** The roles of the platform itself: shown only when someone in this restaurant has one. */

/** Owner and Co-Owner share one card, "Owners" (user rule); inside, a switch picks which of the two you're looking at. */
private val OwnerRoles = listOf("OWNER", "CO_OWNER")
private const val OWNERS_CARD = "OWNERS"

private fun ownersCard(owners: List<RoleDto>) = RoleDto(
    id = OWNERS_CARD, code = OWNERS_CARD, name = "Owners", description = "The owner and the co-owners who run the restaurant",
    rank = owners.maxOf { it.rank ?: 0 }, isSystem = true, isActive = true
)

/** A role's colour and picture, matched by its code or name (custom roles like "Cleaner" go by their name). */
private class RoleLook(val color: Color, val image: DrawableResource)

private fun lookOf(role: RoleDto): RoleLook {
    // A picture the restaurant picked for its own role comes first.
    rolePicture(role.picture)?.let { return RoleLook(it.color, it.image) }
    val words = (role.code + " " + role.name).uppercase()
    fun has(vararg keys: String) = keys.any { it in words }
    return when {
        has("SUPER_ADMIN") -> RoleLook(Color(0xFF6B4E9B), Res.drawable.workspace_fraud)
        role.code == "OWNER" || role.code == OWNERS_CARD -> RoleLook(Color(0xFFB0812C), Res.drawable.users_owner)
        role.code == "CO_OWNER" -> RoleLook(Color(0xFF9A7434), Res.drawable.users_owner)
        role.code == "ADMIN" || has("ADMIN") -> RoleLook(Color(0xFF2E7D7A), Res.drawable.users_admin)
        has("MANAGER", "SUPERVISOR") -> RoleLook(Kit.Blue, Res.drawable.salary_managers)
        has("KITCHEN", "CHEF", "COOK") -> RoleLook(Color(0xFF9C5B34), Res.drawable.salary_kitchen)
        has("CLEAN", "HOUSEKEEP", "JANITOR", "PORTER") -> RoleLook(Color(0xFF3B8FC4), Res.drawable.salary_cleaning)
        has("WAITER", "SERVER", "HOST", "BAR", "RUNNER", "CASHIER") -> RoleLook(Kit.Green, Res.drawable.salary_waiters)
        has("HELP", "TEMP", "EXTRA") -> RoleLook(Kit.Purple, Res.drawable.salary_extra)
        has("VIEWER") -> RoleLook(Kit.Grey, Res.drawable.users_viewer)
        else -> RoleLook(Kit.Grey, Res.drawable.overview_guests)
    }
}

private enum class RoleTab(val label: String, val icon: ImageVector) { PEOPLE("People", Icons.Outlined.Groups), PERMISSIONS("What they can do", Icons.Outlined.Key) }

/** Admin Hub → Users: the restaurant's people by role. */
@Composable
internal fun UsersScreen(modifier: Modifier = Modifier) {
    val staff = koinInject<StaffScreenModel>()
    val roles = koinInject<RolesScreenModel>()
    BindToLifecycle(staff::setActive, staff::onDispose)
    BindToLifecycle(roles::setActive, roles::onDispose)
    val staffState by staff.state.collectAsState()
    val rolesState by roles.state.collectAsState()
    UsersContent(staffState, staff, rolesState, roles, modifier)
}

@Composable
internal fun UsersContent(
    state: StaffState, model: StaffScreenModel, rolesState: RolesState, roles: RolesScreenModel, modifier: Modifier = Modifier,
    // For the look check: a role (by code) open from the start, on one of its tabs.
    startRole: String? = null, startOnPermissions: Boolean = false
) {
    // Every role there is (when roles can be read), else the ones this person can give.
    // Never the platform's own roles (Super Admin, Co-Super-Admin): nothing about them shows inside a restaurant (user rule).
    val allRoles = (rolesState.roles.takeIf { rolesState.canRead && it.isNotEmpty() } ?: state.assignableRoles).filter { it.code !in PlatformRoleCodes }
    // Only roles at your own level or below (you see people below you and the ones you added at your level), from the
    // highest to the lowest (user rule), then the restaurant's own roles at the end, the newest last (user, 2026-10-31;
    // role ids follow the time they were made).
    val myRank = if (rolesState.superAdmin) Long.MAX_VALUE
        else allRoles.filter { it.code in state.myRoles }.maxOfOrNull { it.rank ?: 0 } ?: Long.MAX_VALUE
    val visible = (allRoles.filter { it.isSystem }.sortedByDescending { it.rank ?: 0 } + allRoles.filter { !it.isSystem }.sortedBy { it.id })
        .filter { (it.rank ?: 0) <= myRank }
    val owners = visible.filter { it.code in OwnerRoles }
    val cards = buildList {
        visible.forEach { role ->
            when {
                role.code !in OwnerRoles || owners.size == 1 -> add(role)
                role == owners.first() -> add(ownersCard(owners))
            }
        }
    }
    val statsOf = { role: RoleDto ->
        if (role.code != OWNERS_CARD) state.roleStats[role.code]
        else owners.mapNotNull { state.roleStats[it.code] }.takeIf { it.isNotEmpty() }?.let { list ->
            RoleStats(list.sumOf { it.active }, list.sumOf { it.inactive }, list.flatMap { it.names })
        }
    }
    var openCode by rememberSaveable { mutableStateOf(startRole) }
    // The role card's Permissions window (a list of two for the Owners card: Owner and Co-Owner).
    var permissionsFor by remember { mutableStateOf<List<RoleDto>?>(null) }
    var tab by rememberSaveable { mutableStateOf(if (startOnPermissions) RoleTab.PERMISSIONS else RoleTab.PEOPLE) }
    val open = allRoles.firstOrNull { it.code == openCode }
    val openRole = { role: RoleDto? ->
        openCode = role?.code
        tab = RoleTab.PEOPLE
        model.search("")
        model.role(role?.code)
        role?.let { roles.select(it.id) }
    }
    // The role cards' numbers: on opening, once the roles are known, and after every change.
    LaunchedEffect(state.canRead, allRoles.map { it.code }, state.notice) {
        model.loadCounts()
        model.loadRoleStats(allRoles.map { it.code })
    }
    // A role opened from the start (look check) needs its people and permissions too.
    LaunchedEffect(startRole, allRoles.isNotEmpty()) {
        val role = allRoles.firstOrNull { it.code == startRole } ?: return@LaunchedEffect
        if (state.filter.roleCode != role.code) model.role(role.code)
        if (rolesState.selectedId != role.id) roles.select(role.id)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val size = screenSizeFor(maxWidth)
        val width = maxWidth
        Column(
            Modifier.fillMaxSize().padding(start = if (size.isPhone) 12.dp else 22.dp, end = if (size.isPhone) 12.dp else 22.dp, top = 18.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (size.isPhone) 10.dp else 14.dp)
        ) {
            // Inside a role its own header (with refresh) is the page's top, so the list gets the room (user, 2026-10-30).
            if (open == null) UsersHeader(state, model, rolesState, roles, size, insideRole = false)
            ScreenMessages(state.error.takeIf { state.draft == null } ?: rolesState.error.takeIf { rolesState.form == null }, state.notice ?: rolesState.notice,
                { model.clearMessages(); roles.clearMessages() })
            // Until the roles and their numbers are in, loading shapes laid out like the cards, then a soft fade into them (as
            // Inventory does; user, 2026-10-30).
            val waiting = (allRoles.isEmpty() && (state.loading || rolesState.loading)) || (allRoles.isNotEmpty() && !state.roleStatsLoaded)
            when {
                !state.canRead -> PageState(PageStateKind.NO_ACCESS, "No access to users", "Your role needs “View users” to see staff accounts.")
                allRoles.isEmpty() && !waiting -> PageState(PageStateKind.FAILED, action = { RetryText(onClick = { model.refresh(); roles.refresh() }) })
                else -> Crossfade(waiting, Modifier.fillMaxWidth().weight(1f), animationSpec = tween(220), label = "users-loading") { skeleton ->
                    val editor = state.personPermissions
                    when {
                        skeleton -> RolesSkeleton(size, Modifier.fillMaxSize())
                        // A person's own permissions open here, as a page with a back arrow (user, 2026-10-30).
                        editor != null -> PersonPermissionsPage(editor, rolesState.catalog, state.myPermissions, state.superAdmin, model,
                            compact = size.isPhone, modifier = Modifier.fillMaxSize())
                        // Your own role's card says "You are here" (you're never in the lists yourself; user, 2026-10-30).
                        open == null -> RolesOverview(Modifier.fillMaxSize(), state, cards, visible, size, statsOf,
                            isMine = { role -> if (role.code == OWNERS_CARD) owners.any { it.code in state.myRoles } else role.code in state.myRoles },
                            // What the role can do, in a big window over the cards; the page stays (user, 2026-10-30: "it should open
                            // the form, not go inside").
                            onPermissions = { role: RoleDto ->
                                val list = if (role.code == OWNERS_CARD) owners else listOf(role)
                                roles.select((list.firstOrNull { rolesState.permissionsEditable(it) } ?: list.first()).id)
                                permissionsFor = list
                            }.takeIf { rolesState.canRead }) { role ->
                            openRole(if (role.code == OWNERS_CARD) owners.first() else role)
                        }
                        else -> RolePage(Modifier.fillMaxSize(), open, owners.takeIf { open.code in OwnerRoles && it.size > 1 }.orEmpty(), state, model,
                            rolesState, roles, size, width, tab, { tab = it }, onSwitch = { role ->
                                // Owner ↔ Co-Owner inside the Owners card: the same tab, the other role's people and permissions.
                                openCode = role.code
                                model.role(role.code)
                                roles.select(role.id)
                            }) { openRole(null) }
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
    RoleDialogs(rolesState, roles)
    permissionsFor?.let { list -> RolePermissionsDialog(list, rolesState, roles, state) { permissionsFor = null } }
}

/**
 * A role's permissions in a big window over the role cards (Users → a card's Permissions): the same area cards as the
 * role's page, saved with the window's own button, which then closes it. The Owners card switches between Owner and
 * Co-Owner at the top.
 */
@Composable
private fun RolePermissionsDialog(list: List<RoleDto>, rolesState: RolesState, roles: RolesScreenModel, staff: StaffState, onClose: () -> Unit) {
    val role = rolesState.selected?.takeIf { selected -> list.any { it.id == selected.id } } ?: list.first()
    val close = { roles.discardChanges(); onClose() }
    // A save that went through closes the window.
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(rolesState.saving) {
        if (saving && !rolesState.saving && !rolesState.dirty) onClose()
        saving = rolesState.saving
    }
    AppDialog("What ${role.label()} can do", close, subtitle = "Changes apply to everyone with ${role.label()} here", busy = rolesState.saving,
        maxWidth = 1180.dp, buttons = {
            if (rolesState.dirty) KitButton("Discard", roles::discardChanges, style = ButtonStyle.SECONDARY, enabled = !rolesState.saving)
            KitButton(if (rolesState.canEditPermissions()) "Cancel" else "Close", close, style = ButtonStyle.SECONDARY, enabled = !rolesState.saving)
            if (rolesState.canEditPermissions()) KitButton("Save", roles::savePermissions, icon = Icons.Outlined.Check, loading = rolesState.saving,
                enabled = rolesState.dirty)
        }) {
        if (list.size > 1) RoleSwitch(list, role, staff) { roles.select(it.id) }
        RolePermissionsPanel(rolesState, roles, Modifier.fillMaxWidth().height(if (LocalNarrowDialog.current) 520.dp else 600.dp),
            compact = LocalNarrowDialog.current, inDialog = true)
    }
}

/**
 * Title on the left, refresh on the right. People are added inside their role ("Add waiter" on the role's page) and the
 * roles are the built-in ones for now, so there's no Add person or New role here (user, 2026-10-30).
 */
@Composable
private fun UsersHeader(state: StaffState, model: StaffScreenModel, rolesState: RolesState, roles: RolesScreenModel, size: ScreenSize, insideRole: Boolean) {
    val refresh = { model.refresh(); roles.refresh(); model.loadCounts() }
    if (size.isPhone) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Users", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Kit.Ink)
                Text("Who works here, by role", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RefreshButton(state.loading || rolesState.loading, refresh, size = 38.dp)
            if (rolesState.canCreate && !insideRole) KitButton("New role", roles::startNew, Modifier.height(38.dp), icon = Icons.Outlined.Add)
        }
    } else {
        // Refresh and New role stay beside the title (the page header would give them a row of their own on a tablet).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Users", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1)
                Text("Who works here, by role, and what each role can do", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            RefreshButton(state.loading || rolesState.loading, refresh)
            // A restaurant makes its own roles (a Driver, a Host) and gives them permissions (user, 2026-10-31).
            if (rolesState.canCreate && !insideRole) KitButton("New role", roles::startNew, icon = Icons.Outlined.Add)
        }
    }
}

// ---------- The roles ----------

/** How wide a role card gets on a big screen (like Salary's team cards). */
private val RoleCardMaxWidth = 360.dp

/**
 * A card per role (no summary cards on top, user rule), three a row (two on a small tablet) and all the same size; a
 * shorter last row sits in the middle. A phone has one card a row.
 */
@Composable
private fun RolesOverview(
    modifier: Modifier, state: StaffState, shown: List<RoleDto>, allRoles: List<RoleDto>, size: ScreenSize, statsOf: (RoleDto) -> RoleStats?,
    isMine: (RoleDto) -> Boolean = { false }, onPermissions: ((RoleDto) -> Unit)? = null, onOpen: (RoleDto) -> Unit
) {
    val gap = if (size.isPhone) 10.dp else 14.dp
    ScrollColumn(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // Three a row; two on a small tablet, where three would squeeze the pictures and the faces.
            val columns = when { size.isPhone -> 1; maxWidth < 840.dp -> 2; else -> 3 }
            val cardWidth = if (columns == 1) maxWidth else ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(RoleCardMaxWidth)
            Column(Modifier.width(cardWidth * columns + gap * (columns - 1)), verticalArrangement = Arrangement.spacedBy(gap)) {
                shown.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                        row.forEach { role ->
                            val stats = statsOf(role)
                            val permissions = onPermissions?.let { { it(role) } }
                            // The coloured band and picture on a phone too (user, 2026-10-31: "don't lose the banner").
                            RoleCard(role, stats, Modifier.width(cardWidth), mine = isMine(role), onPermissions = permissions) { onOpen(role) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** The roles page while it loads: cards shaped like the role cards (a line each on a phone), softly pulsing. */
@Composable
private fun RolesSkeleton(size: ScreenSize, modifier: Modifier) {
    val alpha = rememberSkeletonAlpha("users-roles")
    val gap = if (size.isPhone) 10.dp else 14.dp
    BoxWithConstraints(modifier.alpha(alpha).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val columns = when { size.isPhone -> 1; maxWidth < 840.dp -> 2; else -> 3 }
        val cardWidth = if (columns == 1) maxWidth else ((maxWidth - gap * (columns - 1)) / columns).coerceAtMost(RoleCardMaxWidth)
        Column(Modifier.width(cardWidth * columns + gap * (columns - 1)), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(if (size.isPhone) 3 else 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    repeat(columns) { RoleCardSkeleton(Modifier.width(cardWidth)) }
                }
            }
        }
    }
}

@Composable
private fun RoleCardSkeleton(modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column {
            Box(Modifier.fillMaxWidth().height(112.dp).background(SkeletonLight.copy(alpha = 0.55f))) {
                SkeletonBox(Modifier.align(Alignment.TopStart).padding(12.dp).size(width = 92.dp, height = 24.dp), SkeletonLight, RoundedCornerShape(50))
                SkeletonBox(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(84.dp), SkeletonLight, RoundedCornerShape(42.dp))
            }
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBox(Modifier.size(width = 130.dp, height = 16.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.92f).height(11.dp), SkeletonLight)
                SkeletonBox(Modifier.fillMaxWidth(0.6f).height(11.dp), SkeletonLight)
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(width = 68.dp, height = 22.dp), SkeletonLight, RoundedCornerShape(50))
                    Spacer(Modifier.weight(1f))
                    repeat(3) { SkeletonBox(Modifier.padding(start = 2.dp).size(26.dp), SkeletonLight, CircleShape) }
                }
            }
        }
    }
}

private fun peopleText(count: Long): String = if (count == 1L) "1 person" else "$count people"

private fun roleNote(role: RoleDto): String = role.description?.takeIf { it.isNotBlank() } ?: if (role.isSystem) "Comes with the app" else "Made for your restaurant"

/** A role as a card: its picture on a band of its colour, how many have it, what it's for, and their faces. */
@Composable
private fun RoleCard(role: RoleDto, stats: RoleStats?, modifier: Modifier, mine: Boolean = false, onPermissions: (() -> Unit)? = null,
                     onOpen: () -> Unit) {
    val look = lookOf(role)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        onClick = onOpen, modifier = modifier.hoverable(interaction).pointerHoverIcon(PointerIcon.Hand), shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(if (hovered) 1.5.dp else 1.dp, if (hovered) look.color.copy(alpha = 0.55f) else Kit.Border),
        shadowElevation = if (hovered) 6.dp else 1.dp, interactionSource = interaction
    ) {
        Column(Modifier.alpha(if (role.isActive) 1f else 0.6f)) {
            Box(Modifier.fillMaxWidth().height(112.dp).background(Brush.horizontalGradient(listOf(look.color.copy(alpha = 0.05f), look.color.copy(alpha = 0.17f))))) {
                Image(painterResource(look.image), null, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, top = 10.dp, bottom = 8.dp).height(92.dp),
                    contentScale = ContentScale.Fit)
                Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BandChip(Icons.Outlined.Groups, stats?.let { peopleText(it.active) } ?: "…", look.color)
                    if (mine) YouAreHere()
                    if (!role.isActive) BandChip(Icons.Outlined.ToggleOff, "Switched off", Kit.Grey)
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(look.color))
                    Spacer(Modifier.width(8.dp))
                    Text(role.label(), Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Kit.Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = if (hovered) look.color else Kit.Faint)
                }
                Text(roleNote(role), fontFamily = Inter(), fontSize = 12.sp, lineHeight = 16.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    minLines = 2)
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(if (role.isSystem) "Built-in" else "Your role", if (role.isSystem) Kit.Blue else Kit.Green)
                        onPermissions?.let { PermissionsButton(compact = false, onClick = it) }
                        stats?.inactive?.takeIf { it > 0 }?.let { Text("$it switched off", fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1) }
                    }
                    AvatarStack(stats?.names.orEmpty(), max = 4, size = 28.dp)
                }
            }
        }
    }
}

/** "You are here" on the card of your own role: green, with a pin. */
@Composable
private fun YouAreHere() {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Kit.Green).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Outlined.PersonPinCircle, null, Modifier.size(15.dp), tint = Color.White)
        Text("You are here", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun BandChip(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f)).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
    }
}

@Composable
private fun RolePicture(look: RoleLook, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(look.color.copy(alpha = 0.06f), look.color.copy(alpha = 0.2f)))),
        Alignment.Center) {
        Image(painterResource(look.image), null, Modifier.padding(6.dp).fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

// ---------- One role ----------

/** A role's own page: back to the roles, the role, Add person and its menu; then its people or what it can do. */
@Composable
private fun RolePage(
    modifier: Modifier, role: RoleDto, siblings: List<RoleDto>, state: StaffState, model: StaffScreenModel, rolesState: RolesState, roles: RolesScreenModel,
    size: ScreenSize, width: Dp, tab: RoleTab, onTab: (RoleTab) -> Unit, onSwitch: (RoleDto) -> Unit, onBack: () -> Unit
) {
    val look = lookOf(role)
    val stats = state.roleStats[role.code]
    val canAdd = state.canCreate && state.assignableRoles.any { it.id == role.id }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // One step back inside the page: a plain arrow (the curved one in the top bar leaves the workspace; user rule).
            HoverTooltip("Back to the roles") {
                Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the roles", Modifier.size(22.dp), tint = Kit.Ink)
                }
            }
            RolePicture(look, if (size.isPhone) 42.dp else 50.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (siblings.isNotEmpty()) "Owners" else role.label(), Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold,
                        fontSize = if (size.isPhone) 17.sp else 19.sp,
                        color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!size.isPhone) StatusChip(if (role.isSystem) "Built-in" else "Your role", if (role.isSystem) Kit.Blue else Kit.Green)
                    // Whether its people get paid, and where (user, 2026-10-31); changed from the ⋮ menu.
                    if (!size.isPhone && siblings.isEmpty() && role.paid != null) StatusChip(payText(role.paid, role.payTeam), if (role.paid == true) Kit.Green else Kit.Grey)
                    if (!role.isActive) StatusChip("Switched off", Kit.Grey)
                }
                Text(listOfNotNull(stats?.let { peopleText(it.active) }, if (size.isPhone && role.paid != null) payText(role.paid, role.payTeam) else null, roleNote(role))
                    .joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RefreshButton(state.loading || rolesState.loading, { model.refresh(); roles.refresh(); model.loadCounts() },
                size = if (size.isPhone) 38.dp else PageControlHeight)
            if (canAdd && !size.isPhone) KitButton("Add ${role.label().lowercase()}", { model.startNew(role.id) }, icon = Icons.Outlined.PersonAdd)
            // Built-in roles have nothing in their menu, so no empty space for it: Add sits at the right edge.
            roleActions(role, rolesState, roles).takeIf { rolesState.canRead && it.isNotEmpty() }?.let { RowActionsMenu(it) }
        }
        if (canAdd && size.isPhone) KitButton("Add ${role.label().lowercase()}", { model.startNew(role.id) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.PersonAdd)
        if (siblings.isNotEmpty()) RoleSwitch(siblings, role, state, onSwitch)
        // Only the people: what the role can do is changed from its card's Permissions window (user, 2026-10-30).
        RolePeople(Modifier.fillMaxWidth().weight(1f), role, state, model, size, width, canAdd)
    }
}

/** Owner | Co-Owner inside the Owners card: which of the two roles the page shows, with how many have each. */
@Composable
private fun RoleSwitch(roles: List<RoleDto>, chosen: RoleDto, state: StaffState, onSwitch: (RoleDto) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Kit.Tint).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        roles.forEach { role ->
            val on = role.id == chosen.id
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Color.White else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Kit.Border, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onSwitch(role) }.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(role.label(), fontFamily = Inter(), fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (on) Kit.Ink else Kit.Muted, maxLines = 1)
                state.roleStats[role.code]?.let { Text("${it.active}", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Kit.Muted) }
            }
        }
    }
}

/** People | What they can do, underlined like the supplier page's tabs. */
@Composable
private fun RoleTabs(tab: RoleTab, people: Long?, onTab: (RoleTab) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Kit.Border))
        Row {
            RoleTab.entries.forEach { option ->
                val chosen = option == tab
                Column(Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).clickable { onTab(option) }.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(option.icon, null, Modifier.size(17.dp), tint = if (chosen) Kit.Green else Kit.Muted)
                        Text(option.label, fontFamily = Inter(), fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.5.sp,
                            letterSpacing = 0.sp, color = if (chosen) Kit.Green else Kit.Ink, maxLines = 1, softWrap = false)
                        if (option == RoleTab.PEOPLE && people != null) {
                            Text("$people", Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.GreenSoft else Kit.Tint).padding(horizontal = 7.dp, vertical = 1.dp),
                                fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (chosen) Kit.Green else Kit.Muted)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (chosen) Kit.Green else Color.Transparent))
                }
            }
        }
    }
}

/** The role's people: search and one options button (active, switched off, everyone), then a line each. */
@Composable
private fun RolePeople(modifier: Modifier, role: RoleDto, state: StaffState, model: StaffScreenModel, size: ScreenSize, width: Dp, canAdd: Boolean) {
    val show = when (state.filter.active) { true -> "Active"; false -> "Switched off"; null -> "Everyone" }
    val options: @Composable (Dp) -> Unit = { height ->
        PhoneOptionsButton(Icons.Outlined.Tune, "Filter people", active = if (state.filter.active != true) 1 else 0, size = height) { close ->
            OptionGroup("Show")
            val stats = state.roleStats[role.code]
            OptionLine("Active", state.filter.active == true, { model.showActive(true); close() }, icon = Icons.Outlined.HowToReg, count = stats?.active?.toInt())
            OptionLine("Switched off", state.filter.active == false, { model.showActive(false); close() }, icon = Icons.Outlined.PersonOff, color = Kit.Grey,
                count = stats?.inactive?.toInt())
            OptionLine("Everyone", state.filter.active == null, { model.showActive(null); close() }, icon = Icons.Outlined.Groups, color = Kit.Blue,
                count = stats?.everyone?.toInt())
        }
    }
    // More room between the search and the list, and no title row: the role's header already says how many (user, 2026-10-30).
    OverviewPanel(Icons.Outlined.Groups, if (state.filter.active == true) "People" else "People · $show", modifier, count = state.total.toInt(), bottomPadding = 0.dp,
        flat = size.isPhone, gap = 14.dp, showTitle = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(state.filter.search, model::search, if (size.isPhone) Modifier.weight(1f) else Modifier.width(380.dp),
                placeholder = "Find a person", height = if (size.isPhone) PhoneControlHeight else PageControlHeight)
            if (!size.isPhone) Spacer(Modifier.weight(1f))
            // The pages sit up here, beside the search, so they're always in sight however long the list (user, 2026-10-30:
            // "the pagination isn't fixed, I have to scroll to it").
            if (!size.isPhone) PeoplePager(state.page, state.pageSize, state.total, state.loading, model::goToPage)
            options(if (size.isPhone) PhoneControlHeight else PageControlHeight)
        }
        if (size.isPhone) PeoplePager(state.page, state.pageSize, state.total, state.loading, model::goToPage, Modifier.fillMaxWidth())
        // Loading lines shaped like the people (also while another page loads), then a fade into the list (user, 2026-10-31).
        // A plain refresh and a search keep the list until the answer comes. The shapes stay a moment once shown, and the
        // two never show on top of each other ("it shows the data, then starts loading").
        val waiting = com.saporini.mobile_desktop.core.components.steadyLoading(
            state.filter.roleCode != role.code || (state.loading && (state.people.isEmpty() || state.pages != state.page + 1)))
        AnimatedContent(waiting, Modifier.fillMaxWidth().weight(1f), transitionSpec = { com.saporini.mobile_desktop.core.components.loadingSwap(targetState) },
            label = "people-loading") { skeleton ->
            when {
                skeleton -> com.saporini.mobile_desktop.core.components.PeopleLinesSkeleton(10, Modifier.fillMaxSize(),
                    figures = if (size.isDesktop) 3 else 2, buttons = if (state.canUpdate) 2 else 0, phone = size.isPhone)
                state.people.isEmpty() && state.stale -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    OverviewEmpty("Couldn't load the team", "Check the connection, then try again.", Icons.Outlined.PersonSearch,
                        action = { RetryText(onClick = model::refresh) })
                }
                state.people.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    OverviewEmpty(
                        when {
                            state.filter.search.isNotBlank() -> "Nobody matches “${state.filter.search}”"
                            state.filter.active == false -> "Nobody is switched off"
                            else -> "Nobody is ${role.label().lowercase()} yet"
                        },
                        when {
                            state.filter.search.isNotBlank() -> "Try part of the name, the email or the username."
                            canAdd -> "Add a person with this role to give them a sign-in."
                            else -> "People with this role show here."
                        },
                        Icons.Outlined.PersonSearch, image = lookOf(role).image
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    // A new page starts at its top.
                    LaunchedEffect(state.page) { listState.scrollToItem(0) }
                    // Ten people a page (user, 2026-10-30: "10 a page"); the list scrolls inside and the pages stay put under it.
                    Box(Modifier.fillMaxSize()) {
                        ScrollList(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(PersonLineGap),
                            contentPadding = PaddingValues(bottom = PersonListBottom)) {
                            items(state.people, key = { it.id }) { person -> PersonLine(person, role, state, model, size, width) }
                            if (state.total <= state.pageSize) item(key = "footer") {
                                Text(if (state.total == 1L) "1 person" else "All ${state.total} shown", Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun personActions(person: StaffUserDto, state: StaffState, model: StaffScreenModel): List<RowAction> = buildList {
    if (state.canEdit(person)) {
        add(if (person.isActive) RowAction("Switch off", Icons.Outlined.PersonOff) { model.setActive(person.id, false) }
            else RowAction("Switch on", Icons.Outlined.ToggleOn) { model.setActive(person.id, true) })
        add(RowAction("Send password reset email", Icons.Outlined.LockReset, enabled = person.isActive) { model.sendPasswordReset(person.id) })
    }
    if (state.canRemove(person)) add(RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askRemove(person.id) })
}

/**
 * One person: who, how they sign in, their email (confirmed or not), their other roles and whether they can sign in,
 * with Edit and a menu. The line itself isn't clickable (user rule), and the strip is the role's colour. The page's
 * [width] decides what fits: the email on a desktop, the other roles from a bigger tablet, the sign-in pill off phones.
 */
@Composable
private fun PersonLine(person: StaffUserDto, role: RoleDto, state: StaffState, model: StaffScreenModel, size: ScreenSize, width: Dp) {
    val me = person.id == state.myId
    val others = person.roles.filter { it != role.code }
    val names = state.assignableRoles.associate { it.code to it.label() }
    Surface(Modifier.fillMaxWidth().alpha(if (person.isActive) 1f else 0.7f), shape = RoundedCornerShape(12.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.RowBorder)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(if (person.isActive) lookOf(role).color else Kit.Grey))
            Row(Modifier.weight(1f).heightIn(min = 64.dp).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.weight(2.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShiftAvatar(person.displayName, if (size.isPhone) 36.dp else 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(person.displayName, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (me) StatusChip("You", Kit.Blue)
                        }
                        Text("@${person.username}" + (person.phone?.let { " · $it" } ?: ""), fontFamily = Inter(), fontSize = 11.sp, color = Kit.Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                if (size.isDesktop) {
                    Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(person.email, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (person.emailVerified) Icons.Outlined.Verified else Icons.Outlined.MarkEmailUnread, null, Modifier.size(13.dp),
                                tint = if (person.emailVerified) Kit.Green else Kit.Amber)
                            Text(if (person.emailVerified) "Email confirmed" else "Email not confirmed yet", fontFamily = Inter(), fontSize = 11.sp,
                                color = if (person.emailVerified) Kit.Green else Kit.Amber, maxLines = 1)
                        }
                    }
                }
                if (!size.isPhone) {
                    if (size.isDesktop || width >= 760.dp) Row(Modifier.weight(1.4f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        others.take(2).forEach { StatusChip(names[it] ?: humanize(it), Kit.Blue) }
                        if (others.size > 2) StatusChip("+${others.size - 2}", Kit.Grey)
                    }
                    Box(Modifier.width(112.dp)) { StatusPill(if (person.isActive) "Can sign in" else "Switched off", if (person.isActive) Kit.Green else Kit.Grey) }
                }
                if (state.canEdit(person)) {
                    // Their own permissions, on top of (or less than) the role's (user, 2026-10-30).
                    PermissionsButton(compact = size.isPhone) { model.openPermissions(person.id, role.label()) }
                    EditButton(compact = size.isPhone) { model.startEdit(person.id) }
                }
                RowActionsMenu(personActions(person, state, model), busy = person.id in state.busy)
            }
        }
    }
}

private val PersonLineGap = 6.dp
private val PersonListBottom = 12.dp

/** "1–10 of 23" with ‹ Page 1 of 3 ›, beside the search (under it on a phone) when the people don't fit on one page. */
@Composable
private fun PeoplePager(page: Int, perPage: Int, total: Long, loading: Boolean, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pages = ((total + perPage - 1) / perPage).toInt()
    if (pages <= 1) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${page * perPage + 1}–${minOf((page + 1L) * perPage, total)} of $total", Modifier.padding(end = 6.dp), fontFamily = Inter(),
            fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Muted)
        PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page", page > 0 && !loading) { onPage(page - 1) }
        Text("Page ${page + 1} of $pages", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink)
        PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page", page < pages - 1 && !loading) { onPage(page + 1) }
    }
}

@Composable
private fun PagerArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp), shape = RoundedCornerShape(8.dp), color = Color.White,
        border = BorderStroke(1.dp, Kit.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint) }
    }
}

/** A small outlined Permissions button with a key (just the key on a phone). */
@Composable
private fun PermissionsButton(compact: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Kit.Green.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = if (compact) 8.dp else 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Outlined.VpnKey, if (compact) "Permissions" else null, Modifier.size(15.dp), tint = Kit.Green)
        if (!compact) Text("Permissions", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Green, maxLines = 1)
    }
}

/** A small outlined Edit button (just the pencil on a phone). */
@Composable
private fun EditButton(compact: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Kit.Ink.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Outlined.Edit, if (compact) "Edit" else null, Modifier.size(15.dp), tint = Kit.Ink)
        if (!compact) Text("Edit", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Kit.Ink, maxLines = 1)
    }
}
