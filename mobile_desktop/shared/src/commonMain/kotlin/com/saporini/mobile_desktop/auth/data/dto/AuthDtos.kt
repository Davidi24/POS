package com.saporini.mobile_desktop.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
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
    val permissions: List<String>
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
