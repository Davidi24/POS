package com.saporini.mobile_desktop.auth.ui.login

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false, val error: String? = null, val user: CurrentUserResponse? = null
)

class LoginScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(identifier: String, password: String) {
        screenModelScope.launch {
            _state.value = LoginUiState(isLoading = true)
            try {
                // 1. Login — saves tokens to TokenStore
                repository.login(identifier, password)

                // 2. Prove auth works — call protected /auth/me
                val me = repository.me()
                sessionManager.signIn(me)
                _state.value = LoginUiState(user = me)

                _state.value = LoginUiState(user = me)
            } catch (e: Exception) {
                _state.value = LoginUiState(error = errorMessage(e))
            }
        }
    }

    private fun errorMessage(error: Exception): String {
        return if (isConnectionError(error)) {
            connectionErrorMessage()
        } else {
            error.message ?: "Login failed"
        }
    }

    private fun connectionErrorMessage(): String {
        val attempted = ApiConfig.baseUrlCandidates().joinToString(" or ")
        return "Cannot reach the backend at $attempted. Start the backend, or on Android try your computer's LAN IP or adb reverse with 127.0.0.1."
    }

    private fun isConnectionError(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
            .let { message ->
                message.contains("connect") || message.contains("timeout") || message.contains("unresolved") || message.contains(
                    "network is unreachable"
                ) || message.contains("connection refused")
            }
    }
}