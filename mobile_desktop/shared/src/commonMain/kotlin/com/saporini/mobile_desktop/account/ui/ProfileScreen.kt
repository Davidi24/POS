package com.saporini.mobile_desktop.account.ui

import kotlinx.datetime.minus
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.account.ProfileScreenModel
import com.saporini.mobile_desktop.account.ProfileState
import com.saporini.mobile_desktop.auth.data.dto.MySession
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.passwordChecks
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.components.ScreenMessages
import com.saporini.mobile_desktop.core.components.ScrollColumn
import com.saporini.mobile_desktop.core.components.TextInput
import com.saporini.mobile_desktop.core.format.agoText
import com.saporini.mobile_desktop.core.format.instantOrNull
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.theme.Inter
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * Profile (user, 2026-11-18: "when I click Profile I have things like change password, make it look good"): who you are,
 * change your password, the devices you're signed in on (sign out one, or every one). Opened from the menu behind the
 * person's name in any workspace; the plain back arrow returns there.
 */
object ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<AuthRepository>()
        val session = koinInject<SessionManager>()
        val model = rememberScreenModel { ProfileScreenModel(repository, session) }
        val state by model.state.collectAsState()
        LaunchedEffect(Unit) { model.loadDevices(); model.loadProfile(); model.loadPin() }
        ProfileContent(LocalAccount.current, state, model, onBack = { if (navigator.canPop) navigator.pop() })
    }
}

/** Profile's places in the sidebar (user, 2026-11-18: "make a sidebar on the left and put them separate"). */
internal enum class ProfileSection(val label: String, val icon: ImageVector, val line: String) {
    DETAILS("My details", Icons.Outlined.Badge, "Who you are and where you work."),
    SECURITY("Security", Icons.Outlined.Shield, "Your password, your PIN and who signed in."),
    DEVICES("Devices", Icons.Outlined.Devices, "Where you're signed in."),
    PREFERENCES("Preferences", Icons.Outlined.Tune, "How the app works for you.")
}

@Composable
internal fun ProfileContent(account: AccountInfo, state: ProfileState, model: ProfileScreenModel?, onBack: () -> Unit,
                            start: ProfileSection = ProfileSection.DETAILS) {
    var section by remember { mutableStateOf(start) }
    var askEverywhere by remember { mutableStateOf(false) }
    LaunchedEffect(section) { if (section == ProfileSection.SECURITY && !state.signInsLoaded) model?.loadSignIns() }
    Box(Modifier.fillMaxSize().background(Kit.Canvas)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val phone = maxWidth < 600.dp
            val sidebar = maxWidth >= 860.dp
            val wideCards = maxWidth >= 1180.dp
            if (sidebar) {
                Row(Modifier.fillMaxSize()) {
                    ProfileSidebar(account, section, onSelect = { section = it }, onBack = onBack)
                    ScrollColumn(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max = 860.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            SectionHeader(section, phone = false)
                            SectionBody(section, account, state, model, phone = false, wide = wideCards, onSignOutEverywhere = { askEverywhere = true })
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            } else {
                ScrollColumn(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = if (phone) 14.dp else 24.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Kit.Ink) }
                            Text("Profile", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp, color = Kit.Ink)
                        }
                        // The sidebar's places as a row on narrow windows.
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileSection.entries.forEach { s -> SectionChip(s, s == section) { section = s } }
                        }
                        SectionHeader(section, phone = true)
                        SectionBody(section, account, state, model, phone = phone, wide = false, onSignOutEverywhere = { askEverywhere = true })
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
        ScreenMessages(state.error, state.notice, onClear = { model?.clearMessages() })
    }
    if (askEverywhere && model != null) SignOutEverywhereDialog(model::signOutEverywhere) { askEverywhere = false }
}

/** White column on the left: back, the person, then the four places. */
@Composable
private fun ProfileSidebar(account: AccountInfo, selected: ProfileSection, onSelect: (ProfileSection) -> Unit, onBack: () -> Unit) {
    val user = account.user
    Column(
        Modifier.width(272.dp).fillMaxHeight().background(Color.White).drawBehind {
            drawLine(Kit.Border, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
        }.padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Kit.Ink) }
            Text("Profile", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp, color = Kit.Ink)
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(14.dp))
            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AccountAvatar(user, 64.dp, 22.sp)
            Spacer(Modifier.height(2.dp))
            Text(displayName(user), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.sp, color = Kit.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            roleLabel(user)?.let { HeroPill(it, Kit.Green, Kit.GreenSoft) }
            placeLine(account)?.let {
                Text(it, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(18.dp))
        ProfileSection.entries.forEach { s ->
            val chosen = s == selected
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (chosen) Kit.GreenSoft else Color.Transparent)
                    .clickable { onSelect(s) }.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(s.icon, null, Modifier.size(20.dp), tint = if (chosen) Kit.Green else Kit.Muted)
                Text(s.label, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 14.sp, letterSpacing = 0.sp, color = if (chosen) Kit.Green else Kit.Ink)
                if (chosen) Box(Modifier.size(6.dp).clip(CircleShape).background(Kit.Green))
            }
        }
    }
}

