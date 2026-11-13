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
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.saporini.mobile_desktop.core.components.RowAction
import com.saporini.mobile_desktop.core.components.RowActionsMenu
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
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.NewCoSuperAdminDraft
import com.saporini.mobile_desktop.platform.PERMISSION_AREAS
import com.saporini.mobile_desktop.platform.PermissionTicks
import com.saporini.mobile_desktop.platform.coSuperAdminProblems
import com.saporini.mobile_desktop.platform.permissionArea
import org.koin.compose.koinInject

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.widthIn(max = 920.dp).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = side, end = side, top = 4.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "title") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BackArrow("Back to the restaurants", onBack)
                        Column(Modifier.weight(1f)) {
                            Text("Co-Super-Admins", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (narrow) 18.sp else 21.sp, color = Kit.Ink)
                            Text(
                                if (state.loaded && state.people.isNotEmpty()) "${state.people.size} ${if (state.people.size == 1) "person helps" else "people help"} you run the restaurants"
                                else "People who help you run the restaurants",
                                fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted
                            )
                        }
                        RefreshButton(state.loading, model::refresh)
                        if (state.allowed && state.people.isNotEmpty()) {
                            KitButton(if (narrow) "Add" else "Add Co-Super-Admin", model::startAdd, icon = Icons.Outlined.PersonAdd, enabled = !state.saving)
                        }
                    }
                }
                when {
                    !state.allowed -> item(key = "denied") {
                        PageState(PageStateKind.NO_ACCESS, "Only the Super Admin", "Only the Super Admin adds Co-Super-Admins and chooses what they can do.")
                    }
                    !state.loaded && state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
                    }
                    !state.loaded && state.error != null -> item(key = "failed") {
                        PageState(PageStateKind.FAILED, action = { RetryText(onClick = model::refresh) })
                    }
                    state.people.isEmpty() -> item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            OverviewEmpty(
                                "No Co-Super-Admins yet",
                                "Add someone to help you run the restaurants. They can do only what you allow.",
                                Icons.Outlined.AdminPanelSettings,
                                action = { KitButton("Add Co-Super-Admin", model::startAdd, icon = Icons.Outlined.PersonAdd) }
                            )
                        }
                    }
                    else -> {
                        item(key = "people") {
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
                                Column {
                                    state.people.forEachIndexed { index, person ->
                                        if (index > 0) KitDivider(Modifier.padding(horizontal = 18.dp))
                                        PersonRow(person, state, model, narrow)
                                    }
                                }
                            }
                        }
                        item(key = "rules") {
                            Text(
                                "They can do only what you allow. They never see or change you, and they remove only people they added themselves.",
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
    state.ticks?.let { PermissionsDialog(it, model) }
    state.confirmRemove?.let { person ->
        ConfirmDialog("Remove ${person.displayName}?",
            "They can no longer sign in or manage any restaurant. People and changes they made stay. To stop them for a while, switch them off instead.",
            "Remove", onConfirm = model::confirmRemove, onDismiss = model::dismissRemove, danger = true)
    }
}

/** One Co-Super-Admin: who they are, their status and how much they may do. Clicking opens "What they can do". */
@Composable
private fun PersonRow(person: StaffUserDto, state: CoSuperAdminsState, model: CoSuperAdminsScreenModel, narrow: Boolean) {
    val accent = accentOf(person.id)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (statusText, statusColor) = when {
        !person.isActive -> "Switched off" to Kit.Grey
        !person.emailVerified -> "Email to confirm" to Kit.Amber
        else -> "Active" to Kit.Green
    }
    val counts = state.allowedCounts[person.id]
    Row(
        Modifier.fillMaxWidth()
            .background(if (hovered) Kit.Canvas else Color.White)
            .hoverable(interaction)
            .clickable { model.openPermissions(person) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.soft), contentAlignment = Alignment.Center) {
            Text(initialsOf(person.displayName), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent.strong)
        }
        Column(Modifier.weight(1f)) {
            Text(person.displayName, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(person.email, fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!narrow) {
            Text(
                when {
                    counts == null -> ""
                    counts.first == counts.second -> "Can do everything"
                    else -> "${counts.second - counts.first} switched off"
                },
                Modifier.width(150.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted, maxLines = 1
            )
        }
        Row(Modifier.width(if (narrow) 110.dp else 130.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
            Text(statusText, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Kit.Ink, maxLines = 1)
        }
        RowActionsMenu(listOf(
            RowAction("What they can do", Icons.Outlined.Tune) { model.openPermissions(person) },
            if (person.isActive) RowAction("Switch off", Icons.Outlined.PersonOff) { model.setPersonActive(person, false) }
            else RowAction("Switch on", Icons.Outlined.Person) { model.setPersonActive(person, true) },
            RowAction("Send password reset", Icons.Outlined.LockReset) { model.sendPasswordEmail(person) },
            RowAction("Remove", Icons.Outlined.Delete, danger = true) { model.askRemove(person) }
        ), person.id in state.busy)
    }
}

@Composable
private fun AddDialog(draft: NewCoSuperAdminDraft, state: CoSuperAdminsState, model: CoSuperAdminsScreenModel) {
    val problems = if (draft.showProblems) coSuperAdminProblems(draft) else emptyMap()
    AppDialog(
        "Add a Co-Super-Admin", model::cancelAdd,
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
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Kit.Green) }
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
