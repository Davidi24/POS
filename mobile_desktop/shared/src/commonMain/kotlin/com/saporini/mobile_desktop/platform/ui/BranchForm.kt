package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.GroupLabel
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.platform.BranchDraft
import com.saporini.mobile_desktop.platform.MAX_BRANCH_NAME
import com.saporini.mobile_desktop.platform.MAX_CITY
import com.saporini.mobile_desktop.platform.MAX_CODE
import com.saporini.mobile_desktop.platform.MAX_DESCRIPTION
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.MAX_POSTAL
import com.saporini.mobile_desktop.platform.MAX_STREET
import com.saporini.mobile_desktop.platform.branchProblems

/**
 * A branch being added or changed, with the address guests find it at. [locked] keeps its name and code as they are
 * (owners can't change them).
 */
@Composable
internal fun BranchFormDialog(
    title: String,
    draft: BranchDraft,
    saving: Boolean,
    error: String?,
    onChange: ((BranchDraft) -> BranchDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    saveText: String,
    subtitle: String? = null,
    locked: Boolean = false
) {
    val problems = if (draft.showProblems) branchProblems(draft) else emptyMap()
    AppDialog(title, onCancel, busy = saving, maxWidth = 680.dp, subtitle = subtitle, buttons = {
        KitButton("Cancel", onCancel, style = ButtonStyle.SECONDARY, enabled = !saving)
        KitButton(saveText, onSave, loading = saving)
    }) {
        error?.let { MessageBar(it, MessageKind.ERROR) }
        FieldPair({ m -> TextInput(draft.name, { v -> onChange { it.copy(name = v) } }, m, label = "Name", required = true,
            icon = Icons.Outlined.Business, problem = problems["name"], maxLength = MAX_BRANCH_NAME, enabled = !locked, placeholder = "e.g. Blloku") },
            { m -> TextInput(draft.code, { v -> onChange { it.copy(code = v) } }, m, label = "Code", optional = draft.id == null,
                problem = problems["code"], maxLength = MAX_CODE, enabled = !locked, hint = if (draft.id == null) "Made from the name when empty" else null) })
        FieldPair({ m -> TextInput(draft.phone, { v -> onChange { it.copy(phone = v) } }, m, label = "Phone", optional = true,
            icon = Icons.Outlined.Phone, problem = problems["phone"]) },
            { m -> TextInput(draft.email, { v -> onChange { it.copy(email = v) } }, m, label = "Email", optional = true,
                icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT) })
        TextInput(draft.description, { v -> onChange { it.copy(description = v) } }, Modifier.fillMaxWidth(), label = "Description",
            optional = true, singleLine = false, minLines = 2, problem = problems["description"], maxLength = MAX_DESCRIPTION)
        GroupLabel("Address", "Printed on this branch's receipts")
        TextInput(draft.street, { v -> onChange { it.copy(street = v) } }, Modifier.fillMaxWidth(), label = "Street",
            icon = Icons.Outlined.Place, problem = problems["street"], maxLength = MAX_STREET, optional = true)
        val postal: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft.postalCode, { v -> onChange { it.copy(postalCode = v) } }, m, label = "Postal code",
                problem = problems["postalCode"], maxLength = MAX_POSTAL)
        }
        val city: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft.city, { v -> onChange { it.copy(city = v) } }, m, label = "City",
                problem = problems["city"], maxLength = MAX_CITY)
        }
        val country: @Composable (Modifier) -> Unit = { m ->
            TextInput(draft.country, { v -> onChange { it.copy(country = v) } }, m, label = "Country",
                problem = problems["country"], maxLength = MAX_CITY)
        }
        // Three a row; on a phone the postal code and city share one and the country gets its own.
        if (com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                postal(Modifier.weight(0.75f))
                city(Modifier.weight(1f))
            }
            country(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            postal(Modifier.weight(0.6f))
            city(Modifier.weight(1f))
            country(Modifier.weight(1f))
        }
    }
}