@Composable
private fun SectionChip(section: ProfileSection, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (chosen) Kit.GreenSoft else Color.White)
            .border(1.dp, if (chosen) Color(0xFFCFE0C8) else Kit.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(section.icon, null, Modifier.size(16.dp), tint = if (chosen) Kit.Green else Kit.Muted)
        Text(section.label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp,
            color = if (chosen) Kit.Green else Kit.Ink)
    }
}

@Composable
private fun SectionHeader(section: ProfileSection, phone: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(section.label, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (phone) 20.sp else 24.sp, letterSpacing = (-0.3).sp, color = Kit.Ink)
        Text(section.line, fontFamily = Inter(), fontSize = 13.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
    }
}

@Composable
private fun SectionBody(section: ProfileSection, account: AccountInfo, state: ProfileState, model: ProfileScreenModel?, phone: Boolean, wide: Boolean,
                        onSignOutEverywhere: () -> Unit) {
    when (section) {
        ProfileSection.DETAILS -> ProfileDetailsCard(account, state, model, phone)
        ProfileSection.SECURITY -> {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                    PasswordCard(state, model, Modifier.weight(1.1f))
                    PinCard(state, model, Modifier.weight(1f))
                }
            } else {
                PasswordCard(state, model, Modifier.fillMaxWidth())
                PinCard(state, model, Modifier.fillMaxWidth())
            }
            SignInsCard(state, model)
        }
        ProfileSection.DEVICES -> DevicesCard(state, model, phone, onSignOutEverywhere)
        ProfileSection.PREFERENCES -> PreferencesCard()
    }
}

/**
 * Your PIN for the restaurant's tills (user, 2026-11-18): nobody types their own; Generate shows a random 5-digit PIN nobody
 * else has, "Generate another" a new one (10 a day), and "Use this PIN" makes it yours (3 a day). It's shown only then.
 */
@Composable
private fun PinCard(state: ProfileState, model: ProfileScreenModel?, modifier: Modifier) {
    val pin = state.pin
    val offered = state.pinSuggestion
    ProfileCard("Your PIN", Icons.Outlined.Dialpad, modifier, note = "5 digits to sign in fast on the restaurant's tills.") {
        when {
            !state.pinLoaded -> Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(Kit.Tint))
            pin == null -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
                .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Kit.Muted)
                Text(state.pinProblem ?: "Your PIN couldn't be loaded.", Modifier.weight(1f), fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink)
            }
            offered != null -> {
                // The PIN offered: big digits, then use it or ask for another.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Kit.GreenSoft.copy(alpha = 0.55f))
                    .border(1.dp, Kit.Green.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(vertical = 18.dp, horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your new PIN", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Green)
                    Spacer(Modifier.height(10.dp))
                    // Each digit 42 dp, narrower when the card is (small phones), so the five always fit.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        offered.pin.forEach { digit ->
                            Box(Modifier.weight(1f, fill = false).width(42.dp).height(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                                .border(1.dp, Kit.Border, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(digit.toString(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 0.sp, color = Kit.Ink)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Remember it: after you use it, it isn't shown again.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp,
                        color = Kit.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.height(12.dp))
                state.pinProblem?.let { PinProblem(it); Spacer(Modifier.height(10.dp)) }
                // Short labels so nothing is cut; how many are left goes under them. Side by side when they fit, else one under the other.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val another: @Composable (Modifier) -> Unit = { m ->
                        com.saporini.mobile_desktop.core.components.KitButton("Generate another", { model?.suggestPin() }, m, style = com.saporini.mobile_desktop.core.components.ButtonStyle.SECONDARY,
                            enabled = !state.pinBusy && offered.suggestionsLeft > 0)
                    }
                    val use: @Composable (Modifier) -> Unit = { m ->
                        com.saporini.mobile_desktop.core.components.KitButton("Use this PIN", { model?.usePin() }, m, loading = state.pinBusy, enabled = pin.changesLeft > 0)
                    }
                    if (maxWidth >= 300.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            another(Modifier.weight(1f)); use(Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            use(Modifier.fillMaxWidth()); another(Modifier.fillMaxWidth())
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(when (offered.suggestionsLeft) {
                    0 -> "No more new PINs today."
                    1 -> "You can generate 1 more today."
                    else -> "You can generate ${offered.suggestionsLeft} more today."
                }, Modifier.fillMaxWidth(), fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { model?.dropPinSuggestion() }, enabled = !state.pinBusy) {
                        Text(if (pin.hasPin) "Keep my PIN" else "Not now", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            letterSpacing = 0.sp, color = Kit.Muted)
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(5) { Box(Modifier.size(12.dp).clip(CircleShape).background(if (pin.hasPin) Kit.Green else Kit.Border)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (pin.hasPin) "Your PIN is ready" else "No PIN yet", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                            letterSpacing = 0.sp, color = Kit.Ink)
                        val since = pin.setAt?.let { com.saporini.mobile_desktop.core.format.instantOrNull(it) }
                            ?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
                        Text(when {
                            pin.hasPin && since != null -> "Set on ${since.day} ${since.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${since.year}"
                            pin.hasPin -> "Use it on the tills."
                            else -> "Generate one to use the tills."
                        }, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted)
                    }
                    // A status, not a button (user, 2026-11-18: "Set looks like a button"): a dot and the word, no box.
                    if (pin.hasPin) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Kit.Green))
                        Text("Active", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Green)
                    }
                }
                Spacer(Modifier.height(12.dp))
                state.pinProblem?.let { PinProblem(it); Spacer(Modifier.height(10.dp)) }
                val blocked = when {
                    pin.changesLeft <= 0 -> "You changed your PIN 3 times today. Try again tomorrow."
                    pin.suggestionsLeft <= 0 -> "No more PINs today. Try again tomorrow."
                    else -> null
                }
                com.saporini.mobile_desktop.core.components.KitButton(if (pin.hasPin) "Get a new PIN" else "Generate PIN", { model?.suggestPin() },
                    Modifier.fillMaxWidth(), icon = Icons.Outlined.Dialpad, loading = state.pinBusy, enabled = blocked == null)
                Spacer(Modifier.height(8.dp))
                Text(blocked ?: "Forgot it? Get a new one here, or ask your manager to reset it.", fontFamily = Inter(), fontSize = 12.sp,
                    letterSpacing = 0.sp, color = if (blocked != null) Kit.Amber else Kit.Muted)
            }
        }
    }
}

