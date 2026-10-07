@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.saporini.mobile_desktop.admin.inventory

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.admin.isStale
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.PendingInventoryMoveStorage
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class InventoryTab { STOCK, ITEMS, SUPPLIERS, PLACES, HISTORY, COUNTS }

const val MOVEMENT_PAGE_SIZE = 100
const val COUNT_PAGE_SIZE = 50

data class InventoryState(
    val userId: String? = null,
    val restaurantId: String? = null,
    val myBranchId: String? = null,
    val canRead: Boolean = false,
    val canEdit: Boolean = false,
    val tab: InventoryTab = InventoryTab.STOCK,
    val showInactive: Boolean = false,
    val search: String = "",
    val items: List<StockItemDto> = emptyList(),
    val locations: List<StockLocationDto> = emptyList(),
    val saleSources: List<SaleStockSourceDto> = emptyList(),
    // Stock on hand, for one place or all of them.
    val levelLocationId: String? = null,
    val levels: List<StockLevelDto> = emptyList(),
    val lowStock: List<StockLevelDto> = emptyList(),
    val movementType: String? = null,
    val movementItemId: String? = null,
    val movements: List<StockMovementDto> = emptyList(),
    val movementPages: Int = 0,
    val movementsHasNext: Boolean = false,
    val countStatus: String? = null,
    val counts: List<StockCountDto> = emptyList(),
    val countPages: Int = 0,
    val countsHasNext: Boolean = false,
    val countsLoadingMore: Boolean = false,
    val openCount: StockCountDto? = null,
    val itemDraft: StockItemDraft? = null,
    val itemProblems: Map<ItemField, String> = emptyMap(),
    val locationDraft: StockLocationDraft? = null,
    val moveDraft: StockMoveDraft? = null,
    /** Kept with the draft after an uncertain failure so a retry cannot apply stock twice. */
    val moveRequestKey: String? = null,
    /** The server may have committed this exact movement, so it must be replayed unchanged. */
    val moveOutcomeUnknown: Boolean = false,
    val pendingMoveStorageError: Boolean = false,
    val confirm: InventoryConfirm? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val saving: Boolean = false,
    val stale: Boolean = true,
    val notice: String? = null,
    val error: String? = null
) {
    private val lowIds: Set<String> get() = lowStock.map { it.inventoryItemId }.toSet()

    /** Items shown on the Items tab: active ones (or all), narrowed by the search text. */
    val visibleItems: List<StockItemDto> get() {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return items.filter { showInactive || it.active }.filter { item ->
            val text = listOfNotNull(item.name, item.code, item.barcode, item.supplierName, item.supplierSku).joinToString(" ").lowercase()
            words.all { it in text }
        }.sortedBy { it.name.lowercase() }
    }

    val suppliers: List<SupplierSummary> get() = suppliersOf(items, lowIds)
    val activeLocations: List<StockLocationDto> get() = locations.filter { it.active }
    fun item(id: String?): StockItemDto? = items.firstOrNull { it.id == id }
    fun location(id: String?): StockLocationDto? = locations.firstOrNull { it.id == id }
    fun saleSource(branchId: String, itemId: String): SaleStockSourceDto? =
        saleSources.firstOrNull { it.branchId == branchId && it.inventoryItemId == itemId }
}

/** An action that needs a second tap. */
sealed interface InventoryConfirm {
    data class DeactivateItem(val itemId: String) : InventoryConfirm
    data class DeactivateLocation(val locationId: String) : InventoryConfirm
    data class CountStep(val countId: String, val step: String) : InventoryConfirm
}

/**
 * Admin Hub → Inventory: stock on hand and low stock, stock items and their suppliers, storage places, the
 * history of stock changes (paged; a refresh reloads every page shown), receiving/wasting/moving/correcting stock,
 * and stock counts from draft to approval. Reading needs SETTINGS_READ, changing needs SETTINGS_UPDATE.
 */
