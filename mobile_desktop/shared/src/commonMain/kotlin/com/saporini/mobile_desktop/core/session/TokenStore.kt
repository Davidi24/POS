package com.saporini.mobile_desktop.core.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Encrypted/owner-only persistence for stock movements with an uncertain server outcome. */
interface PendingInventoryMoveStorage {
    suspend fun loadPendingInventoryMoves(): String?
    suspend fun savePendingInventoryMoves(payload: String?)
}

/**
 * This device connected to its restaurant as a till (user, 2026-11-18: "only connected devices have Sign in with PIN"):
 * its id and secret from pairing, so the server knows which restaurant a PIN belongs to. Kept when someone signs out.
 */
@kotlinx.serialization.Serializable
data class TillConnection(
    val deviceId: String,
    val secret: String,
    val restaurantId: String,
    val branchId: String? = null,
    val name: String,
    val type: String? = null
)

object TokenStore : PendingInventoryMoveStorage {
    private val persistence: TokenPersistence? by lazy {
        try { TokenPersistence() } catch (_: Throwable) { null }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow()

    private val _till = MutableStateFlow<TillConnection?>(null)
    /** This device's connection as a till, or null when it isn't one (then there's no PIN sign-in). */
    val till: StateFlow<TillConnection?> = _till.asStateFlow()

    private val tillJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    init {
        // Load saved tokens synchronously at startup so isLoggedIn returns the right value immediately
        runBlocking {
            _accessToken.value = persistence?.loadAccessToken()
            _refreshToken.value = persistence?.loadRefreshToken()
            _till.value = persistence?.loadConnectedTill()?.let { runCatching { tillJson.decodeFromString(TillConnection.serializer(), it) }.getOrNull() }
        }
    }

    /** Makes this device a till of [connection]'s restaurant. */
    fun connectTill(connection: TillConnection) {
        _till.value = connection
        scope.launch { persistence?.saveConnectedTill(tillJson.encodeToString(TillConnection.serializer(), connection)) }
    }

    /** This device is no longer a till (disconnected here, or the server no longer knows it). */
    fun forgetTill() {
        _till.value = null
        scope.launch { persistence?.saveConnectedTill(null) }
    }

    fun save(accessToken: String, refreshToken: String) {
        _accessToken.value = accessToken
        _refreshToken.value = refreshToken
        scope.launch {
            persistence?.saveAccessToken(accessToken)
            persistence?.saveRefreshToken(refreshToken)
        }
    }

    fun clear() {
        _accessToken.value = null
        _refreshToken.value = null
        scope.launch {
            persistence?.clear()
        }
    }

    override suspend fun loadPendingInventoryMoves(): String? = persistence?.loadPendingInventoryMoves()

    override suspend fun savePendingInventoryMoves(payload: String?) {
        persistence?.savePendingInventoryMoves(payload)
    }

    val isLoggedIn: Boolean
        get() = _accessToken.value != null
}
