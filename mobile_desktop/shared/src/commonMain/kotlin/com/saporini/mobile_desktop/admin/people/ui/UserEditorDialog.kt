package com.saporini.mobile_desktop.admin.people.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Adding a person (email, username, temporary password) or editing one (name, phone, on/off, roles). */
@Composable
internal fun UserEditorDialog(state: StaffState, model: StaffScreenModel) {
    val draft = state.draft ?: return
    val problems = state.problems
    val loadingRoles = draft.id != null && draft.id in state.busy
    AppDialog(
        if (draft.isNew) "Add a person" else "Edit ${listOf(draft.firstName, draft.lastName).joinToString(" ").trim().ifBlank { "person" }}",
        model::cancelEdit,
        subtitle = if (draft.isNew) "They sign in with this username or email and the temporary password, then choose their own."
        else "@${draft.username} · ${draft.email}",
        busy = state.saving,
        buttons = {
            KitButton("Cancel", model::cancelEdit, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (draft.isNew) "Add person" else "Save", model::save, icon = Icons.Outlined.Check, loading = state.saving, enabled = !loadingRoles)
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
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
                else -> state.assignableRoles.forEachIndexed { index, role ->
                    SettingsRow(role.label(), role.description?.takeIf { it.isNotBlank() }, divider = index > 0 || !draft.isNew,
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
            problems[StaffField.ROLES]?.let { Text(it, Modifier.padding(top = 4.dp), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
        }
    }
}
