package com.saporini.mobile_desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.auth.ui.login.LoginScreen
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.workspace.ui.resolveStartScreen
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    var isCheckingSession by remember { mutableStateOf(true) }
    val sessionManager = koinInject<SessionManager>()
    val authRepository = koinInject<AuthRepository>()

    val isAuthenticated by sessionManager.isAuthenticated.collectAsState()
    val user by sessionManager.currentUser.collectAsState()
    val contextError by sessionManager.contextError.collectAsState()
    val signedOutReason by sessionManager.signedOutReason.collectAsState()
    val actingRestaurant by sessionManager.actingRestaurant.collectAsState()
    var startupError by remember { mutableStateOf(false) }
    var startupAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(user?.id, user?.restaurantId, user?.defaultBranchId) {
        if (user?.restaurantId == null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(15_000)
            try { sessionManager.refreshPermissions() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Requests retain their retryable failure state during outages. */ }
        }
    }

    // The restaurant's logo in the bars (user, 2026-11-23): what sign-in says, else (super admins inside a restaurant) its
    // branding; outside a restaurant the app's own.
    val platformData = org.koin.compose.getKoin().getOrNull<com.saporini.mobile_desktop.platform.PlatformRepository>()
    LaunchedEffect(isAuthenticated, user?.id, user?.restaurantId, user?.restaurantLogoUrl) {
        val brand = com.saporini.mobile_desktop.core.session.RestaurantBrand
        val restaurantId = user?.restaurantId?.takeIf { isAuthenticated }
        if (restaurantId == null) return@LaunchedEffect brand.set(null, null)
        if (brand.restaurantId != restaurantId || user?.restaurantLogoUrl != null) brand.set(restaurantId, user?.restaurantLogoUrl)
        if (user?.restaurantLogoUrl == null && "RESTAURANTS_READ" in user?.permissions.orEmpty()) {
            try { brand.changed(restaurantId, platformData?.branding(restaurantId)?.logoUrl) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* The app's own logo stays. */ }
        }
    }

    // Amounts also in the currency chosen on this device (user, 2026-11-19: only to see; payments stay in the restaurant's
    // currency): the restaurant's currency and today's rates after sign-in, on a change of restaurant or currency, then
    // every 6 hours. Signed out, nothing is known about a restaurant.
    val exchangeRates = koinInject<com.saporini.mobile_desktop.core.money.ExchangeRateApi>()
    val shownCurrency = com.saporini.mobile_desktop.core.money.ShownMoney.chosen
    LaunchedEffect(isAuthenticated, user?.id, user?.restaurantId, shownCurrency) {
        val money = com.saporini.mobile_desktop.core.money.ShownMoney
        if (!isAuthenticated || user == null) {
            money.forget()
            return@LaunchedEffect
        }
        while (true) {
            money.refresh(exchangeRates)
            kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
        }
    }

    // A connected till locks when nobody touches it (user, 2026-11-18): 30 seconds, 3 minutes inside important work, never
    // on the kitchen display. Locking signs the person out, and the PIN pad comes back. Devices that aren't tills never lock.
    val till by TokenStore.till.collectAsState()
    LaunchedEffect(Unit) { com.saporini.mobile_desktop.core.session.watchPlatformInput { com.saporini.mobile_desktop.core.session.IdleLock.touch() } }
    LaunchedEffect(isAuthenticated, till) {
        if (!isAuthenticated || till == null) return@LaunchedEffect
        val idle = com.saporini.mobile_desktop.core.session.IdleLock
        idle.touch()
        while (true) {
            kotlinx.coroutines.delay(1_000)
            val limit = idle.level.seconds
            if (limit < 0) { idle.touch(); continue }
            if (idle.idleSeconds() >= limit) {
                sessionManager.signOut("IDLE")
                break
            }
        }
    }

    LaunchedEffect(startupAttempt) {
        startupError = false
        isCheckingSession = true
        if (TokenStore.isLoggedIn) {
            try {
                val me = authRepository.me()
                sessionManager.signIn(me)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // The server ended the sign-in while the app was closed: Signed out with why (the refresh usually said it already).
                if (e is com.saporini.mobile_desktop.core.network.ApiException && e.status in listOf(400, 401, 403)) {
                    if (sessionManager.signedOutReason.value == null) sessionManager.endedByServer(e.reason)
                }
                else startupError = true
            }
        }
        isCheckingSession = false
    }

    // The app's words in the language in use (user, 2026-11-18); every Text below looks them up.
    val language by com.saporini.mobile_desktop.core.i18n.I18n.language.collectAsState()
    val translations by com.saporini.mobile_desktop.core.i18n.I18n.revision.collectAsState()
    LaunchedEffect(Unit) { com.saporini.mobile_desktop.core.i18n.I18n.load() }
    CompositionLocalProvider(
        com.saporini.mobile_desktop.core.i18n.LocalAppLanguage provides language,
        com.saporini.mobile_desktop.core.i18n.LocalTranslations provides translations
    ) {
    SaporiniTheme {
        Surface(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            // Any touch or click in the app keeps a connected till awake (desktop also hears it from the window itself).
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    com.saporini.mobile_desktop.core.session.IdleLock.touch()
                }
            }
        }) {
            if (isCheckingSession) {
                // A quiet look while the saved sign-in is checked, never the sign-in screen first (user, 2026-11-18).
                StartupLoading()
            } else if (startupError) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.saporini.mobile_desktop.core.components.PageState(
                        com.saporini.mobile_desktop.core.components.PageStateKind.FAILED,
                        "Session couldn't be restored", "Check the connection and try again.",
                        action = { com.saporini.mobile_desktop.core.components.RetryText(onClick = { startupAttempt++ }) })
                }
            } else {
                val startScreen = user?.let { resolveStartScreen(it) }
                    ?: signedOutReason?.takeIf { till == null }?.let { com.saporini.mobile_desktop.auth.ui.login.SignedOutScreen(it) }
                    ?: com.saporini.mobile_desktop.auth.ui.login.signInScreen()
                Navigator(startScreen) { navigator ->
                    LaunchedEffect(isAuthenticated, user?.id) {
                        if (!isAuthenticated) {
                            // Ended by the server: say why; signed out by the person: straight to sign in.
                            // A till is shared, so it goes straight back to the PIN pad for whoever comes next.
                            if (TokenStore.till.value != null) sessionManager.clearSignedOutReason()
                            val reason = sessionManager.signedOutReason.value
                            if (reason != null) navigator.replaceAll(com.saporini.mobile_desktop.auth.ui.login.SignedOutScreen(reason))
                            else if (navigator.lastItem !is com.saporini.mobile_desktop.auth.ui.login.SignedOutScreen)
                                navigator.replaceAll(com.saporini.mobile_desktop.auth.ui.login.signInScreen())
                        } else if (navigator.lastItem == LoginScreen || navigator.lastItem == com.saporini.mobile_desktop.auth.ui.login.PinLoginScreen) {
                            user?.let { navigator.replaceAll(resolveStartScreen(it)) }
                        }
                    }
                    LaunchedEffect(user, navigator.lastItem) {
                        val current = user ?: return@LaunchedEffect
                        val allowed = com.saporini.mobile_desktop.core.session.accessibleWorkspaces(current)
                        val required = com.saporini.mobile_desktop.core.session.Workspace.entries.firstOrNull {
                            com.saporini.mobile_desktop.workspace.ui.screenFor(it) == navigator.lastItem
                        }
                        if ((required != null && required !in allowed) ||
                            (navigator.lastItem == com.saporini.mobile_desktop.workspace.ui.NoAccessScreen && allowed.isNotEmpty())) {
                            navigator.replaceAll(com.saporini.mobile_desktop.workspace.ui.workspaceStart(current))
                        }
                    }
                    LaunchedEffect(contextError) {
                        if (contextError != null) navigator.replaceAll(com.saporini.mobile_desktop.platform.ui.RestaurantListScreen)
                    }
                    key(user?.id, user?.restaurantId, user?.defaultBranchId) {
                        // Who is signed in and where they work now (a super admin's entered restaurant), for the menu and Profile.
                        val account = com.saporini.mobile_desktop.account.ui.AccountInfo(user,
                            restaurantName = actingRestaurant?.restaurantName ?: user?.restaurantName,
                            branchName = actingRestaurant?.branchName ?: user?.branchName)
                        CompositionLocalProvider(com.saporini.mobile_desktop.account.ui.LocalAccount provides account) {
                            CurrentScreen()
                        }
                    }
                    com.saporini.mobile_desktop.core.components.ScreenMessages(contextError, null, sessionManager::clearContextError)
                }
            }
        }
    }
    }
}

/** While the saved sign-in is checked: white, the small corner of the sign-in screens and three dots breathing in turn. */
@Composable
private fun StartupLoading() {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition()
    val phase by pulse.animateFloat(0f, 3f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)))
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        com.saporini.mobile_desktop.workspace.ui.BottomLeftDecoration(Modifier.matchParentSize(), 0.6f)
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            repeat(3) { i ->
                val near = 1f - kotlin.math.min(1f, kotlin.math.abs(((phase - i + 3f) % 3f) - 0.5f))
                Box(Modifier.size(10.dp).graphicsLayer { scaleX = 0.7f + 0.5f * near; scaleY = 0.7f + 0.5f * near }
                    .background(androidx.compose.ui.graphics.Color(0xFF111111).copy(alpha = 0.25f + 0.6f * near), androidx.compose.foundation.shape.CircleShape))
            }
        }
    }
}
