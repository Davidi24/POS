package com.saporini.mobile_desktop.admin.people.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.saporini.mobile_desktop.core.components.SettingsSwitch
import com.saporini.mobile_desktop.core.components.SettingsRow
import com.saporini.mobile_desktop.core.components.SettingsCard
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.people.MAX_EMAIL
import com.saporini.mobile_desktop.admin.people.MAX_PASSWORD
import com.saporini.mobile_desktop.admin.people.MAX_PERSON_NAME
import com.saporini.mobile_desktop.admin.people.MAX_PHONE
import com.saporini.mobile_desktop.admin.people.MAX_USERNAME
import com.saporini.mobile_desktop.admin.people.StaffField
import com.saporini.mobile_desktop.admin.people.StaffScreenModel
import com.saporini.mobile_desktop.admin.people.StaffState
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.theme.Inter
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.saporini.mobile_desktop.admin.people.DraftPermissions
import com.saporini.mobile_desktop.admin.people.DraftStep
import com.saporini.mobile_desktop.admin.people.PermissionChoice
import com.saporini.mobile_desktop.admin.people.PermissionDto
import com.saporini.mobile_desktop.admin.people.PermissionTree
import com.saporini.mobile_desktop.core.components.SettingsBorder

/**
 * Adding a person (email, username, temporary password) or editing one (name, phone, on/off, roles), in two parts (user,
 * 2026-11-19): their details and roles, then what they can do. Adding someone always shows the second part, with what the
 * roles give all on ("only if you want to add or remove"); editing shows it only when their roles changed, since that
 * clears what was changed for them before.
 */
