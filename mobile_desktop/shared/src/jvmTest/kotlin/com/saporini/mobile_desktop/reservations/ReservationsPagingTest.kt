@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.saporini.mobile_desktop.reservations

import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.reservations.domain.model.Reservation
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationPage
import com.saporini.mobile_desktop.pos.reservations.domain.model.ReservationStatus
import com.saporini.mobile_desktop.pos.reservations.domain.repository.ReservationRepository
import com.saporini.mobile_desktop.pos.reservations.ui.ReservationsScreenModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.test.*

// Paging on the reservations overview: a refresh keeps every page already loaded, and the Arriving feed continues
// the same list page by page.
class ReservationsPagingTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val models = mutableListOf<ReservationsScreenModel>()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { models.forEach { it.onDispose() }; Dispatchers.resetMain() }

    private fun model(repository: ReservationRepository): ReservationsScreenModel {
        val session = SessionManager().also { it.signIn(user(permissions = listOf("RESERVATION_READ"))) }
        return ReservationsScreenModel(repository, session).also { models += it; scheduler.runCurrent() }
    }

    @Test fun refreshReloadsEveryPageAlreadyLoaded() = runTest(dispatcher) {
        val requested = mutableListOf<Int>()
        val model = model(repository { name, args ->
            when (name) {
                "getTodayReservationsPage" -> {
                    val page = args[2] as Int
                    requested += page
                    if (page == 0) pageOf(listOf(booking("r1"), booking("r2")), 0, hasNext = true) else pageOf(listOf(booking("r3")), page, hasNext = false)
                }
                else -> null
            }
        })
        runCurrent()
        assertEquals(listOf("r1", "r2"), model.state.value.reservations.map { it.id })
        assertTrue(model.state.value.hasMoreReservations)

        model.loadMoreReservations(); runCurrent()
        assertEquals(listOf("r1", "r2", "r3"), model.state.value.reservations.map { it.id })
        assertFalse(model.state.value.hasMoreReservations)

        requested.clear()
        assertTrue(model.refreshNow())
        assertEquals(listOf(0, 1), requested)
        assertEquals(listOf("r1", "r2", "r3"), model.state.value.reservations.map { it.id })
    }

    @Test fun arrivalsContinueTheSameListAndRefreshKeepsItsPages() = runTest(dispatcher) {
        val calls = mutableListOf<Triple<String?, String?, Int>>()
        val model = model(repository { name, args ->
            when (name) {
                "getTodayReservationsPage" -> pageOf(emptyList(), 0, hasNext = false)
                "getArrivalsPage" -> {
                    val page = args[4] as Int
                    calls += Triple(args[2] as String?, args[3] as String?, page)
                    if (page == 0) pageOf(listOf(booking("a1")), 0, hasNext = true) else pageOf(listOf(booking("a2")), page, hasNext = false)
                }
                else -> null
            }
        })
        runCurrent()

        model.refreshArrivals(null); runCurrent()
        assertTrue(model.state.value.arrivalsLoaded)
        assertEquals(listOf("a1"), model.state.value.arrivals.map { it.id })
        assertTrue(model.state.value.hasMoreArrivals)

        model.loadMoreArrivals(); runCurrent()
        assertEquals(listOf("a1", "a2"), model.state.value.arrivals.map { it.id })
        assertFalse(model.state.value.hasMoreArrivals)
        // The second page continues the first one: same starting moment.
        assertEquals(calls[0].first, calls[1].first)

        calls.clear()
        model.refreshArrivals(null); runCurrent()
        assertEquals(listOf(0, 1), calls.map { it.third })
        assertEquals(listOf("a1", "a2"), model.state.value.arrivals.map { it.id })

        // Another floor starts over from its first page.
        calls.clear()
        model.refreshArrivals("Terrace"); runCurrent()
        assertEquals(1, calls.size)
        assertEquals("Terrace", calls[0].second)
        assertEquals(0, calls[0].third)
    }

    @Test fun aFailedPageStopsUntilTriedAgain() = runTest(dispatcher) {
        var failNext = true
        val model = model(repository { name, args ->
            when (name) {
                "getTodayReservationsPage" -> pageOf(emptyList(), 0, hasNext = false)
                "getArrivalsPage" -> {
                    val page = args[4] as Int
                    when {
                        page == 0 -> pageOf(listOf(booking("a1")), 0, hasNext = true)
                        failNext -> { failNext = false; throw IOException("offline") }
                        else -> pageOf(listOf(booking("a2")), page, hasNext = false)
                    }
                }
                else -> null
            }
        })
        runCurrent()
        model.refreshArrivals(null); runCurrent()

        model.loadMoreArrivals(); runCurrent()
        assertTrue(model.state.value.loadMoreArrivalsFailed)
        assertFalse(model.state.value.isLoadingMoreArrivals)
        assertEquals(listOf("a1"), model.state.value.arrivals.map { it.id })

        model.loadMoreArrivals(); runCurrent()
        assertFalse(model.state.value.loadMoreArrivalsFailed)
        assertEquals(listOf("a1", "a2"), model.state.value.arrivals.map { it.id })
    }

    @Test fun aFeedWhoseFirstPageFailedOffersTryAgain() = runTest(dispatcher) {
        var online = false
        val model = model(repository { name, _ ->
            when (name) {
                "getTodayReservationsPage" -> pageOf(emptyList(), 0, hasNext = false)
                "getArrivalsPage" -> if (online) pageOf(listOf(booking("a1")), 0, hasNext = false) else throw IOException("offline")
                else -> null
            }
        })
        runCurrent()
        model.refreshArrivals(null); runCurrent()
        // No endless spinner: the list is "ready" and shows "Try again".
        assertTrue(model.state.value.arrivalsLoaded)
        assertTrue(model.state.value.loadMoreArrivalsFailed)

        online = true
        model.loadMoreArrivals(); runCurrent()
        assertEquals(listOf("a1"), model.state.value.arrivals.map { it.id })
        assertFalse(model.state.value.loadMoreArrivalsFailed)
        assertFalse(model.state.value.hasMoreArrivals)
    }

    private fun booking(id: String) = Reservation(
        id = id, restaurantId = "restaurant-1", branchId = "branch-1", status = ReservationStatus.CONFIRMED, partySize = 2,
        reservationStart = "2026-09-27T18:00:00Z", reservationEnd = "2026-09-27T19:30:00Z"
    )

    private fun pageOf(items: List<Reservation>, page: Int, hasNext: Boolean) =
        ReservationPage(items, page, 100, items.size.toLong(), if (items.isEmpty()) 0 else 1, hasNext, page > 0)

    // Answers by method name; anything else fails like a server that can't be reached (the model copes with that).
    private fun repository(answer: (String, Array<Any?>) -> Any?): ReservationRepository = Proxy.newProxyInstance(
        ReservationRepository::class.java.classLoader, arrayOf(ReservationRepository::class.java)
    ) { _, method, rawArgs ->
        when (method.name) {
            "toString" -> "FakeReservationRepository"
            "hashCode" -> 0
            "equals" -> false
            else -> answer(method.name, rawArgs ?: emptyArray()) ?: throw IOException("No sample data for ${method.name}")
        }
    } as ReservationRepository
}
