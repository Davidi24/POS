package com.saporini.mobile_desktop.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
    // At the device limit: the device the person chose to sign out (left out otherwise).
    val signOutSessionId: String? = null
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
    // Signing out only: LOCKED (the Lock button on a till) or IDLE (the till locked itself), for the audit log.
    val reason: String? = null
)

@Serializable
data class AuthenticationResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: CurrentUserResponse
)

@Serializable
data class CurrentUserResponse(
    val id: String,
    val restaurantId: String? = null,
    val defaultBranchId: String? = null,
    val email: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val isActive: Boolean,
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val roles: List<String>,
    val permissions: List<String>,
    // The person's restaurant and default branch by name, for their menu and Profile.
    val restaurantName: String? = null,
    val branchName: String? = null,
    // The person's own photo, or null for their initials.
    val photoUrl: String? = null
)

/** Profile → My details as the server keeps them; [pendingEmail] = a new address waiting for its link. */
@Serializable
data class MyProfile(
    val firstName: String = "",
    val lastName: String = "",
    val username: String? = null,
    val email: String = "",
    val emailVerified: Boolean = false,
    val pendingEmail: String? = null,
    val phone: String? = null,
    val phoneVerified: Boolean = false,
    val photoUrl: String? = null,
    val usernameChangesLeft: Int = 3,
    val nextUsernameChangeAt: String? = null
)

@Serializable
data class ChangeMyUsernameRequest(val username: String, val currentPassword: String)

/** A page of the person's own sign-ins, how many there are, and the 30 days' counts by result. */
@Serializable
data class SignInPage(
    val items: List<SignInRecord> = emptyList(),
    val total: Long = 0,
    val page: Int = 0,
    val size: Int = 8,
    val counts: Map<String, Long> = emptyMap()
)

@Serializable
data class UpdateMyDetailsRequest(val firstName: String, val lastName: String, val phone: String?)

@Serializable
data class ChangeMyEmailRequest(val newEmail: String, val currentPassword: String)

/** Profile → Change password. */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

/** One of the person's own sign-in tries (Profile → Security): [result] SIGNED_IN or why it failed. */
@Serializable
data class SignInRecord(
    val id: String,
    val at: String? = null,
    val result: String = "",
    val deviceId: String? = null,
    val deviceName: String? = null,
    val ipAddress: String? = null
)

/** One device the person is signed in on (Profile → Your devices); [current] = this device. */
@Serializable
data class MySession(
    val id: String,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val lastUsedAt: String? = null,
    val createdAt: String? = null,
    val current: Boolean = false
)

@Serializable
data class PasswordLinkCheckRequest(
    val token: String
)

/** What an emailed password link is for and whether it still works (who it's for only while it works). */
@Serializable
data class PasswordLinkView(
    val status: String,
    val purpose: String? = null,
    val message: String? = null,
    val firstName: String? = null,
    val username: String? = null,
    val email: String? = null,
    val restaurantName: String? = null,
    val expiresAt: String? = null
) {
    val valid: Boolean get() = status == "VALID"
    val invite: Boolean get() = purpose == "INVITE"
}

@Serializable
data class SetPasswordRequest(
    val token: String,
    val newPassword: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
    val channel: String,
    val clientTarget: String
)

/** Someone applying to open a restaurant (no sign-in); a super admin reviews it before the restaurant exists. */
@Serializable
data class RestaurantApplicationRequest(
    val name: String,
    val legalName: String,
    val email: String,
    val phone: String,
    val currency: String,
    val timezone: String,
    val owner: ApplicantRequest
)

@Serializable
data class ApplicantRequest(
    val email: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null
)

/** Right after applying: the private code (only shown here and in the email) for checking on the application. */
@Serializable
data class RestaurantApplicationReceipt(
    val id: String,
    val name: String,
    val status: String = "PENDING",
    val statusCode: String,
    val ownerEmail: String? = null,
    val submittedAt: String? = null
)

@Serializable
data class ApplicationStatusRequest(val code: String)

/** Where an application stands: PENDING, APPROVED or REJECTED (with the reason). */
@Serializable
data class RestaurantApplicationStatusView(
    val id: String,
    val name: String,
    val status: String,
    val rejectionReason: String? = null,
    val submittedAt: String? = null,
    val reviewedAt: String? = null,
    val message: String? = null
)

/** A till's pairing code, used once to connect it to its restaurant. */
@Serializable
data class PairingRedeemRequest(val pairingToken: String)

/** What pairing gives back: the device and its secret (only this once). */
@Serializable
data class PairingRedeemResponse(
    val deviceId: String,
    val restaurantId: String,
    val branchId: String? = null,
    val deviceName: String,
    val deviceType: String? = null,
    val deviceSecret: String
)

/** Signing in on a connected till with a PIN. */
@Serializable
data class PinLoginRequest(val deviceId: String, val deviceSecret: String, val pin: String)

/** Profile → PIN: whether the person has one, since when, and what's left today. */
@Serializable
data class PinStatus(
    val hasPin: Boolean = false,
    val setAt: String? = null,
    val suggestionsLeft: Int = 0,
    val changesLeft: Int = 0
)

/** A random PIN offered to the person; it becomes theirs only with "Use this PIN". */
@Serializable
data class PinSuggestion(val pin: String, val suggestionsLeft: Int = 0)

@Serializable
data class SavePinRequest(val pin: String)
