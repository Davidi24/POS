package com.saporini.mobile_desktop.admin.audit

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.core.network.ApiConfig
import com.saporini.mobile_desktop.core.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The server's page size limit for the settings log. */
const val AUDIT_PAGE_SIZE = 50

@Serializable
data class AuditEntryDto(
    val id: String,
    val branchId: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val action: String? = null,
    val message: String? = null,
    val actorUserId: String? = null,
    val actorName: String? = null,
    val occurredAt: String? = null
)

@Serializable
data class AuditPageDto(
    val items: List<AuditEntryDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false
)

interface AuditLogRepository {
    suspend fun entries(restaurantId: String, page: Int, size: Int): AuditPageDto
}

class AuditLogApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String = { ApiConfig.BASE_URL }
) : AuditLogRepository {
    override suspend fun entries(restaurantId: String, page: Int, size: Int): AuditPageDto =
        client.get("${baseUrlProvider().trimEnd('/')}/restaurants/${restaurantId.encodeURLPathPart()}/settings/audit-logs") {
            parameter("page", page)
            parameter("size", size)
        }.body()
}

data class AuditLogState(
    val restaurantId: String? = null,
    val canRead: Boolean = false,
    val entries: List<AuditEntryDto> = emptyList(),
    val pages: Int = 0,
    val total: Long = 0,
    val hasNext: Boolean = false,
    // Narrows what is already loaded: the kind of setting (entityType) and free text.
    val area: String? = null,
    val search: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val stale: Boolean = true,
    val error: String? = null
) {
    val areas: List<String> get() = entries.mapNotNull { it.entityType }.distinct().sorted()

    val visible: List<AuditEntryDto> get() {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.filter { area == null || it.entityType == area }.filter { entry ->
            val text = listOfNotNull(entry.message, entry.action, entry.entityType, entry.actorName).joinToString(" ").lowercase()
            words.all { it in text }
        }
    }
}

/**
 * Admin Hub → Audit log: who changed which setting and when, newest first. Paged; a refresh reloads every page
 * already shown so the list keeps its place. Needs SETTINGS_AUDIT.
 */
class AuditLogScreenModel(
    private val repository: AuditLogRepository,
    session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(AuditLogState())
    val state: StateFlow<AuditLogState> = mutable.asStateFlow()
    private var revision = 0L
    private var active = false
    private var loadJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                loadJob?.cancel()
                mutable.value = AuditLogState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    canRead = user?.takeIf { it.isActive }?.permissions.orEmpty().contains("SETTINGS_AUDIT")
                )
                if (active) load(keepPages = false)
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load(keepPages = true) else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false, loadingMore = false) }
        }
    }

    fun area(area: String?) = mutable.update { it.copy(area = area) }

    fun search(text: String) = mutable.update { it.copy(search = text.take(200)) }

    fun refresh() = load(keepPages = true)

    private fun load(keepPages: Boolean) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                val pages = if (keepPages) current.pages.coerceAtLeast(1) else 1
                val entries = mutableListOf<AuditEntryDto>()
                var total = 0L
                var hasNext = false
                var loaded = 0
                for (page in 0 until pages) {
                    val result = repository.entries(restaurantId, page, AUDIT_PAGE_SIZE)
                    entries += result.items
                    total = result.totalElements
                    hasNext = result.hasNext
                    loaded = page + 1
                    if (!result.hasNext) break
                }
                if (token == revision) mutable.update {
                    it.copy(entries = entries.distinctBy { e -> e.id }, pages = loaded, total = total, hasNext = hasNext, stale = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(stale = true, error = adminMessage(e, write = false), canRead = it.canRead && !isDenied(e)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    fun loadMore() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || current.loading || current.loadingMore || !current.hasNext) return
        val token = revision
        mutable.update { it.copy(loadingMore = true) }
        work.launch {
            try {
                val result = repository.entries(restaurantId, current.pages, AUDIT_PAGE_SIZE)
                if (token == revision) mutable.update {
                    it.copy(entries = (it.entries + result.items).distinctBy { e -> e.id }, pages = it.pages + 1, total = result.totalElements,
                        hasNext = result.hasNext, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = adminMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun clearError() = mutable.update { it.copy(error = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }
}
