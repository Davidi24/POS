package com.saporini.mobile_desktop.account

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.auth.data.dto.MySession
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
 * Profile (user, 2026-11-18: "when I click Profile I have things like change password, make it look good"): the person's
 * devices, changing their password, signing out one device or every one.
 */
data class ProfileState(
    // My details as the server keeps them (null until loaded).
    val profile: com.saporini.mobile_desktop.auth.data.dto.MyProfile? = null,
    val profileProblem: String? = null,
    val savingDetails: Boolean = false,
    val detailsProblem: String? = null,
    val detailsSaved: Int = 0,
    val emailBusy: Boolean = false,
    val emailProblem: String? = null,
    val emailSent: Int = 0,
    val photoBusy: Boolean = false,
    val devices: List<MySession> = emptyList(),
    val devicesLoaded: Boolean = false,
    val devicesProblem: String? = null,
    val endingDevice: String? = null,
    // Recent sign-ins, a page at a time (user, 2026-11-18: "make it with pages").
    val signInPage: com.saporini.mobile_desktop.auth.data.dto.SignInPage? = null,
    val signInsPageIndex: Int = 0,
    val signInsLoading: Boolean = false,
    val signInsLoaded: Boolean = false,
    val signInsProblem: String? = null,
    val savingPassword: Boolean = false,
    // The password form's own problem, shown inside the form (wrong current password, a rule, today's limit).
    val passwordProblem: String? = null,
    // Bumped after a change, so the form empties itself.
    val passwordChanged: Int = 0,
    // Your PIN (user, 2026-11-18): whether there is one, the PIN offered right now (shown only until it's used or let go),
    // and the card's own problem (today's limits, a PIN just taken, or "a PIN is for a restaurant's staff").
    val pin: com.saporini.mobile_desktop.auth.data.dto.PinStatus? = null,
    val pinLoaded: Boolean = false,
    val pinSuggestion: com.saporini.mobile_desktop.auth.data.dto.PinSuggestion? = null,
    val pinBusy: Boolean = false,
    val pinProblem: String? = null,
    val savingLanguage: Boolean = false,
    val notice: String? = null,
    val error: String? = null
)

class ProfileScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun loadDevices() {
        screenModelScope.launch {
            try {
                val devices = repository.mySessions().sortedWith(compareByDescending<MySession> { it.current }.thenByDescending { it.lastUsedAt })
                _state.update { it.copy(devices = devices, devicesLoaded = true, devicesProblem = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(devicesLoaded = true, devicesProblem = "Your devices couldn't be loaded.") }
            }
        }
    }

