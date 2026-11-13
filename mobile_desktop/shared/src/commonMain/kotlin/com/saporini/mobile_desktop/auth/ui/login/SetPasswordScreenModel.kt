package com.saporini.mobile_desktop.auth.ui.login

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.auth.data.dto.PasswordLinkView
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.network.ApiException
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The rules for a password chosen from an emailed link; the server checks the same ones. */
internal fun passwordChecks(password: String): List<Pair<String, Boolean>> = listOf(
    "At least 8 characters" to (password.length >= 8),
    "An uppercase letter" to password.any { it.isUpperCase() },
    "A number" to password.any { it.isDigit() },
    "A special character, like ! or #" to password.any { !it.isLetterOrDigit() }
)

/** The key from a pasted link (".../public/account/password?token=…"), or the pasted key itself. */
internal fun tokenFrom(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val at = trimmed.indexOf("token=")
    val raw = if (at >= 0) trimmed.substring(at + "token=".length).substringBefore('&').substringBefore('#') else trimmed
    return runCatching { raw.decodeURLQueryComponent() }.getOrDefault(raw).trim().ifEmpty { null }
}

data class SetPasswordState(
    val link: String = "",
    val checking: Boolean = false,
    // What the server says about the link; null until checked.
    val view: PasswordLinkView? = null,
    val linkError: String? = null,
    val password: String = "",
    val confirm: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
) {
    val linkWorks: Boolean get() = view?.valid == true
    val passwordOk: Boolean get() = passwordChecks(password).all { it.second } && password.trim() == password
    val same: Boolean get() = password.isNotEmpty() && password == confirm
    val canSave: Boolean get() = linkWorks && passwordOk && same && !saving
}

/** Setting a password from an emailed link: an owner's invitation, or "Forgot password?". */
class SetPasswordScreenModel(private val repository: AuthRepository) : ScreenModel {

    private val mutable = MutableStateFlow(SetPasswordState())
    val state: StateFlow<SetPasswordState> = mutable.asStateFlow()

    private var checkJob: Job? = null
    private var started = false

    fun start(link: String) {
        if (started) return
        started = true
        if (link.isNotBlank()) changeLink(link, wait = false)
    }

    fun changeLink(text: String, wait: Boolean = true) {
        mutable.update { it.copy(link = text, view = null, linkError = null, error = null, checking = false) }
        checkJob?.cancel()
        val token = tokenFrom(text) ?: return
        checkJob = screenModelScope.launch {
            // Wait until they've finished pasting or typing.
            if (wait) delay(350)
            mutable.update { it.copy(checking = true) }
            try {
                val view = repository.checkPasswordLink(token)
                mutable.update { if (tokenFrom(it.link) == token) it.copy(view = view, checking = false) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                mutable.update { if (tokenFrom(it.link) == token) it.copy(linkError = e.message, checking = false) else it }
            } catch (e: Exception) {
                mutable.update { if (tokenFrom(it.link) == token) it.copy(linkError = CONNECTION_PROBLEM, checking = false) else it }
            }
        }
    }

    fun retryCheck() = changeLink(state.value.link, wait = false)

    fun changePassword(value: String) = mutable.update { it.copy(password = value, error = null) }

    fun changeConfirm(value: String) = mutable.update { it.copy(confirm = value, error = null) }

    fun save() {
        val current = state.value
        val token = tokenFrom(current.link) ?: return
        when {
            current.saving -> return
            !current.linkWorks -> {
                mutable.update { it.copy(error = it.view?.message ?: "Paste the link from your email first.") }
                return
            }
            !current.passwordOk -> {
                mutable.update { it.copy(error = "The password doesn't follow the rules below yet.") }
                return
            }
            !current.same -> {
                mutable.update { it.copy(error = "The two passwords aren't the same.") }
                return
            }
        }
        mutable.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                repository.setPassword(token, current.password)
                mutable.update { it.copy(saving = false, done = true, password = "", confirm = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                mutable.update { it.copy(saving = false, error = e.message) }
                // Used or replaced in the meantime: show what the link says now.
                runCatching { repository.checkPasswordLink(token) }.getOrNull()?.let { view ->
                    mutable.update { if (tokenFrom(it.link) == token) it.copy(view = view) else it }
                }
            } catch (e: Exception) {
                mutable.update { it.copy(saving = false, error = CONNECTION_PROBLEM) }
            }
        }
    }

    private companion object {
        const val CONNECTION_PROBLEM = "Couldn't reach the server. Check the connection and try again."
    }
}
