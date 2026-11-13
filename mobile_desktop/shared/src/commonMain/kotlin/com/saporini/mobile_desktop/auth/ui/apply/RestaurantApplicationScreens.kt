package com.saporini.mobile_desktop.auth.ui.apply

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationReceipt
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationStatusView
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.FieldPair
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.SearchableSelect
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.platform.CURRENCIES
import com.saporini.mobile_desktop.platform.MAX_EMAIL_TEXT
import com.saporini.mobile_desktop.platform.MAX_LEGAL_NAME
import com.saporini.mobile_desktop.platform.MAX_RESTAURANT_NAME
import com.saporini.mobile_desktop.platform.TIME_ZONES
import com.saporini.mobile_desktop.platform.timeZoneLabel
import com.saporini.mobile_desktop.platform.ui.BackArrow
import kotlinx.datetime.TimeZone
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.brand_logo
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/** "Open a restaurant": anyone can apply from the sign-in screen; a super admin reviews it before it exists. */
object ApplyRestaurantScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<AuthRepository>()
        val model = rememberScreenModel { ApplyRestaurantScreenModel(repository) }
        val state by model.state.collectAsState()
        PublicPage(onBack = { if (!navigator.pop()) navigator.replaceAll(LoginScreen) }) { wide ->
            ApplyContent(state, model, wide, onCheck = { code -> navigator.replace(ApplicationStatusScreen(code)) })
        }
        ScreenMessages(state.error, null, onClear = model::clearError)
    }
}

/** "Check an application": the applicant enters their code and sees whether it was approved, or why not. */
data class ApplicationStatusScreen(val code: String = "") : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<AuthRepository>()
        val model = rememberScreenModel { ApplicationStatusScreenModel(repository) }
        val state by model.state.collectAsState()
        LaunchedEffect(Unit) { model.start(code) }
        PublicPage(onBack = { if (!navigator.pop()) navigator.replaceAll(LoginScreen) }) { _ ->
            StatusContent(state, model, onSignIn = { navigator.replaceAll(LoginScreen) })
        }
    }
}

@Composable
internal fun ApplyContent(state: ApplyState, model: ApplyRestaurantScreenModel, wide: Boolean, onCheck: (String) -> Unit) {
    val receipt = state.receipt
    if (receipt != null) {
        Received(receipt, onCheck)
        return
    }
    val draft = state.draft
    val problems = if (draft.showProblems) applicationProblems(draft) else emptyMap()
    PageTitle("Open a restaurant", "Tell us about your restaurant and who owns it. We review every application and email you the answer.")
    Section("The restaurant", Icons.Outlined.Storefront) {
        FieldPair({ m -> TextInput(draft.name, { v -> model.edit { it.copy(name = v) } }, m, label = "Name", required = true,
            placeholder = "As guests know it", problem = problems["name"], maxLength = MAX_RESTAURANT_NAME, enabled = !state.sending) },
            { m -> TextInput(draft.legalName, { v -> model.edit { it.copy(legalName = v) } }, m, label = "Legal name", required = true,
                icon = Icons.Outlined.Badge, problem = problems["legalName"], maxLength = MAX_LEGAL_NAME, enabled = !state.sending) }, wide)
        FieldPair({ m -> TextInput(draft.email, { v -> model.edit { it.copy(email = v) } }, m, label = "Email", required = true,
            icon = Icons.Outlined.Mail, problem = problems["email"], maxLength = MAX_EMAIL_TEXT, placeholder = "hello@restaurant.com",
            hint = "Guests see it on the booking pages and in their emails", enabled = !state.sending) },
            { m -> TextInput(draft.phone, { v -> model.edit { it.copy(phone = v) } }, m, label = "Phone", required = true,
                icon = Icons.Outlined.Phone, problem = problems["phone"], maxLength = 50, placeholder = "+355 69 123 4567",
                hint = "Guests call it with questions about their booking", enabled = !state.sending) }, wide)
        FieldPair({ m -> SearchableSelect(CURRENCIES.firstOrNull { it.code == draft.currency }, CURRENCIES, { it.label },
            { c -> model.edit { it.copy(currency = c.code) } }, m, label = "Currency", required = true, problem = problems["currency"],
            enabled = !state.sending) },
            { m -> SearchableSelect(draft.timezone.ifBlank { null }, TIME_ZONES, ::timeZoneLabel, { z -> model.edit { it.copy(timezone = z) } }, m,
                label = "Time zone", required = true, problem = problems["timezone"], detailOf = { it }, enabled = !state.sending) }, wide)
    }
    Section("You, the owner", Icons.Outlined.Person) {
        FieldPair({ m -> TextInput(draft.firstName, { v -> model.edit { it.copy(firstName = v) } }, m, label = "First name", required = true,
            problem = problems["firstName"], maxLength = 50, enabled = !state.sending) },
            { m -> TextInput(draft.lastName, { v -> model.edit { it.copy(lastName = v) } }, m, label = "Last name", required = true,
                problem = problems["lastName"], maxLength = 50, enabled = !state.sending) }, wide)
        FieldPair({ m -> TextInput(draft.ownerEmail, { v -> model.edit { it.copy(ownerEmail = v) } }, m, label = "Your email", required = true,
            icon = Icons.Outlined.Mail, problem = problems["ownerEmail"], maxLength = MAX_EMAIL_TEXT,
            hint = "Your application code and our answer go here", enabled = !state.sending) },
            { m -> TextInput(draft.ownerPhone, { v -> model.edit { it.copy(ownerPhone = v) } }, m, label = "Your phone", optional = true,
                icon = Icons.Outlined.Phone, problem = problems["ownerPhone"], maxLength = 30, enabled = !state.sending) }, wide)
        TextInput(draft.username, { v -> model.edit { it.copy(username = v, usernameTouched = true) } }, Modifier.fillMaxWidth(),
            label = "Username", required = true, problem = problems["username"], maxLength = 50,
            hint = "Once approved, you sign in with it or with your email", enabled = !state.sending)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        KitButton("Send the application", model::send, icon = Icons.AutoMirrored.Outlined.Send, loading = state.sending)
    }
}

