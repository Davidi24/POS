package com.saporini.mobile_desktop.notifications

import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaffNotification(
    val id: String,
    val eventCode: String,
    val title: String,
    val message: String,
    val referenceType: String?,
    val referenceId: String?,
    val createdAt: String?,
    val read: Boolean
)

data class NotificationsState(
    val items: List<StaffNotification> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = false,
    val available: Boolean = false
)

// One shared inbox for the signed-in user: loads the latest notifications and follows the live stream.
class NotificationCenter(
    private val api: NotificationApi,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    // Fires for notifications that arrived while the app was open (for a small pop-up).
    private val _arrivals = MutableSharedFlow<StaffNotification>(extraBufferCapacity = 8)
    val arrivals: SharedFlow<StaffNotification> = _arrivals.asSharedFlow()

    // A notification the user clicked; the POS screen opens what it points at (e.g. the reservation).
    private val _openRequest = MutableStateFlow<StaffNotification?>(null)
    val openRequest: StateFlow<StaffNotification?> = _openRequest.asStateFlow()
    fun requestOpen(notification: StaffNotification) { _openRequest.value = notification }
    fun openHandled() { _openRequest.value = null }

    private var streamJob: Job? = null
    private var seenIds: Set<String>? = null

    init {
        scope.launch {
            sessionManager.currentUser.collectLatest { user ->
                streamJob?.cancel()
                seenIds = null
                val restaurantId = user?.restaurantId?.takeIf(String::isNotBlank)
                val branchId = user?.defaultBranchId?.takeIf(String::isNotBlank)
                if (restaurantId == null || branchId == null) {
                    _state.value = NotificationsState()
                    return@collectLatest
                }
                _state.value = NotificationsState(loading = true, available = true)
                streamJob = scope.launch { api.changes(restaurantId, branchId).collect { refresh(restaurantId, branchId) } }
                // Safety net in case the live connection is down for a while.
                while (true) {
                    refresh(restaurantId, branchId)
                    delay(120_000)
                }
            }
        }
    }

    private suspend fun refresh(restaurantId: String, branchId: String) {
        runCatching {
            val page = api.latest(restaurantId, branchId)
            val unread = api.unreadCount(restaurantId, branchId)
            page.items.map { it.toModel() } to unread
        }.onSuccess { (items, unread) ->
            val known = seenIds
            if (known != null) items.filter { !it.read && it.id !in known }.forEach { _arrivals.tryEmit(it) }
            seenIds = items.map { it.id }.toSet() + (known ?: emptySet())
            _state.update { it.copy(items = items, unreadCount = unread.toInt(), loading = false) }
        }.onFailure {
            _state.update { it.copy(loading = false) }
        }
    }

    fun markRead(notification: StaffNotification) {
        if (notification.read) return
        val restaurantId = sessionManager.currentUser.value?.restaurantId ?: return
        _state.update { current ->
            current.copy(
                items = current.items.map { if (it.id == notification.id) it.copy(read = true) else it },
                unreadCount = (current.unreadCount - 1).coerceAtLeast(0)
            )
        }
        scope.launch { runCatching { api.markRead(restaurantId, notification.id) } }
    }

    fun markAllRead() {
        val user = sessionManager.currentUser.value ?: return
        val restaurantId = user.restaurantId ?: return
        val branchId = user.defaultBranchId ?: return
        _state.update { current -> current.copy(items = current.items.map { it.copy(read = true) }, unreadCount = 0) }
        scope.launch { runCatching { api.markAllRead(restaurantId, branchId) } }
    }
}

private fun NotificationDto.toModel() = StaffNotification(
    id = id,
    eventCode = eventCode.orEmpty(),
    title = subject?.takeIf(String::isNotBlank) ?: "Notification",
    message = body.orEmpty(),
    referenceType = referenceType,
    referenceId = referenceId,
    createdAt = createdAt,
    read = readAt != null
)