@Composable
private fun PinProblem(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFDECEC)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = Kit.Danger)
        Text(text, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
    }
}

/**
 * Recent sign-ins (user, 2026-11-18: "make it with pages and make it look better"): the 30 days in three figures, then the
 * tries by day (what happened, which device, the time), eight a page.
 */
@Composable
private fun SignInsCard(state: ProfileState, model: ProfileScreenModel?) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { kotlin.time.Clock.System.now().toLocalDateTime(zone).date }
    val thisDevice = remember { com.saporini.mobile_desktop.core.session.DeviceIdentity.id }
    val page = state.signInPage
    ProfileCard("Recent sign-ins", Icons.Outlined.History, Modifier.fillMaxWidth(), note = "The last 30 days, on every device.") {
        val counts = page?.counts.orEmpty()
        val worked = counts["SIGNED_IN"] ?: 0L
        val wrong = counts["INVALID_CREDENTIALS"] ?: 0L
        val blocked = (counts["ACCOUNT_LOCKED"] ?: 0L) + (counts["DEVICE_RATE_LIMITED"] ?: 0L) + (counts["IP_RATE_LIMITED"] ?: 0L)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Narrow (a phone, a small window): the icon beside the number and the words under it on up to two lines.
            val compact = maxWidth < 540.dp
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                SignInFigure(Icons.Outlined.CheckCircle, Kit.Green, worked, if (worked == 1L) "Sign-in" else "Sign-ins", Modifier.weight(1f), compact)
                SignInFigure(Icons.Outlined.Cancel, Kit.Danger, wrong, if (wrong == 1L) "Wrong password" else "Wrong passwords", Modifier.weight(1f), compact)
                SignInFigure(Icons.Outlined.Lock, Kit.Amber, blocked, "Locked or paused", Modifier.weight(1f), compact)
            }
        }
        if (wrong > 0) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF6E5)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp), tint = Kit.Amber)
                Text("Wrong passwords you didn't type? Change your password.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Ink)
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            !state.signInsLoaded -> repeat(4) { i ->
                if (i > 0) HorizontalDivider(color = Kit.RowBorder)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Kit.Tint))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(150.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(Kit.Tint))
                        Box(Modifier.size(210.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(Kit.Tint))
                    }
                }
            }
            state.signInsProblem != null && page == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.signInsProblem, fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Danger)
                com.saporini.mobile_desktop.core.components.RetryText(onClick = { model?.loadSignIns() })
            }
            page == null || page.items.isEmpty() -> Text("No sign-ins in the last 30 days.", fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Muted)
            else -> Column(Modifier.graphicsLayer { alpha = if (state.signInsLoading) 0.5f else 1f }) {
                var lastDay: kotlinx.datetime.LocalDate? = null
                page.items.forEach { record ->
                    val at = instantOrNull(record.at)?.toLocalDateTime(zone)
                    val day = at?.date
                    if (day != null && day != lastDay) {
                        lastDay = day
                        val label = when (day) {
                            today -> "Today"
                            today.minus(1, kotlinx.datetime.DateTimeUnit.DAY) -> "Yesterday"
                            else -> day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " +
                                com.saporini.mobile_desktop.core.format.shortDate(day)
                        }
                        Text(label.uppercase(), Modifier.padding(top = 10.dp, bottom = 4.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp, letterSpacing = 0.6.sp, color = Kit.Faint)
                    }
                    SignInLine(record, at, thisDevice)
                }
                SignInPager(page, state.signInsLoading, onPage = { model?.loadSignIns(it) })
            }
        }
    }
}

