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

// Pages of twelve: four rows of three, six of two (user, 2026-11-21: "put pagination").
const val APPLICATION_PAGE_SIZE = 12

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

/** Bringing a rejected application back to waiting (user, 2026-11-21: "I can return a rejected one"). */
data class ReopenStep(val application: RestaurantApplicationDto, val saving: Boolean = false, val error: String? = null)

/** An application just approved, and how many still wait, for the celebration. */
data class ApprovedApplication(val application: RestaurantApplicationDto, val stillWaiting: Long, val tick: Int)

data class ApplicationsState(
    val canRead: Boolean = false,
    // Approving creates a restaurant, so it takes the right to create restaurants.
    val canReview: Boolean = false,
    val filter: ApplicationFilter = ApplicationFilter.WAITING,
    val search: String = "",
    val items: List<RestaurantApplicationDto> = emptyList(),
    // The page shown (from 0), a page at a time.
    val page: Int = 0,
    val pageSize: Int = APPLICATION_PAGE_SIZE,
    val hasNext: Boolean = false,
    val total: Long = 0,
    // Both lists' sizes whatever is shown, for the tabs and the banner (null until loaded).
    val waitingCount: Long? = null,
    val rejectedCount: Long? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val review: ReviewStep? = null,
    val reopen: ReopenStep? = null,
    // Just approved: the celebration shows over the page, and the card leaves when it closes (user, 2026-11-21).
    val approved: ApprovedApplication? = null,
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
                if (active) load()
            }
        }
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) load() else {
            revision++
            loadJob?.cancel()
            mutable.update { it.copy(loading = false, loadingMore = false) }
        }
    }

    /** Reads the page shown again. */
    fun refresh() = load()

    fun filter(filter: ApplicationFilter) {
        if (filter == state.value.filter) return
        mutable.update { it.copy(filter = filter, items = emptyList(), page = 0, hasNext = false, total = 0, loaded = false, error = null) }
        load()
    }

    fun search(text: String) {
        val query = text.take(100)
        if (query == state.value.search) return
        mutable.update { it.copy(search = query, items = emptyList(), page = 0, hasNext = false, total = 0, loaded = false, error = null) }
        load()
    }

    /** Another page (‹ ›); the list stays until it comes. */
    fun goToPage(page: Int) {
        val current = state.value
        val last = (((current.total - 1).coerceAtLeast(0)) / current.pageSize).toInt()
        val target = page.coerceIn(0, last)
        if (target == current.page || current.loading) return
        mutable.update { it.copy(page = target) }
        load()
    }

    private fun load() {
        if (!active || !state.value.canRead) return
        val current = state.value
        val token = ++revision
        loadJob?.cancel()
        loadJob = actions.launch {
            mutable.update { it.copy(loading = true) }
            try {
                session.requireRestaurantAccess { it.read }
                var result = repository.applications(current.filter.server, current.search, current.page, current.pageSize)
                // The page emptied (decided here or elsewhere): show the last page that still has some.
                if (result.items.isEmpty() && current.page > 0 && result.totalElements > 0) {
                    result = repository.applications(current.filter.server, current.search, ((result.totalElements - 1) / current.pageSize).toInt(),
                        current.pageSize)
                }
                // How many wait and how many were rejected, for the tabs; the list works without it.
                val counts = runCatching {
                    repository.applications(ApplicationFilter.WAITING.server, "", 0, 1).totalElements to
                        repository.applications(ApplicationFilter.REJECTED.server, "", 0, 1).totalElements
                }.getOrNull()
                if (token != revision) return@launch
                mutable.update { state ->
                    state.copy(items = result.items.distinctBy { it.id }, page = result.page, hasNext = result.hasNext, total = result.totalElements,
                        loaded = true, error = null,
                        waitingCount = counts?.first ?: state.waitingCount, rejectedCount = counts?.second ?: state.rejectedCount)
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

    fun startReopen(application: RestaurantApplicationDto) {
        if (!state.value.canReview || state.value.reopen != null || application.status != "REJECTED") return
        mutable.update { it.copy(reopen = ReopenStep(application), notice = null) }
    }

    fun cancelReopen() {
        if (state.value.reopen?.saving != true) mutable.update { it.copy(reopen = null) }
    }

    /** It waits again with the others; it leaves the Rejected list and the page is read again. */
    fun confirmReopen() {
        val step = state.value.reopen ?: return
        if (step.saving || !state.value.canReview) return
        val token = signIn
        mutable.update { it.copy(reopen = step.copy(saving = true, error = null)) }
        actions.launch {
            try {
                session.requireRestaurantAccess { it.create }
                repository.reopenApplication(step.application.id)
                if (token != signIn) return@launch
                mutable.update { state ->
                    state.copy(reopen = null, items = state.items.filterNot { it.id == step.application.id },
                        total = (state.total - 1).coerceAtLeast(0),
                        waitingCount = state.waitingCount?.plus(1), rejectedCount = state.rejectedCount?.minus(1)?.coerceAtLeast(0),
                        notice = "${step.application.name} is waiting again.")
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token != signIn) return@launch
                mutable.update { state -> state.copy(reopen = state.reopen?.copy(saving = false, error = platformMessage(e, write = true))) }
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
                    val waiting = (state.total - 1).coerceAtLeast(0)
                    if (step.approve) {
                        // The card stays (marked approved) under the celebration, and leaves when it closes.
                        state.copy(review = null, total = waiting, waitingCount = state.waitingCount?.minus(1)?.coerceAtLeast(0),
                            approved = ApprovedApplication(step.application, if (state.filter == ApplicationFilter.WAITING) waiting else 0, (state.approved?.tick ?: 0) + 1))
                    } else {
                        state.copy(
                            review = null,
                            // It leaves the list it was in; the totals follow.
                            items = state.items.filterNot { it.id == step.application.id },
                            total = waiting,
                            waitingCount = state.waitingCount?.minus(1)?.coerceAtLeast(0),
                            rejectedCount = state.rejectedCount?.plus(1),
                            notice = "$name was rejected. The applicant got an email with the reason."
                        )
                    }
                }
                // The next page's first one moves up into the room left.
                if (!step.approve) load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token != signIn) return@launch
                mutable.update { state -> state.copy(review = state.review?.copy(saving = false, error = platformMessage(e, write = true))) }
                // Someone else decided it first: show the list as it is now.
                if (e is com.saporini.mobile_desktop.core.network.ApiException && (e.status == 409 || e.status == 404)) load()
            }
        }
    }

    /** The celebration closes and the approved card leaves the list. */
    fun closeApproved() {
        val done = state.value.approved ?: return
        mutable.update { state -> state.copy(approved = null, items = state.items.filterNot { it.id == done.application.id }) }
        load()
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        revision++
        active = false
        work.cancel()
    }
}
