@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.saporini.mobile_desktop.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import com.saporini.mobile_desktop.admin.people.PeopleRepository
import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.platform.ui.BranchListContent
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

/** The "Choose a branch" page and the restaurant entry, now that the server pages and searches the branches. */
class BranchChooserPagingTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val disposals = mutableListOf<() -> Unit>()
    private val session = SessionManager().apply {
        signIn(CurrentUserResponse(id = "admin", email = "admin@example.invalid", username = "admin", firstName = "Super", lastName = "Admin",
            isActive = true, emailVerified = true, phoneVerified = false, roles = listOf("SUPER_ADMIN"),
            permissions = listOf("RESTAURANTS_READ", "RESTAURANTS_UPDATE", "RESTAURANTS_CREATE")))
    }
    private var restaurant = RestaurantDto("r", "Trattoria", status = "ACTIVE", isActive = true)
    // 60 branches; every fifth one is closed.
    private var branches = (0 until 60).map { i ->
        BranchCardDto("b$i", "Branch ${i.toString().padStart(2, '0')}", code = "B$i", active = i % 5 != 0, status = if (i % 5 == 0) "INACTIVE" else "ACTIVE")
    }
    private val pageCalls = mutableListOf<Triple<String, Int, Int>>()
    private val branchLookups = mutableListOf<String>()
    private var failPage: Int? = null
    private val repository = fake<PlatformRepository> { name, args ->
        when (name) {
            "restaurant" -> restaurant
            "branchCards" -> {
                val search = args!![1] as String
                val page = args[2] as Int
                val size = args[3] as Int
                pageCalls += Triple(search, page, size)
                if (page == failPage) throw ApiException(503, "Offline")
                val matching = branches.filter { search.isBlank() || it.name.contains(search, true) }.sortedByDescending { it.open }
                BranchCardPageDto(matching.drop(page * size).take(size), page, size, matching.size.toLong(), (matching.size + size - 1) / size,
                    (page + 1) * size < matching.size, branches.size.toLong(), branches.count { it.open }.toLong())
            }
            "branch" -> branches.also { branchLookups += args!![1] as String }.firstOrNull { it.id == args!![1] }?.let { BranchDto(it.id, it.name, status = it.status, isActive = it.active) }
                ?: throw ApiException(404, "Branch not found")
            "applications" -> PageDto<RestaurantApplicationDto>()
            "cards" -> PageDto<RestaurantCardDto>()
            else -> error("Unexpected $name")
        }
    }

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() {
        disposals.forEach { it() }
        Dispatchers.resetMain()
    }

    private fun chooser() = BranchListScreenModel(repository, session).also { model ->
        disposals += { model.onDispose() }
        scheduler.runCurrent()
        model.show("r", "Trattoria")
        model.setActive(true)
        scheduler.runCurrent()
    }

    @Test fun firstPageLoadsOpenBranchesFirstWithWholeRestaurantTotals() {
        val model = chooser()
        val state = model.state.value
        assertEquals(24, state.cards.size)
        assertTrue(state.cards.all { it.open })
        assertTrue(state.hasNext)
        assertEquals(60, state.total)
        assertEquals(60, state.allBranches)
        assertEquals(48, state.openBranches)
        assertEquals(listOf(Triple("", 0, 24)), pageCalls)
    }

    @Test fun loadMoreAppendsAndRefreshReloadsEveryLoadedPage() {
        val model = chooser()
        model.loadMore(); scheduler.runCurrent()
        model.loadMore(); scheduler.runCurrent()
        assertEquals(60, model.state.value.cards.size)
        assertEquals(60, model.state.value.cards.map { it.id }.toSet().size)
        assertFalse(model.state.value.hasNext)
        // Nothing more to load: no request.
        model.loadMore(); scheduler.runCurrent()
        assertEquals(3, pageCalls.size)

        pageCalls.clear()
        model.refresh(); scheduler.runCurrent()
        assertEquals(listOf(0, 1, 2), pageCalls.map { it.second })
        assertEquals(60, model.state.value.cards.size)
    }

    @Test fun aFailedNextPageKeepsLoadedRowsAndCanBeRetried() {
        val model = chooser()
        failPage = 1
        model.loadMore(); scheduler.runCurrent()
        assertEquals(24, model.state.value.cards.size)
        assertNotNull(model.state.value.error)
        assertEquals(0, model.state.value.page)
        failPage = null
        model.loadMore(); scheduler.runCurrent()
        assertEquals(48, model.state.value.cards.size)
        assertNull(model.state.value.error)
    }

    @Test fun searchGoesToTheServerAndClearsOldResultsAtOnce() {
        val model = chooser()
        model.loadMore(); scheduler.runCurrent()
        model.search("branch 1")
        assertTrue(model.state.value.cards.isEmpty())
        assertFalse(model.state.value.loaded)
        scheduler.runCurrent()
        assertEquals(Triple("branch 1", 0, 24), pageCalls.last())
        assertEquals((10..19).map { "b$it" }.toSet(), model.state.value.cards.map { it.id }.toSet())
        assertEquals(10, model.state.value.total)
        assertEquals(60, model.state.value.allBranches)
        // Over-long searches are cut, and the same search doesn't load again.
        val calls = pageCalls.size
        model.search("branch 1"); scheduler.runCurrent()
        assertEquals(calls, pageCalls.size)
        model.search("x".repeat(150)); scheduler.runCurrent()
        assertEquals(100, model.state.value.search.length)
    }

    @Test fun choosingChecksOnlyThatBranchAndEntersWithTheRestaurantsBranchCount() {
        val model = chooser()
        val card = model.state.value.cards.first()
        var entered = 0
        val pagesBefore = pageCalls.size
        model.choose(card) {
            entered++
            // Choosing checks that one branch, not every page again.
            assertEquals(pagesBefore, pageCalls.size)
        }
        scheduler.runCurrent()
        assertEquals(1, entered)
        assertEquals(listOf(card.id), branchLookups)
        val acting = session.actingRestaurant.value
        assertEquals(card.id, acting?.branchId)
        assertEquals(60, acting?.branchCount)
    }

    @Test fun aBranchRemovedOrClosedMeanwhileIsRefusedAndUpdatedInPlace() {
        val model = chooser()
        session.leaveRestaurant()
        val removed = model.state.value.cards[0]
        val closed = model.state.value.cards[1]
        branches = branches.filterNot { it.id == removed.id }.map { if (it.id == closed.id) it.copy(active = false, status = "INACTIVE") else it }
        var entered = 0
        model.choose(removed) { entered++ }; scheduler.runCurrent()
        assertEquals(0, entered)
        assertTrue(model.state.value.cards.none { it.id == removed.id })
        assertNotNull(model.state.value.error)
        model.choose(model.state.value.cards.first { it.id == closed.id }) { entered++ }; scheduler.runCurrent()
        assertEquals(0, entered)
        assertFalse(model.state.value.cards.first { it.id == closed.id }.open)
        assertNull(session.actingRestaurant.value)
    }

    @Test fun enteringARestaurantNeedsOnlyTheFirstTwoCards() {
        val people = fake<PeopleRepository> { name, _ -> error("Unexpected people call $name") }
        val list = RestaurantListScreenModel(repository, people, session).also { m -> disposals += { m.onDispose() } }
        scheduler.runCurrent()
        list.setActive(true)
        scheduler.runCurrent()
        var entered = 0
        var chosen: RestaurantCardDto? = null
        list.enter(RestaurantCardDto("r", "Trattoria"), { entered++ }, { chosen = it }); scheduler.runCurrent()
        assertEquals(60L, chosen?.branches)
        assertEquals(Triple("", 0, 2), pageCalls.last())

        branches = listOf(BranchCardDto("only", "Only branch"))
        list.enter(RestaurantCardDto("r", "Trattoria"), { entered++ }, { chosen = null }); scheduler.runCurrent()
        assertEquals(1, entered)
        assertEquals("only", session.actingRestaurant.value?.branchId)

        session.leaveRestaurant(); scheduler.runCurrent()
        branches = listOf(BranchCardDto("closed", "Closed", active = false, status = "INACTIVE"), BranchCardDto("also", "Also closed", active = false, status = "INACTIVE"))
        list.enter(RestaurantCardDto("r", "Trattoria"), { entered++ }, { chosen = it }); scheduler.runCurrent()
        assertEquals(2, entered)
        assertNull(session.actingRestaurant.value?.branchId)
        assertEquals(2, session.actingRestaurant.value?.branchCount)
    }

    @Test fun thePageShowsSearchCountsAndNoMatchAndLoadsMoreNearTheEnd() {
        val model = chooser()
        val scene = ImageComposeScene(1280, 900, coroutineContext = dispatcher) {
            val state by model.state.collectAsState()
            ScenePage { BranchListContent(state, model, narrow = false, onBack = {}, onEntered = {}, modifier = Modifier.fillMaxSize()) }
        }
        try {
            scene.settle(scheduler)
            val text = scene.labels()
            assertTrue(text.contains("60 branches · 48 open"), text)
            assertTrue(text.contains("Search by name, code, city or street"), text)
            assertEquals(24, model.state.value.cards.size)

            model.search("nothing like this")
            scene.settle(scheduler)
            assertTrue(scene.labels().contains("No branch matches"), scene.labels())
        } finally {
            scene.close()
        }
        // A page tall enough to show the end of the grid asks for the next page by itself.
        val tall = chooser()
        val whole = ImageComposeScene(1280, 9000, coroutineContext = dispatcher) {
            val state by tall.state.collectAsState()
            ScenePage { BranchListContent(state, tall, narrow = false, onBack = {}, onEntered = {}, modifier = Modifier.fillMaxSize()) }
        }
        try {
            whole.settle(scheduler)
            assertTrue(tall.state.value.cards.size > 24, "loaded ${tall.state.value.cards.size}")
        } finally {
            whole.close()
        }
    }

    private inline fun <reified T> fake(crossinline call: (String, Array<out Any?>?) -> Any?): T = fakeOf(call)
}