@Composable
internal fun UserEditorDialog(state: StaffState, model: StaffScreenModel, catalog: List<PermissionDto>) {
    val draft = state.draft ?: return
    val problems = state.problems
    val loadingRoles = draft.id != null && draft.id in state.busy
    val onPermissions = state.draftStep == DraftStep.PERMISSIONS && state.draftNeedsPermissions
    val personName = listOf(draft.firstName, draft.lastName).joinToString(" ").trim()
    val roleNames = state.assignableRoles.filter { it.id in draft.roleIds }.joinToString(" + ") { it.label() }.ifBlank { "their role" }
    val perms = state.draftPermissions
    AppDialog(
        if (draft.isNew) "Add a person" else "Edit ${personName.ifBlank { "person" }}",
        model::cancelEdit,
        subtitle = when {
            onPermissions && personName.isBlank() -> "What they can do. Everything $roleNames gives is on; change only what you want."
            onPermissions -> "What $personName can do. Everything $roleNames gives is on; change only what you want."
            draft.isNew -> "They sign in with this username or email and the temporary password, then choose their own."
            else -> "@${draft.username} · ${draft.email}"
        },
        busy = state.saving,
        maxWidth = if (onPermissions) 980.dp else 620.dp,
        onBack = if (onPermissions && !state.saving) model::backToDetails else null,
        buttons = {
            if (onPermissions) {
                KitButton("Back", model::backToDetails, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                KitButton(if (draft.isNew) "Add person" else "Save", model::save, icon = Icons.Outlined.Check, loading = state.saving,
                    enabled = perms?.loading != true)
            } else {
                KitButton("Cancel", model::cancelEdit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
                if (state.draftNeedsPermissions) {
                    KitButton("Next: permissions", model::toPermissions, icon = Icons.AutoMirrored.Outlined.ArrowForward, enabled = !loadingRoles)
                } else {
                    KitButton("Save", model::save, icon = Icons.Outlined.Check, loading = state.saving, enabled = !loadingRoles)
                }
            }
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (onPermissions) {
            StepLine(second = true)
            DraftPermissionsPart(state, perms, roleNames, catalog, model)
            return@AppDialog
        }
        if (state.draftNeedsPermissions) StepLine(second = false)
        if (draft.isNew) {
            TextInput(draft.email, { v -> model.edit { it.copy(email = v) } }, label = "Email", required = true, icon = Icons.Outlined.Mail,
                placeholder = "name@restaurant.com", problem = problems[StaffField.EMAIL], maxLength = MAX_EMAIL, keyboardType = KeyboardType.Email)
            FieldPair(
                { m ->
                    TextInput(draft.username, { v -> model.edit { it.copy(username = v.trim()) } }, m, label = "Username", required = true,
                        icon = Icons.Outlined.AlternateEmail, placeholder = "anna.b", problem = problems[StaffField.USERNAME], maxLength = MAX_USERNAME,
                        hint = "Letters, numbers, dots, dashes or underscores")
                },
                { m ->
                    TextInput(draft.temporaryPassword, { v -> model.edit { it.copy(temporaryPassword = v) } }, m, label = "Temporary password",
                        required = true, icon = Icons.Outlined.Key, password = true, problem = problems[StaffField.PASSWORD], maxLength = MAX_PASSWORD,
                        hint = "At least 8 characters. Tell it to them in person.")
                }
            )
        }
        FieldPair(
            { m ->
                TextInput(draft.firstName, { v -> model.edit { it.copy(firstName = v) } }, m, label = "First name", required = true,
                    icon = Icons.Outlined.Person, problem = problems[StaffField.FIRST_NAME], maxLength = MAX_PERSON_NAME)
            },
            { m ->
                TextInput(draft.lastName, { v -> model.edit { it.copy(lastName = v) } }, m, label = "Last name", required = true,
                    problem = problems[StaffField.LAST_NAME], maxLength = MAX_PERSON_NAME)
            }
        )
        TextInput(draft.phone, { v -> model.edit { it.copy(phone = v) } }, label = "Phone", optional = true, icon = Icons.Outlined.Phone,
            placeholder = "+39 333 123 4567", problem = problems[StaffField.PHONE], maxLength = MAX_PHONE, keyboardType = KeyboardType.Phone)
        // What they may do, set like the Settings pages: signing in, then a switch for each role you can give.
        SettingsCard("Access", note = "What they can do comes from their roles. You can give roles below your own.", icon = Icons.Outlined.Key) {
            Spacer(Modifier.height(6.dp))
            if (!draft.isNew) {
                SettingsRow("Can sign in", "Switch off someone who left or is away for a long time. You can switch them on again later.", divider = false,
                    onClick = { model.edit { it.copy(active = !it.active) } },
                    leading = {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Kit.Green.copy(alpha = 0.12f)), Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.size(17.dp), tint = Kit.Green)
                        }
                    }) {
                    SettingsSwitch(draft.active, { v -> model.edit { it.copy(active = v) } })
                }
            }
            when {
                loadingRoles -> Text("Loading their roles…", Modifier.padding(vertical = 10.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                state.assignableRoles.isEmpty() -> Text("No roles you can give. Ask an owner to give you permission to manage roles.",
                    Modifier.padding(vertical = 10.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                else -> RoleChoice(state, model, draft.isNew)
            }
            problems[StaffField.ROLES]?.let { Text(it, Modifier.padding(top = 4.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
            // A new role clears what was changed for them alone (user, 2026-11-19); the next part sets it again.
            if (state.draftRolesChanged) AmberNote("Their role changed, so the permissions you changed for them before are cleared. You set them again in the next part.")
        }
    }
}

/** "1 Details · 2 Permissions", the part in use in green. */
@Composable
private fun StepLine(second: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepDot(1, "Details", on = !second, done = second)
        Box(Modifier.width(28.dp).height(1.dp).background(Kit.Border))
        StepDot(2, "Permissions", on = second, done = false)
    }
}

@Composable
private fun StepDot(number: Int, label: String, on: Boolean, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (on || done) Kit.Green else Kit.Tint), Alignment.Center) {
            if (done) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = Color.White)
            else androidx.compose.material3.Text("$number", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                color = if (on) Color.White else Kit.Muted)
        }
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = if (on) Kit.Ink else Kit.Muted)
    }
}

@Composable
private fun AmberNote(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF7E6))
        .border(1.dp, Color(0xFFF3D8A6), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(17.dp), tint = Color(0xFFB7791F))
        Text(text, Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, color = Kit.Ink)
    }
}

/**
 * The Permissions part: what the chosen roles give, all on, as the same workspace cards as a role's Permissions window;
 * a line says what's different for this person, and "Same as the role" takes it back.
 */
