package com.saporini.mobile_desktop.auth.data.repository

import io.ktor.client.request.delete
import io.ktor.client.request.put
import com.saporini.mobile_desktop.auth.data.dto.AuthenticationResponse
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.data.dto.ForgotPasswordRequest
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

    suspend fun login(identifier: String, password: String, signOutSessionId: String? = null): AuthenticationResponse {
        val response = executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/device/login") {
                contentType(ContentType.Application.Json)
                // This device's id, for the per-device sign-in limit (user, 2026-11-17), and its name for the devices list.
                header("X-Device-Id", com.saporini.mobile_desktop.core.session.DeviceIdentity.id)
                // Only plain ASCII may go in a header; anything else would stop the request before it's sent.
                header("X-Device-Name", com.saporini.mobile_desktop.core.session.DeviceIdentity.name.filter { it.code in 32..126 }.trim())
                setBody(LoginRequest(identifier, password, signOutSessionId))
            }.body<AuthenticationResponse>()
        }

        TokenStore.save(response.accessToken, response.refreshToken)

        client.clearAuthTokens()

        return response
    }

    /**
     * Signs in on a connected till with a PIN (user, 2026-11-18). The till's id and secret tell the server which restaurant
     * the PIN belongs to. Saves the tokens like a password sign-in.
     */
    suspend fun pinLogin(till: com.saporini.mobile_desktop.core.session.TillConnection, pin: String): AuthenticationResponse {
        val response = executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/device/pin-login") {
                contentType(ContentType.Application.Json)
                header("X-Device-Id", com.saporini.mobile_desktop.core.session.DeviceIdentity.id)
                header("X-Device-Name", till.name.filter { it.code in 32..126 }.trim().ifBlank { "Till" })
                setBody(com.saporini.mobile_desktop.auth.data.dto.PinLoginRequest(till.deviceId, till.secret, pin))
            }.body<AuthenticationResponse>()
        }
        TokenStore.save(response.accessToken, response.refreshToken)
        client.clearAuthTokens()
        return response
    }

    /** Connects this device as a till with a pairing code (made by the Admin Hub), and keeps the connection on the device. */
    suspend fun connectTill(pairingToken: String): com.saporini.mobile_desktop.core.session.TillConnection {
        val answer = executeWithFallback { baseUrl ->
            client.post("$baseUrl/public/devices/pairing/redeem") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.PairingRedeemRequest(pairingToken.trim()))
            }.body<com.saporini.mobile_desktop.auth.data.dto.PairingRedeemResponse>()
        }
        val connection = com.saporini.mobile_desktop.core.session.TillConnection(answer.deviceId, answer.deviceSecret, answer.restaurantId,
            answer.branchId, answer.deviceName, answer.deviceType)
        TokenStore.connectTill(connection)
        return connection
    }

    /** Profile → Preferences → Language: the person's own language (en, it, es, sq). */
    suspend fun setMyLanguage(code: String) {
        executeWithFallback { baseUrl ->
            client.put("$baseUrl/auth/me/language") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.LanguageRequest(code))
            }
        }
    }

    /** Profile → PIN. */
    suspend fun myPin(): com.saporini.mobile_desktop.auth.data.dto.PinStatus =
        executeWithFallback { baseUrl -> client.get("$baseUrl/auth/me/pin").body() }

    /** A new random PIN to choose from (10 a day). */
    suspend fun suggestPin(): com.saporini.mobile_desktop.auth.data.dto.PinSuggestion =
        executeWithFallback { baseUrl -> client.post("$baseUrl/auth/me/pin/suggestion").body() }

    /** Makes a suggested PIN the person's own (3 a day). */
    suspend fun savePin(pin: String): com.saporini.mobile_desktop.auth.data.dto.PinStatus =
        executeWithFallback { baseUrl ->
            client.put("$baseUrl/auth/me/pin") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.SavePinRequest(pin))
            }.body()
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

    /** Profile → Change password: keeps this device signed in and signs out the others. */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        executeWithFallback { baseUrl ->
            client.put("$baseUrl/auth/change-password") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.ChangePasswordRequest(currentPassword, newPassword))
            }
        }
    }

    /** The devices the person is signed in on. */
    suspend fun mySessions(): List<com.saporini.mobile_desktop.auth.data.dto.MySession> =
        executeWithFallback { baseUrl -> client.get("$baseUrl/auth/sessions").body() }

    /** Profile → My details. */
    suspend fun myProfile(): com.saporini.mobile_desktop.auth.data.dto.MyProfile =
        executeWithFallback { baseUrl -> client.get("$baseUrl/auth/me/profile").body() }

    suspend fun updateMyDetails(firstName: String, lastName: String, phone: String?): com.saporini.mobile_desktop.auth.data.dto.MyProfile =
        executeWithFallback { baseUrl ->
            client.put("$baseUrl/auth/me/details") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.UpdateMyDetailsRequest(firstName, lastName, phone))
            }.body()
        }

    /** Sends a link to [newEmail]; the email changes once it's used. */
    suspend fun changeMyEmail(newEmail: String, currentPassword: String): com.saporini.mobile_desktop.auth.data.dto.MyProfile =
        executeWithFallback { baseUrl ->
            client.post("$baseUrl/auth/me/email") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.ChangeMyEmailRequest(newEmail, currentPassword))
            }.body()
        }

    suspend fun cancelEmailChange(): com.saporini.mobile_desktop.auth.data.dto.MyProfile =
        executeWithFallback { baseUrl -> client.delete("$baseUrl/auth/me/email").body() }

    suspend fun uploadMyPhoto(bytes: ByteArray, fileName: String, contentType: String): com.saporini.mobile_desktop.auth.data.dto.MyProfile =
        executeWithFallback { baseUrl ->
            client.put("$baseUrl/auth/me/photo") {
                setBody(io.ktor.client.request.forms.MultiPartFormDataContent(io.ktor.client.request.forms.formData {
                    append("file", bytes, io.ktor.http.Headers.build {
                        append(io.ktor.http.HttpHeaders.ContentType, contentType)
                        append(io.ktor.http.HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                    })
                }))
            }.body()
        }

    suspend fun removeMyPhoto(): com.saporini.mobile_desktop.auth.data.dto.MyProfile =
        executeWithFallback { baseUrl -> client.delete("$baseUrl/auth/me/photo").body() }

    /** A page of the person's own sign-ins of the last 30 days. */
    suspend fun mySignIns(page: Int, size: Int = 8): com.saporini.mobile_desktop.auth.data.dto.SignInPage =
        executeWithFallback { baseUrl -> client.get("$baseUrl/auth/sign-ins?page=$page&size=$size").body() }

    /** A new username (3 changes in 30 days at most). */
    suspend fun changeMyUsername(username: String, currentPassword: String): com.saporini.mobile_desktop.auth.data.dto.MyProfile =
        executeWithFallback { baseUrl ->
            client.put("$baseUrl/auth/me/username") {
                contentType(ContentType.Application.Json)
                setBody(com.saporini.mobile_desktop.auth.data.dto.ChangeMyUsernameRequest(username, currentPassword))
            }.body()
        }

    /** Signs out one of the person's other devices. */
    suspend fun endSession(sessionId: String) {
        executeWithFallback { baseUrl -> client.delete("$baseUrl/auth/sessions/$sessionId") }
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
