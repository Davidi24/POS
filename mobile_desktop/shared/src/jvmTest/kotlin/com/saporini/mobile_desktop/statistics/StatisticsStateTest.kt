@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.statistics

import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.statistics.data.KpiDto
import com.saporini.mobile_desktop.statistics.data.KpisDto
import com.saporini.mobile_desktop.statistics.data.ReportInfoDto
import com.saporini.mobile_desktop.statistics.data.StatisticsApi
import com.saporini.mobile_desktop.statistics.data.StatisticsRepository
import com.saporini.mobile_desktop.statistics.data.StatsOverviewDto
import com.saporini.mobile_desktop.statistics.data.StatsPeriodDto
import com.saporini.mobile_desktop.statistics.data.StatsQuery
import com.saporini.mobile_desktop.statistics.data.StatsSalesDto
import com.saporini.mobile_desktop.statistics.data.StatsStaffDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY = LocalDate(2026, 10, 6)

private class FakeStats : StatisticsRepository {
    val queries = mutableListOf<Pair<String, StatsQuery>>()
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    val reportGates = mutableMapOf<String, CompletableDeferred<Unit>>()

    private suspend fun record(kind: String, query: StatsQuery) {
        queries += kind to query
        gate?.let { withContext(NonCancellable) { it.await() } }
        failure?.let { throw it }
    }

    private fun period(query: StatsQuery) = StatsPeriodDto(query.from.toString(), query.to.toString())

    override suspend fun overview(query: StatsQuery): StatsOverviewDto {
        record("overview", query)
        return StatsOverviewDto(period(query), KpisDto(sales = KpiDto(OrderDecimal("100.00"), OrderDecimal("80.00"))))
    }

    override suspend fun sales(query: StatsQuery): StatsSalesDto {
        record("sales", query); return StatsSalesDto(period(query))
    }

    override suspend fun staff(query: StatsQuery): StatsStaffDto {
        record("staff", query); return StatsStaffDto(period(query))
    }

    override suspend fun reports(restaurantId: String): List<ReportInfoDto> {
        record("reports", StatsQuery(restaurantId, null, TODAY, TODAY)); return listOf(ReportInfoDto("items", "Dishes sold"))
    }

    override suspend fun report(query: StatsQuery, code: String): String {
        queries += "report:$code" to query
        reportGates[code]?.let { withContext(NonCancellable) { it.await() } }
        failure?.let { throw it }
        return "﻿dish,quantity\nPasta,3\n"
    }
}

class StatisticsRulesTest {
    @Test
    fun changesAreShownAsRoundedPercents() {
        assertEquals("+25%", changeText(OrderDecimal("100.00"), OrderDecimal("80.00")))
        assertEquals("-20%", changeText(OrderDecimal("80.00"), OrderDecimal("100.00")))
        assertEquals("+3%", changeText(OrderDecimal("103.40"), OrderDecimal("100.00")))
        assertEquals("±0%", changeText(OrderDecimal("100.00"), OrderDecimal("100.00")))
        assertEquals("new", changeText(OrderDecimal("5.00"), OrderDecimal("0.00")))
        assertNull(changeText(OrderDecimal("0.00"), OrderDecimal("0.00")))
    }

    @Test
    fun presetPeriodsCountRestaurantDays() {
        assertEquals(TODAY to TODAY, periodRange(StatsPeriod.TODAY, TODAY))
        assertEquals(LocalDate(2026, 10, 5) to LocalDate(2026, 10, 5), periodRange(StatsPeriod.YESTERDAY, TODAY))
        assertEquals(LocalDate(2026, 9, 30) to TODAY, periodRange(StatsPeriod.LAST_7_DAYS, TODAY))
        assertEquals(LocalDate(2026, 10, 1) to TODAY, periodRange(StatsPeriod.THIS_MONTH, TODAY))
        assertEquals(LocalDate(2026, 9, 1) to LocalDate(2026, 9, 30), periodRange(StatsPeriod.LAST_MONTH, TODAY))
        assertEquals(LocalDate(2026, 2, 1) to LocalDate(2026, 2, 28), periodRange(StatsPeriod.LAST_MONTH, LocalDate(2026, 3, 15)))
        assertEquals(LocalDate(2025, 12, 1) to LocalDate(2025, 12, 31), periodRange(StatsPeriod.LAST_MONTH, LocalDate(2026, 1, 2)))
        assertNull(periodRange(StatsPeriod.CUSTOM, TODAY))
    }
}

class StatisticsScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakeStats = FakeStats(),
        permissions: List<String> = listOf("REPORTS_READ"),
        block: suspend TestScope.(StatisticsScreenModel, SessionManager) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = StatisticsScreenModel(repo, session, today = { TODAY })
        try {
            runCurrent(); block(model, session)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun withoutPermissionNothingIsAsked() = runTest(dispatcher) {
        val repo = FakeStats()
        check(repo, permissions = listOf("ORDER_READ")) { m, _ ->
            m.setActive(true); runCurrent()
            assertTrue(repo.queries.isEmpty())
            assertFalse(m.state.value.canRead)
        }
    }

    @Test
    fun theOpenTabLoadsForTheLastSevenDaysByDefault() = runTest(dispatcher) {
        val repo = FakeStats()
        check(repo) { m, _ ->
            m.setActive(true); runCurrent()
            val (kind, query) = repo.queries.single()
            assertEquals("overview", kind)
            assertEquals(LocalDate(2026, 9, 30), query.from)
            assertEquals(TODAY, query.to)
            assertNull(query.branchId)
            assertEquals("+25%", m.state.value.overview!!.kpis.sales.let { changeText(it.value, it.previous) })
            m.tab(StatsTab.STAFF); runCurrent()
            assertEquals("staff", repo.queries.last().first)
            m.branch("branch-1"); runCurrent()
            assertEquals("branch-1", repo.queries.last().second.branchId)
            assertNull(m.state.value.overview)
        }
    }

    @Test
    fun customPeriodsAreChecked() = runTest(dispatcher) {
        val repo = FakeStats()
        check(repo) { m, _ ->
            m.setActive(true); runCurrent()
            assertFalse(m.custom(LocalDate(2026, 10, 2), LocalDate(2026, 10, 1)))
            assertNotNull(m.state.value.error)
            assertFalse(m.custom(LocalDate(2025, 1, 1), LocalDate(2026, 1, 2)))
            assertFalse(m.custom(LocalDate(2026, 10, 7), LocalDate(2026, 10, 8)))
            assertFalse(m.custom(LocalDate(2026, 10, 1), LocalDate(2026, 10, 7)))
            assertTrue(m.custom(LocalDate(2025, 1, 1), LocalDate(2026, 1, 1)))
            runCurrent()
            assertEquals(StatsPeriod.CUSTOM, m.state.value.period)
            assertEquals(LocalDate(2025, 1, 1), repo.queries.last().second.from)
            m.period(StatsPeriod.THIS_MONTH); runCurrent()
            assertEquals(LocalDate(2026, 10, 1), repo.queries.last().second.from)
            m.shift(-1); runCurrent()
            assertEquals(LocalDate(2026, 9, 25), repo.queries.last().second.from)
            assertEquals(LocalDate(2026, 9, 30), repo.queries.last().second.to)
            val before = repo.queries.size
            m.shift(2); runCurrent()
            assertEquals(before, repo.queries.size, "a range starting in the future is not loaded")
        }
    }

    @Test
    fun aLateAnswerForAnOldChoiceIsDropped() = runTest(dispatcher) {
        val repo = FakeStats().apply { gate = CompletableDeferred() }
        check(repo) { m, _ ->
            m.setActive(true); runCurrent()
            m.period(StatsPeriod.TODAY); runCurrent()
            repo.gate!!.complete(Unit); runCurrent()
            assertEquals(TODAY.toString(), m.state.value.overview?.period?.from)
        }
    }

    @Test
    fun failuresKeepWhatIsShownAndForbiddenStopsLoading() = runTest(dispatcher) {
        val repo = FakeStats()
        check(repo) { m, _ ->
            m.setActive(true); runCurrent()
            repo.failure = java.io.IOException("offline")
            m.refresh(); runCurrent()
            assertNotNull(m.state.value.overview)
            assertTrue(m.state.value.stale)
            assertNotNull(m.state.value.error)

            repo.failure = null
            m.tab(StatsTab.SALES); runCurrent()
            m.tab(StatsTab.STAFF); runCurrent()
            m.tab(StatsTab.REPORTS); runCurrent()
            m.download("items"); runCurrent()
            assertNotNull(m.state.value.download)

            repo.failure = ApiException(403, "Access denied")
            m.refresh(); runCurrent()
            assertFalse(m.state.value.canRead)
            assertNull(m.state.value.overview, "revoked access must clear previously loaded figures")
            assertNull(m.state.value.sales)
            assertNull(m.state.value.staff)
            assertTrue(m.state.value.reports.isEmpty())
            assertNull(m.state.value.download)
            val count = repo.queries.size
            m.refresh(); runCurrent()
            assertEquals(count, repo.queries.size)
        }
    }

    @Test
    fun reportsDownloadForTheShownPeriod() = runTest(dispatcher) {
        val repo = FakeStats()
        check(repo) { m, _ ->
            m.setActive(true); runCurrent()
            m.tab(StatsTab.REPORTS); runCurrent()
            assertEquals("items", m.state.value.reports.single().code)
            m.download("items"); runCurrent()
            val download = m.state.value.download!!
            assertEquals("items-2026-09-30-to-2026-10-06.csv", download.fileName)
            assertTrue(download.csv.contains("Pasta,3"))
            m.downloadHandled()
            assertNull(m.state.value.download)
        }
    }


    @Test
    fun anOlderCancelledDownloadCannotClearTheNewDownloadState() = runTest(dispatcher) {
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        val repo = FakeStats().apply {
            reportGates["items"] = oldGate
            reportGates["sales"] = newGate
        }
        check(repo) { m, _ ->
            m.setActive(true); runCurrent()
            m.tab(StatsTab.REPORTS); runCurrent()
            m.download("items"); runCurrent()
            assertEquals("items", m.state.value.downloading)

            m.branch("branch-1"); runCurrent()
            assertNull(m.state.value.downloading)
            m.download("sales"); runCurrent()
            assertEquals("sales", m.state.value.downloading)

            oldGate.complete(Unit); runCurrent()
            assertEquals("sales", m.state.value.downloading)
            newGate.complete(Unit); runCurrent()
            assertNull(m.state.value.downloading)
            assertEquals("sales", m.state.value.download?.code)
        }
    }

    @Test
    fun theApiSendsTheWindowAndRefusesOddReportCodes() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            seen += request.url.encodedPath + "?" + request.url.parameters.entries().sortedBy { it.key }
                .joinToString("&") { "${it.key}=${it.value.single()}" }
            if (request.url.encodedPath.endsWith(".csv")) respond("a,b\n", headers = headersOf(HttpHeaders.ContentType, "text/csv"))
            else respond("""{"period":{"from":"2026-10-01","to":"2026-10-06"},"kpis":{"sales":{"value":12.50,"previous":0}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val api = StatisticsApi(client) { "http://localhost" }
            val query = StatsQuery("r", "b", LocalDate(2026, 10, 1), TODAY)
            assertEquals("12.50", api.overview(query).kpis.sales.value.value)
            assertEquals("a,b\n", api.report(query, "daily-sales"))
            assertEquals(listOf(
                "/restaurants/r/statistics/overview?branchId=b&from=2026-10-01&to=2026-10-06",
                "/restaurants/r/statistics/reports/daily-sales.csv?branchId=b&from=2026-10-01&to=2026-10-06"
            ), seen)
            kotlin.test.assertFailsWith<IllegalArgumentException> { api.report(query, "../secrets") }
        } finally {
            client.close()
        }
    }
}
