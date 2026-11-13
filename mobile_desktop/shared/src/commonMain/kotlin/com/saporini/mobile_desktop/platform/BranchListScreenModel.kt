package com.saporini.mobile_desktop.platform

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.session.ActingRestaurant
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.core.session.isPlatformAdmin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val BRANCH_PAGE_SIZE = 24

data class BranchListState(
    val restaurantId: String? = null,
    val restaurantName: String = "",
    val restaurantOpen: Boolean = true,
    val canRead: Boolean = false,
    val canChangeCover: Boolean = false,
    val cards: List<BranchCardDto> = emptyList(),
    // The search on the page (name, code, city or street), and the pages loaded so far.
    val search: String = "",
    val page: Int = 0,
    val hasNext: Boolean = false,
    // Branches matching the search, and every branch of the restaurant (open ones too), whatever is searched.
    val total: Long = 0,
    val allBranches: Long = 0,
    val openBranches: Long = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val entering: String? = null,
    // Branches whose cover picture is being changed.
    val coverBusy: Set<String> = emptySet(),
    // The "Add branch" tile.
    val branch: BranchStep? = null,
    val notice: String? = null,
    val error: String? = null
)

/**
 * "Choose a branch": a restaurant's branches as cards, after a super admin picks a restaurant with several. The server
 * pages and searches them, so a restaurant with hundreds of branches loads a page at a time.
 */
