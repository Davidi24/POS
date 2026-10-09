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

object TokenStore : PendingInventoryMoveStorage {
    private val persistence: TokenPersistence? by lazy {
        try { TokenPersistence() } catch (_: Throwable) { null }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow()

    init {
        // Load saved tokens synchronously at startup so isLoggedIn returns the right value immediately
        runBlocking {
            _accessToken.value = persistence?.loadAccessToken()
            _refreshToken.value = persistence?.loadRefreshToken()
        }
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
