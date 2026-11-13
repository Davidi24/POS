package com.saporini.mobile_desktop.auth.ui.apply

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.auth.data.dto.ApplicantRequest
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationReceipt
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationRequest
import com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationStatusView
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.platform.MAX_LEGAL_NAME
import com.saporini.mobile_desktop.platform.MAX_RESTAURANT_NAME
import com.saporini.mobile_desktop.platform.emailProblem
import com.saporini.mobile_desktop.platform.lengthProblem
import com.saporini.mobile_desktop.platform.phoneProblem
import com.saporini.mobile_desktop.platform.usernameFrom
import com.saporini.mobile_desktop.platform.usernameProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CONNECTION_PROBLEM = "Couldn't reach the server. Check the connection and try again."

/** An application to open a restaurant: the restaurant (how guests reach it) and the person who will own it. */
data class ApplicationDraft(
    val name: String = "",
    val legalName: String = "",
    val email: String = "",
    val phone: String = "",
    val currency: String = "EUR",
    val timezone: String = "Europe/Tirane",
    val firstName: String = "",
    val lastName: String = "",
    val ownerEmail: String = "",
    val ownerPhone: String = "",
    val username: String = "",
    // True once the username was typed by hand, so it stops following the owner's email.
    val usernameTouched: Boolean = false,
    val showProblems: Boolean = false
) {
    /** Applies [change]; the legal name follows the name and the username the email until typed by hand. */
    fun edit(change: (ApplicationDraft) -> ApplicationDraft): ApplicationDraft {
        var next = change(this)
        if (next.name != name && (legalName.isBlank() || legalName == name)) next = next.copy(legalName = next.name)
        if (next.ownerEmail != ownerEmail && !next.usernameTouched) next = next.copy(username = usernameFrom(next.ownerEmail))
        return next
    }

    fun request() = RestaurantApplicationRequest(
        name = name.trim(),
        legalName = legalName.trim(),
        email = email.trim(),
        phone = phone.trim(),
        currency = currency,
        timezone = timezone,
        owner = ApplicantRequest(ownerEmail.trim(), username.trim(), firstName.trim(), lastName.trim(), ownerPhone.trim().ifBlank { null })
    )
}

fun applicationProblems(draft: ApplicationDraft): Map<String, String> = buildMap {
    lengthProblem(draft.name, MAX_RESTAURANT_NAME, true, "the restaurant's name")?.let { put("name", it) }
    lengthProblem(draft.legalName, MAX_LEGAL_NAME, true, "the legal name")?.let { put("legalName", it) }
    // Guests see both on the booking pages and in their emails, so the restaurant needs them from the start.
    emailProblem(draft.email, required = true)?.let { put("email", it) }
    (if (draft.phone.isBlank()) "Enter a phone number" else phoneProblem(draft.phone))?.let { put("phone", it) }
    if (draft.currency.isBlank()) put("currency", "Choose a currency")
    if (draft.timezone.isBlank()) put("timezone", "Choose a time zone")
    lengthProblem(draft.firstName, 50, true, "your first name")?.let { put("firstName", it) }
    lengthProblem(draft.lastName, 50, true, "your last name")?.let { put("lastName", it) }
    emailProblem(draft.ownerEmail, required = true)?.let { put("ownerEmail", it) }
    usernameProblem(draft.username)?.let { put("username", it) }
    if (draft.ownerPhone.trim().length > 30) put("ownerPhone", "At most 30 characters")
}

data class ApplyState(
    val draft: ApplicationDraft = ApplicationDraft(),
    val sending: Boolean = false,
    val error: String? = null,
    // After sending: the private code to keep.
    val receipt: RestaurantApplicationReceipt? = null
)

/** "Open a restaurant": sends the application, then shows the code for checking on it. */
class ApplyRestaurantScreenModel(private val repository: AuthRepository) : ScreenModel {

    private val mutable = MutableStateFlow(ApplyState())
    val state: StateFlow<ApplyState> = mutable.asStateFlow()

    fun edit(change: (ApplicationDraft) -> ApplicationDraft) = mutable.update { it.copy(draft = it.draft.edit(change), error = null) }

    fun clearError() = mutable.update { it.copy(error = null) }

    fun send() {
        val current = state.value
        if (current.sending || current.receipt != null) return
        if (applicationProblems(current.draft).isNotEmpty()) {
            mutable.update { it.copy(draft = it.draft.copy(showProblems = true), error = "Some details are missing or need a fix.") }
            return
        }
        mutable.update { it.copy(sending = true, error = null) }
        screenModelScope.launch {
            try {
                val receipt = repository.applyForRestaurant(current.draft.request())
                mutable.update { it.copy(sending = false, receipt = receipt) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                mutable.update {
                    it.copy(sending = false, error = when (e.status) {
                        429 -> e.message.ifBlank { "Too many applications were sent from here. Try again later." }
                        403 -> "Applications are closed right now. Ask us to set up your restaurant."
                        else -> e.message.ifBlank { "The application couldn't be sent. Try again." }
                    })
                }
            } catch (e: Exception) {
                mutable.update { it.copy(sending = false, error = CONNECTION_PROBLEM) }
            }
        }
    }
}

data class ApplicationStatusState(
    val code: String = "",
    val checking: Boolean = false,
    val view: RestaurantApplicationStatusView? = null,
    val error: String? = null
)

/** "Check an application": the applicant enters the code they got and sees where it stands. */
class ApplicationStatusScreenModel(private val repository: AuthRepository) : ScreenModel {

    private val mutable = MutableStateFlow(ApplicationStatusState())
    val state: StateFlow<ApplicationStatusState> = mutable.asStateFlow()
    private var started = false

    /** Opened right after applying: the code is filled in and checked at once. */
    fun start(code: String) {
        if (started) return
        started = true
        if (code.isNotBlank()) {
            mutable.update { it.copy(code = code) }
            check()
        }
    }

    fun changeCode(text: String) = mutable.update { it.copy(code = text.take(64), view = null, error = null) }

    fun check() {
        val current = state.value
        if (current.checking) return
        val letters = current.code.count { it.isLetterOrDigit() }
        if (letters != 20) {
            mutable.update { it.copy(error = "The code has 20 letters and numbers, like 7KQ2M-X9D4T-PW3HB-R8N6C.") }
            return
        }
        mutable.update { it.copy(checking = true, error = null) }
        screenModelScope.launch {
            try {
                val view = repository.applicationStatus(current.code)
                mutable.update { it.copy(checking = false, view = view) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                mutable.update {
                    it.copy(checking = false, view = null, error = when (e.status) {
                        404 -> "No application has this code. Check that you typed it exactly as in the email."
                        429 -> e.message.ifBlank { "Too many checks from here. Wait a few minutes and try again." }
                        else -> e.message.ifBlank { "The application couldn't be checked. Try again." }
                    })
                }
            } catch (e: Exception) {
                mutable.update { it.copy(checking = false, error = CONNECTION_PROBLEM) }
            }
        }
    }
}
