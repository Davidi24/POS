package com.saporini.mobile_desktop.platform

import cafe.adriel.voyager.core.model.ScreenModel
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val APPLICATION_PAGE_SIZE = 20

const val MAX_REJECTION_REASON = 500

enum class ApplicationFilter(val label: String, val server: String) { WAITING("Waiting", "PENDING"), REJECTED("Rejected", "REJECTED") }

/** Approving or rejecting one application; a rejection needs a reason the applicant sees. */
data class ReviewStep(
    val application: RestaurantApplicationDto,
    val approve: Boolean,
    val reason: String = "",
    val showProblems: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

fun reviewProblem(step: ReviewStep): String? = if (step.approve) null else
    lengthProblem(step.reason, MAX_REJECTION_REASON, true, "the reason the applicant will see")

data class ApplicationsState(
    val canRead: Boolean = false,
    // Approving creates a restaurant, so it takes the right to create restaurants.
    val canReview: Boolean = false,
    val filter: ApplicationFilter = ApplicationFilter.WAITING,
    val search: String = "",
    val items: List<RestaurantApplicationDto> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val total: Long = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val review: ReviewStep? = null,
    val notice: String? = null,
    val error: String? = null
)

/**
 * Super admins' review queue of public restaurant applications: the waiting ones oldest first (or the rejected ones),
 * paged and searched on the server, each approved or rejected with a reason.
 */
class RestaurantApplicationsScreenModel(
    private val repository: PlatformRepository,
    private val session: SessionManager
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
    private val mutable = MutableStateFlow(ApplicationsState())
    val state: StateFlow<ApplicationsState> = mutable.asStateFlow()
    private var revision = 0L
    private var signIn = 0L
    private var loadJob: Job? = null
    private var active = false

    init {
        work.watchRestaurantPermissions(session) { active }
        work.launch {
            session.currentUser.collectLatest { user ->
                actions.cancel()
                actions = CoroutineScope(work.coroutineContext + SupervisorJob(work.coroutineContext[Job]))
                revision++
                signIn++
                val access = restaurantAccess(user)
                mutable.value = ApplicationsState(canRead = isPlatformAdmin(user) && access.read, canReview = isPlatformAdmin(user) && access.create)
                if (active) load(reset = true)
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load(reset = true) else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false, loadingMore = false) }
        }
    }

    /** Reloads every page already shown. */
    fun refresh() = load(reset = true)

    fun filter(filter: ApplicationFilter) {
        if (filter == state.value.filter) return
        mutable.update { it.copy(filter = filter, items = emptyList(), page = 0, hasNext = false, total = 0, loaded = false, error = null) }
        load(reset = true)
    }

    fun search(text: String) {
        val query = text.take(100)
        if (query == state.value.search) return
        mutable.update { it.copy(search = query, items = emptyList(), page = 0, hasNext = false, total = 0, loaded = false, error = null) }
        load(reset = true)
    }

    fun loadMore() {
        val current = state.value
        if (!current.hasNext || current.loading || current.loadingMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        if (!active || !state.value.canRead) return
        val current = state.value
        val token = ++revision
        loadJob?.cancel()
        loadJob = actions.launch {
            mutable.update { if (reset) it.copy(loading = true) else it.copy(loadingMore = true) }
            try {
                session.requireRestaurantAccess { it.read }
                val first = if (reset) 0 else current.page + 1
                val pages = if (reset) (current.page + 1).coerceAtLeast(1) else 1
                val loaded = mutableListOf<RestaurantApplicationDto>()
                var last: PageDto<RestaurantApplicationDto>? = null
                for (page in first until first + pages) {
                    val result = repository.applications(current.filter.server, current.search, page, APPLICATION_PAGE_SIZE)
                    loaded += result.items
                    last = result
                    if (!result.hasNext) break
                }
                if (token != revision) return@launch
                mutable.update { state ->
                    val items = if (reset) loaded.distinctBy { it.id } else (state.items + loaded).distinctBy { it.id }
                    state.copy(items = items, page = last?.page ?: 0, hasNext = last?.hasNext ?: false,
                        total = last?.totalElements ?: items.size.toLong(), loaded = true, error = null)
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

    fun startApprove(application: RestaurantApplicationDto) = startReview(application, approve = true)

    fun startReject(application: RestaurantApplicationDto) = startReview(application, approve = false)

    private fun startReview(application: RestaurantApplicationDto, approve: Boolean) {
        if (!state.value.canReview || state.value.review != null || application.status != "PENDING") return
        mutable.update { it.copy(review = ReviewStep(application, approve), notice = null) }
    }

    fun editReason(text: String) = mutable.update { state -> state.review?.let { state.copy(review = it.copy(reason = text, error = null)) } ?: state }

    fun cancelReview() {
        if (state.value.review?.saving != true) mutable.update { it.copy(review = null) }
    }

    fun confirmReview() {
        val step = state.value.review ?: return
        if (step.saving || !state.value.canReview) return
        if (reviewProblem(step) != null) {
            mutable.update { it.copy(review = step.copy(showProblems = true)) }
            return
        }
        val token = signIn
        mutable.update { it.copy(review = step.copy(saving = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.create }
                val request = if (step.approve) ReviewApplicationRequestDto("APPROVE")
                    else ReviewApplicationRequestDto("REJECT", step.reason.trim())
                repository.reviewApplication(step.application.id, request)
                if (token != signIn) return@launch
                val name = step.application.name
                mutable.update { state ->
                    state.copy(
                        review = null,
                        // It leaves the list it was in; the totals follow.
                        items = state.items.filterNot { it.id == step.application.id },
                        total = (state.total - 1).coerceAtLeast(0),
                        notice = if (step.approve) "$name is approved. ${step.application.owner?.displayName ?: "The owner"} got an email to set a password."
                            else "$name was rejected. The applicant got an email with the reason."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token != signIn) return@launch
                mutable.update { state -> state.copy(review = state.review?.copy(saving = false, error = platformMessage(e, write = true))) }
                // Someone else decided it first: show the list as it is now.
                if (e is com.saporini.mobile_desktop.core.network.ApiException && (e.status == 409 || e.status == 404)) load(reset = true)
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
