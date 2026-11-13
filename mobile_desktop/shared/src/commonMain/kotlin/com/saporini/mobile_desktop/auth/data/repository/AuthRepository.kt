package com.saporini.mobile_desktop.auth.data.repository

import com.saporini.mobile_desktop.auth.data.dto.AuthenticationResponse
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.data.dto.ForgotPasswordRequest
import com.saporini.mobile_desktop.auth.data.dto.PasswordLinkCheckRequest
import com.saporini.mobile_desktop.auth.data.dto.PasswordLinkView
import com.saporini.mobile_desktop.auth.data.dto.SetPasswordRequest
import com.saporini.mobile_desktop.auth.data.dto.LoginRequest
import com.saporini.mobile_desktop.auth.data.dto.RefreshRequest
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.session.TokenStore
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.clearAuthTokens
import io.ktor.client.request.*
import io.ktor.http.*


class AuthRepository(

    private val client: HttpClient,
    private val baseUrlCandidatesProvider: () -> List<String> = ApiConfig::baseUrlCandidates,
    private val onBaseUrlSelected: (String) -> Unit = ApiConfig::configureBaseUrl
) {

    suspend fun login(identifier: String, password: String): AuthenticationResponse {
        val response = executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/device/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(identifier, password))
            }.body<AuthenticationResponse>()
        }

        TokenStore.save(response.accessToken, response.refreshToken)

        client.clearAuthTokens()

        return response
    }

    suspend fun refresh(refreshToken: String): AuthenticationResponse {
        return executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/device/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }.body()
        }
    }

    suspend fun me(): CurrentUserResponse {
        return executeWithFallback { baseUrl ->
            client.get("$baseUrl/auth/me").body()
        }
    }

    suspend fun logout(refreshToken: String) {
        executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/device/logout") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }
        }

        TokenStore.clear()

        client.clearAuthTokens()
    }

    /** Whether the link (or the key from it) still works, and whose account it is for. */
    suspend fun checkPasswordLink(token: String): PasswordLinkView {
        return executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/password-link") {
                contentType(ContentType.Application.Json)
                setBody(PasswordLinkCheckRequest(token))
            }.body()
        }
    }

    /** Sets the password from an emailed link: an owner's first password, or a reset. */
    suspend fun setPassword(token: String, newPassword: String) {
        executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(SetPasswordRequest(token, newPassword))
            }
        }
    }

    /** Emails a reset link (or a fresh invitation to someone who never set a password). Says nothing about whether the email is known. */
    suspend fun requestPasswordReset(email: String) {
        executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest(email = email, channel = "EMAIL", clientTarget = "UNIVERSAL"))
            }
        }
    }

    /** Applies to open a restaurant (no sign-in). The answer carries the private code for checking on it. */
    suspend fun applyForRestaurant(request: com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationRequest):
        com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationReceipt =
        executeWithFallback { baseUrl ->
            client.post("$baseUrl/restaurants/registrations") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    /** Where an application stands, for whoever has its code. */
    suspend fun applicationStatus(code: String): com.saporini.mobile_desktop.auth.data.dto.RestaurantApplicationStatusView =
        executeWithFallback { baseUrl ->
            client.post("$baseUrl/restaurants/registrations/status") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.ApplicationStatusRequest(code.trim()))
            }.body()
        }

    private suspend fun <T> executeWithFallback(block: suspend (String) -> T): T {
        var lastError: Throwable? = null

        for (baseUrl in baseUrlCandidatesProvider()) {
            try {
                val result = block(baseUrl)
                onBaseUrlSelected(baseUrl)
                return result
            } catch (error: Throwable) {
                if (!isRetryable(error)) {
                    throw error
                }
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("No backend URL candidates configured")
    }

    private fun isRetryable(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

        return message.contains("connect") ||
            message.contains("timeout") ||
            message.contains("unresolved") ||
            message.contains("network is unreachable") ||
            message.contains("connection refused")
    }
}