class InventoryScreenModel(
    private val repository: InventoryRepository,
    session: SessionManager,
    private val pendingMoveStorage: PendingInventoryMoveStorage? = null
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = mutable.asStateFlow()
    private val writes = Mutex()
    private val pendingMoveWrites = Mutex()
    private val pendingMoveJson = Json { ignoreUnknownKeys = true }
    private var revision = 0L
    // Changes when someone else signs in; a write's answer for the previous person is dropped.
    private var signIn = 0L
    private var active = false
    private var loadJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                signIn++
                val currentSignIn = signIn
                loadJob?.cancel()
                val activeUser = user?.takeIf { it.isActive }
                val permissions = activeUser?.permissions.orEmpty().toSet()
                mutable.value = InventoryState(
                    userId = activeUser?.id,
                    restaurantId = activeUser?.restaurantId,
                    myBranchId = activeUser?.defaultBranchId,
                    canRead = "SETTINGS_READ" in permissions,
                    canEdit = "SETTINGS_UPDATE" in permissions
                )
                if (activeUser != null && "SETTINGS_UPDATE" in permissions) {
                    activeUser.restaurantId?.let { restaurantId ->
                        restorePendingMove(activeUser.id, restaurantId, currentSignIn)
                    }
                }
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

    fun tab(tab: InventoryTab) {
        if (state.value.tab == tab) return
        mutable.update { it.copy(tab = tab, error = null, notice = null) }
        load(keepPages = true)
    }

    fun search(text: String) = mutable.update { it.copy(search = text.take(MAX_STOCK_NAME)) }

    fun showInactive(show: Boolean) {
        if (state.value.showInactive == show) return
        mutable.update { it.copy(showInactive = show) }
        load(keepPages = true)
    }

    fun levelsAt(locationId: String?) {
        if (state.value.levelLocationId == locationId) return
        mutable.update { it.copy(levelLocationId = locationId, levels = emptyList()) }
        load(keepPages = true)
    }

    fun historyFilter(type: String?, itemId: String?) {
        if (type != null && type !in INVENTORY_MOVEMENT_TYPES) return
        val current = state.value
        if (current.movementType == type && current.movementItemId == itemId) return
        mutable.update { it.copy(movementType = type, movementItemId = itemId, movements = emptyList(), movementPages = 0, movementsHasNext = false) }
        load(keepPages = false)
    }

    fun countFilter(status: String?) {
        if (state.value.countStatus == status) return
        mutable.update { it.copy(countStatus = status, counts = emptyList(), countPages = 0, countsHasNext = false) }
        load(keepPages = false)
    }

    fun refresh() = load(keepPages = true)

    private fun load(keepPages: Boolean, preserveError: Boolean = false) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        val token = ++revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                // Items and places are needed by every tab (names, pickers); the rest only by its own tab.
                // coroutineScope: a failed call ends up in the catch below instead of escaping the screen.
                val loaded = coroutineScope {
                    val items = async { repository.items(restaurantId, includeInactive = true) }
                    val locations = async { repository.locations(restaurantId, includeInactive = true) }
                    val lowStock = async { repository.lowStock(restaurantId) }
                    val saleSources = async { repository.saleSources(restaurantId) }
                    Pair(Triple(items.await(), locations.await(), lowStock.await()), saleSources.await())
                }
                val (loadedItems, loadedLocations, low) = loaded.first
                val sources = loaded.second
                if (token != revision) return@launch
                mutable.update { it.copy(items = loadedItems, locations = loadedLocations, lowStock = low, saleSources = sources) }
                when (current.tab) {
                    InventoryTab.STOCK -> {
                        val levels = repository.levels(restaurantId, current.levelLocationId, null)
                        if (token == revision) mutable.update { it.copy(levels = levels) }
                    }
                    InventoryTab.HISTORY -> {
                        // Every page already shown comes back, so the list keeps its place.
                        val pages = if (keepPages) current.movementPages.coerceAtLeast(1) else 1
                        val movements = mutableListOf<StockMovementDto>()
                        var loaded = 0
                        var hasNext = false
                        for (page in 0 until pages) {
                            val result = repository.movements(restaurantId, current.movementType, current.movementItemId, page, MOVEMENT_PAGE_SIZE)
                            movements += result
                            loaded = page + 1
                            hasNext = result.size == MOVEMENT_PAGE_SIZE
                            if (!hasNext) break
                        }
                        if (token == revision) mutable.update {
                            it.copy(movements = movements.distinctBy { m -> m.id }, movementPages = loaded, movementsHasNext = hasNext)
                        }
                    }
                    InventoryTab.COUNTS -> {
                        val requestedPages = if (keepPages) current.countPages.coerceAtLeast(1) else 1
                        val counts = mutableListOf<StockCountDto>()
                        var loadedPages = 0
                        var hasNext = false
                        for (page in 0 until requestedPages) {
                            val result = repository.counts(restaurantId, current.countStatus, page, COUNT_PAGE_SIZE)
                            require(result.page == page && (!result.hasNext || result.items.isNotEmpty())) { "Invalid inventory count page" }
                            counts += result.items
                            loadedPages = page + 1
                            hasNext = result.hasNext
                            if (!hasNext) break
                        }
                        val open = current.openCount?.let { repository.count(restaurantId, it.id) }
                        if (token == revision) mutable.update {
                            it.copy(counts = counts.distinctBy { count -> count.id }, countPages = loadedPages,
                                countsHasNext = hasNext, openCount = open)
                        }
                    }
                    else -> Unit
                }
                if (token == revision) mutable.update {
                    it.copy(stale = false, error = if (preserveError || it.pendingMoveStorageError || it.moveOutcomeUnknown) it.error else null)
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

    /** The next page of the history, near the end of the list. */
    fun loadMore() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || current.tab != InventoryTab.HISTORY || current.loading || current.loadingMore || !current.movementsHasNext) return
        val token = revision
        mutable.update { it.copy(loadingMore = true) }
        work.launch {
            try {
                val result = repository.movements(restaurantId, current.movementType, current.movementItemId, current.movementPages, MOVEMENT_PAGE_SIZE)
                if (token == revision) mutable.update {
                    it.copy(movements = (it.movements + result).distinctBy { m -> m.id }, movementPages = it.movementPages + 1,
                        movementsHasNext = result.size == MOVEMENT_PAGE_SIZE, error = null)
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

    /** The next page of the physical-count history. */
    fun loadMoreCounts() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || current.tab != InventoryTab.COUNTS || current.loading || current.countsLoadingMore || !current.countsHasNext) return
        val token = revision
        mutable.update { it.copy(countsLoadingMore = true) }
        work.launch {
            try {
                val result = repository.counts(restaurantId, current.countStatus, current.countPages, COUNT_PAGE_SIZE)
                require(result.page == current.countPages && (!result.hasNext || result.items.isNotEmpty())) { "Invalid inventory count page" }
                if (token == revision) mutable.update {
                    it.copy(counts = (it.counts + result.items).distinctBy { count -> count.id },
                        countPages = it.countPages + 1, countsHasNext = result.hasNext, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = adminMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(countsLoadingMore = false) }
            }
        }
    }

    // ---- Stock items ----

    fun newItem() = needsEdit { mutable.update { it.copy(itemDraft = StockItemDraft(), itemProblems = emptyMap(), notice = null) } }

    fun editItem(itemId: String) = needsEdit {
        val item = state.value.item(itemId) ?: return@needsEdit
        mutable.update { it.copy(itemDraft = itemDraftOf(item), itemProblems = emptyMap(), notice = null) }
    }

    fun changeItem(change: (StockItemDraft) -> StockItemDraft) = mutable.update { state ->
        val draft = state.itemDraft ?: return@update state
        val next = change(draft).copy(id = draft.id)
        state.copy(itemDraft = next, itemProblems = itemProblems(next).filterKeys { it in state.itemProblems })
    }

    fun cancelItem() = mutable.update { it.copy(itemDraft = null, itemProblems = emptyMap()) }

    fun saveItem() {
        val current = state.value
        val draft = current.itemDraft ?: return
        val problems = itemProblems(draft)
        if (problems.isNotEmpty()) {
            mutable.update { it.copy(itemProblems = problems, error = "Check the highlighted fields") }
            return
        }
        write("${draft.name.trim()} was saved") { restaurantId ->
            val saved = repository.saveItem(restaurantId, draft.id, itemRequest(draft))
            mutable.update { state ->
                val items = if (state.items.any { it.id == saved.id }) state.items.map { if (it.id == saved.id) saved else it } else state.items + saved
                state.copy(items = items, itemDraft = null, itemProblems = emptyMap())
            }
        }
    }

    fun askDeactivateItem(itemId: String) = needsEdit { mutable.update { it.copy(confirm = InventoryConfirm.DeactivateItem(itemId)) } }

    /** Assigns the stock location used for a branch's sales of this item. */
    fun setSaleSource(branchId: String, itemId: String, locationId: String) = needsEdit {
        val current = state.value
        val item = current.item(itemId)
        val location = current.location(locationId)
        val problem = when {
            branchId.isBlank() -> "Choose a branch for the sale stock source"
            item == null || !item.active || !item.trackInventory -> "Choose an active item that tracks stock"
            location == null || !location.active -> "Choose an active stock location"
            location.branchId != null && location.branchId != branchId -> "Choose a location for this branch"
            else -> null
        }
        if (problem != null) {
            mutable.update { it.copy(error = problem) }
            return@needsEdit
        }
        write("Sale stock source saved") { restaurantId ->
            val saved = repository.setSaleSource(restaurantId, branchId, itemId, locationId)
            mutable.update { state ->
                state.copy(saleSources = (state.saleSources.filterNot {
                    it.branchId == saved.branchId && it.inventoryItemId == saved.inventoryItemId
                } + saved))
            }
        }
    }

    fun removeSaleSource(branchId: String, itemId: String) = needsEdit {
        val current = state.value
        if (current.saleSource(branchId, itemId) == null) return@needsEdit
        write("Sale stock source removed") { restaurantId ->
            repository.removeSaleSource(restaurantId, branchId, itemId)
            mutable.update { state -> state.copy(saleSources = state.saleSources.filterNot {
                it.branchId == branchId && it.inventoryItemId == itemId
            }) }
        }
    }

    // ---- Places ----

    fun newLocation() = needsEdit { mutable.update { it.copy(locationDraft = StockLocationDraft(branchId = it.myBranchId), notice = null) } }

    fun editLocation(locationId: String) = needsEdit {
        val location = state.value.location(locationId) ?: return@needsEdit
        mutable.update {
            it.copy(locationDraft = StockLocationDraft(location.id, location.name, location.code.orEmpty(), location.locationType,
                location.notes.orEmpty(), location.branchId, location.active), notice = null)
        }
    }

    fun changeLocation(change: (StockLocationDraft) -> StockLocationDraft) = mutable.update { state ->
        val draft = state.locationDraft ?: return@update state
        state.copy(locationDraft = change(draft).copy(id = draft.id))
    }

    fun cancelLocation() = mutable.update { it.copy(locationDraft = null) }

    fun saveLocation() {
        val draft = state.value.locationDraft ?: return
        locationProblem(draft)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        val request = StockLocationRequestDto(draft.branchId, draft.code.trim().ifEmpty { null }, draft.name.trim(), draft.locationType,
            draft.notes.trim().ifEmpty { null }, draft.active)
        write("${draft.name.trim()} was saved") { restaurantId ->
            val saved = repository.saveLocation(restaurantId, draft.id, request)
            mutable.update { state ->
                val locations = if (state.locations.any { it.id == saved.id }) state.locations.map { if (it.id == saved.id) saved else it }
                else state.locations + saved
                state.copy(locations = locations, locationDraft = null)
            }
        }
    }

    fun askDeactivateLocation(locationId: String) = needsEdit { mutable.update { it.copy(confirm = InventoryConfirm.DeactivateLocation(locationId)) } }

    // ---- Stock changes ----

    /** Starts a stock change, optionally for a known item and place (e.g. from a low-stock row). */
    fun startMove(kind: StockMoveKind, itemId: String? = null, locationId: String? = null) = needsEdit {
        if (state.value.moveOutcomeUnknown || state.value.pendingMoveStorageError) {
            mutable.update { it.copy(error = "Retry the pending stock change unchanged before starting another one") }
            return@needsEdit
        }
        mutable.update { it.copy(moveDraft = StockMoveDraft(kind = kind, itemId = itemId, locationId = locationId), moveRequestKey = null, notice = null) }
    }

    fun changeMove(change: (StockMoveDraft) -> StockMoveDraft) = mutable.update { state ->
        if (state.moveOutcomeUnknown || state.saving) return@update state
        state.copy(
            moveDraft = state.moveDraft?.let { draft -> change(draft).let { it.copy(reason = it.reason.take(MAX_STOCK_TEXT + 20)) } },
            moveRequestKey = null,
            error = null
        )
    }

    fun cancelMove() {
        val current = state.value
        if (current.saving || current.moveOutcomeUnknown) {
            mutable.update { it.copy(error = "Retry the pending stock change unchanged before cancelling it") }
            return
        }
        val userId = current.userId
        val restaurantId = current.restaurantId
        if (userId != null && restaurantId != null) work.launch { removePendingMove(userId, restaurantId) }
        mutable.update { it.copy(moveDraft = null, moveRequestKey = null, moveOutcomeUnknown = false) }
    }

    fun saveMove() {
        val current = state.value
        val draft = current.moveDraft ?: return
        if (current.pendingMoveStorageError) {
            mutable.update { it.copy(error = "The saved stock change cannot be read safely. Resolve it before changing stock.") }
            return
        }
        moveProblem(draft)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        if (current.item(draft.itemId) == null || current.location(draft.locationId) == null ||
            (draft.kind == StockMoveKind.TRANSFER && current.location(draft.toLocationId) == null)) {
            mutable.update { it.copy(error = "That item or place is gone. Refresh and try again.") }
            return
        }
        if (current.moveOutcomeUnknown && current.moveRequestKey == null) {
            mutable.update { it.copy(error = "The pending stock change cannot be retried safely. Contact support before changing stock.") }
            return
        }
        val userId = current.userId
        val restaurantId = current.restaurantId
        if (userId.isNullOrBlank() || restaurantId.isNullOrBlank()) {
            mutable.update { it.copy(error = "Sign in to the restaurant before changing stock") }
            return
        }
        val requestKey = current.moveRequestKey ?: kotlin.uuid.Uuid.random().toString()
        val pending = PendingInventoryMove(userId, restaurantId, requestKey, draft)
        mutable.update { it.copy(moveRequestKey = requestKey, saving = true, error = null, notice = null) }
        val token = signIn
        work.launch {
            writes.withLock {
                try {
                    persistPendingMove(pending)
                    if (token != signIn) return@withLock
                    repository.move(restaurantId, draft.kind, moveRequest(draft), requestKey)
                    removePendingMove(userId, restaurantId)
                    if (token == signIn) {
                        mutable.update { it.copy(moveDraft = null, moveRequestKey = null, moveOutcomeUnknown = false, notice = moveNotice(draft.kind)) }
                        load(keepPages = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    // HTTP 4xx responses mean the server rejected the movement. In particular,
                    // stock conflicts (409) are transactional rejections, not uncertain writes.
                    val knownRejection = e is ApiException && e.status in 400..499
                    if (knownRejection) {
                        removePendingMove(userId, restaurantId)
                        mutable.update { it.copy(moveRequestKey = null, moveOutcomeUnknown = false, error = adminMessage(e, write = true)) }
                    } else {
                        mutable.update {
                            it.copy(moveRequestKey = requestKey, moveOutcomeUnknown = true,
                                error = "The stock change may have saved. Retry this exact change before editing or cancelling it. ${adminMessage(e, write = true)}")
                        }
                    }
                    if (isStale(e)) load(keepPages = true, preserveError = true)
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    private suspend fun restorePendingMove(userId: String, restaurantId: String, currentSignIn: Long) {
        val storage = pendingMoveStorage ?: return
        try {
            val matches = pendingMoveWrites.withLock {
                val payload = storage.loadPendingInventoryMoves() ?: return
                pendingMoveJson.decodeFromString<List<PendingInventoryMove>>(payload)
                    .filter { it.userId == userId && it.restaurantId == restaurantId }
            }
            if (currentSignIn != signIn || matches.isEmpty()) return
            val pending = matches.first()
            mutable.update { it.copy(moveDraft = pending.draft, moveRequestKey = pending.requestKey, moveOutcomeUnknown = true,
                error = "A stock change may have saved before the app closed. Retry this exact change before editing or cancelling it.") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentSignIn == signIn) mutable.update { it.copy(pendingMoveStorageError = true,
                error = "Could not read the saved stock change safely. Do not make another stock change until this is resolved.") }
        }
    }

    private suspend fun persistPendingMove(pending: PendingInventoryMove) {
        val storage = pendingMoveStorage ?: return
        pendingMoveWrites.withLock {
            val existing = storage.loadPendingInventoryMoves()?.let { pendingMoveJson.decodeFromString<List<PendingInventoryMove>>(it) }.orEmpty()
            val updated = existing.filterNot { it.userId == pending.userId && it.restaurantId == pending.restaurantId } + pending
            storage.savePendingInventoryMoves(pendingMoveJson.encodeToString(updated))
        }
    }

    private suspend fun removePendingMove(userId: String, restaurantId: String) {
        val storage = pendingMoveStorage ?: return
        pendingMoveWrites.withLock {
            val existing = storage.loadPendingInventoryMoves()?.let { pendingMoveJson.decodeFromString<List<PendingInventoryMove>>(it) }.orEmpty()
            val updated = existing.filterNot { it.userId == userId && it.restaurantId == restaurantId }
            storage.savePendingInventoryMoves(updated.takeIf { it.isNotEmpty() }?.let { pendingMoveJson.encodeToString(it) })
        }
    }

    private fun moveNotice(kind: StockMoveKind) = when (kind) {
        StockMoveKind.RECEIVE -> "Stock received"
        StockMoveKind.WASTE -> "Waste recorded"
        StockMoveKind.RETURN -> "Return recorded"
        StockMoveKind.ADJUST -> "Stock corrected"
        StockMoveKind.TRANSFER -> "Stock moved"
    }

    // ---- Counts ----

    fun newCount(locationId: String, notes: String = "") {
        if (state.value.location(locationId)?.active != true) {
            mutable.update { it.copy(error = "Choose a place to count") }
            return
        }
        if (notes.trim().length > MAX_STOCK_TEXT) {
            mutable.update { it.copy(error = "Notes can be at most $MAX_STOCK_TEXT characters") }
            return
        }
        needsEdit {
            write("Count started") { restaurantId ->
                val created = repository.createCount(restaurantId,
                    StockCountCreateRequestDto(locationId, state.value.location(locationId)?.branchId, notes.trim().ifEmpty { null }))
                mutable.update { it.copy(openCount = created, counts = prependCount(it.counts, created, it.countPages)) }
            }
        }
    }

    fun openCount(countId: String?) {
        val restaurantId = state.value.restaurantId ?: return
        if (countId == null) {
            mutable.update { it.copy(openCount = null) }
            return
        }
        val token = revision
        work.launch {
            try {
                val count = repository.count(restaurantId, countId)
                if (token == revision) mutable.update { it.copy(openCount = count, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = adminMessage(e, write = false)) }
            }
        }
    }

    /** Saves what was counted for one item in the open count. */
    fun countItem(itemId: String, countedText: String, notes: String = "") {
        val count = state.value.openCount ?: return
        if (!count.editable) {
            mutable.update { it.copy(error = "This count is finished and can't change") }
            return
        }
        val counted = typedNumber(countedText, allowZero = true)
        if (counted.value == null) {
            mutable.update { it.copy(error = "Counted: ${counted.problem}") }
            return
        }
        if (notes.trim().length > MAX_STOCK_TEXT) {
            mutable.update { it.copy(error = "Notes can be at most $MAX_STOCK_TEXT characters") }
            return
        }
        needsEdit {
            write(null) { restaurantId ->
                val saved = repository.countLine(restaurantId, count.id, itemId, StockCountLineRequestDto(counted.value, notes.trim().ifEmpty { null }))
                applyCount(saved)
            }
        }
    }

    fun removeCountLine(lineId: String) {
        val count = state.value.openCount ?: return
        if (!count.editable) return
        needsEdit {
            write(null) { restaurantId -> applyCount(repository.removeCountLine(restaurantId, count.id, lineId)) }
        }
    }

    /** Asks before moving the open count on: approving changes stock, cancelling throws the count away. */
    fun askCountStep(step: String) {
        val count = state.value.openCount ?: return
        if (step !in countSteps(count.status)) return
        needsEdit {
            if (step == "start" || step == "complete") runCountStep(count.id, step)
            else mutable.update { it.copy(confirm = InventoryConfirm.CountStep(count.id, step)) }
        }
    }

    private fun runCountStep(countId: String, step: String) {
        write(when (step) {
            "approve" -> "Count approved; stock now matches what was counted"
            "cancel" -> "Count cancelled"
            "complete" -> "Count finished; approve it to update stock"
            else -> null
        }) { restaurantId ->
            val saved = repository.countStep(restaurantId, countId, step)
            applyCount(saved)
            if (step == "approve") load(keepPages = true)
        }
    }

    private fun applyCount(saved: StockCountDto) = mutable.update { state ->
        val matches = state.countStatus == null || saved.status == state.countStatus
        state.copy(
            openCount = saved.takeIf { state.openCount?.id == saved.id } ?: state.openCount,
            counts = if (matches) {
                if (state.counts.any { it.id == saved.id }) state.counts.map { if (it.id == saved.id) saved else it }
                else prependCount(state.counts, saved, state.countPages)
            } else state.counts.filterNot { it.id == saved.id }
        )
    }

    private fun prependCount(counts: List<StockCountDto>, count: StockCountDto, pages: Int): List<StockCountDto> =
        (listOf(count) + counts.filterNot { it.id == count.id }).take((pages.coerceAtLeast(1) * COUNT_PAGE_SIZE))

    // ---- Confirmations ----

    fun dismissConfirm() = mutable.update { it.copy(confirm = null) }

    fun confirm() {
        val confirm = state.value.confirm ?: return
        mutable.update { it.copy(confirm = null) }
        when (confirm) {
            is InventoryConfirm.DeactivateItem -> write("The item was switched off") { restaurantId ->
                repository.deactivateItem(restaurantId, confirm.itemId)
                mutable.update { state -> state.copy(items = state.items.map { if (it.id == confirm.itemId) it.copy(active = false) else it }) }
            }
            is InventoryConfirm.DeactivateLocation -> write("The place was switched off") { restaurantId ->
                repository.deactivateLocation(restaurantId, confirm.locationId)
                mutable.update { state ->
                    state.copy(locations = state.locations.map { if (it.id == confirm.locationId) it.copy(active = false) else it },
                        levelLocationId = state.levelLocationId.takeUnless { it == confirm.locationId })
                }
            }
            is InventoryConfirm.CountStep -> runCountStep(confirm.countId, confirm.step)
        }
    }

    // ---- Plumbing ----

    private inline fun needsEdit(action: () -> Unit) {
        if (!state.value.canEdit) {
            mutable.update { it.copy(error = "You can only look at inventory") }
            return
        }
        action()
    }

    private fun write(notice: String?, action: suspend (restaurantId: String) -> Unit) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (current.saving) return
        if (!current.canEdit) {
            mutable.update { it.copy(error = "You can only look at inventory") }
            return
        }
        val token = signIn
        mutable.update { it.copy(saving = true, error = null, notice = null) }
        work.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    action(restaurantId)
                    if (notice != null) mutable.update { it.copy(notice = notice) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = adminMessage(e, write = true)) }
                    if (isStale(e)) load(keepPages = true, preserveError = true)
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }
}
