package com.saporini.mobile_desktop.platform

import com.saporini.mobile_desktop.auth.data.dto.AuthenticationResponse
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.network.createHttpClient
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import com.sun.net.httpserver.HttpServer
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** The production Ktor auth/validation plugins against an actual loopback HTTP server. */
class RestaurantSessionHttpTest {
    private val fresh=CurrentUserResponse(id="u",email="u@example.invalid",username="u",firstName="User",lastName="Test",
        isActive=true,emailVerified=true,phoneVerified=false,roles=listOf("CO_SUPER_ADMIN"),permissions=listOf("RESTAURANTS_READ"))

    private fun scenario(refreshStatus:Int=200, signedIn:Boolean=true, test:suspend (SessionManager,io.ktor.client.HttpClient,AtomicInteger)->Unit) = runBlocking {
        // Fail before touching TokenStore if this suite is launched outside its isolated Gradle test JVM.
        assertTrue(System.getProperty("user.home").endsWith("test-home"),"Tests require isolated token storage")
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val calls=AtomicInteger()
        server.createContext("/") { exchange ->
            val isRefresh=exchange.requestURI.path=="/auth/device/refresh"
            val authenticated=exchange.requestHeaders.getFirst("Authorization")=="Bearer fresh-access"
            val status=if(isRefresh) refreshStatus else if(authenticated) 200 else 401
            val json=when {
                isRefresh && status==200 -> Json.encodeToString(AuthenticationResponse("fresh-access","fresh-refresh","Bearer",900,fresh))
                !isRefresh && status==200 -> Json.encodeToString(fresh)
                else -> """{"message":"Session or refresh unavailable"}"""
            }
            if(isRefresh) calls.incrementAndGet()
            exchange.requestBody.close()
            exchange.responseHeaders.add("Content-Type","application/json")
            if(status==401) exchange.responseHeaders.add("WWW-Authenticate","Bearer realm=\"test\"")
            val bytes=json.toByteArray();exchange.sendResponseHeaders(status,bytes.size.toLong())
            exchange.responseBody.use {it.write(bytes)}
        }
        server.start()
        val oldBase=ApiConfig.BASE_URL
        ApiConfig.configureBaseUrl("http://127.0.0.1:${server.address.port}")
        val session=SessionManager()
        if(signedIn) session.signIn(fresh.copy(permissions=fresh.permissions+"RESTAURANTS_UPDATE"))
        TokenStore.save("expired-access","valid-refresh")
        val client=createHttpClient(session)
        try {test(session,client,calls)}
        finally {client.close();server.stop(0);TokenStore.clear();ApiConfig.configureBaseUrl(oldBase)}
    }
    @Test fun expiredAccessRefreshesBeforeTheStartupProfileHasLoaded() = scenario(signedIn=false) {session,client,calls ->
        val me=client.get("${ApiConfig.BASE_URL}/auth/me").body<CurrentUserResponse>()
        assertEquals(fresh,me);assertEquals(fresh,session.currentUser.value);assertEquals(1,calls.get())
        assertEquals("fresh-access",TokenStore.accessToken.value)
    }
    @Test fun refreshPublishesNewPermissionsToAnAlreadyOpenSession() = scenario {session,_,calls ->
        session.refreshPermissions()
        assertEquals(fresh,session.currentUser.value);assertFalse(restaurantAccess(session.currentUser.value).edit)
        assertEquals(1,calls.get())
    }
    @Test fun revokedRefreshSignsOutAndClearsSavedCredentials() = scenario(refreshStatus=401) {session,_,calls ->
        assertFailsWith<ApiException> {session.refreshPermissions()}
        assertNull(session.currentUser.value);assertNull(TokenStore.accessToken.value);assertEquals(1,calls.get())
    }
    @Test fun refreshOutagesAndRateLimitsPreserveTheSessionForRetry() {
        for(status in listOf(503,429)) scenario(refreshStatus=status) {session,_,calls ->
            val error=assertFailsWith<ApiException> {session.refreshPermissions()}
            assertEquals(status,error.status);assertNotNull(session.currentUser.value)
            assertEquals("valid-refresh",TokenStore.refreshToken.value);assertEquals(1,calls.get())
        }
    }
}