@Composable
private fun Received(receipt: RestaurantApplicationReceipt, onCheck: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    StatusHeader(Icons.Outlined.CheckCircle, Kit.Green, "Application sent",
        "We'll review ${receipt.name} and email ${receipt.ownerEmail ?: "you"} the answer.")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Kit.Border, RoundedCornerShape(12.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Your application code", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Muted)
        SelectionContainer {
            Text(receipt.statusCode, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Kit.Ink, letterSpacing = 1.sp)
        }
        Text("Keep it: with it you can see whether your application was approved. We also sent it by email. Don't share it.",
            fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KitButton("Copy the code", { clipboard.setText(AnnotatedString(receipt.statusCode)) }, icon = Icons.Outlined.Key, style = ButtonStyle.SECONDARY)
            KitButton("Check the application", { onCheck(receipt.statusCode) }, icon = Icons.Outlined.Search)
        }
    }
}

@Composable
internal fun StatusContent(state: ApplicationStatusState, model: ApplicationStatusScreenModel, onSignIn: () -> Unit) {
    PageTitle("Check an application", "Enter the code from your application email.")
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput(state.code, model::changeCode, Modifier.weight(1f), label = "Application code", placeholder = "7KQ2M-X9D4T-PW3HB-R8N6C",
            icon = Icons.Outlined.Key, problem = state.error, maxLength = 64)
        Box(Modifier.padding(top = 22.dp)) { KitButton("Check", model::check, icon = Icons.Outlined.Search, loading = state.checking) }
    }
    state.view?.let { StatusResult(it, onSignIn) }
}

@Composable
private fun StatusResult(view: RestaurantApplicationStatusView, onSignIn: () -> Unit) {
    val zone = TimeZone.currentSystemDefault()
    when (view.status) {
        "APPROVED" -> {
            StatusHeader(Icons.Outlined.CheckCircle, Kit.Green, "${view.name} was approved", view.message ?: "Check your email to set your password.")
            KitButton("Go to sign in", onSignIn)
        }
        "REJECTED" -> {
            StatusHeader(Icons.Outlined.Cancel, Kit.Danger, "${view.name} wasn't approved", view.message ?: "")
            view.rejectionReason?.let { reason ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Kit.Danger.copy(alpha = 0.06f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The reason", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Kit.Danger)
                    Text(reason, fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Ink)
                }
            }
        }
        else -> StatusHeader(Icons.Outlined.HourglassTop, Kit.Amber, "${view.name} is waiting for review",
            view.message ?: "We will email you when it is decided.")
    }
    Text(listOfNotNull(
        view.submittedAt?.let { "Sent ${dateTimeText(it, zone)}" },
        view.reviewedAt?.let { "Reviewed ${dateTimeText(it, zone)}" }
    ).joinToString(" · "), fontFamily = Inter(), fontSize = 12.sp, color = Kit.Faint)
}

/** A white page with the brand on top and the curved back arrow, for the sign-in side of the app. */
@Composable
private fun PublicPage(onBack: () -> Unit, content: @Composable ColumnScope.(wide: Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFCFCFB))) {
        val wide = maxWidth >= 640.dp
        Box(Modifier.padding(12.dp)) { BackArrow("Back to sign in", onBack) }
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Image(painterResource(Res.drawable.brand_logo), "Saporini Italiano", Modifier.height(if (wide) 72.dp else 56.dp).align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit)
            content(wide)
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Kit.Ink)
        Text(subtitle, fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Muted)
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Kit.Border, RoundedCornerShape(14.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = Kit.Green)
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Kit.Ink)
        }
        content()
    }
}

@Composable
private fun StatusHeader(icon: ImageVector, tint: Color, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(34.dp), tint = tint)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Kit.Ink)
            if (detail.isNotBlank()) Text(detail, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Muted)
        }
    }
}