@Composable
private fun SignInFigure(icon: ImageVector, tint: Color, count: Long, label: String, modifier: Modifier, compact: Boolean) {
    if (compact) {
        Column(modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.06f)).border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(16.dp), tint = tint)
                Text(count.toString(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.sp, color = Kit.Ink)
            }
            Text(label, fontFamily = Inter(), fontSize = 11.5.sp, lineHeight = 14.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 2)
        }
        return
    }
    Row(modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.06f)).border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = tint)
        }
        Column {
            Text(count.toString(), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp, color = Kit.Ink)
            Text(label, fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SignInLine(record: com.saporini.mobile_desktop.auth.data.dto.SignInRecord, at: kotlinx.datetime.LocalDateTime?, thisDevice: String) {
    val (title, tint, icon) = when (record.result) {
        "SIGNED_IN" -> Triple("Signed in", Kit.Green, Icons.Outlined.CheckCircle)
        "INVALID_CREDENTIALS" -> Triple("Wrong password", Kit.Danger, Icons.Outlined.Cancel)
        "ACCOUNT_LOCKED" -> Triple("Tried while locked", Kit.Amber, Icons.Outlined.Lock)
        "DEVICE_RATE_LIMITED", "IP_RATE_LIMITED" -> Triple("Paused after many wrong tries", Kit.Amber, Icons.Outlined.Lock)
        "ACCOUNT_INACTIVE" -> Triple("Refused: account switched off", Kit.Muted, Icons.Outlined.Cancel)
        "EMAIL_NOT_VERIFIED" -> Triple("Refused: email not confirmed", Kit.Muted, Icons.Outlined.Cancel)
        else -> Triple("Refused", Kit.Muted, Icons.Outlined.Cancel)
    }
    val mine = record.deviceId != null && record.deviceId == thisDevice
    val device = record.deviceName?.takeIf { it.isNotBlank() } ?: if (mine) null else "Another device"
    val computer = device != null && listOf("computer", "windows", "mac", "linux").any { device.contains(it, ignoreCase = true) }
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (mine) HeroPill("This device", Kit.Green, Kit.GreenSoft)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (device != null) Icon(if (computer) Icons.Outlined.Computer else Icons.Outlined.Smartphone, null, Modifier.size(13.dp), tint = Kit.Muted)
                Text(listOfNotNull(device, record.ipAddress?.takeIf { !mine }).joinToString(" · ").ifBlank { "This device" },
                    fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(at?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" } ?: "–",
            fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
    }
}

/** "1–8 of 23" and ‹ Page 1 of 3 ›. */
@Composable
private fun SignInPager(page: com.saporini.mobile_desktop.auth.data.dto.SignInPage, loading: Boolean, onPage: (Int) -> Unit) {
    val pages = ((page.total + page.size - 1) / page.size).toInt().coerceAtLeast(1)
    if (page.total <= page.size) return
    HorizontalDivider(Modifier.padding(top = 8.dp), color = Kit.RowBorder)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val from = page.page * page.size + 1
        val to = minOf(page.total, (page.page + 1L) * page.size)
        Text("$from–$to of ${page.total}", Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        PagerButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Newer", enabled = !loading && page.page > 0) { onPage(page.page - 1) }
        Text("Page ${page.page + 1} of $pages", Modifier.padding(horizontal = 10.dp), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
            letterSpacing = 0.sp, color = Kit.Ink)
        PagerButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Older", enabled = !loading && page.page < pages - 1) { onPage(page.page + 1) }
    }
}

@Composable
private fun PagerButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).border(1.dp, Kit.Border, RoundedCornerShape(9.dp)).background(Color.White)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(18.dp), tint = if (enabled) Kit.Ink else Kit.Faint)
    }
}

/** Language, what opens first and notifications: shown now, switched on with their own steps. */
@Composable
private fun PreferencesCard() {
    ProfileCard("Preferences", Icons.Outlined.Tune, Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 480.dp
            Column {
                PreferenceLine(Icons.Outlined.Language, "Language", "The language of the app", "English", narrow, first = true)
                PreferenceLine(Icons.Outlined.Apps, "Opens first", "What you see after signing in", "Choose a workspace", narrow)
                PreferenceLine(Icons.Outlined.NotificationsNone, "Notifications", "Which alerts you get", "All", narrow)
            }
        }
    }
}

@Composable
private fun PreferenceLine(icon: ImageVector, title: String, line: String, value: String, narrow: Boolean, first: Boolean = false) {
    if (!first) HorizontalDivider(color = Kit.RowBorder)
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = if (narrow) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = Kit.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp, color = Kit.Ink)
            Text(line, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            // On a narrow card the value and "Coming soon" go under the words, so the words keep the width.
            if (narrow) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink)
                HeroPill("Coming soon", Kit.Muted, Kit.Canvas)
            }
        }
        if (!narrow) {
            Text(value, fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Muted)
            HeroPill("Coming soon", Kit.Muted, Kit.Canvas)
        }
    }
}

@Composable
private fun HeroPill(text: String, ink: Color, fill: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(fill).padding(horizontal = 10.dp, vertical = 4.dp),
        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = ink)
}

@Composable
private fun HeroFact(icon: ImageVector, text: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Kit.Canvas).border(1.dp, Kit.Border, RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = Kit.Muted)
        Text(text, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A white card with a title, an icon tile and its content. */
@Composable
private fun ProfileCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, note: String? = null, trailing: (@Composable () -> Unit)? = null,
                        content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Kit.GreenSoft), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(19.dp), tint = Kit.Green)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.sp, color = Kit.Ink)
                    note?.let { Text(it, fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Muted) }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** Current password, the new one twice, the rules ticking green as they're met; the other devices are signed out. */