@Composable
private fun DraftPermissionsPart(state: StaffState, perms: DraftPermissions?, roleNames: String, catalog: List<PermissionDto>, model: StaffScreenModel) {
    if (state.draftRolesChanged) AmberNote("Their role changed: this starts from what $roleNames gives. What you changed for them before is cleared.")
    when {
        perms == null || perms.loading -> Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(26.dp), color = Kit.Green, strokeWidth = 3.dp)
        }
        perms.error != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MessageBar("Couldn't load what $roleNames can do. ${perms.error}", MessageKind.ERROR)
            KitButton("Try again", model::retryDraftPermissions, style = ButtonStyle.SECONDARY, icon = Icons.Outlined.Refresh)
        }
        else -> {
            val tree = androidx.compose.runtime.remember(catalog) { PermissionTree(catalog) }
            val shown = tree.shown(perms.ticked)
            val choices = catalog.map { permission ->
                PermissionChoice(permission, permission.code in shown,
                    state.superAdmin || permission.code in state.myPermissions || permission.code in perms.fromRoles)
            }
            // The workspaces the roles work in (and any where something was added); all of them when the roles give nothing.
            val areas = byWorkspace(choices).let { all ->
                all.filter { (_, list) -> list.any { it.permission.code in perms.fromRoles || it.granted } }.ifEmpty { all }
            }
            val extra = perms.ticked - perms.fromRoles
            val missing = perms.fromRoles - perms.ticked
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, SettingsBorder, RoundedCornerShape(14.dp))
                .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (!perms.changed) "Same as $roleNames"
                        else listOfNotNull(
                            extra.size.takeIf { it > 0 }?.let { "$it more than $roleNames" },
                            missing.size.takeIf { it > 0 }?.let { "$it taken away" }
                        ).joinToString(" · "),
                        fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (perms.changed) Kit.Green else Kit.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${areas.sumOf { (_, list) -> list.count { it.granted } }} of ${areas.sumOf { (_, list) -> list.size }} allowed",
                        fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1)
                }
                if (perms.changed) KitButton("Same as the role", model::resetDraftPermissions, style = ButtonStyle.SECONDARY,
                    icon = Icons.Outlined.Restore, enabled = !state.saving)
            }
            WorkspaceCards(areas, tree, canEdit = !state.saving,
                onToggle = { model.tickDraftPermissions(listOf(it.permission.code), !it.granted, tree) },
                onToggleAll = { list, on -> model.tickDraftPermissions(list.map { it.permission.code }, on, tree) })
        }
    }
}

/**
 * Their role (user, 2026-11-19): one main role, then "Other roles" unfolding the roles below it. An admin gets all of them
 * by default (that's where POS and the kitchen display come from); an owner gets everything, with a warning.
 */
@Composable
private fun RoleChoice(state: StaffState, model: StaffScreenModel, isNew: Boolean) {
    val draft = state.draft ?: return
    val roles = state.assignableRoles
    val main = roles.filter { it.id in draft.roleIds }.maxByOrNull { it.rank ?: 0 }
    val below = main?.let { m -> roles.filter { (it.rank ?: 0) < (m.rank ?: 0) } }.orEmpty()
    val others = below.count { it.id in draft.roleIds }
    var showOthers by androidx.compose.runtime.remember(draft.id) { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Their role", Modifier.padding(top = 6.dp, bottom = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
        roles.forEach { role ->
            val chosen = role.id == main?.id
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (chosen) Kit.GreenSoft else Color.Transparent)
                    .clickable { model.chooseMainRole(role.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(if (chosen) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(19.dp),
                    tint = if (chosen) Kit.Green else Kit.Faint)
                Column(Modifier.weight(1f)) {
                    Text(role.label(), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = if (chosen) Kit.Green else Kit.Ink)
                    role.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontFamily = Inter(), fontSize = 11.5.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        when (main?.code) {
            "OWNER" -> AmberNote("An owner can do everything in this restaurant, the same as you. Only the Super Admin can remove an owner later.")
            "ADMIN" -> AmberNote("An admin opens the Admin Hub (shifts, users, settings, audit log), Inventory and Statistics. POS and the kitchen display come from the roles below, which are on for them: switch off what they shouldn't do. Only the owner can give them more.")
            else -> {}
        }
        if (main != null && below.isNotEmpty()) {
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Kit.Border, RoundedCornerShape(10.dp))
                    .clickable { showOthers = !showOthers }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.GroupAdd, null, Modifier.size(17.dp), tint = Kit.Ink)
                Text(if (others == 0) "Other roles" else "Other roles · $others on", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                Icon(if (showOthers) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Kit.Muted)
            }
            if (showOthers) {
                Text("Roles below ${main.label()} they also have.", Modifier.padding(top = 6.dp, bottom = 2.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                below.forEachIndexed { index, role ->
                    SettingsRow(role.label(), role.description?.takeIf { it.isNotBlank() }, divider = index > 0,
                        onClick = { model.toggleRole(role.id) },
                        leading = {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Kit.Blue.copy(alpha = 0.12f)), Alignment.Center) {
                                Icon(Icons.Outlined.AdminPanelSettings, null, Modifier.size(17.dp), tint = Kit.Blue)
                            }
                        }) {
                        SettingsSwitch(role.id in draft.roleIds, { model.toggleRole(role.id) })
                    }
                }
            }
        }
    }
}