class BranchListScreenModel(
    private val repository: PlatformRepository,
    private val session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
    private val mutable = MutableStateFlow(BranchListState())
    val state: StateFlow<BranchListState> = mutable.asStateFlow()
    private var revision = 0L
    private var loadJob: Job? = null
    private var entryJob: Job? = null
    private var active = false

    init {
        work.watchRestaurantPermissions(session) { active }
        work.launch {
            var previousUser: String? = session.currentUser.value?.id
            session.currentUser.collectLatest { user ->
                actions.cancel()
                actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
                revision++
                val access = restaurantAccess(user)
                val current = state.value
                val keepContext = previousUser == user?.id && isPlatformAdmin(user) && access.read
                previousUser = user?.id
                mutable.value = BranchListState(
                    restaurantId = current.restaurantId.takeIf { keepContext },
                    restaurantName = if (keepContext) current.restaurantName else "",
                    canRead = isPlatformAdmin(user) && access.read,
                    canChangeCover = access.manage
                )
                if (active) load(reset = true)
            }
        }
    }

    /** Which restaurant's branches to show. */
    fun show(restaurantId: String, restaurantName: String) {
        if (state.value.restaurantId == restaurantId) return
        revision++
        loadJob?.cancel()
        actions.cancel()
        actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
        mutable.update { BranchListState(restaurantId = restaurantId, restaurantName = restaurantName,
            canRead = it.canRead, canChangeCover = it.canChangeCover) }
        load(reset = true)
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load(reset = true) else {
            revision++
            loadJob?.cancel()
            entryJob?.cancel()
            mutable.update { it.copy(entering = null, loading = false, loadingMore = false) }
        }
    }

    /** Reloads every page already shown, so the grid keeps its place. */
    fun refresh() = load(reset = true)

    /** Searches the branches on the server; the old results go at once, and paging starts again. */
    fun search(text: String) {
        val query = text.take(100)
        if (query == state.value.search) return
        entryJob?.cancel()
        mutable.update { it.copy(search = query, cards = emptyList(), page = 0, hasNext = false, total = 0, loaded = false,
            entering = null, error = null) }
        load(reset = true)
    }

    /** The next page, near the end of the grid (or "Try again" after a failure). */
    fun loadMore() {
        val current = state.value
        if (!current.hasNext || current.loading || current.loadingMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val restaurantId = state.value.restaurantId ?: return
        if (!active || !state.value.canRead) return
        val current = state.value
        val token = ++revision
        loadJob?.cancel()
        loadJob = actions.launch {
            mutable.update { if (reset) it.copy(loading = true) else it.copy(loadingMore = true) }
            try {
                session.requireRestaurantAccess { it.read }
                val restaurant = if (reset) repository.restaurant(restaurantId) else null
                val first = if (reset) 0 else current.page + 1
                val pages = if (reset) (current.page + 1).coerceAtLeast(1) else 1
                val loaded = mutableListOf<BranchCardDto>()
                var last: BranchCardPageDto? = null
                for (page in first until first + pages) {
                    val result = repository.branchCards(restaurantId, current.search, page, BRANCH_PAGE_SIZE)
                    loaded += result.items
                    last = result
                    if (!result.hasNext) break
                }
                if (token != revision) return@launch
                mutable.update { state ->
                    val cards = if (reset) loaded.distinctBy { it.id } else (state.cards + loaded).distinctBy { it.id }
                    state.copy(
                        cards = cards,
                        page = last?.page ?: 0,
                        hasNext = last?.hasNext ?: false,
                        total = last?.totalElements ?: cards.size.toLong(),
                        allBranches = last?.allBranches ?: state.allBranches,
                        openBranches = last?.openBranches ?: state.openBranches,
                        restaurantName = restaurant?.name ?: state.restaurantName,
                        restaurantOpen = restaurant?.open ?: state.restaurantOpen,
                        loaded = true,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = platformMessage(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false, loadingMore = false) }
            }
        }
    }

    /** Works inside this branch from now on, then [onEntered] opens the six apps. */
    fun choose(card: BranchCardDto, onEntered: () -> Unit) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!current.canRead || !current.restaurantOpen || current.entering != null || !card.open || current.cards.none { it.id == card.id && it.open }) return
        mutable.update { it.copy(entering = card.id, error = null) }
        entryJob = actions.launch {
            try {
                session.requireRestaurantAccess { it.read }
                val restaurant = repository.restaurant(restaurantId)
                // Only the chosen branch is checked again, not every page: it may have been closed or removed meanwhile.
                val branch = try {
                    repository.branch(restaurantId, card.id)
                } catch (e: com.saporini.mobile_desktop.core.network.ApiException) {
                    if (e.status == 404) null else throw e
                }
                if (!active || state.value.restaurantId != restaurantId) return@launch
                if (!restaurant.open || branch == null || !branch.open) {
                    mutable.update { state ->
                        state.copy(
                            cards = if (branch == null) state.cards.filterNot { it.id == card.id }
                                else state.cards.map { if (it.id == card.id) it.copy(status = branch.status, active = branch.isActive) else it },
                            restaurantOpen = restaurant.open,
                            error = "This restaurant or branch is no longer open. Choose another branch or refresh the list."
                        )
                    }
                    return@launch
                }
                val branches = state.value.allBranches.takeIf { it > 0 }?.toInt() ?: state.value.cards.size
                session.enterRestaurant(ActingRestaurant(restaurantId, restaurant.name, branch.id, branch.name, branches))
                onEntered()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = false)) }
            } finally {
                if (state.value.restaurantId == restaurantId) mutable.update { it.copy(entering = null) }
            }
        }
    }

    fun uploadCover(card: BranchCardDto, bytes: ByteArray, fileName: String, contentType: String) =
        changeCover(card, "${card.name} has a new picture") { restaurantId -> repository.uploadCover(restaurantId, card.id, bytes, fileName, contentType) }

    fun removeCover(card: BranchCardDto) = changeCover(card, "${card.name}'s picture was removed") { restaurantId -> repository.removeCover(restaurantId, card.id) }

    fun coverProblem(message: String) = mutable.update { it.copy(error = message, notice = null) }

    private fun changeCover(card: BranchCardDto, notice: String, action: suspend (String) -> CoverDto) {
        if (!state.value.canChangeCover) return
        val restaurantId = state.value.restaurantId ?: return
        if (card.id in state.value.coverBusy) return
        mutable.update { it.copy(coverBusy = it.coverBusy + card.id, error = null, notice = null) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val cover = action(restaurantId)
                mutable.update { state ->
                    state.copy(cards = state.cards.map { if (it.id == card.id) it.copy(coverUrl = cover.coverUrl) else it }, notice = notice)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = platformMessage(e, write = true)) }
            } finally {
                mutable.update { it.copy(coverBusy = it.coverBusy - card.id) }
            }
        }
    }

    fun addBranch() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        if (!current.canChangeCover) return
        val sample = current.cards.firstOrNull()
        mutable.update { it.copy(branch = BranchStep(restaurantId, current.restaurantName,
            BranchDraft(city = sample?.city.orEmpty(), country = sample?.country.orEmpty())), notice = null, error = null) }
    }

    fun editBranch(change: (BranchDraft) -> BranchDraft) = mutable.update { state ->
        val step = state.branch ?: return@update state
        state.copy(branch = step.copy(draft = change(step.draft)))
    }

    fun cancelBranch() {
        if (state.value.branch?.saving != true) mutable.update { it.copy(branch = null) }
    }

    fun saveBranch() {
        if (!state.value.canChangeCover) return
        val step = state.value.branch ?: return
        if (step.saving) return
        if (branchProblems(step.draft).isNotEmpty()) {
            mutable.update { it.copy(branch = step.copy(draft = step.draft.copy(showProblems = true))) }
            return
        }
        mutable.update { it.copy(branch = step.copy(saving = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.manage }
                val branch = repository.createBranchWithAddress(step.restaurantId, step.draft)
                mutable.update { it.copy(branch = null, notice = "${branch.name} was added") }
                load(reset = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { state -> state.copy(branch = state.branch?.copy(saving = false, error = platformMessage(e, write = true))) }
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