    fun loadProfile() {
        screenModelScope.launch {
            try {
                val profile = repository.myProfile()
                _state.update { it.copy(profile = profile, profileProblem = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(profileProblem = "Your details couldn't be loaded.") }
            }
        }
    }

    /** Name and phone; the menu's name follows (the signed-in person is loaded again). */
    fun saveDetails(firstName: String, lastName: String, phone: String) {
        if (_state.value.savingDetails) return
        screenModelScope.launch {
            _state.update { it.copy(savingDetails = true, detailsProblem = null) }
            try {
                val profile = repository.updateMyDetails(firstName.trim(), lastName.trim(), phone.trim().ifBlank { null })
                _state.update { it.copy(savingDetails = false, profile = profile, detailsSaved = it.detailsSaved + 1, notice = "Your details were saved.") }
                runCatching { sessionManager.refreshPermissions() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(savingDetails = false, detailsProblem = (e as? ApiException)?.message ?: "Couldn't reach the server. Try again.") }
            }
        }
    }

    /** Sends a link to the new address; until it's used the old email stays. */
    fun changeEmail(newEmail: String, password: String) {
        if (_state.value.emailBusy) return
        screenModelScope.launch {
            _state.update { it.copy(emailBusy = true, emailProblem = null) }
            try {
                val profile = repository.changeMyEmail(newEmail.trim(), password)
                _state.update { it.copy(emailBusy = false, profile = profile, emailSent = it.emailSent + 1,
                    notice = "We sent a link to ${newEmail.trim()}. Open it to use your new email.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(emailBusy = false, emailProblem = (e as? ApiException)?.message ?: "Couldn't reach the server. Try again.") }
            }
        }
    }

    fun cancelEmailChange() {
        screenModelScope.launch {
            try {
                val profile = repository.cancelEmailChange()
                _state.update { it.copy(profile = profile, notice = "The email change was cancelled.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't cancel it. Try again.") }
            }
        }
    }

    fun uploadPhoto(bytes: ByteArray, fileName: String, contentType: String) {
        if (_state.value.photoBusy) return
        screenModelScope.launch {
            _state.update { it.copy(photoBusy = true) }
            try {
                val profile = repository.uploadMyPhoto(bytes, fileName, contentType)
                _state.update { it.copy(photoBusy = false, profile = profile, notice = "Your photo was saved.") }
                runCatching { sessionManager.refreshPermissions() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(photoBusy = false, error = (e as? ApiException)?.message ?: "Your photo couldn't be saved. Try again.") }
            }
        }
    }

    fun removePhoto() {
        if (_state.value.photoBusy) return
        screenModelScope.launch {
            _state.update { it.copy(photoBusy = true) }
            try {
                val profile = repository.removeMyPhoto()
                _state.update { it.copy(photoBusy = false, profile = profile, notice = "Your photo was removed.") }
                runCatching { sessionManager.refreshPermissions() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(photoBusy = false, error = "Your photo couldn't be removed. Try again.") }
            }
        }
    }

    fun photoProblem(message: String?) {
        if (message != null) _state.update { it.copy(error = message) }
    }

    fun loadSignIns(page: Int = _state.value.signInsPageIndex) {
        screenModelScope.launch {
            _state.update { it.copy(signInsLoading = true, signInsPageIndex = page) }
            try {
                val found = repository.mySignIns(page)
                _state.update { it.copy(signInPage = found, signInsLoaded = true, signInsLoading = false, signInsProblem = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(signInsLoaded = true, signInsLoading = false, signInsProblem = "Your recent sign-ins couldn't be loaded.") }
            }
        }
    }

    /**
     * Saves the profile form: name and phone, and the username when it changed (that needs [password]). A refused username
     * keeps the form open with the reason; the name and phone saved before it stay saved.
     */
    fun saveProfile(firstName: String, lastName: String, phone: String, username: String, password: String?) {
        val before = _state.value.profile ?: return
        if (_state.value.savingDetails) return
        screenModelScope.launch {
            _state.update { it.copy(savingDetails = true, detailsProblem = null) }
            var profile = before
            try {
                val detailsChanged = firstName.trim() != before.firstName || lastName.trim() != before.lastName || phone.trim() != before.phone.orEmpty()
                if (detailsChanged) profile = repository.updateMyDetails(firstName.trim(), lastName.trim(), phone.trim().ifBlank { null })
                if (username.trim().lowercase() != before.username.orEmpty()) profile = repository.changeMyUsername(username.trim(), password.orEmpty())
                _state.update { it.copy(savingDetails = false, profile = profile, detailsSaved = it.detailsSaved + 1, notice = "Your profile was saved.") }
                runCatching { sessionManager.refreshPermissions() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(savingDetails = false, profile = profile, detailsProblem = (e as? ApiException)?.message ?: "Couldn't reach the server. Try again.") }
                if (profile !== before) runCatching { sessionManager.refreshPermissions() }
            }
        }
    }

    fun changePassword(current: String, new: String) {
        if (_state.value.savingPassword) return
        screenModelScope.launch {
            _state.update { it.copy(savingPassword = true, passwordProblem = null) }
            try {
                repository.changePassword(current, new)
                _state.update {
                    it.copy(savingPassword = false, passwordChanged = it.passwordChanged + 1,
                        notice = "Password changed. Your other devices were signed out.")
                }
                loadDevices()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val problem = when {
                    e.status == 401 -> "Your current password isn't right."
                    e.status == 400 && e.message.contains("between 8 and 100", ignoreCase = true) -> "Your current password isn't right."
                    else -> e.message
                }
                _state.update { it.copy(savingPassword = false, passwordProblem = problem) }
            } catch (e: Exception) {
                _state.update { it.copy(savingPassword = false, passwordProblem = "Couldn't reach the server. Try again.") }
            }
        }
    }

    fun endDevice(device: MySession) {
        if (_state.value.endingDevice != null || device.current) return
        screenModelScope.launch {
            _state.update { it.copy(endingDevice = device.id) }
            try {
                repository.endSession(device.id)
                _state.update { s -> s.copy(endingDevice = null, devices = s.devices.filterNot { it.id == device.id },
                    notice = "${device.deviceName?.takeIf { it.isNotBlank() && it != "UNKNOWN" } ?: "That device"} was signed out.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(endingDevice = null, error = "That device couldn't be signed out. Try again.") }
            }
        }
    }

    fun loadPin() {
        screenModelScope.launch {
            try {
                val pin = repository.myPin()
                _state.update { it.copy(pin = pin, pinLoaded = true, pinProblem = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pinLoaded = true, pinProblem = (e as? ApiException)?.message ?: "Your PIN couldn't be loaded.") }
            }
        }
    }

    /** A new random PIN to look at; it's the person's only after [usePin]. */
    fun suggestPin() {
        if (_state.value.pinBusy) return
        screenModelScope.launch {
            _state.update { it.copy(pinBusy = true, pinProblem = null) }
            try {
                val suggestion = repository.suggestPin()
                _state.update { s ->
                    s.copy(pinBusy = false, pinSuggestion = suggestion, pin = s.pin?.copy(suggestionsLeft = suggestion.suggestionsLeft))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pinBusy = false, pinProblem = (e as? ApiException)?.message ?: "Couldn't reach the server. Try again.") }
            }
        }
    }

    fun usePin() {
        val pin = _state.value.pinSuggestion?.pin ?: return
        if (_state.value.pinBusy) return
        screenModelScope.launch {
            _state.update { it.copy(pinBusy = true, pinProblem = null) }
            try {
                val status = repository.savePin(pin)
                _state.update { it.copy(pinBusy = false, pin = status, pinSuggestion = null, notice = "Your new PIN is ready to use on the tills.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A PIN taken meanwhile can't be used: the person generates another one.
                _state.update {
                    it.copy(pinBusy = false, pinProblem = (e as? ApiException)?.message ?: "Couldn't reach the server. Try again.",
                        pinSuggestion = if ((e as? ApiException)?.status == 409) null else it.pinSuggestion)
                }
            }
        }
    }

    /** Lets go of the PIN shown without using it. */
    fun dropPinSuggestion() = _state.update { it.copy(pinSuggestion = null, pinProblem = null) }

    /** The person's own language: saved on the server (used at every sign-in) and used here at once. */
    fun saveLanguage(language: com.saporini.mobile_desktop.core.i18n.AppLanguage) {
        if (_state.value.savingLanguage) return
        com.saporini.mobile_desktop.core.i18n.I18n.use(language)
        screenModelScope.launch {
            _state.update { it.copy(savingLanguage = true) }
            try {
                repository.setMyLanguage(language.code)
                runCatching { sessionManager.refreshPermissions() }
                _state.update { it.copy(savingLanguage = false, notice = "Your language was saved.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(savingLanguage = false, error = (e as? ApiException)?.message ?: "Couldn't reach the server. Try again.") }
            }
        }
    }

    /** Every device, this one too; throws when the server can't be reached (the window says so). */
    suspend fun signOutEverywhere() = sessionManager.signOutEverywhere()

    fun clearMessages() = _state.update { it.copy(notice = null, error = null) }
}
