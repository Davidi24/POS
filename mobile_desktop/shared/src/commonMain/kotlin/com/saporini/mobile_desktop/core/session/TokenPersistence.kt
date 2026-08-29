package com.saporini.mobile_desktop.core.session

expect class TokenPersistence() {
    suspend fun saveAccessToken(token: String?)
    suspend fun saveRefreshToken(token: String?)
    suspend fun loadAccessToken(): String?
    suspend fun loadRefreshToken(): String?
    suspend fun clear()
}