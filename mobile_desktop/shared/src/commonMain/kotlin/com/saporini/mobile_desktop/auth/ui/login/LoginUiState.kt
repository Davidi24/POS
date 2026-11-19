package com.saporini.mobile_desktop.auth.ui.login

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Why a sign-in didn't work (user, 2026-11-16: "tell exactly what's wrong", the safe way): a wrong username or password is
 * one answer; switched off, email not confirmed and restaurant closed only come after the right password; a lock opens
 * Account locked; too many tries from this device; no connection.
 */
enum class LoginProblemKind { WRONG, LOCKED, ACCOUNT_OFF, EMAIL_NOT_CONFIRMED, RESTAURANT_CLOSED, DEVICE_PAUSED, NETWORK_PAUSED, TOO_MANY_TRIES, NO_CONNECTION, OTHER }

/** [counted] = false when the server never looked at the try (a password shorter than 8 can't be anyone's), so it doesn't count toward the warning. */
data class LoginProblem(val kind: LoginProblemKind, val message: String, val lockedUntil: String? = null, val counted: Boolean = true)

/**
 * The sign-in screen's state. [problem] is what went wrong (null = nothing); [tries] counts the answers, so the same wrong
 * password twice still shakes the fields; [wrongInARow] counts this device's wrong passwords since the last sign-in, for the
 * warning after the 3rd (no number shown, so it tells nothing about the account).
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val problem: LoginProblem? = null,
    val tries: Int = 0,
    val wrongInARow: Int = 0,
    val user: CurrentUserResponse? = null,
    // At the device limit (user, 2026-11-18: "ask the person to sign out one"): the signed-in devices to choose from, unused longest first.
    val devices: List<com.saporini.mobile_desktop.core.network.SignedInDevice> = emptyList(),
    val devicesMessage: String? = null
) {
    /** "Careful: more wrong tries pause sign-in." after this device's 3rd wrong password in a row. */
    val warnAboutPause: Boolean get() = wrongInARow >= 3

    val error: String? get() = problem?.message
}

class LoginScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /** [signOutSessionId]: at the device limit, the device the person chose to sign out first. */
    fun login(identifier: String, password: String, signOutSessionId: String? = null) {
        if (_state.value.isLoading || identifier.isBlank() || password.isEmpty()) return
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true, problem = null) }
            try {
                // 1. Login — saves tokens to TokenStore
                repository.login(identifier, password, signOutSessionId)

                // 2. Prove auth works — call protected /auth/me
                val me = repository.me()
                // The person's own language from Profile, from now on on this device (user, 2026-11-18).
                com.saporini.mobile_desktop.core.i18n.AppLanguage.of(me.language)?.let { com.saporini.mobile_desktop.core.i18n.I18n.use(it) }
                sessionManager.signIn(me)
                _state.update { it.copy(isLoading = false, user = me, wrongInARow = 0, devices = emptyList(), devicesMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.code == "TOO_MANY_DEVICES" && e.devices.isNotEmpty()) {
                    // The right password: pick which device to sign out, then sign in again with it.
                    _state.update { it.copy(isLoading = false, devices = e.devices, devicesMessage = e.message, wrongInARow = 0) }
                } else fail(e)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun fail(e: Exception) {
        val problem = problemOf(e)
        _state.update {
            it.copy(isLoading = false, problem = problem, tries = it.tries + 1, devices = emptyList(), devicesMessage = null,
                wrongInARow = if (problem.kind == LoginProblemKind.WRONG && problem.counted) it.wrongInARow + 1 else it.wrongInARow)
        }
    }

    /** Closing the device choice without picking one. */
    fun dismissDevices() {
        _state.update { it.copy(devices = emptyList(), devicesMessage = null) }
    }

    /** Typing again lets go of the message. */
    fun clearProblem() {
        if (_state.value.problem != null) _state.update { it.copy(problem = null) }
    }

    private fun problemOf(error: Exception): LoginProblem {
        if (isConnectionError(error)) return LoginProblem(LoginProblemKind.NO_CONNECTION,
            "Can't reach Saporini. Check the internet connection and try again.")
        val api = error as? ApiException ?: return LoginProblem(LoginProblemKind.OTHER, "Something went wrong. Try again.")
        val kind = when (api.code) {
            "WRONG_CREDENTIALS" -> LoginProblemKind.WRONG
            "ACCOUNT_LOCKED" -> LoginProblemKind.LOCKED
            "ACCOUNT_OFF" -> LoginProblemKind.ACCOUNT_OFF
            "EMAIL_NOT_CONFIRMED" -> LoginProblemKind.EMAIL_NOT_CONFIRMED
            "RESTAURANT_CLOSED" -> LoginProblemKind.RESTAURANT_CLOSED
            "DEVICE_PAUSED" -> LoginProblemKind.DEVICE_PAUSED
            "NETWORK_PAUSED" -> LoginProblemKind.NETWORK_PAUSED
            "TOO_MANY_TRIES" -> LoginProblemKind.TOO_MANY_TRIES
            else -> when (api.status) {
                // 400: the server's field check (a password shorter than 8 characters can't be right either).
                400, 401 -> LoginProblemKind.WRONG
                429 -> LoginProblemKind.TOO_MANY_TRIES
                in 500..599 -> LoginProblemKind.NO_CONNECTION
                else -> LoginProblemKind.OTHER
            }
        }
        val message = when (kind) {
            LoginProblemKind.WRONG -> "Wrong username or password."
            LoginProblemKind.NO_CONNECTION -> "Saporini isn't answering right now. Try again in a moment."
            else -> api.message
        }
        // 400 is the server's field check: passwords shorter than 8 characters are turned away before anything is counted.
        return LoginProblem(kind, message, api.lockedUntil, counted = !(api.code == null && api.status == 400))
    }

    private fun isConnectionError(error: Throwable): Boolean {
        if (error is ApiException) return false
        return generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
            .let { message ->
                message.contains("connect") || message.contains("timeout") || message.contains("unresolved") || message.contains(
                    "network is unreachable"
                ) || message.contains("connection refused")
            }
    }
}
