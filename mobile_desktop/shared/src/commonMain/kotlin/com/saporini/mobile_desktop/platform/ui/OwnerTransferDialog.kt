package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.ChoiceChip
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.RetryText
import com.saporini.mobile_desktop.core.components.SearchField
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.humanize
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.MAX_TRANSFER_REASON
import com.saporini.mobile_desktop.platform.OwnerCandidateDto
import com.saporini.mobile_desktop.platform.OwnerStep
import com.saporini.mobile_desktop.platform.OwnerTransferStep
import com.saporini.mobile_desktop.platform.RestaurantDetailsScreenModel
import com.saporini.mobile_desktop.platform.TransferMode
import com.saporini.mobile_desktop.platform.transferProblems

/**
 * "Transfer ownership" (Super Admin): give the restaurant to someone already in the POS or to a new person, who is
 * invited. Spells out what happens to the former owner and asks for the restaurant's name before it's done.
 */
@Composable
internal fun OwnerTransferDialog(step: OwnerTransferStep, model: RestaurantDetailsScreenModel) {
    val problems = if (step.showProblems) transferProblems(step) else emptyMap()
    val ready = transferProblems(step).isEmpty()
    AppDialog(
        "Transfer ${step.restaurantName}", model::cancelTransfer,
        subtitle = "${step.currentOwnerName} owns it now.",
        busy = step.saving, maxWidth = 660.dp,
        buttons = {
            KitButton("Cancel", model::cancelTransfer, style = ButtonStyle.SECONDARY, enabled = !step.saving)
            KitButton("Transfer ownership", model::saveTransfer, icon = Icons.Outlined.SwapHoriz, style = ButtonStyle.DANGER,
                loading = step.saving, enabled = !step.showProblems || ready)
        }
    ) {
        step.error?.let { MessageBar(it, MessageKind.ERROR) }
        val existing: @Composable (Modifier) -> Unit = { m ->
            ChoiceChip("Someone in the POS", step.mode == TransferMode.EXISTING, { model.transferMode(TransferMode.EXISTING) }, m, enabled = !step.saving)
        }
        val invite: @Composable (Modifier) -> Unit = { m ->
            ChoiceChip("Invite a new person", step.mode == TransferMode.INVITE, { model.transferMode(TransferMode.INVITE) }, m, enabled = !step.saving)
        }
        // Side by side; one under the other on a phone, where the two don't fit on a line.
        if (com.saporini.mobile_desktop.core.components.LocalNarrowDialog.current) {
            existing(Modifier.fillMaxWidth())
            invite(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            existing(Modifier)
            invite(Modifier)
        }
        if (step.mode == TransferMode.EXISTING) {
            CandidatePicker(step, model, problems["person"])
        } else {
            OwnerFields(OwnerStep(step.restaurantId, step.restaurantName, step.invite.copy(showProblems = step.showProblems)), model::editTransferInvite)
        }
        TextInput(step.reason, model::transferReason, Modifier.fillMaxWidth(), label = "Reason", optional = true,
            problem = problems["reason"], maxLength = MAX_TRANSFER_REASON, placeholder = "For example: the business was sold",
            hint = "Kept in the restaurant's owner history")
        Consequences(step)
        TextInput(step.confirmedName, model::transferConfirmation, Modifier.fillMaxWidth(),
            label = "Type the restaurant's name to confirm", placeholder = step.restaurantName, problem = problems["confirm"])
    }
}

@Composable
private fun CandidatePicker(step: OwnerTransferStep, model: RestaurantDetailsScreenModel, problem: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(step.search, model::searchCandidates, Modifier.fillMaxWidth(), placeholder = "Search staff by name, email or username", height = 44.dp)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, if (problem != null) Kit.Danger else Kit.Border, RoundedCornerShape(10.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                step.loadingCandidates && step.candidates.isEmpty() -> Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Kit.Green, strokeWidth = 2.dp)
                }
                step.candidatesError != null -> Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.candidatesError, fontFamily = Inter(), fontSize = 13.sp, color = Kit.Danger)
                    RetryText { model.searchCandidates(step.search) }
                }
                step.candidates.isEmpty() -> Text(
                    if (step.search.isBlank()) "Nobody else works at this restaurant yet. Invite a new person instead."
                    else "No staff member matches “${step.search.trim()}”.",
                    Modifier.padding(10.dp), fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted
                )
                else -> step.candidates.take(8).forEach { candidate ->
                    CandidateRow(candidate, candidate.id == step.chosen?.id, enabled = !step.saving) { model.chooseCandidate(candidate) }
                }
            }
        }
        if (step.candidates.size > 8) Text("Showing 8 of ${step.candidates.size}. Search to narrow it down.", fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 12.sp, color = Kit.Danger) }
    }
}

@Composable
private fun CandidateRow(candidate: OwnerCandidateDto, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (selected) Kit.GreenSoft else Kit.Tint)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(18.dp),
            tint = if (selected) Kit.Green else Kit.Faint)
        Column(Modifier.weight(1f)) {
            Text(candidate.displayName, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(candidate.email, candidate.roles.joinToString(", ") { humanize(it) }).filter { it.isNotBlank() }.joinToString(" · "),
                fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What happens when it's saved, in plain words. */
@Composable
private fun Consequences(step: OwnerTransferStep) {
    val newOwner = step.newOwnerName.ifBlank { if (step.mode == TransferMode.INVITE) "The new person" else "The person you choose" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Amber.copy(alpha = 0.08f)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = Kit.Amber)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$newOwner becomes the owner" + if (step.mode == TransferMode.INVITE) " and gets an email to set a password." else ".",
                fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
            Text("${step.currentOwnerName} loses the restaurant: their account is switched off, they're signed out everywhere and their " +
                "open invitation or reset links stop working.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Ink)
            Text("The restaurant, its staff, sales and history stay as they are.", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted)
        }
    }
}

/** The restaurant's past owners, newest first, in the Owner panel. */
@Composable
internal fun OwnerHistory(transfers: List<com.saporini.mobile_desktop.platform.OwnerTransferDto>) {
    if (transfers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Owner history", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Muted)
        transfers.take(5).forEach { transfer ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(if (transfer.newOwnerInvited) Icons.Outlined.Mail else Icons.Outlined.Person, null, Modifier.size(15.dp).padding(top = 2.dp), tint = Kit.Faint)
                Column {
                    Text("${transfer.previousOwnerName ?: "No owner"} → ${transfer.newOwnerName ?: "new owner"}", fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Kit.Ink)
                    Text(listOfNotNull(
                        transfer.transferredAt?.let { dateTimeText(it, kotlinx.datetime.TimeZone.currentSystemDefault()) },
                        transfer.transferredByName?.let { "by $it" },
                        transfer.reason
                    ).joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
                }
            }
        }
    }
}