@Composable
private fun PasswordCard(state: ProfileState, model: ProfileScreenModel?, modifier: Modifier) {
    var current by remember(state.passwordChanged) { mutableStateOf("") }
    var new by remember(state.passwordChanged) { mutableStateOf("") }
    var repeat by remember(state.passwordChanged) { mutableStateOf("") }
    val checks = passwordChecks(new)
    val rulesMet = checks.all { it.second }
    val same = new.isNotEmpty() && new == current
    val matches = repeat.isNotEmpty() && repeat == new
    val ready = current.isNotEmpty() && rulesMet && matches && !same && !state.savingPassword
    ProfileCard("Change password", Icons.Outlined.Lock, modifier, note = "Your other devices will be signed out.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(current, { current = it }, label = "Current password", password = true, enabled = !state.savingPassword)
            TextInput(new, { new = it }, label = "New password", password = true, enabled = !state.savingPassword,
                problem = if (same) "Choose a password different from your current one." else null)
            TextInput(repeat, { repeat = it }, label = "Repeat the new password", password = true, enabled = !state.savingPassword,
                problem = if (repeat.isNotEmpty() && !matches && repeat.length >= new.length) "The two passwords aren't the same." else null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                checks.forEach { (rule, met) -> RuleChip(rule, met) }
            }
            state.passwordProblem?.let {
                Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFBEDEB)).padding(horizontal = 12.dp, vertical = 9.dp),
                    fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, color = Kit.Danger)
            }
            // Said before they press it (user, 2026-11-18: "notify the user all the other devices will be signed out").
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEF4F8)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Devices, null, Modifier.size(16.dp), tint = Kit.Blue)
                Text("You'll stay signed in here. Your other devices will be signed out.", fontFamily = Inter(), fontSize = 12.5.sp,
                    lineHeight = 17.sp, letterSpacing = 0.sp, color = Kit.Ink)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                KitButton("Change password", { model?.changePassword(current, new) }, enabled = ready, loading = state.savingPassword)
            }
        }
    }
}

@Composable
private fun RuleChip(text: String, met: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (met) Kit.GreenSoft else Kit.Canvas)
            .border(1.dp, if (met) Color(0xFFCFE0C8) else Kit.Border, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(if (met) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(14.dp), tint = if (met) Kit.Green else Kit.Faint)
        Text(text, fontFamily = Inter(), fontSize = 12.sp, letterSpacing = 0.sp, color = if (met) Kit.Green else Kit.Muted)
    }
}

/** Every device the person is signed in on: this one first, then by last use; any other can be signed out. */
@Composable
private fun DevicesCard(state: ProfileState, model: ProfileScreenModel?, phone: Boolean, onSignOutEverywhere: () -> Unit) {
    val now = remember { kotlin.time.Clock.System.now() }
    val zone = remember { TimeZone.currentSystemDefault() }
    ProfileCard(
        "Your devices", Icons.Outlined.Devices, Modifier.fillMaxWidth(),
        note = "Where you're signed in. You can be signed in on up to 5 devices.",
        trailing = if (phone) null else ({
            KitButton("Sign out everywhere", onSignOutEverywhere, style = ButtonStyle.DANGER_OUTLINE, enabled = state.devicesLoaded)
        })
    ) {
        when {
            !state.devicesLoaded -> repeat(2) { i ->
                if (i > 0) HorizontalDivider(color = Kit.RowBorder)
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Kit.Tint))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(180.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(Kit.Tint))
                        Box(Modifier.size(120.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(Kit.Tint))
                    }
                }
            }
            state.devicesProblem != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.devicesProblem, fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Danger)
                com.saporini.mobile_desktop.core.components.RetryText(onClick = { model?.loadDevices() })
            }
            else -> state.devices.forEachIndexed { i, device ->
                if (i > 0) HorizontalDivider(color = Kit.RowBorder)
                DeviceLine(device, now, zone, phone, ending = state.endingDevice == device.id, busy = state.endingDevice != null,
                    onSignOut = { model?.endDevice(device) })
            }
        }
        if (phone) {
            Spacer(Modifier.height(14.dp))
            KitButton("Sign out everywhere", onSignOutEverywhere, Modifier.fillMaxWidth(), style = ButtonStyle.DANGER_OUTLINE, enabled = state.devicesLoaded)
        }
    }
}

@Composable
private fun DeviceLine(device: MySession, now: kotlin.time.Instant, zone: TimeZone, phone: Boolean, ending: Boolean, busy: Boolean, onSignOut: () -> Unit) {
    val name = device.deviceName?.takeIf { it.isNotBlank() && it != "UNKNOWN" } ?: "Unknown device"
    val computer = listOf("computer", "windows", "mac", "linux").any { name.contains(it, ignoreCase = true) }
    val signedIn = instantOrNull(device.createdAt)?.toLocalDateTime(zone)?.date?.let { com.saporini.mobile_desktop.core.format.shortDate(it) }
    val detail = listOfNotNull(
        if (device.current) "Using it now" else "Last used " + agoText(device.lastUsedAt ?: device.createdAt, now, zone),
        signedIn?.let { "signed in $it" }
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (device.current) Kit.GreenSoft else Kit.Canvas)
            .border(1.dp, if (device.current) Color(0xFFCFE0C8) else Kit.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(if (computer) Icons.Outlined.Computer else Icons.Outlined.Smartphone, null, Modifier.size(21.dp), tint = if (device.current) Kit.Green else Kit.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, Modifier.weight(1f, fill = false), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.sp,
                    color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (device.current) HeroPill("This device", Kit.Green, Kit.GreenSoft)
            }
            Text(detail, fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!device.current) {
            KitButton(if (phone) "Sign out" else "Sign out", onSignOut, style = ButtonStyle.SECONDARY, enabled = !busy, loading = ending)
        }
    }
}

