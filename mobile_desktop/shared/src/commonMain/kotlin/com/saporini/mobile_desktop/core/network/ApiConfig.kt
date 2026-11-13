package com.saporini.mobile_desktop.core.network

import com.saporini.mobile_desktop.auth.data.dto.AuthenticationResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import com.saporini.mobile_desktop.core.session.TokenStore
import io.ktor.http.encodedPath
import com.saporini.mobile_desktop.auth.data.dto.RefreshRequest
import com.saporini.mobile_desktop.core.session.SessionManager
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody

object ApiConfig {
    private var baseUrlOverride: String? = null

    val BASE_URL: String
        get() = baseUrlOverride ?: platformBaseUrl()

    fun configureBaseUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        baseUrlOverride = normalized
        return normalized
    }

    fun baseUrlCandidates(): List<String> {
        return (listOf(BASE_URL) + platformFallbackBaseUrls())
            .map(::normalizeBaseUrl)
            .distinct()
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            return platformBaseUrl()
        }

        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}

expect fun platformBaseUrl(): String

expect fun platformFallbackBaseUrls(): List<String>

fun createHttpClient(sessionManager: SessionManager): HttpClient = HttpClient {
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                val message = try {
                    response.body<ApiErrorResponse>().message
                } catch (e: Exception) {
                    println("Unparsed API error: ${response.status.value}")
                    "Something went wrong. Please try again."
                }
                if (response.headers["X-POS-Context-Invalid"] == "true") {
                    sessionManager.rejectRestaurantContext(response.call.request.headers["X-Restaurant-Id"],
                        response.call.request.headers["X-Branch-Id"], message)
                }
                throw ApiException(response.status.value, message)
            }
        }
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    install(SSE)
    install(Logging) {
        level = LogLevel.NONE
    }
    install(Auth) {
        bearer {
            loadTokens {
                val access = TokenStore.accessToken.value
                val refresh = TokenStore.refreshToken.value
                if (access != null && refresh != null) {
                    BearerTokens(access, refresh)
                } else null
            }
            refreshTokens {
                val currentRefreshToken = TokenStore.refreshToken.value ?: return@refreshTokens null
                val refreshingUser = sessionManager.currentUser.value?.id

                try {
                    // Call the refresh endpoint with a fresh HttpClient (no auth plugin)
                    // to avoid infinite loops if the refresh endpoint itself returns 401.
                    val refreshResponse: AuthenticationResponse = client.post("${ApiConfig.BASE_URL}/auth/device/refresh") {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(currentRefreshToken))
                    }.body()

                    if (sessionManager.currentUser.value?.id != refreshingUser || TokenStore.refreshToken.value != currentRefreshToken) return@refreshTokens null

                    // Save the new tokens
                    TokenStore.save(refreshResponse.accessToken, refreshResponse.refreshToken)
                    sessionManager.signIn(refreshResponse.user)

                    BearerTokens(refreshResponse.accessToken, refreshResponse.refreshToken)
                } catch (e: ApiException) {
                    // Expired/revoked credentials end the session. Rate limits and outages remain retryable.
                    if (e.status !in listOf(400, 401, 403)) throw e
                    if (sessionManager.currentUser.value?.id == refreshingUser && TokenStore.refreshToken.value == currentRefreshToken) sessionManager.signOut()
                    null
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Preserve the connection error so a permission recheck does not mistake it for an expired session.
                    throw e
                }
            }
            sendWithoutRequest { request ->
                // Always send the bearer token, even on the first request (after login)
                !request.url.encodedPath.contains("/auth/device/login") &&
                        !request.url.encodedPath.contains("/auth/web/login")
            }
        }
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
        // Super admins work inside the restaurant (and branch) they entered; the server reads them from these headers
        // and ignores them for everyone else. Read on every request, so entering another restaurant takes effect at once.
        sessionManager.actingRestaurant.value?.let { acting ->
            headers.append("X-Restaurant-Id", acting.restaurantId)
            acting.branchId?.let { headers.append("X-Branch-Id", it) }
        }
    }
}.also { client ->
    sessionManager.setUserLoader { client.get("${ApiConfig.BASE_URL}/auth/me").body() }
}
