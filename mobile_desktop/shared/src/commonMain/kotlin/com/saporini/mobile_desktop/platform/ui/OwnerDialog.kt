package com.saporini.mobile_desktop.platform.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import com.saporini.mobile_desktop.core.components.AnimatedStatusIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.MessageBar
import com.saporini.mobile_desktop.core.components.MessageKind
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.OwnerDraft
import com.saporini.mobile_desktop.platform.OwnerStep
import com.saporini.mobile_desktop.platform.ownerProblems

/** "1 Restaurant — 2 Owner": where the person is in setting up a restaurant. */
@Composable
internal fun SetupSteps(current: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SetupStep(1, "Restaurant", current)
        Box(Modifier.weight(1f).height(2.dp).clip(CircleShape).background(if (current > 1) Kit.Green else Kit.Border))
        SetupStep(2, "Owner", current)
    }
}

@Composable
private fun SetupStep(number: Int, label: String, current: Int) {
    val done = current > number
    val active = current == number
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (done || active) Kit.Green else Kit.Tint),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = Color.White)
            else Text("$number", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (active) Color.White else Kit.Muted)
        }
        Text(label, fontFamily = Inter(), fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp,
            color = if (active || done) Kit.Ink else Kit.Muted)
    }
}

/**
 * Adding a restaurant's owner later, for a restaurant without one. (Right after creating a restaurant the same fields
 * are the second step of the setup dialog.) The owner gets an email with a link to set their password.
 */
@Composable
internal fun OwnerDialog(step: OwnerStep, onChange: ((OwnerDraft) -> OwnerDraft) -> Unit, onSave: () -> Unit, onSkip: () -> Unit) {
    AppDialog(
        "Add the owner of ${step.restaurantName}", onSkip,
        subtitle = "They get an email with a link to set their password.",
        busy = step.saving, maxWidth = 640.dp,
        buttons = { OwnerButtons(step, onSave, onSkip) }
    ) {
        OwnerFields(step, onChange)
    }
}

@Composable
internal fun OwnerButtons(step: OwnerStep, onSave: () -> Unit, onSkip: () -> Unit) {
    KitButton(if (step.afterCreate) "Later" else "Cancel", onSkip, style = ButtonStyle.SECONDARY, enabled = !step.saving)
    KitButton("Add owner and send the invitation", onSave, icon = Icons.AutoMirrored.Outlined.Send, loading = step.saving)
}

@Composable
internal fun ColumnScope.OwnerFields(step: OwnerStep, onChange: ((OwnerDraft) -> OwnerDraft) -> Unit) {
    val draft = step.draft
    val problems = if (draft.showProblems) ownerProblems(draft) else emptyMap()
    step.error?.let { MessageBar(it, MessageKind.ERROR) }
    FieldPair({ m -> TextInput(draft.firstName, { v -> onChange { it.copy(firstName = v) } }, m, label = "First name", required = true,
        icon = Icons.Outlined.Person, problem = problems["firstName"], maxLength = 50) },
        { m -> TextInput(draft.lastName, { v -> onChange { it.copy(lastName = v) } }, m, label = "Last name", required = true,
            problem = problems["lastName"], maxLength = 50) })
    FieldPair({ m -> TextInput(draft.email, { v -> onChange { it.copy(email = v) } }, m, label = "Email", required = true,
        icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT, placeholder = "owner@restaurant.com",
        hint = "The invitation goes here, and they sign in with it") },
        { m -> TextInput(draft.phone, { v -> onChange { it.copy(phone = v) } }, m, label = "Phone", optional = true,
            icon = Icons.Outlined.Phone, problem = problems["phone"], maxLength = 30) })
    TextInput(draft.username, { v -> onChange { it.copy(username = v, usernameTouched = true) } }, Modifier.fillMaxWidth(), label = "Username",
        required = true, problem = problems["username"], maxLength = 50, hint = "They can sign in with it or with their email")
    NoteBox(Icons.Outlined.Mail, "The owner gets an email with a link to set their password. Until they do, the restaurant shows “Invitation sent”, " +
        "and you can send it again from the Restaurant app." + if (step.afterCreate) " You can also add the owner later from the restaurant's card." else "")
}

/** Between the two steps: a check that draws itself, "Restaurant created", then the owner step opens by itself. */
@Composable
internal fun RestaurantCreated(name: String, onDone: () -> Unit, addOwnerNext: Boolean = true) {
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(name) {
        delay(1_700)
        done()
    }
    val appear = remember(name) { Animatable(0f) }
    LaunchedEffect(name) { appear.animateTo(1f, tween(420, delayMillis = 250)) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedStatusIcon(Icons.Outlined.Check, Kit.Green, size = 84.dp, strokeWidth = 2.5.dp)
        Column(
            Modifier.graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 12.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Restaurant created", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Kit.Ink)
            Text("$name is ready with its first branch." + if (addOwnerNext) " Next: its owner." else "", fontFamily = Inter(), fontSize = 13.sp, color = Kit.Muted,
                textAlign = TextAlign.Center)
        }
    }
}
