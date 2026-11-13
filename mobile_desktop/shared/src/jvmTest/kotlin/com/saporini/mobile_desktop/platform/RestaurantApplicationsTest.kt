@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.saporini.mobile_desktop.platform

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.platform.ui.RestaurantApplicationsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Super admins reviewing public restaurant applications. */
class RestaurantApplicationsTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val disposals = mutableListOf<() -> Unit>()
    private var waiting = (1..45).map { application("a$it", "Bistro $it") }
    private var rejected = listOf(application("x1", "Old Grill", status = "REJECTED", reason = "No licence"))
    private val calls = mutableListOf<List<Any?>>()
    private val reviews = mutableListOf<Pair<String, ReviewApplicationRequestDto>>()
    private var reviewFailure: ApiException? = null
    private val repository = fakeOf<PlatformRepository> { name, args ->
        when (name) {
            "applications" -> {
                val status = args!![0] as String
                val search = args[1] as String
                val page = args[2] as Int
                val size = args[3] as Int
                calls += listOf(status, search, page, size)
                val source = if (status == "PENDING") waiting else rejected
                val matching = source.filter { search.isBlank() || it.name.contains(search, true) }
                PageDto(matching.drop(page * size).take(size), page, size, matching.size.toLong(), (matching.size + size - 1) / size, (page + 1) * size < matching.size)
            }
            "reviewApplication" -> {
                val id = args!![0] as String
                val request = args[1] as ReviewApplicationRequestDto
                reviews += id to request
                reviewFailure?.let { throw it }
                val reviewed = waiting.first { it.id == id }
                waiting = waiting.filterNot { it.id == id }
                reviewed.copy(status = if (request.decision == "APPROVE") "APPROVED" else "REJECTED", rejectionReason = request.rejectionReason)
            }
            else -> error("Unexpected $name")
        }
    }

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() {
        disposals.forEach { it() }
        Dispatchers.resetMain()
    }

    private fun model(roles: List<String> = listOf("SUPER_ADMIN"), permissions: List<String> = listOf("RESTAURANTS_READ", "RESTAURANTS_CREATE")) =
        RestaurantApplicationsScreenModel(repository, SessionManager().apply {
            signIn(CurrentUserResponse(id = "admin", email = "admin@example.invalid", username = "admin", firstName = "Super", lastName = "Admin",
                isActive = true, emailVerified = true, phoneVerified = false, roles = roles, permissions = permissions))
        }).also { model ->
            disposals += { model.onDispose() }
            scheduler.runCurrent()
            model.setActive(true)
            scheduler.runCurrent()
        }

    @Test fun waitingApplicationsLoadAPageAtATimeAndRefreshKeepsThePlace() {
        val model = model()
        assertEquals(20, model.state.value.items.size)
        assertEquals(45, model.state.value.total)
        assertEquals(listOf<Any?>("PENDING", "", 0, 20), calls.single())
        model.loadMore(); scheduler.runCurrent()
        model.loadMore(); scheduler.runCurrent()
        assertEquals(45, model.state.value.items.size)
        assertFalse(model.state.value.hasNext)
        calls.clear()
        model.refresh(); scheduler.runCurrent()
        assertEquals(listOf(0, 1, 2), calls.map { it[2] })
    }

    @Test fun filterAndSearchStartOverOnTheServer() {
        val model = model()
        model.filter(ApplicationFilter.REJECTED)
        assertTrue(model.state.value.items.isEmpty())
        scheduler.runCurrent()
        assertEquals(listOf("Old Grill"), model.state.value.items.map { it.name })
        assertEquals("REJECTED", calls.last()[0])
        model.filter(ApplicationFilter.WAITING); scheduler.runCurrent()
        model.search("Bistro 4"); scheduler.runCurrent()
        assertEquals(listOf<Any?>("PENDING", "Bistro 4", 0, 20), calls.last())
        assertEquals(setOf("Bistro 4", "Bistro 40", "Bistro 41", "Bistro 42", "Bistro 43", "Bistro 44", "Bistro 45"), model.state.value.items.map { it.name }.toSet())
    }

    @Test fun approvingSendsTheDecisionRemovesTheCardAndSaysWhatHappened() {
        val model = model()
        val first = model.state.value.items.first()
        model.startApprove(first)
        assertTrue(model.state.value.review!!.approve)
        model.confirmReview(); scheduler.runCurrent()
        assertEquals(first.id to ReviewApplicationRequestDto("APPROVE"), reviews.single())
        assertNull(model.state.value.review)
        assertTrue(model.state.value.items.none { it.id == first.id })
        assertEquals(44, model.state.value.total)
        assertTrue(model.state.value.notice.orEmpty().contains("is approved"), model.state.value.notice)
    }

    @Test fun rejectingNeedsAReasonThatIsSentTrimmed() {
        val model = model()
        val first = model.state.value.items.first()
        model.startReject(first)
        model.confirmReview(); scheduler.runCurrent()
        assertTrue(model.state.value.review!!.showProblems)
        assertNotNull(reviewProblem(model.state.value.review!!))
        assertTrue(reviews.isEmpty())
        model.editReason("x".repeat(MAX_REJECTION_REASON + 1))
        assertNotNull(reviewProblem(model.state.value.review!!))
        model.editReason("  Add your VAT number  ")
        model.confirmReview(); scheduler.runCurrent()
        assertEquals(ReviewApplicationRequestDto("REJECT", "Add your VAT number"), reviews.single().second)
        assertTrue(model.state.value.notice.orEmpty().contains("was rejected"))
    }

    @Test fun anApplicationDecidedElsewhereShowsTheErrorAndReloads() {
        val model = model()
        val first = model.state.value.items.first()
        model.startApprove(first)
        reviewFailure = ApiException(409, "This application was already reviewed")
        calls.clear()
        model.confirmReview(); scheduler.runCurrent()
        assertEquals("This application was already reviewed", model.state.value.review!!.error)
        assertFalse(model.state.value.review!!.saving)
        assertEquals(1, calls.size)
    }

    @Test fun reviewingNeedsTheRightToCreateRestaurantsAndOnlyWaitingOnesCanBeReviewed() {
        val readOnly = model(roles = listOf("CO_SUPER_ADMIN"), permissions = listOf("RESTAURANTS_READ"))
        assertTrue(readOnly.state.value.canRead)
        assertFalse(readOnly.state.value.canReview)
        readOnly.startApprove(readOnly.state.value.items.first())
        assertNull(readOnly.state.value.review)

        val staff = model(roles = listOf("OWNER"), permissions = listOf("RESTAURANTS_READ", "RESTAURANTS_CREATE"))
        assertFalse(staff.state.value.canRead)
        assertTrue(staff.state.value.items.isEmpty())

        val admin = model()
        admin.startApprove(rejected.first())
        assertNull(admin.state.value.review)
    }

    @Test fun thePageShowsTheQueueAndTheRejectDialog() {
        val model = model()
        val scene = ImageComposeScene(1280, 1000, coroutineContext = dispatcher) {
            val state by model.state.collectAsState()
            ScenePage { RestaurantApplicationsContent(state, model, narrow = false, onBack = {}) }
        }
        try {
            scene.settle(scheduler)
            val text = scene.labels()
            assertTrue(text.contains("45 applications are waiting for review"), text)
            assertTrue(text.contains("Bistro 1"), text)
            assertTrue(text.contains("owner1@example.invalid"), text)
            scene.click("Reject")
            scene.settle(scheduler)
            assertTrue(scene.labels().contains("Reject Bistro"), scene.labels())
            assertTrue(scene.labels().contains("gets an email with your reason"), scene.labels())
        } finally {
            scene.close()
        }
    }

    private fun application(id: String, name: String, status: String = "PENDING", reason: String? = null) = RestaurantApplicationDto(
        id, name, legalName = "$name LLC", email = "hello@${id}.example", phone = "+355691234567", currency = "EUR", timezone = "Europe/Tirane",
        status = status, rejectionReason = reason, submittedAt = "2026-10-20T10:00:00Z",
        owner = ApplicantDto("Owner", id, "owner${id.drop(1)}@example.invalid", "owner$id")
    )
}
