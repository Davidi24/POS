package com.saporini.mobile_desktop.admin.people.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.admin.people.MAX_ROLE_DESCRIPTION
import com.saporini.mobile_desktop.admin.people.MAX_ROLE_NAME
import com.saporini.mobile_desktop.admin.people.PayTeams
import com.saporini.mobile_desktop.admin.people.RoleForm
import com.saporini.mobile_desktop.admin.people.RolesScreenModel
import com.saporini.mobile_desktop.admin.people.RolesState
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.KitSwitch
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.theme.Inter
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.role_bartender
import mobile_desktop.shared.generated.resources.role_cashier
import mobile_desktop.shared.generated.resources.role_delivery
import mobile_desktop.shared.generated.resources.role_driver
import mobile_desktop.shared.generated.resources.role_finance
import mobile_desktop.shared.generated.resources.role_general
import mobile_desktop.shared.generated.resources.role_host
import mobile_desktop.shared.generated.resources.role_inventory
import mobile_desktop.shared.generated.resources.role_security
import mobile_desktop.shared.generated.resources.salary_cleaning
import mobile_desktop.shared.generated.resources.salary_extra
import mobile_desktop.shared.generated.resources.salary_kitchen
import mobile_desktop.shared.generated.resources.salary_managers
import mobile_desktop.shared.generated.resources.salary_waiters
import mobile_desktop.shared.generated.resources.users_admin
import mobile_desktop.shared.generated.resources.users_owner
import mobile_desktop.shared.generated.resources.users_viewer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Making or changing a role (user, 2026-10-31): its name, a picture to pick (the built-in roles' figures and the new
// ones), and whether its people get paid, in which Salary team. A built-in role only changes its pay here.

/** A picture a role can have, by the name the server keeps with the role, with the colour its card takes. */
internal class RolePicture(val key: String, val label: String, val image: DrawableResource, val color: Color)

/** What a new role starts with: the general figure, for any role of the restaurant's own (user, 2026-10-31). */
internal const val DefaultRolePicture = "role_general"

internal val RolePictures = listOf(
    RolePicture("role_general", "General", Res.drawable.role_general, Color(0xFF3F7D58)),
    RolePicture("salary_managers", "Manager", Res.drawable.salary_managers, Kit.Blue),
    RolePicture("salary_kitchen", "Kitchen", Res.drawable.salary_kitchen, Color(0xFF9C5B34)),
    RolePicture("salary_waiters", "Waiter", Res.drawable.salary_waiters, Kit.Green),
    RolePicture("role_bartender", "Bartender", Res.drawable.role_bartender, Color(0xFF2F7D5B)),
    RolePicture("role_cashier", "Cashier", Res.drawable.role_cashier, Color(0xFF3C61B5)),
    RolePicture("role_host", "Host", Res.drawable.role_host, Color(0xFFB34C65)),
    RolePicture("salary_cleaning", "Cleaner", Res.drawable.salary_cleaning, Color(0xFF3B8FC4)),
    RolePicture("salary_extra", "Extra worker", Res.drawable.salary_extra, Kit.Purple),
    RolePicture("role_driver", "Driver", Res.drawable.role_driver, Color(0xFF5B6B3A)),
    RolePicture("role_delivery", "Delivery", Res.drawable.role_delivery, Color(0xFFB36412)),
    RolePicture("role_inventory", "Stock keeper", Res.drawable.role_inventory, Color(0xFF1F7A83)),
    RolePicture("role_security", "Security", Res.drawable.role_security, Color(0xFF455A64)),
    RolePicture("role_finance", "Finance", Res.drawable.role_finance, Color(0xFF7350B6)),
    RolePicture("users_admin", "Admin", Res.drawable.users_admin, Color(0xFF2E7D7A)),
    RolePicture("users_owner", "Owner", Res.drawable.users_owner, Color(0xFFB0812C)),
    RolePicture("users_viewer", "Viewer", Res.drawable.users_viewer, Kit.Grey)
)

internal fun rolePicture(key: String?): RolePicture? = RolePictures.firstOrNull { it.key == key }

@Composable
internal fun RoleFormDialog(form: RoleForm, state: RolesState, model: RolesScreenModel) {
    val role = state.roles.firstOrNull { it.id == form.roleId }
    // A name another role has (Owner, Waiter, …) can't be used again (user, 2026-10-31).
    val taken = !form.payOnly && form.name.isNotBlank() && state.roles.any { it.id != form.roleId && it.name.trim().equals(form.name.trim(), ignoreCase = true) }
    val title = when {
        form.payOnly -> "Pay for ${role?.label() ?: form.name}"
        form.mode == RoleForm.Mode.NEW -> "New role"
        form.mode == RoleForm.Mode.RENAME -> "Edit role"
        else -> "Copy role"
    }
    AppDialog(
        title, model::cancelForm,
        subtitle = when {
            form.payOnly -> "Whether people with this role get paid here"
            form.mode == RoleForm.Mode.NEW -> "Switch on what it can do after saving"
            form.mode == RoleForm.Mode.COPY -> "The copy starts with the same permissions"
            else -> null
        },
        busy = state.saving, maxWidth = 620.dp,
        buttons = {
            KitButton("Cancel", model::cancelForm, style = ButtonStyle.SECONDARY, enabled = !state.saving)
            KitButton(if (form.mode == RoleForm.Mode.NEW || form.mode == RoleForm.Mode.COPY) "Create role" else "Save", model::saveForm,
                icon = Icons.Outlined.Check, loading = state.saving, enabled = form.payOnly || (form.name.isNotBlank() && !taken))
        }
    ) {
        state.error?.let { MessageBar(it, MessageKind.ERROR) }
        if (!form.payOnly) {
            TextInput(form.name, model::formName, label = "Name", required = true, icon = Icons.Outlined.Badge, placeholder = "Driver", maxLength = MAX_ROLE_NAME,
                problem = when {
                    form.name.trim().length > MAX_ROLE_NAME -> "At most $MAX_ROLE_NAME characters"
                    taken -> "There's already a role with this name"
                    else -> null
                })
            TextInput(form.description, model::formDescription, label = "Description", optional = true, singleLine = false, minLines = 2,
                placeholder = "What this role is for", maxLength = MAX_ROLE_DESCRIPTION,
                problem = if (form.description.trim().length > MAX_ROLE_DESCRIPTION) "At most $MAX_ROLE_DESCRIPTION characters" else null)
            FormHeading("Picture")
            PicturePicker(form.picture ?: DefaultRolePicture, model::formPicture)
        }
        PayChoice(form.paid, ownTeam = !form.payOnly, onPaid = model::formPaid)
    }
}

@Composable
private fun FormHeading(text: String) {
    Text(text, Modifier.padding(top = 2.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
}

/** The pictures as a grid of tiles; the chosen one has a green frame and a tick. */
@Composable
private fun PicturePicker(chosen: String, onPick: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 80.dp).toInt().coerceIn(4, 8)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RolePictures.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { picture -> PictureTile(picture, picture.key == chosen, Modifier.weight(1f)) { onPick(picture.key) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PictureTile(picture: RolePicture, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(picture.color.copy(alpha = 0.06f), picture.color.copy(alpha = 0.2f))))
                .border(if (selected) 2.dp else 1.dp, if (selected) Kit.Green else Kit.Border, RoundedCornerShape(12.dp))
        ) {
            Image(painterResource(picture.image), picture.label, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(Kit.Green), Alignment.Center) {
                Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = Color.White)
            }
        }
        Text(picture.label, fontFamily = Inter(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp,
            color = if (selected) Kit.Green else Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/**
 * Whether the role's people get paid: then they show in Salary (a restaurant's own role as a team of its own, user
 * 2026-10-31) and can be put on shifts.
 */
@Composable
private fun PayChoice(paid: Boolean, ownTeam: Boolean, onPaid: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (paid) Kit.GreenSoft else Kit.Tint), Alignment.Center) {
            Icon(Icons.Outlined.Payments, null, Modifier.size(19.dp), tint = if (paid) Kit.Green else Kit.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Gets paid", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Kit.Ink)
            Text(when {
                !paid -> "Not in Salary and not on shifts"
                ownTeam -> "Gets its own team in Salary and can be put on shifts"
                else -> "Shows in Salary and can be put on shifts"
            }, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        }
        KitSwitch(paid, onPaid)
    }
}

/** The pay of a role in a word or two, for its page: "Paid · Waiters" or "Not paid". */
internal fun payText(paid: Boolean?, team: String?): String = when {
    paid == false -> "Not paid"
    team?.startsWith("ROLE:") == true -> "Paid · own team"
    else -> "Paid · ${PayTeams.label(team)}"
}

