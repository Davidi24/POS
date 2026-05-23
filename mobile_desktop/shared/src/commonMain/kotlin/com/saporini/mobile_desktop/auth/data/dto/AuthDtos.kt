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