/** "Are you sure?" before signing out on every device; a failure stays in the window (the other devices are still signed in). */
@Composable
private fun SignOutEverywhereDialog(signOutEverywhere: suspend () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    AppDialog(
        title = "Sign out everywhere?",
        onDismiss = onClose,
        busy = busy,
        maxWidth = 440.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY, enabled = !busy)
            KitButton("Sign out everywhere", {
                busy = true; problem = null
                scope.launch {
                    try { signOutEverywhere() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { problem = "Couldn't reach the server. Your devices are still signed in; try again."; busy = false }
                }
            }, style = ButtonStyle.DANGER, loading = busy)
        }
    ) {
        Text("You'll be signed out on all your devices, this one too.", fontFamily = Inter(), fontSize = 14.sp, lineHeight = 20.sp, color = Kit.Muted)
        problem?.let { Text(it, fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, color = Kit.Danger) }
    }
}

/**
 * My details (user, 2026-11-18: "the form shows good and there's an Edit button which makes things editable; make the
 * profile better"): a banner in the role's colour with the role's picture (as on its Users card), the photo, name and role,
 * then the details as a tidy read-only form. Edit profile turns name, username and phone into fields; the photo gets
 * "Change photo" on it; Save asks for the password only when the username changes.
 */
@Composable
private fun ProfileDetailsCard(account: AccountInfo, state: ProfileState, model: ProfileScreenModel?, phone: Boolean) {
    val user = account.user
    val profile = state.profile
    val look = remember(user?.roles) { com.saporini.mobile_desktop.admin.people.ui.roleLookOf(user?.roles?.firstOrNull().orEmpty()) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(state.detailsSaved) { if (state.detailsSaved > 0) editing = false }
    var first by remember(profile, editing) { mutableStateOf(profile?.firstName ?: user?.firstName.orEmpty()) }
    var last by remember(profile, editing) { mutableStateOf(profile?.lastName ?: user?.lastName.orEmpty()) }
    var username by remember(profile, editing) { mutableStateOf(profile?.username ?: user?.username.orEmpty()) }
    var phoneNumber by remember(profile, editing) { mutableStateOf(profile?.phone ?: user?.phone.orEmpty()) }
    var askPassword by remember { mutableStateOf(false) }
    var changeEmail by remember { mutableStateOf(false) }
    val photo = profile?.photoUrl ?: user?.photoUrl
    val pick = com.saporini.mobile_desktop.admin.inventory.ui.rememberPhotoPicker(onProblem = { model?.photoProblem(it) },
        onPicked = { picked -> model?.uploadPhoto(picked.bytes, picked.fileName, picked.contentType) })
    val usernameChanged = profile != null && username.trim().lowercase() != profile.username.orEmpty()
    val changed = profile != null && (first.trim() != profile.firstName || last.trim() != profile.lastName ||
        phoneNumber.trim() != profile.phone.orEmpty() || usernameChanged)
    val canSave = changed && first.isNotBlank() && last.isNotBlank() && username.isNotBlank() && !state.savingDetails
    val avatarSize = if (phone) 84.dp else 108.dp
    val bannerHeight = if (phone) 92.dp else 132.dp
    val save: () -> Unit = { if (usernameChanged) askPassword = true else model?.saveProfile(first, last, phoneNumber, username, null) }

    // A narrow card (a phone, or a small window beside the sidebar): the buttons go under the name and the fields one a row,
    // so the name, a long email and the restaurant's name are never squeezed.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stack = phone || maxWidth < 640.dp
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Kit.Border)) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                Column {
                    // The banner: the role's colour, fading, with the role's own 3D picture on the right.
                    Box(Modifier.fillMaxWidth().height(bannerHeight)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(look.color.copy(alpha = 0.20f), look.color.copy(alpha = 0.07f), Color(0xFFF7F9F7))))) {
                        androidx.compose.foundation.Image(org.jetbrains.compose.resources.painterResource(look.image), null,
                            Modifier.align(Alignment.BottomEnd).padding(end = if (phone) 12.dp else 28.dp).size(if (phone) 96.dp else 150.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = avatarSize / 2 + 14.dp)
                            .padding(start = (if (phone) 16.dp else 28.dp) + avatarSize + (if (phone) 12.dp else 20.dp), end = if (phone) 16.dp else 24.dp, top = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(displayName(user), fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (phone) 19.sp else 24.sp,
                                letterSpacing = (-0.3).sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("@" + (profile?.username ?: user?.username.orEmpty()), fontFamily = Inter(), fontSize = 13.sp, letterSpacing = 0.sp,
                                    color = Kit.Muted, modifier = Modifier.padding(top = 3.dp))
                                roleLabel(user)?.let { HeroPill(it, look.color, look.color.copy(alpha = 0.12f)) }
                                placeLine(account)?.let { HeroFact(Icons.Outlined.Storefront, it) }
                            }
                        }
                        if (!stack) EditButtons(editing, canSave, state.savingDetails, onEdit = { editing = true }, onCancel = { editing = false }, onSave = save)
                    }
                }
                // The photo, half over the banner. While editing it says "Change photo" on it.
                Box(Modifier.padding(start = if (phone) 16.dp else 28.dp, top = bannerHeight - avatarSize / 2)
                    .size(avatarSize).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).padding(4.dp)) {
                    AccountAvatar(user, avatarSize - 8.dp, if (phone) 26.sp else 34.sp, photoUrl = photo)
                    if (editing || state.photoBusy) {
                        Box(Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                            .clickable(enabled = !state.photoBusy) { pick() }, contentAlignment = Alignment.Center) {
                            if (state.photoBusy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Color.White)
                            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(if (phone) 20.dp else 24.dp), tint = Color.White)
                                Text(if (photo == null) "Add photo" else "Change", fontFamily = Inter(), fontWeight = FontWeight.SemiBold,
                                    fontSize = if (phone) 10.5.sp else 12.sp, letterSpacing = 0.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = if (phone) 16.dp else 28.dp, vertical = if (phone) 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (editing && photo != null) {
                    Text("Remove photo", Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !state.photoBusy) { model?.removePhoto() }.padding(vertical = 2.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Danger)
                }
                if (stack) EditButtons(editing, canSave, state.savingDetails, onEdit = { editing = true }, onCancel = { editing = false }, onSave = save, full = true)

                FormGroupTitle("Personal information")
                FieldGrid(stack) {
                    ProfileField("First name", first, editing, onChange = { first = it }, modifier = it)
                    ProfileField("Last name", last, editing, onChange = { last = it }, modifier = it)
                }
                FieldGrid(stack) {
                    ProfileField("Username", if (editing) username else "@" + username, editing, onChange = { username = it }, modifier = it,
                        hint = if (editing) usernameHint(profile) else null, enabled = (profile?.usernameChangesLeft ?: 0) > 0)
                    if (!stack) Spacer(it)
                }

                // Email and phone together (user, 2026-11-18: "email and phone in the same category").
                FormGroupTitle("Contact")
                FieldGrid(stack) {
                    EmailField(profile, it, onChange = { changeEmail = true }, onCancelPending = { model?.cancelEmailChange() })
                    PhoneField(phoneNumber, editing, it, onChange = { phoneNumber = it })
                }
                state.detailsProblem?.let { ProblemLine(it) }

                FormGroupTitle("Work")
                FieldGrid(stack, columns = 3) {
                    ProfileField("Role", roleLabel(user) ?: "–", editing = false, locked = editing, modifier = it)
                    ProfileField("Restaurant", account.restaurantName ?: "–", editing = false, locked = editing, modifier = it)
                    ProfileField("Branch", account.branchName ?: "–", editing = false, locked = editing, modifier = it)
                }
                if (editing) Text("Your role, restaurant and branch are set by your manager.", fontFamily = Inter(), fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Faint)
            }
        }
    }
    }
    if (askPassword) ConfirmPasswordDialog(state.savingDetails, state.detailsProblem, onConfirm = { password ->
        model?.saveProfile(first, last, phoneNumber, username, password)
    }, onClose = { askPassword = false })
    LaunchedEffect(state.detailsSaved, state.detailsProblem) { if (state.detailsSaved > 0 && state.detailsProblem == null) askPassword = false }
    if (changeEmail) ChangeEmailDialog(state, model) { changeEmail = false }
}

