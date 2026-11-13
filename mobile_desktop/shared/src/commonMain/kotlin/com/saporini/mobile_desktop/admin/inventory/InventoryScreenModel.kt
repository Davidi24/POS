@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.saporini.mobile_desktop.admin.inventory

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.admin.adminMessage
import com.saporini.mobile_desktop.admin.isDenied
import com.saporini.mobile_desktop.admin.isStale
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.PendingInventoryMoveStorage
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.format.localOf
import com.saporini.mobile_desktop.pos.menu.ui.item.PhotoChange
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlin.time.Clock

enum class InventoryTab { OVERVIEW, STOCK, ITEMS, SUPPLIERS, PLACES, HISTORY, COUNTS }

/** One item of a supplier delivery going into stock: where, how much (in the item's unit) and the price per unit. */
data class DeliveryLine(val itemId: String, val locationId: String, val quantity: String, val unitCost: String)

const val MOVEMENT_PAGE_SIZE = 100
/** How many of the newest changes the History side counts per kind (the server's largest page). */
const val MOVEMENT_KIND_SAMPLE = 500
const val COUNT_PAGE_SIZE = 50
/** A count's steps, as the server names them; the Counts side shows how many counts are at each. */
val COUNT_STATUSES = listOf("DRAFT", "IN_PROGRESS", "COMPLETED", "APPROVED", "CANCELLED")
/** The periods the overview's in-and-out chart can cover, in days. */
val ACTIVITY_PERIODS = listOf(7, 14, 30)
/** How many of the newest stock changes the overview lists. */
const val RECENT_MOVEMENTS = 6

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
    // Stock on hand in every place (always the whole list); levelLocationId only narrows what's shown.
    val levelLocationId: String? = null,
    val levels: List<StockLevelDto> = emptyList(),
    /** Stock tab: only the lines under their reorder point (opened from the overview's "Running low"). */
    val lowOnly: Boolean = false,
    val lowStock: List<StockLevelDto> = emptyList(),
    // The overview's chart: what went in and out per day (null until loaded or when it couldn't be).
    val activity: StockActivityDto? = null,
    val activityDays: Int = 14,
    // The overview's "Recent stock changes": the newest few, apart from the History tab's paged list.
    val recentMovements: List<StockMovementDto> = emptyList(),
    // The kinds of items and of places (built-in ones the restaurant kept, its own, then Uncategorized), with counts.
    val itemKinds: List<StockKindDto> = defaultKinds(ITEM_KINDS),
    val placeKinds: List<StockKindDto> = defaultKinds(PLACE_KINDS),
    // Something just made (a new item, place, kind, stock change, count or supplier): plays the screen's "done" moment.
    val done: com.saporini.mobile_desktop.core.components.DoneMark? = null,
    // While a kind is removed with its items: how many of them were handled so far.
    val kindRemoval: KindRemoval? = null,
    // The overview's "Most valuable stock" lists items worth this much or more (Admin Hub → Settings → Inventory).
    val valuableStockFrom: Double = DEFAULT_VALUABLE_STOCK_FROM,
    val movementType: String? = null,
    val movementItemId: String? = null,
    val movements: List<StockMovementDto> = emptyList(),
    val movementPages: Int = 0,
    val movementsHasNext: Boolean = false,
    // The History side's count per kind of change (movement type → changes) for the item picked, or every item. Loaded
    // without the kind filter and kept while only the kind filter changes, so picking a kind doesn't change the numbers.
    val movementTypeCounts: Map<String, Int>? = null,
    val movementTypeCountsItemId: String? = null,
    // True when there were more changes than MOVEMENT_KIND_SAMPLE, so the counts cover only the newest ones.
    val movementTypeCountsCapped: Boolean = false,
    // History's day (YYYY-MM-DD, the restaurant's day) and person filters (user, 2026-10-30), who can be picked, and what
    // happened to items themselves (added, changed, deleted) for the same filters.
    val movementDay: String? = null,
    val movementPersonId: String? = null,
    val movementPeople: List<StockPersonDto> = emptyList(),
    val itemEvents: List<StockItemEventDto> = emptyList(),
    val historyCounts: List<StockCountDto> = emptyList(),
    val historyCountPages: Int = 0,
    val historyCountsHasNext: Boolean = false,
    val countStatus: String? = null,
    val counts: List<StockCountDto> = emptyList(),
    val countPages: Int = 0,
    val countsHasNext: Boolean = false,
    val countsLoadingMore: Boolean = false,
    val openCount: StockCountDto? = null,
    // The Counts side's number of counts at each step (status → total, from the server), kept while only the step filter
    // changes. Null until loaded.
    val countStatusTotals: Map<String, Long>? = null,
    val countSchedule: InventoryCountScheduleDto = InventoryCountScheduleDto(),
    val scheduleCounts: List<StockCountDto> = emptyList(),
    val itemDraft: StockItemDraft? = null,
    val itemProblems: Map<ItemField, String> = emptyMap(),
    val locationDraft: StockLocationDraft? = null,
    // Stock → Places → Manage places is open.
    val managingPlaces: Boolean = false,
    val moveDraft: StockMoveDraft? = null,
    /** A stock change put aside while its scanned (new) item is being created; it comes back with that item chosen. */
    val resumeMove: StockMoveDraft? = null,
    /** A change for several selected stock lines at once. */
    val bulkMove: BulkMoveDraft? = null,
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

    /** Stock on hand at the chosen place, or everywhere. */
    val visibleLevels: List<StockLevelDto> get() = levels.filter { (levelLocationId == null || it.locationId == levelLocationId) && (!lowOnly || it.lowStock) }

    /** Each item's stock per place. */
    val levelsByItem: Map<String, List<StockLevelDto>> get() = levels.groupBy { it.inventoryItemId }

    val suppliers: List<SupplierSummary> get() = suppliersOf(items, lowIds)
    val activeLocations: List<StockLocationDto> get() = locations.filter { it.active }
    fun item(id: String?): StockItemDto? = items.firstOrNull { it.id == id }

    /** The item with this barcode (as normalised by the server: trimmed, upper case), if any. */
    fun itemByBarcode(code: String): StockItemDto? {
        val wanted = code.trim().replace(" ", "").uppercase()
        return items.firstOrNull { it.barcode?.trim()?.replace(" ", "")?.uppercase() == wanted }
    }
    fun location(id: String?): StockLocationDto? = locations.firstOrNull { it.id == id }
    fun saleSource(branchId: String, itemId: String): SaleStockSourceDto? =
        saleSources.firstOrNull { it.branchId == branchId && it.inventoryItemId == itemId }
}

