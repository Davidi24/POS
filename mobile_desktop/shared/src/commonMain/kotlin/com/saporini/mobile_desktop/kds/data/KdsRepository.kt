package com.saporini.mobile_desktop.kds.data

import com.saporini.mobile_desktop.kds.model.*
import kotlinx.coroutines.flow.Flow

interface KdsRepository {
    suspend fun board(scope: KdsScope, stationId: String? = null, deviceId: String? = null): List<KdsBoard>
    suspend fun display(scope: KdsScope, deviceId: String): KdsBoard
    suspend fun ticket(scope: KdsScope, ticketId: String): KdsTicket
    suspend fun orderTickets(scope: KdsScope, orderId: String): List<KdsTicket>
    suspend fun syncOrder(scope: KdsScope, orderId: String): List<KdsTicket>
    suspend fun history(scope: KdsScope, filter: KdsHistoryFilter, stationId: String?, deviceId: String?, page: Int, size: Int = 30): KdsTicketPage
    suspend fun action(scope: KdsScope, ticketId: String, itemId: String?, action: KdsAction, input: KdsActionInput): KdsTicket
    suspend fun stations(scope: KdsScope): List<KdsStation>
    suspend fun devices(scope: KdsScope): List<KdsDevice>
    suspend fun saveStation(scope: KdsScope, id: String?, input: KdsStationInput): KdsStation
    fun changes(scope: KdsScope): Flow<KdsLiveEvent>
    // POS Kitchen Status: a waiter took the ready food out, and the timings from Admin Hub → Settings → Orders & kitchen.
    suspend fun pickUp(scope: KdsScope, ticketId: String): KdsTicket = throw UnsupportedOperationException("Picking up is not available here.")
    suspend fun posTiming(scope: KdsScope): KdsPosTiming = KdsPosTiming()
}
