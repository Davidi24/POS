package com.saporini.mobile_desktop.home.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.saporini.mobile_desktop.auth.data.repository.AuthRepository
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoggingOut: Boolean = false,
    val loggedOut: Boolean = false
)

class HomeScreenModel : ScreenModel {

    private val repository = AuthRepository()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun logout() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoggingOut = true)
            try {
                TokenStore.refreshToken.value?.let { repository.logout(it) }
            } catch (_: Exception) { }
            SessionManager.signOut()
            _state.value = _state.value.copy(loggedOut = true)
        }
    }


}