/** An action that needs a second tap. */
/** Removing a kind with its items: [done] of [total] handled, [kept] of them refused by the server (they stay, in Uncategorized). */
data class KindRemoval(val done: Int, val total: Int, val kept: Int)

sealed interface InventoryConfirm {
    data class DeactivateItem(val itemId: String) : InventoryConfirm
    data class DeleteItem(val itemId: String) : InventoryConfirm
    /** [ref] is a built-in code or an own kind's id; [label] and [count] are for the question. */
    data class DeleteKind(val scope: String, val ref: String, val label: String, val count: Long, val custom: Boolean = false) : InventoryConfirm
    /** Asks what happens to the place's stock: kept (moved to Uncategorized) or taken out. */
    data class DeleteLocation(val locationId: String) : InventoryConfirm
    data class DeactivateLocation(val locationId: String) : InventoryConfirm
    data class CountStep(val countId: String, val step: String) : InventoryConfirm
}

/**
 * Admin Hub → Inventory: stock on hand and low stock, stock items and their suppliers, storage places, the
 * history of stock changes (paged; a refresh reloads every page shown), receiving/wasting/moving/correcting stock,
 * and stock counts from draft to approval. Reading needs INVENTORY_READ, changing needs INVENTORY_UPDATE.
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
    private var changeEventsJob: Job? = null

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
                    canRead = "INVENTORY_READ" in permissions,
                    canEdit = "INVENTORY_UPDATE" in permissions
                )
                if (activeUser != null && "INVENTORY_UPDATE" in permissions) {
                    activeUser.restaurantId?.let { restaurantId ->
                        restorePendingMove(activeUser.id, restaurantId, currentSignIn)
                    }
                }
                if (active) {
                    load(keepPages = false)
                    observeChanges()
                }
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) {
            load(keepPages = true)
            observeChanges()
        } else {
            revision++
            loadJob?.cancel()
            changeEventsJob?.cancel()
            changeEventsJob = null
            mutable.update { it.copy(loading = false, loadingMore = false) }
        }
    }

    /** Keep the visible screen fresh; the stream carries invalidations, not stock payloads. */
    private fun observeChanges() {
        changeEventsJob?.cancel()
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        changeEventsJob = work.launch {
            repository.observeChanges(restaurantId).collect {
                if (!active || state.value.restaurantId != restaurantId) return@collect
                // Don't drop a notification that arrives during a load; reload once it settles.
                if (state.value.loading) loadJob?.join()
                if (active && state.value.restaurantId == restaurantId) {
                    load(keepPages = true)
                }
            }
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

    /** Narrows stock to one place (null: every place). Every place is already loaded, so nothing is fetched. */
    fun levelsAt(locationId: String?) {
        if (state.value.levelLocationId == locationId) return
        mutable.update { it.copy(levelLocationId = locationId) }
    }

    fun lowOnly(value: Boolean) = mutable.update { it.copy(lowOnly = value) }

    /** Changes the period of the overview's chart and loads only the chart again. */
    fun activityDays(days: Int) {
        val current = state.value
        if (days !in ACTIVITY_PERIODS || current.activityDays == days) return
        mutable.update { it.copy(activityDays = days) }
        val restaurantId = current.restaurantId ?: return
        if (!active || !current.canRead) return
        val token = revision
        work.launch {
            val activity = try {
                repository.activity(restaurantId, days)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (token == revision && state.value.activityDays == days) mutable.update { it.copy(activity = activity) }
        }
    }

    /** History → day: only that day's changes ([day] as YYYY-MM-DD), or every day with null. */
    fun historyDay(day: String?) {
        if (state.value.movementDay == day) return
        mutable.update { it.copy(movementDay = day, movements = emptyList(), movementPages = 0, movementsHasNext = false) }
        load(keepPages = false)
    }

    /** History → person: only the changes this person made, or everyone's with null. */
    fun historyPerson(personId: String?) {
        if (state.value.movementPersonId == personId) return
        mutable.update { it.copy(movementPersonId = personId, movements = emptyList(), movementPages = 0, movementsHasNext = false) }
        load(keepPages = false)
    }

    fun historyFilter(type: String?, itemId: String?) {
        if (type != null && type !in INVENTORY_MOVEMENT_TYPES && type != ITEM_CHANGES && type != COUNT_HISTORY) return
        val current = state.value
        val nextItemId = itemId
        if (current.movementType == type && current.movementItemId == nextItemId) return
        mutable.update { it.copy(movementType = type, movementItemId = nextItemId, movements = emptyList(), movementPages = 0, movementsHasNext = false,
            historyCounts = emptyList(), historyCountPages = 0, historyCountsHasNext = false,
            movementDay = if (type == COUNT_HISTORY) null else it.movementDay,
            movementPersonId = if (type == COUNT_HISTORY) null else it.movementPersonId) }
        // Only the kind changed: the counts per kind are still right.
        load(keepPages = false, keepSideCounts = itemId == current.movementItemId)
    }

    fun countFilter(status: String?) {
        if (state.value.countStatus == status) return
        mutable.update { it.copy(countStatus = status, counts = emptyList(), countPages = 0, countsHasNext = false) }
        // Only the step filter changed: the numbers per step are still right.
        load(keepPages = false, keepSideCounts = true)
    }

    fun refresh() = load(keepPages = true)

    private fun load(keepPages: Boolean, preserveError: Boolean = false, keepSideCounts: Boolean = false) {
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
                // The kinds are extra: without them the built-in ones show.
                val kinds = try {
                    coroutineScope {
                        val itemKinds = async { repository.kinds(restaurantId, ITEM_KINDS) }
                        val placeKinds = async { repository.kinds(restaurantId, PLACE_KINDS) }
                        itemKinds.await() to placeKinds.await()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (token == revision && kinds != null) mutable.update { it.copy(itemKinds = kinds.first, placeKinds = kinds.second) }
                // The suppliers (the item form picks one, Suppliers shows them); extras, so the rest shows without them.
                SupplierBook.connect(repository, restaurantId, work)
                val supplierList = try {
                    repository.suppliers(restaurantId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (token == revision && supplierList != null) SupplierBook.fill(restaurantId, supplierList)
                when (current.tab) {
                    // Overview, Items and Stock all show what is where, so they get every place's stock.
                    InventoryTab.OVERVIEW, InventoryTab.STOCK, InventoryTab.ITEMS, InventoryTab.SUPPLIERS -> {
                        val levels = repository.levels(restaurantId, null, null)
                        if (token == revision) mutable.update { it.copy(levels = levels) }
                        // The chart is extra: if it can't load, the rest of the overview still shows.
                        if (current.tab == InventoryTab.OVERVIEW) {
                            val activity = try {
                                repository.activity(restaurantId, current.activityDays)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                            if (token == revision) mutable.update { it.copy(activity = activity) }
                            val recent = try {
                                repository.movements(restaurantId, null, null, 0, RECENT_MOVEMENTS)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                            if (token == revision && recent != null) mutable.update { it.copy(recentMovements = recent) }
                            // Without the setting the default (100) stays.
                            val valuableFrom = try {
                                repository.valuableStockFrom(restaurantId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                            if (token == revision && valuableFrom != null) mutable.update { it.copy(valuableStockFrom = valuableFrom) }
                        }
                    }
                    InventoryTab.HISTORY -> {
                        if (current.movementType == COUNT_HISTORY) {
                            val pages = if (keepPages) current.historyCountPages.coerceAtLeast(1) else 1
                            val history = mutableListOf<StockCountDto>()
                            var loaded = 0
                            var hasNext = false
                            for (page in 0 until pages) {
                                val result = repository.counts(restaurantId, "APPROVED", page, COUNT_PAGE_SIZE)
                                require(result.page == page && (!result.hasNext || result.items.isNotEmpty())) { "Invalid count history page" }
                                history += result.items
                                loaded = page + 1
                                hasNext = result.hasNext
                                if (!hasNext) break
                            }
                            val filtered = current.movementItemId?.let { itemId -> history.filter { count -> count.lines.any { it.inventoryItemId == itemId } } } ?: history
                            if (token == revision) mutable.update {
                                it.copy(historyCounts = filtered.distinctBy(StockCountDto::id), historyCountPages = loaded,
                                    historyCountsHasNext = hasNext, movements = emptyList(), itemEvents = emptyList())
                            }
                        } else {
                        // Every page already shown comes back, so the list keeps its place.
                        val pages = if (keepPages) current.movementPages.coerceAtLeast(1) else 1
                        val movements = mutableListOf<StockMovementDto>()
                        var loaded = 0
                        var hasNext = false
                        // "Item changes" shows only the items' own events, so no stock changes are loaded then.
                        val onlyItems = current.movementType == ITEM_CHANGES
                        if (!onlyItems) for (page in 0 until pages) {
                            val result = repository.movements(restaurantId, current.movementType, current.movementItemId, page, MOVEMENT_PAGE_SIZE,
                                current.movementDay, current.movementPersonId)
                            movements += result
                            loaded = page + 1
                            hasNext = result.size == MOVEMENT_PAGE_SIZE
                            if (!hasNext) break
                        }
                        // What happened to items themselves, and who can be picked, for the same filters; extras, so a
                        // failure leaves the stock changes showing.
                        val events = try {
                            repository.itemEvents(restaurantId, current.movementItemId, current.movementPersonId, current.movementDay)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                        val people = try {
                            repository.movementPeople(restaurantId, current.movementItemId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        if (token == revision) mutable.update {
                            it.copy(movements = movements.distinctBy { m -> m.id }, movementPages = loaded, movementsHasNext = hasNext,
                                itemEvents = events, movementPeople = people ?: it.movementPeople)
                        }
                        // The counts per kind come back with every History load (opening the tab, refresh, after a
                        // change), except when only the kind filter changed.
                        if (!keepSideCounts || current.movementTypeCounts == null || current.movementTypeCountsItemId != current.movementItemId) {
                            val sample = repository.movements(restaurantId, null, current.movementItemId, 0, MOVEMENT_KIND_SAMPLE,
                                current.movementDay, current.movementPersonId)
                            if (token == revision) mutable.update {
                                it.copy(movementTypeCounts = sample.groupingBy { m -> m.movementType }.eachCount(),
                                    movementTypeCountsItemId = current.movementItemId, movementTypeCountsCapped = sample.size >= MOVEMENT_KIND_SAMPLE)
                            }
                        }
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
                        // How many counts are at each step: the server's total for each, one small page per step.
                        if (!keepSideCounts || current.countStatusTotals == null) {
                            val totals = coroutineScope {
                                COUNT_STATUSES.map { status -> status to async { repository.counts(restaurantId, status, 0, 1).totalElements } }
                                    .associate { (status, total) -> status to total.await() }
                            }
                            if (token == revision) mutable.update { it.copy(countStatusTotals = totals) }
                        }
                        val open = current.openCount?.let { repository.count(restaurantId, it.id) }
                        val schedule = try {
                            repository.countSchedule(restaurantId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        val now = Clock.System.now().toLocalDateTime(RestaurantTime.zone)
                        val weekStart = now.date.minus(DatePeriod(days = now.date.dayOfWeek.isoDayNumber - 1))
                        val scheduled = mutableListOf<StockCountDto>()
                        if (schedule != null) {
                            var page = 0
                            var more = true
                            while (more) {
                                val result = repository.counts(restaurantId, "APPROVED", page, COUNT_PAGE_SIZE)
                                require(result.page == page && (!result.hasNext || result.items.isNotEmpty())) { "Invalid schedule count page" }
                                scheduled += result.items
                                more = result.hasNext
                                val oldestCreated = result.items.lastOrNull()?.createdAt?.let { localOf(it, RestaurantTime.zone)?.date }
                                if (oldestCreated != null && oldestCreated < weekStart) more = false
                                page++
                            }
                        }
                        if (token == revision) mutable.update {
                            it.copy(counts = counts.distinctBy { count -> count.id }, countPages = loadedPages,
                                countsHasNext = hasNext, openCount = open, countSchedule = schedule ?: it.countSchedule,
                                scheduleCounts = scheduled.distinctBy { count -> count.id })
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
        val countHistory = current.movementType == COUNT_HISTORY
        if (!active || current.tab != InventoryTab.HISTORY || current.loading || current.loadingMore ||
            (if (countHistory) !current.historyCountsHasNext else !current.movementsHasNext) || current.movementType == ITEM_CHANGES) return
        val token = revision
        mutable.update { it.copy(loadingMore = true) }
        work.launch {
            try {
                if (countHistory) {
                    val result = repository.counts(restaurantId, "APPROVED", current.historyCountPages, COUNT_PAGE_SIZE)
                    require(result.page == current.historyCountPages && (!result.hasNext || result.items.isNotEmpty())) { "Invalid count history page" }
                    val filtered = current.movementItemId?.let { itemId -> result.items.filter { count -> count.lines.any { it.inventoryItemId == itemId } } } ?: result.items
                    if (token == revision) mutable.update {
                        it.copy(historyCounts = (it.historyCounts + filtered).distinctBy { count -> count.id },
                            historyCountPages = it.historyCountPages + 1, historyCountsHasNext = result.hasNext, error = null)
                    }
                } else {
                    val result = repository.movements(restaurantId, current.movementType, current.movementItemId, current.movementPages, MOVEMENT_PAGE_SIZE,
                        current.movementDay, current.movementPersonId)
                    if (token == revision) mutable.update {
                        it.copy(movements = (it.movements + result).distinctBy { m -> m.id }, movementPages = it.movementPages + 1,
                            movementsHasNext = result.size == MOVEMENT_PAGE_SIZE, error = null)
                    }
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

    // What to do once the item open in the editor is saved (a supplier's catalog product links itself to it).
    private var afterItemSaved: ((StockItemDto) -> Unit)? = null

    /** Opens the new-item form already filled in (e.g. from a supplier's price list); [onSaved] runs once it's saved. */
    fun newItemPrefilled(draft: StockItemDraft, onSaved: (StockItemDto) -> Unit) = needsEdit {
        afterItemSaved = onSaved
        mutable.update { it.copy(itemDraft = draft.copy(id = null), itemProblems = emptyMap(), notice = null) }
    }

    /** Opens the editor with a copy of the item (no code, barcode or photo), to add a similar one quickly. */
    fun duplicateItem(itemId: String) = needsEdit {
        val item = state.value.item(itemId) ?: return@needsEdit
        val copy = itemDraftOf(item).copy(id = null, name = "${item.name} (copy)".take(MAX_STOCK_NAME), code = "", barcode = "", imageUrl = null, active = true)
        mutable.update { it.copy(itemDraft = copy, itemProblems = emptyMap(), notice = null) }
    }

    fun editItem(itemId: String) = needsEdit {
        val item = state.value.item(itemId) ?: return@needsEdit
        mutable.update { it.copy(itemDraft = itemDraftOf(item), itemProblems = emptyMap(), notice = null) }
    }

    fun changeItem(change: (StockItemDraft) -> StockItemDraft) = mutable.update { state ->
        val draft = state.itemDraft ?: return@update state
        val next = change(draft).copy(id = draft.id)
        state.copy(itemDraft = next, itemProblems = itemProblems(next).filterKeys { it in state.itemProblems })
    }

    fun cancelItem() {
        afterItemSaved = null
        mutable.update { it.copy(itemDraft = null, itemProblems = emptyMap(), moveDraft = it.resumeMove ?: it.moveDraft, resumeMove = null) }
    }

    /** A scanned barcode no item has: opens a new item with it filled in. */
    fun newItemWithBarcode(barcode: String) = needsEdit {
        mutable.update { it.copy(itemDraft = StockItemDraft(barcode = barcode), itemProblems = emptyMap(), notice = null) }
    }

    /**
     * The same from inside a stock change: the change is put aside while the item is created, then comes back with the
     * new item chosen (or unchanged when the item is cancelled).
     */
    fun createItemFromScan(barcode: String) = needsEdit {
        val current = state.value
        if (current.moveOutcomeUnknown || current.saving) return@needsEdit
        mutable.update {
            it.copy(resumeMove = it.moveDraft, moveDraft = null, itemDraft = StockItemDraft(barcode = barcode), itemProblems = emptyMap(), notice = null)
        }
    }

    fun saveItem() {
        val current = state.value
        val draft = current.itemDraft ?: return
        val problems = itemProblems(draft)
        if (problems.isNotEmpty()) {
            mutable.update { it.copy(itemProblems = problems, error = "Check the highlighted fields") }
            return
        }
        write(null) { restaurantId ->
            val saved = repository.saveItem(restaurantId, draft.id, itemRequest(draft))
            putItem(saved)
            // The photo goes up after the item. If it fails the item is kept, and the editor stays open on it so
            // saving again only retries the photo instead of adding the item twice.
            val withPhoto = try {
                when (val photo = draft.photo) {
                    PhotoChange.Keep -> saved
                    PhotoChange.Remove -> if (saved.imageUrl == null) saved else repository.removeItemPhoto(restaurantId, saved.id)
                    is PhotoChange.Replace -> repository.uploadItemPhoto(restaurantId, saved.id, photo.bytes, photo.fileName, photo.contentType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update {
                    it.copy(itemDraft = draft.copy(id = saved.id, imageUrl = saved.imageUrl), itemProblems = emptyMap(),
                        error = "${saved.name} was saved, but its photo wasn't: ${adminMessage(e, write = true)}")
                }
                return@write
            }
            putItem(withPhoto)
            // A new item (also one put in stock from a supplier's price list) gets the "done" moment.
            if (draft.id == null) celebrate("${saved.name} is in your stock")
            mutable.update {
                it.copy(itemDraft = null, itemProblems = emptyMap(), notice = "${saved.name} was saved",
                    moveDraft = it.resumeMove?.copy(itemId = saved.id) ?: it.moveDraft, resumeMove = null)
            }
            afterItemSaved?.let { done -> afterItemSaved = null; done(withPhoto) }
        }
    }

    private fun putLocation(saved: StockLocationDto) = mutable.update { state ->
        val locations = if (state.locations.any { it.id == saved.id }) state.locations.map { if (it.id == saved.id) saved else it }
        else state.locations + saved
        state.copy(locations = locations)
    }

    private fun putItem(saved: StockItemDto) = mutable.update { state ->
        val items = if (state.items.any { it.id == saved.id }) state.items.map { if (it.id == saved.id) saved else it } else state.items + saved
        state.copy(items = items)
    }

    fun askDeactivateItem(itemId: String) = needsEdit { mutable.update { it.copy(confirm = InventoryConfirm.DeactivateItem(itemId)) } }

    /** Items → ⋮ → Delete: asks first (user, 2026-10-30). */
    fun askDeleteItem(itemId: String) = needsEdit { mutable.update { it.copy(confirm = InventoryConfirm.DeleteItem(itemId)) } }

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

    /** Opens or closes Stock → Places → Manage places, where places are added, changed and deleted (user, 2026-11-11). */
    fun managePlaces(open: Boolean) = mutable.update { it.copy(managingPlaces = open) }

    fun newLocation() = needsEdit { mutable.update { it.copy(locationDraft = StockLocationDraft(branchId = it.myBranchId), notice = null) } }

    fun editLocation(locationId: String) = needsEdit {
        val location = state.value.location(locationId) ?: return@needsEdit
        mutable.update { it.copy(locationDraft = locationDraftOf(location), notice = null) }
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
            draft.kindId.takeIf { draft.locationType == CUSTOM_KIND }, draft.notes.trim().ifEmpty { null }, draft.active, draft.iconCode)
        write("${draft.name.trim()} was saved") { restaurantId ->
            val saved = repository.saveLocation(restaurantId, draft.id, request)
            putLocation(saved)
            // The place's own picture goes up after the place. If it fails the place is kept and the editor stays open on
            // it, so saving again only retries the picture instead of adding the place twice.
            val withPicture = try {
                when (val photo = draft.photo) {
                    PhotoChange.Keep -> saved
                    PhotoChange.Remove -> if (saved.imageUrl == null) saved else repository.removeLocationPhoto(restaurantId, saved.id)
                    is PhotoChange.Replace -> repository.uploadLocationPhoto(restaurantId, saved.id, photo.bytes, photo.fileName, photo.contentType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update {
                    it.copy(locationDraft = draft.copy(id = saved.id, imageUrl = saved.imageUrl),
                        error = "${saved.name} was saved, but its picture wasn't: ${adminMessage(e, write = true)}")
                }
                return@write
            }
            putLocation(withPicture)
            if (draft.id == null) celebrate("${saved.name} was added")
            mutable.update { it.copy(locationDraft = null) }
        }
    }

    fun askDeactivateLocation(locationId: String) = needsEdit { mutable.update { it.copy(confirm = InventoryConfirm.DeactivateLocation(locationId)) } }

    /** Places → ⋮ → Delete: asks whether its stock goes with it or moves to Uncategorized (user, 2026-10-30). */
    fun askDeleteLocation(locationId: String) = needsEdit { mutable.update { it.copy(confirm = InventoryConfirm.DeleteLocation(locationId)) } }

    fun deleteLocation(locationId: String, keepStock: Boolean) = needsEdit {
        val name = state.value.location(locationId)?.name ?: "The place"
        mutable.update { it.copy(confirm = null) }
        write("$name was deleted") { restaurantId ->
            repository.deleteLocation(restaurantId, locationId, keepStock)
            mutable.update { state ->
                state.copy(locations = state.locations.filterNot { it.id == locationId },
                    levels = state.levels.filterNot { it.locationId == locationId },
                    lowStock = state.lowStock.filterNot { it.locationId == locationId },
                    levelLocationId = state.levelLocationId.takeUnless { it == locationId })
            }
            // Brings in the Uncategorized place (when the stock was kept) and the History lines.
            load(keepPages = true)
        }
    }

    // ---- Kinds (user, 2026-10-30: the built-in kinds are there by default, the restaurant removes them or adds its own) ----

    /** Adds one of the restaurant's own kinds of items ([ITEM_KINDS]) or places ([PLACE_KINDS]). With [pick], the open
     *  item or place form takes it straight away. */
    fun addKind(scope: String, name: String, pick: Boolean = false) = needsEdit {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > MAX_KIND_NAME) {
            mutable.update { it.copy(error = if (clean.isEmpty()) "Give the kind a name" else "A kind's name can be at most $MAX_KIND_NAME characters") }
            return@needsEdit
        }
        write("$clean was added") { restaurantId ->
            val made = repository.createKind(restaurantId, scope, clean)
            celebrate("$clean was added")
            mutable.update { state ->
                // Own kinds sit before Uncategorized, which stays last.
                fun withIt(kinds: List<StockKindDto>) = kinds.filterNot { it.code == UNCATEGORIZED_KIND } + made + kinds.filter { it.code == UNCATEGORIZED_KIND }
                val next = if (scope == ITEM_KINDS) state.copy(itemKinds = withIt(state.itemKinds)) else state.copy(placeKinds = withIt(state.placeKinds))
                when {
                    !pick -> next
                    scope == ITEM_KINDS && next.itemDraft != null -> next.copy(itemDraft = next.itemDraft.copy(itemType = CUSTOM_KIND, kindId = made.id))
                    scope == PLACE_KINDS && next.locationDraft != null -> next.copy(locationDraft = next.locationDraft.copy(locationType = CUSTOM_KIND, kindId = made.id))
                    else -> next
                }
            }
        }
    }

    fun askDeleteKind(scope: String, kind: StockKindDto, label: String) = needsEdit {
        if (kind.code == UNCATEGORIZED_KIND) return@needsEdit
        mutable.update {
            it.copy(confirm = InventoryConfirm.DeleteKind(scope, if (kind.code == CUSTOM_KIND) kind.id.orEmpty() else kind.code, label, kind.count,
                custom = kind.code == CUSTOM_KIND))
        }
    }

    /**
     * Removes the kind being asked about. With [deleteItems] (user, 2026-10-30: "a check box: delete the items inside, with
     * a loading animation until all are deleted") its items are deleted one by one first, with [InventoryState.kindRemoval]
     * counting along; an item the server keeps (a recipe or an open count uses it) goes to Uncategorized with the kind.
     */
    fun removeKind(deleteItems: Boolean) = needsEdit {
        val confirm = state.value.confirm as? InventoryConfirm.DeleteKind ?: return@needsEdit
        val ids = if (!deleteItems || confirm.scope != ITEM_KINDS) emptyList() else state.value.items.filter { item ->
            if (confirm.custom) item.itemType == CUSTOM_KIND && item.kindId == confirm.ref else item.itemType == confirm.ref
        }.map { it.id }
        mutable.update { it.copy(kindRemoval = KindRemoval(0, ids.size, 0)) }
        write(null) { restaurantId ->
            try {
                var kept = 0
                ids.forEachIndexed { index, id ->
                    val deleted = try {
                        repository.deleteItem(restaurantId, id)
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        kept++
                        false
                    }
                    // Each deleted item leaves the lists as it goes, so the screen behind follows the count.
                    mutable.update { state ->
                        state.copy(items = if (deleted) state.items.filterNot { it.id == id } else state.items,
                            kindRemoval = state.kindRemoval?.copy(done = index + 1, kept = kept))
                    }
                }
                repository.deleteKind(restaurantId, confirm.scope, confirm.ref)
                val deleted = ids.size - kept
                mutable.update {
                    it.copy(confirm = null, notice = when {
                        ids.isEmpty() -> "${confirm.label} was removed"
                        kept == 0 -> "${confirm.label} and its $deleted ${if (deleted == 1) "item were" else "items were"} deleted"
                        else -> "${confirm.label} was removed: $deleted deleted, $kept kept in Uncategorized (a recipe or an open count uses them)"
                    })
                }
            } finally {
                mutable.update { it.copy(kindRemoval = null) }
            }
            load(keepPages = true)
        }
    }

    // ---- Stock changes ----

    /** Starts a stock change, optionally for a known item and place (e.g. from a low-stock row). */
    fun startMove(kind: StockMoveKind, itemId: String? = null, locationId: String? = null) = needsEdit {
        if (state.value.moveOutcomeUnknown || state.value.pendingMoveStorageError) {
            mutable.update { it.copy(error = "Retry the pending stock change unchanged before starting another one") }
            return@needsEdit
        }
        mutable.update { it.copy(moveDraft = StockMoveDraft(kind = kind, itemId = itemId, locationId = locationId, itemFixed = itemId != null), moveRequestKey = null, notice = null) }
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
                        celebrate(moveNotice(draft.kind))
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

    // ---- Several lines at once, and quick receiving after a scan ----

    fun startBulk(kind: StockMoveKind, lines: List<Pair<String, String>>) = needsEdit {
        if (lines.isEmpty()) return@needsEdit
        mutable.update { it.copy(bulkMove = BulkMoveDraft(kind, lines.map { (item, place) -> BulkMoveLine(item, place) }), notice = null, error = null) }
    }

    fun changeBulk(change: (BulkMoveDraft) -> BulkMoveDraft) = mutable.update { state ->
        if (state.saving) return@update state
        state.copy(bulkMove = state.bulkMove?.let(change), error = null)
    }

    fun cancelBulk() = mutable.update { if (it.saving) it else it.copy(bulkMove = null) }

    /**
     * Sends every line as its own stock change, one after the other. Lines that went through leave the dialog; the
     * rest stay with what went wrong (and their request key, so sending again can't count them twice).
     */
    fun saveBulk() {
        val bulk = state.value.bulkMove ?: return
        val problems = bulk.lines.associate { line -> line to moveProblem(bulk.draftFor(line)) }
        if (problems.values.any { it != null }) {
            mutable.update { s -> s.copy(bulkMove = bulk.copy(lines = bulk.lines.map { it.copy(problem = problems[it]) }, quiet = false),
                error = problems.values.firstNotNullOf { it }) }
            return
        }
        write(null) { restaurantId ->
            val left = mutableListOf<BulkMoveLine>()
            var sent = 0
            for (line in bulk.lines) {
                val key = line.requestKey ?: kotlin.uuid.Uuid.random().toString()
                try {
                    repository.move(restaurantId, bulk.kind, moveRequest(bulk.draftFor(line)), key)
                    sent++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A clear rejection gets a fresh key next time; anything else keeps it (it may have been saved).
                    val rejected = e is ApiException && e.status in 400..499
                    left += line.copy(requestKey = if (rejected) null else key, problem = adminMessage(e, write = true))
                }
            }
            if (left.isEmpty() && bulk.quiet) {
                mutable.update { it.copy(bulkMove = null, notice = "All done. You can see it in Stock.") }
                celebrate("All done")
            } else if (left.isEmpty()) {
                mutable.update { it.copy(bulkMove = null, notice = "${moveNotice(bulk.kind)} for $sent ${if (sent == 1) "line" else "lines"}") }
                celebrate(moveNotice(bulk.kind))
            } else {
                mutable.update {
                    it.copy(bulkMove = bulk.copy(lines = left, quiet = false),
                        error = "$sent of ${bulk.lines.size} saved. The ones below didn't go through; check them and send again.")
                }
            }
            if (sent > 0) load(keepPages = true, preserveError = left.isNotEmpty())
        }
    }

    /**
     * Receives a supplier delivery: one stock receipt per line, each into its own stock place at the order's price, sent
     * like several lines at once but without showing that dialog; it only opens when a line fails, to send it again.
     */
    fun receiveDelivery(lines: List<DeliveryLine>, reason: String) = needsEdit {
        if (lines.isEmpty()) return@needsEdit
        mutable.update {
            it.copy(bulkMove = BulkMoveDraft(StockMoveKind.RECEIVE, lines.map { line -> BulkMoveLine(line.itemId, line.locationId, line.quantity, unitCostText = line.unitCost) },
                reason = reason.take(MAX_STOCK_TEXT), quiet = true), notice = null, error = null)
        }
        saveBulk()
    }

    /** Receives [quantityText] of an item into a place straight from a scan, without the full form. */
    fun quickReceive(itemId: String, locationId: String?, quantityText: String) = needsEdit {
        val current = state.value
        if (current.moveOutcomeUnknown || current.pendingMoveStorageError || current.saving) {
            mutable.update { it.copy(error = "Retry the pending stock change before receiving more stock") }
            return@needsEdit
        }
        val draft = StockMoveDraft(StockMoveKind.RECEIVE, itemId, locationId, quantityText = quantityText)
        moveProblem(draft)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return@needsEdit
        }
        mutable.update {
            it.copy(moveDraft = draft, moveRequestKey = null, error = null, notice = null)
        }
        // Use the same saved idempotency key and uncertain-outcome recovery as the full receive form.
        saveMove()
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
                celebrate("Count started")
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
            is InventoryConfirm.DeleteItem -> write("${state.value.item(confirm.itemId)?.name ?: "The item"} was deleted") { restaurantId ->
                repository.deleteItem(restaurantId, confirm.itemId)
                // Gone from every list at once; the refresh that follows brings the "Item deleted" corrections into History.
                mutable.update { state ->
                    state.copy(items = state.items.filterNot { it.id == confirm.itemId },
                        levels = state.levels.filterNot { it.inventoryItemId == confirm.itemId },
                        lowStock = state.lowStock.filterNot { it.inventoryItemId == confirm.itemId })
                }
                load(keepPages = true)
            }
            is InventoryConfirm.DeleteKind -> write("${confirm.label} was removed") { restaurantId ->
                repository.deleteKind(restaurantId, confirm.scope, confirm.ref)
                // Its items or places are Uncategorized now: the reload brings them, and the kinds without it.
                load(keepPages = true)
            }
            // The place dialog asks keep or not and calls deleteLocation itself; this is only the plain "yes".
            is InventoryConfirm.DeleteLocation -> deleteLocation(confirm.locationId, keepStock = true)
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

    /** Plays the screen's "done" moment with [text] (user, 2026-10-30). Suppliers (still local) call it too. */
    /**
     * The item form's "New supplier": adds the supplier on the server first (so the item can be saved with it), then
     * picks it in the form. A supplier with that name already there is just picked.
     */
    fun addSupplierForItem(name: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return
        SupplierBook.suppliers.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { existing ->
            changeItem { it.copy(supplierId = existing.id, supplierName = existing.name) }
            return
        }
        write(null) { restaurantId ->
            val added = repository.createSupplier(restaurantId, SupplierRequestDto(name = clean, active = true))
            SupplierBook.added(added)
            changeItem { it.copy(supplierId = added.id, supplierName = added.name) }
        }
    }

    fun celebrate(text: String) = mutable.update {
        it.copy(done = com.saporini.mobile_desktop.core.components.DoneMark((it.done?.tick ?: 0) + 1, text))
    }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
        changeEventsJob?.cancel()
    }
}