private fun usernameHint(profile: com.saporini.mobile_desktop.auth.data.dto.MyProfile?): String {
    val left = profile?.usernameChangesLeft ?: 3
    return if (left == 0) "No changes left this month." + (instantOrNull(profile?.nextUsernameChangeAt)?.let { at ->
        " Next on " + com.saporini.mobile_desktop.core.format.shortDate(at.toLocalDateTime(TimeZone.currentSystemDefault()).date) + "." } ?: "")
    else "Letters, numbers, . - _ · $left ${if (left == 1) "change" else "changes"} left in 30 days"
}

@Composable
private fun EditButtons(editing: Boolean, canSave: Boolean, saving: Boolean, onEdit: () -> Unit, onCancel: () -> Unit, onSave: () -> Unit, full: Boolean = false) {
    Row(if (full) Modifier.fillMaxWidth() else Modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, if (full) Alignment.End else Alignment.Start)) {
        if (editing) {
            KitButton("Cancel", onCancel, style = ButtonStyle.SECONDARY, enabled = !saving)
            KitButton("Save changes", onSave, enabled = canSave, loading = saving)
        } else {
            KitButton("Edit profile", onEdit, icon = Icons.Outlined.Edit, style = ButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun FormGroupTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Ink)
        HorizontalDivider(Modifier.weight(1f), color = Kit.RowBorder)
    }
}

/** Two (or three) fields a row; one a row on a phone. */
@Composable
private fun FieldGrid(phone: Boolean, columns: Int = 2, content: @Composable (Modifier) -> Unit) {
    if (phone) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content(Modifier.fillMaxWidth()) }
    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) { content(Modifier.weight(1f)) }
}

/**
 * One detail: its name above, the value in a soft box when reading, a field when editing ([onChange]); [locked] shows a lock
 * (set by the manager) while editing.
 */
