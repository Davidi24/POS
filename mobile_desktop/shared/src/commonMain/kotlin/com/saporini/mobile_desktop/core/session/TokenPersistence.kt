package com.saporini.mobile_desktop.core.session

expect class TokenPersistence() {
    suspend fun saveAccessToken(token: String?)
    suspend fun saveRefreshToken(token: String?)
    suspend fun loadAccessToken(): String?
    suspend fun loadRefreshToken(): String?
    suspend fun savePendingInventoryMoves(payload: String?)
    suspend fun loadPendingInventoryMoves(): String?
    /** This till's connection to its restaurant (PIN sign-in); kept when someone signs out. */
    suspend fun saveConnectedTill(payload: String?)
    suspend fun loadConnectedTill(): String?
    suspend fun clear()
}
