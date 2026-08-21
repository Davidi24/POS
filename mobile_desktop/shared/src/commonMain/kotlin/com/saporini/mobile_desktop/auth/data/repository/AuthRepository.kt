package com.saporini.mobile_desktop.auth.data.repository

import com.saporini.mobile_desktop.auth.data.dto.AuthenticationResponse
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
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
