package com.saporini.mobile_desktop.workspace.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkspaceUiState (
    val isLoggingOut: Boolean = false,
    val loggedOut: Boolean = false
)

class WorkspaceScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager
) : ScreenModel {


    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    fun logout() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoggingOut = true)
            try {
                TokenStore.refreshToken.value?.let { repository.logout(it) }
            } catch (_: Exception) { }
            sessionManager.signOut()
            _state.value = _state.value.copy(loggedOut = true)
        }
    }


}