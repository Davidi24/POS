package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager {
    private val _currentUser = MutableStateFlow<CurrentUserResponse?>(null)
    val currentUser: StateFlow<CurrentUserResponse?> = _currentUser.asStateFlow()


    private val _isAuthenticated = MutableStateFlow(false)

    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun signIn(user: CurrentUserResponse) {
        _currentUser.value = user
        _isAuthenticated.value = true
    }

    fun signOut() {
        _currentUser.value = null
        _isAuthenticated.value = false
        TokenStore.clear()
    }
}