@Composable
private fun ProfileField(label: String, value: String, editing: Boolean, modifier: Modifier = Modifier, onChange: ((String) -> Unit)? = null,
                         hint: String? = null, placeholder: String = "", locked: Boolean = false, muted: Boolean = false, enabled: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        if (editing && onChange != null) {
            TextInput(value, onChange, placeholder = placeholder, enabled = enabled, maxLength = 100)
            hint?.let { Text(it, fontFamily = Inter(), fontSize = 11.5.sp, letterSpacing = 0.sp, color = Kit.Faint) }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(11.dp)).background(if (locked) Color(0xFFF2F3F1) else Kit.Canvas)
                .border(1.dp, Kit.RowBorder, RoundedCornerShape(11.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold, fontSize = 14.sp,
                    letterSpacing = 0.sp, color = if (muted || locked) Kit.Muted else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (locked) Icon(Icons.Outlined.Lock, "Set by your manager", Modifier.size(15.dp), tint = Kit.Faint)
            }
        }
    }
}

/** The email in its box, Confirmed, and Change (always: it never changes without the link); a waiting change under it. */
@Composable
private fun EmailField(profile: com.saporini.mobile_desktop.auth.data.dto.MyProfile?, modifier: Modifier, onChange: () -> Unit, onCancelPending: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // "Confirmed" sits beside the label, so a long email keeps the box's width (it used to get only half of it).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Email", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
            if (profile?.emailVerified == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(13.dp), tint = Kit.Green)
                Text("Confirmed", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = Kit.Green)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(11.dp))) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 34.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Email, null, Modifier.size(16.dp), tint = Kit.Muted)
                Text(profile?.email ?: "–", Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    letterSpacing = 0.sp, color = Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile != null && profile.pendingEmail == null) {
                    Text("Change", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onChange).padding(horizontal = 10.dp, vertical = 6.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Green)
                }
            }
            profile?.pendingEmail?.let { pending ->
                HorizontalDivider(color = Kit.RowBorder)
                Row(Modifier.fillMaxWidth().background(Color(0xFFFFF8EA)).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.MarkEmailUnread, null, Modifier.size(16.dp), tint = Kit.Amber)
                    Text("Waiting for you to open the link we sent to $pending", Modifier.weight(1f), fontFamily = Inter(), fontSize = 12.5.sp,
                        letterSpacing = 0.sp, color = Kit.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Cancel", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancelPending).padding(horizontal = 10.dp, vertical = 6.dp),
                        fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = Kit.Danger)
                }
            }
        }
    }
}

/** The phone beside the email, in the same box; a field while editing. Confirming it by SMS comes later. */
@Composable
private fun PhoneField(value: String, editing: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Phone", fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.sp, color = Kit.Muted)
        if (editing) {
            TextInput(value, onChange, placeholder = "+355 …", icon = Icons.Outlined.Phone, maxLength = 50,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Kit.Canvas).border(1.dp, Kit.RowBorder, RoundedCornerShape(11.dp))
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 34.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Phone, null, Modifier.size(16.dp), tint = Kit.Muted)
                Text(value.ifBlank { "Not added" }, Modifier.weight(1f), fontFamily = Inter(), fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    fontSize = 14.sp, letterSpacing = 0.sp, color = if (value.isBlank()) Kit.Muted else Kit.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A new username needs the password, like a new email. */
@Composable
private fun ConfirmPasswordDialog(saving: Boolean, problem: String?, onConfirm: (String) -> Unit, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AppDialog(
        title = "Confirm it's you",
        subtitle = "Type your password to change your username.",
        onDismiss = onClose,
        busy = saving,
        maxWidth = 440.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY, enabled = !saving)
            KitButton("Save changes", { onConfirm(password) }, enabled = password.isNotEmpty() && !saving, loading = saving)
        }
    ) {
        TextInput(password, { password = it }, label = "Your password", password = true, enabled = !saving)
        problem?.let { ProblemLine(it) }
    }
}

/** New email + password; the link goes to the new address and the old one gets a note. */
@Composable
private fun ChangeEmailDialog(state: ProfileState, model: ProfileScreenModel?, onClose: () -> Unit) {
    var newEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val sentAtOpen = remember { state.emailSent }
    LaunchedEffect(state.emailSent) { if (state.emailSent > sentAtOpen) onClose() }
    AppDialog(
        title = "Change your email",
        subtitle = "We'll send a link to the new email. It changes once you open it.",
        onDismiss = onClose,
        busy = state.emailBusy,
        maxWidth = 460.dp,
        buttons = {
            KitButton("Cancel", onClose, style = ButtonStyle.SECONDARY, enabled = !state.emailBusy)
            KitButton("Send the link", { model?.changeEmail(newEmail, password) },
                enabled = newEmail.contains('@') && newEmail.substringAfter('@').contains('.') && password.isNotEmpty() && !state.emailBusy,
                loading = state.emailBusy)
        }
    ) {
        TextInput(newEmail, { newEmail = it }, label = "New email", icon = Icons.Outlined.Email, maxLength = 150,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, enabled = !state.emailBusy)
        TextInput(password, { password = it }, label = "Your password", password = true, enabled = !state.emailBusy)
        Text("Your old email gets a short note, so you'll know if it wasn't you.", fontFamily = Inter(), fontSize = 12.5.sp, lineHeight = 17.sp,
            letterSpacing = 0.sp, color = Kit.Muted)
        state.emailProblem?.let { ProblemLine(it) }
    }
}

@Composable
private fun ProblemLine(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFBEDEB)).padding(horizontal = 12.dp, vertical = 9.dp),
        fontFamily = Inter(), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp, color = Kit.Danger)
}
