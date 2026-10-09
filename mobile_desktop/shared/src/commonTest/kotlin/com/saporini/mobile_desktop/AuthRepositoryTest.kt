package com.saporini.mobile_desktop

import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    @Test
    fun loginWorks() = runTest {
        val request = CompletableDeferred<HttpRequestData>()
        val client = HttpClient(MockEngine { httpRequest ->
            request.complete(httpRequest)
            respond(
                content = """
                    {
                      "accessToken": "access-token",
                      "refreshToken": "refresh-token",
                      "tokenType": "Bearer",
                      "expiresIn": 3600,
                      "user": {
                        "id": "1",
                        "email": "admin@example.com",
                        "username": "admin",
                        "firstName": "Admin",
                        "lastName": "User",
                        "phone": null,
                        "isActive": true,
                        "emailVerified": true,
                        "phoneVerified": false,
                        "roles": ["ADMIN"],
                        "permissions": ["users:read"]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        val repo = AuthRepository(
            client = client,
            baseUrlCandidatesProvider = { listOf("http://test-host:8080") },
            onBaseUrlSelected = {}
        )
        val result = repo.login(
            identifier = "admin",
            password = "ChangeMe123!"
        )

        val capturedRequest = request.await()

        assertNotNull(result.accessToken)
        assertEquals("access-token", result.accessToken)
        assertEquals("admin@example.com", result.user.email)
        assertEquals("/auth/device/login", capturedRequest.url.encodedPath)
        assertEquals("test-host", capturedRequest.url.host)
    }

    @Test
    fun loginFallsBackToNextBaseUrlAfterConnectionFailure() = runTest {
        val attemptedHosts = mutableListOf<String>()
        val selectedBaseUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { httpRequest ->
            attemptedHosts += httpRequest.url.host
            if (httpRequest.url.host == "first-host") {
                throw IllegalStateException("connection refused")
            }

            respond(
                content = """
                    {
                      "accessToken": "access-token",
                      "refreshToken": "refresh-token",
                      "tokenType": "Bearer",
                      "expiresIn": 3600,
                      "user": {
                        "id": "1",
                        "email": "admin@example.com",
                        "username": "admin",
                        "firstName": "Admin",
                        "lastName": "User",
                        "phone": null,
                        "isActive": true,
                        "emailVerified": true,
                        "phoneVerified": false,
                        "roles": ["ADMIN"],
                        "permissions": ["users:read"]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        val repo = AuthRepository(
            client = client,
            baseUrlCandidatesProvider = {
                listOf(
                    "http://first-host:8080",
                    "http://second-host:8080"
                )
            },
            onBaseUrlSelected = { selectedBaseUrls += it }
        )

        val result = repo.login(
            identifier = "admin",
            password = "ChangeMe123!"
        )

        assertEquals("access-token", result.accessToken)
        assertEquals(listOf("first-host", "second-host"), attemptedHosts)
        assertEquals(listOf("http://second-host:8080"), selectedBaseUrls)
        assertTrue(result.user.roles.contains("ADMIN"))
    }
}
