package com.saporini.mobile_desktop.auth.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.saporini.mobile_desktop.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.core.theme.Inter
import com.saporini.mobile_desktop.workspace.ui.resolveStartScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A PIN is 5 digits (user, 2026-11-18). */
const val PIN_LENGTH = 5

/** Where signing in starts on this device: the PIN pad on a connected till, the password form anywhere else. */
fun signInScreen(): Screen = if (TokenStore.till.value != null) PinLoginScreen else LoginScreen

/**
 * The PIN pad's state. [shake] counts wrong PINs so the dots shake each time; [pausedUntil] = 5 wrong PINs on this till,
 * no tries until then; [disconnected] = the server no longer knows this device as a till.
 */
data class PinLoginState(
    val pin: String = "",
    val busy: Boolean = false,
    val problem: String? = null,
    val shake: Int = 0,
    val pausedUntil: Instant? = null,
    val disconnected: Boolean = false,
    val user: CurrentUserResponse? = null
)

class PinLoginScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    private val _state = MutableStateFlow(PinLoginState())
    val state: StateFlow<PinLoginState> = _state.asStateFlow()

    private fun paused(): Boolean = _state.value.pausedUntil?.let { it > Clock.System.now() } == true

    /** A digit from the pad or the keyboard; the fifth signs in at once. */
    fun press(digit: Char) {
        val now = _state.value
        if (now.busy || now.disconnected || paused() || !digit.isDigit() || now.pin.length >= PIN_LENGTH) return
        val pin = now.pin + digit
        _state.update { it.copy(pin = pin, problem = null, pausedUntil = null) }
        if (pin.length == PIN_LENGTH) submit(pin)
    }

    fun erase() {
        if (_state.value.busy) return
        _state.update { it.copy(pin = it.pin.dropLast(1), problem = null) }
    }

    /** The pause ran out. */
    fun pauseOver() = _state.update { it.copy(pausedUntil = null) }

    private fun submit(pin: String) {
        val till = TokenStore.till.value ?: run {
            _state.update { it.copy(disconnected = true, pin = "") }
            return
        }
        // Busy at once, so a key pressed while it's sent can't send it again.
        _state.update { it.copy(busy = true, problem = null) }
        screenModelScope.launch {
            try {
                repository.pinLogin(till, pin)
                val me = repository.me()
                // The person's own language from Profile, from now on on this device (user, 2026-11-18).
                com.saporini.mobile_desktop.core.i18n.AppLanguage.of(me.language)?.let { com.saporini.mobile_desktop.core.i18n.I18n.use(it) }
                sessionManager.signIn(me)
                _state.update { it.copy(busy = false, pin = "", user = me) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                when (e.code) {
                    "WRONG_PIN" -> _state.update { it.copy(busy = false, pin = "", problem = "Wrong PIN.", shake = it.shake + 1) }
                    "PIN_PAUSED" -> _state.update {
                        it.copy(busy = false, pin = "", problem = null,
                            pausedUntil = com.saporini.mobile_desktop.core.format.instantOrNull(e.lockedUntil) ?: (Clock.System.now() + 15.minutes))
                    }
                    // The till is outside the restaurant's Wi-Fi while its branch keeps tills there (user, 2026-11-24).
                    "OUTSIDE_NETWORK" -> _state.update {
                        it.copy(busy = false, pin = "", problem = "This till works only on the restaurant's Wi-Fi. Connect it to the Wi-Fi and try again.")
                    }
                    // The person isn't one of those chosen for this till (Devices → Who can sign in here, 2026-11-25).
                    "NOT_ON_THIS_DEVICE" -> _state.update {
                        it.copy(busy = false, pin = "", problem = "You can't use this device. Ask a manager.")
                    }
                    "DEVICE_NOT_CONNECTED" -> {
                        TokenStore.forgetTill()
                        _state.update { it.copy(busy = false, pin = "", disconnected = true) }
                    }
                    else -> _state.update {
                        it.copy(busy = false, pin = "", shake = it.shake + 1,
                            problem = if (e.status >= 500) "The server isn't answering right now. Try again in a moment." else e.message)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, pin = "", problem = "Can't reach Saporini. Check the internet connection and try again.") }
            }
        }
    }
}

/**
 * Sign in with a PIN, only on a device connected to its restaurant as a till (user, 2026-11-18). The till's name on top,
 * five dots, the pad (the keyboard's digits work too), and "Sign in with password" for everyone else.
 */
object PinLoginScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<AuthRepository>()
        val session = koinInject<SessionManager>()
        val model = rememberScreenModel { PinLoginScreenModel(repository, session) }
        val state by model.state.collectAsState()
        val till by TokenStore.till.collectAsState()
        LaunchedEffect(state.user) { state.user?.let { navigator.replaceAll(resolveStartScreen(it)) } }
        val toPassword: () -> Unit = {
            val previous = navigator.items.getOrNull(navigator.items.size - 2)
            if (previous == LoginScreen) navigator.pop() else navigator.push(LoginScreen)
        }
        AuthImageScaffold {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 380.dp || maxHeight < 700.dp
                if (state.disconnected) {
                    Disconnected(isCompact, Modifier.align(Alignment.Center)) { navigator.replaceAll(LoginScreen) }
                } else {
                    PinPad(state, till?.name, isCompact, model, toPassword, Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun PinPad(state: PinLoginState, tillName: String?, isCompact: Boolean, model: PinLoginScreenModel, onPassword: () -> Unit, modifier: Modifier) {
    val ink = Color(0xFF111111)
    val muted = Color(0xFF666666)
    val red = Color(0xFFB24443)
    val keySize = if (isCompact) 58.dp else 68.dp
    val keySpacing = if (isCompact) 12.dp else 16.dp
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Seconds left of a pause after 5 wrong PINs.
    var left by remember(state.pausedUntil) { mutableStateOf(0L) }
    LaunchedEffect(state.pausedUntil) {
        val until = state.pausedUntil ?: return@LaunchedEffect
        while (true) {
            left = (until - Clock.System.now()).inWholeSeconds.coerceAtLeast(0)
            if (left <= 0) { model.pauseOver(); break }
            delay(1000)
        }
    }
    val paused = state.pausedUntil != null
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.shake) {
        if (state.shake == 0) return@LaunchedEffect
        for (x in listOf(-12f, 10f, -8f, 6f, -3f, 0f)) shake.animateTo(x, tween(45))
    }

    Column(
        modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 20.dp)
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                digitOf(event.key)?.let { model.press(it); return@onPreviewKeyEvent true }
                if (event.key == Key.Backspace || event.key == Key.Delete) { model.erase(); return@onPreviewKeyEvent true }
                false
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        tillName?.let { name ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF2F5F0)).border(1.dp, Color(0xFFE1E7DC), RoundedCornerShape(50))
                    .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Outlined.PointOfSale, null, Modifier.size(16.dp), tint = Color(0xFF3E6534))
                Text(name, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = ink, maxLines = 1)
            }
            Spacer(Modifier.height(if (isCompact) 14.dp else 20.dp))
        }
        Text("Enter your PIN", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (isCompact) 23.sp else 27.sp,
            letterSpacing = (-0.3).sp, color = ink)
        Spacer(Modifier.height(6.dp))
        Text("The 5 digits from your profile.", fontFamily = Inter(), fontSize = 14.sp, color = muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(if (isCompact) 22.dp else 30.dp))

        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF2E7D32))
            else PinDots(state.pin.length, PIN_LENGTH, if (state.problem != null) red else null, Modifier.offset(x = shake.value.dp))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            when {
                paused -> Row(
                    Modifier.clip(RoundedCornerShape(50)).background(red.copy(alpha = 0.07f)).border(1.dp, red.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.AccessTime, null, Modifier.size(15.dp), tint = red)
                    Text("Too many wrong PINs. Try again in ${left / 60}:${(left % 60).toString().padStart(2, '0')}", fontFamily = Inter(),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = ink)
                }
                state.problem != null -> Text(state.problem, fontFamily = Inter(), fontWeight = FontWeight.Medium, fontSize = 13.5.sp,
                    letterSpacing = 0.sp, color = red, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(if (isCompact) 10.dp else 16.dp))

        val enabled = !state.busy && !paused
        listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf(' ', '0', '<')).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                row.forEach { key ->
                    when (key) {
                        ' ' -> Spacer(Modifier.size(keySize))
                        '<' -> PinKey(keySize, enabled && state.pin.isNotEmpty(), showBorder = false, onClick = model::erase) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = ink)
                        }
                        else -> PinKey(keySize, enabled, onClick = { model.press(key) }) {
                            Text(key.toString(), fontFamily = Inter(), color = ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(keySpacing))
        }

        Spacer(Modifier.height(if (isCompact) 6.dp else 12.dp))
        TextButton(onClick = onPassword) {
            Text("Sign in with password", style = MaterialTheme.typography.bodyMedium, fontFamily = Inter(), color = muted,
                fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)
        }
    }
}

/** The server no longer knows this device as a till (removed, retired or connected again elsewhere). */
@Composable
private fun Disconnected(isCompact: Boolean, modifier: Modifier, onPassword: () -> Unit) {
    Column(modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFF4F4F2)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LinkOff, null, Modifier.size(34.dp), tint = Color(0xFF666666))
        }
        Spacer(Modifier.height(18.dp))
        Text("This device isn't a till any more", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = if (isCompact) 23.sp else 27.sp,
            letterSpacing = (-0.3).sp, color = Color(0xFF111111), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with your password.", fontFamily = Inter(), fontSize = 14.sp, color = Color(0xFF666666), textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        BlackButton("Sign in with password", onPassword)
    }
}

private fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

@Composable
private fun PinDots(filled: Int, total: Int, problem: Color?, modifier: Modifier = Modifier) {
    val filledColor = problem ?: Color(0xFF2E7D32)
    val emptyColor = problem?.copy(alpha = 0.5f) ?: Color(0xFFD0D0D0)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { index ->
            Box(
                Modifier.size(15.dp)
                    .background(if (index < filled) filledColor else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (index < filled) filledColor else emptyColor, CircleShape)
            )
        }
    }
}

@Composable
private fun PinKey(size: Dp, enabled: Boolean, showBorder: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (showBorder) Modifier.background(Color.White, CircleShape).border(1.dp, Color(0xFFD0D0D0), CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
