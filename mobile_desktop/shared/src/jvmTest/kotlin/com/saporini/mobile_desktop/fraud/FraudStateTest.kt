@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.fraud

import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.fraud.data.FraudActivityDto
import com.saporini.mobile_desktop.fraud.data.FraudActivityPageDto
import com.saporini.mobile_desktop.fraud.data.FraudAlertDto
import com.saporini.mobile_desktop.fraud.data.FraudAlertFilter
import com.saporini.mobile_desktop.fraud.data.FraudAlertPageDto
import com.saporini.mobile_desktop.fraud.data.FraudApi
import com.saporini.mobile_desktop.fraud.data.FraudChecksRequestDto
import com.saporini.mobile_desktop.fraud.data.FraudOverviewDto
import com.saporini.mobile_desktop.fraud.data.FraudQuery
import com.saporini.mobile_desktop.fraud.data.FraudRepository
import com.saporini.mobile_desktop.fraud.data.FraudReviewRequestDto
import com.saporini.mobile_desktop.fraud.data.FraudRuleSettingDto
import com.saporini.mobile_desktop.fraud.data.FraudRulesDto
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

private fun alert(n: Int, status: String = "OPEN") = FraudAlertDto(
    key = "LARGE_REFUND:ref-$n", rule = "LARGE_REFUND", severity = "HIGH", title = "Big refund", status = status
)

private class FakeFraud : FraudRepository {
    var alerts = (1..95).map { alert(it) }
    val alertPages = mutableListOf<Int>()
    val activityPages = mutableListOf<Int>()
    val queries = mutableListOf<FraudQuery>()
    val reviews = mutableListOf<Pair<String, FraudReviewRequestDto>>()
    val checks = mutableListOf<FraudChecksRequestDto>()
    var failure: Exception? = null
    var rules = FraudRulesDto(rules = listOf(
        FraudRuleSettingDto("LARGE_REFUND", "HIGH", "Big refund"),
        FraudRuleSettingDto("HIGH_TIP", "LOW", "High tip", enabled = false)
    ))

    override suspend fun overview(query: FraudQuery): FraudOverviewDto {
        queries += query; failure?.let { throw it }
        return FraudOverviewDto(query.from.toString(), query.to.toString(), openAlerts = 2, latest = alerts.take(2))
    }

    override suspend fun alerts(query: FraudQuery, filter: FraudAlertFilter, page: Int, size: Int): FraudAlertPageDto {
        queries += query; alertPages += page; failure?.let { throw it }
        val matching = alerts.filter { filter.status == null || it.status == filter.status }
        val items = matching.drop(page * size).take(size)
        return FraudAlertPageDto(items, page, size, matching.size.toLong(), (matching.size + size - 1) / size, (page + 1) * size < matching.size)
    }

    override suspend fun activity(query: FraudQuery, type: String?, staffId: String?, page: Int, size: Int): FraudActivityPageDto {
        activityPages += page; failure?.let { throw it }
        val all = (1..50).map { FraudActivityDto(type = type ?: "REFUND", detail = "a$it") }
        return FraudActivityPageDto(all.drop(page * size).take(size), page, size, 50, 2, (page + 1) * size < 50)
    }

    override suspend fun rules(restaurantId: String) = rules

    override suspend fun review(restaurantId: String, alertKey: String, request: FraudReviewRequestDto): FraudAlertDto {
        reviews += alertKey to request; failure?.let { throw it }
        alerts = alerts.map { if (it.key == alertKey) it.copy(status = request.status, reviewNote = request.note) else it }
        return alerts.first { it.key == alertKey }.copy(reviewedByName = "Owner")
    }

    override suspend fun updateChecks(restaurantId: String, request: FraudChecksRequestDto) {
        checks += request
        rules = rules.copy(rules = rules.rules.map { it.copy(enabled = it.rule !in request.fraudDisabledRules) })
    }
}

class FraudScreenModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakeFraud = FakeFraud(),
        permissions: List<String> = listOf("FRAUD_READ", "FRAUD_REVIEW", "SETTINGS_UPDATE"),
        block: suspend TestScope.(FraudScreenModel) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = FraudScreenModel(repo, session, today = { TODAY })
        try {
            runCurrent(); model.setActive(true); runCurrent(); block(model)
        } finally {
            model.onDispose(); runCurrent()
        }
    }

    @Test
    fun overviewLoadsTheLastSevenDays() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo) { m ->
            assertEquals(2, m.state.value.overview?.openAlerts)
            assertEquals(LocalDate(2026, 9, 30), repo.queries.single().from)
        }
    }

    @Test
    fun alertsPageAndARefreshKeepsEveryLoadedPage() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo) { m ->
            m.tab(FraudTab.ALERTS); runCurrent()
            assertEquals(40, m.state.value.alerts.size)
            assertTrue(m.state.value.alertsHasNext)
            m.loadMore(); runCurrent()
            assertEquals(80, m.state.value.alerts.size)
            m.refresh(); runCurrent()
            assertEquals(80, m.state.value.alerts.size)
            assertEquals(listOf(0, 1, 0, 1), repo.alertPages)
            m.loadMore(); runCurrent()
            assertEquals(95, m.state.value.alerts.size)
            assertFalse(m.state.value.alertsHasNext)
            m.loadMore(); runCurrent()
            assertEquals(5, repo.alertPages.size)
        }
    }

    @Test
    fun aFailedPageKeepsTheListAndCanBeRetried() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo) { m ->
            m.tab(FraudTab.ALERTS); runCurrent()
            repo.failure = java.io.IOException("offline")
            m.loadMore(); runCurrent()
            assertEquals(40, m.state.value.alerts.size)
            assertNotNull(m.state.value.error)
            repo.failure = null
            m.loadMore(); runCurrent()
            assertEquals(80, m.state.value.alerts.size)
        }
    }

    @Test
    fun reviewingAnOpenAlertTakesItOffTheOpenList() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo) { m ->
            m.tab(FraudTab.ALERTS); runCurrent()
            m.review("LARGE_REFUND:ref-1", "DISMISSED", "  Guest complained  "); runCurrent()
            assertEquals("Guest complained", repo.reviews.single().second.note)
            assertEquals(39, m.state.value.alerts.size)
            assertEquals(94, m.state.value.alertsTotal)
            m.review("LARGE_REFUND:ref-2", "MAYBE", null); runCurrent()
            m.review("LARGE_REFUND:ref-2", "REVIEWED", "n".repeat(1001)); runCurrent()
            assertEquals(1, repo.reviews.size)
            m.filter(FraudAlertFilter(status = null)); runCurrent()
            assertEquals("DISMISSED", m.state.value.alerts.first { it.key == "LARGE_REFUND:ref-1" }.status)
        }
    }

    @Test
    fun onlyReviewersReview() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo, permissions = listOf("FRAUD_READ")) { m ->
            m.review("LARGE_REFUND:ref-1", "REVIEWED", null); runCurrent()
            assertTrue(repo.reviews.isEmpty())
            assertNotNull(m.state.value.error)
        }
    }

    @Test
    fun periodsAreLimitedToNinetyTwoDays() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo) { m ->
            assertFalse(m.period(LocalDate(2026, 7, 1), TODAY))
            assertTrue(m.period(LocalDate(2026, 7, 7), TODAY))
            runCurrent()
            assertEquals(LocalDate(2026, 7, 7), repo.queries.last().from)
            m.lastDays(500); runCurrent()
            assertEquals(LocalDate(2026, 7, 7), repo.queries.last().from)
        }
    }

    @Test
    fun activityPagesAndFilters() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo) { m ->
            m.tab(FraudTab.ACTIVITY); runCurrent()
            assertEquals(40, m.state.value.activity.size)
            m.loadMore(); runCurrent()
            assertEquals(50, m.state.value.activity.size)
            m.activityFilter("NONSENSE", null); runCurrent()
            assertEquals(50, m.state.value.activity.size)
            m.activityFilter("DISCOUNT", null); runCurrent()
            assertEquals("DISCOUNT", m.state.value.activity.first().type)
        }
    }

    @Test
    fun checksAreSwitchedThroughTheSettings() = runTest(dispatcher) {
        val repo = FakeFraud()
        check(repo) { m ->
            m.tab(FraudTab.RULES); runCurrent()
            m.setRuleEnabled("HIGH_TIP", true); runCurrent()
            assertEquals(emptyList(), repo.checks.single().fraudDisabledRules)
            m.setRuleEnabled("LARGE_REFUND", false); runCurrent()
            assertEquals(listOf("LARGE_REFUND"), repo.checks.last().fraudDisabledRules)
            assertFalse(m.state.value.rules!!.rules.first { it.rule == "LARGE_REFUND" }.enabled)
        }
        val readOnly = FakeFraud()
        check(readOnly, permissions = listOf("FRAUD_READ")) { m ->
            m.tab(FraudTab.RULES); runCurrent()
            m.setRuleEnabled("HIGH_TIP", true); runCurrent()
            assertTrue(readOnly.checks.isEmpty())
        }
    }

    @Test
    fun forbiddenStopsLoading() = runTest(dispatcher) {
        val repo = FakeFraud().apply { failure = ApiException(403, "Access denied") }
        check(repo) { m ->
            assertFalse(m.state.value.canRead)
            assertNull(m.state.value.overview)
        }
    }

    @Test
    fun theApiSendsTheAlertKeyAndFilters() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            seen += "${request.method.value} ${request.url.encodedPath}"
            when {
                request.method == HttpMethod.Put -> {
                    assertTrue((request.body as TextContent).text.contains("\"status\":\"REVIEWED\""))
                    respond("""{"key":"HIGH_TIP:a","rule":"HIGH_TIP","severity":"LOW","title":"High tip","status":"REVIEWED"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                request.method == HttpMethod.Patch -> respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> {
                    assertEquals("HIGH", request.url.parameters["severity"])
                    assertEquals("1", request.url.parameters["page"])
                    respond("""{"items":[],"totalElements":0}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }) { install(ContentNegotiation) { json(json) } }
        try {
            val api = FraudApi(client) { "http://localhost" }
            api.alerts(FraudQuery("r", null, TODAY, TODAY), FraudAlertFilter(severity = "HIGH"), 1, 40)
            assertEquals("REVIEWED", api.review("r", "HIGH_TIP:a", FraudReviewRequestDto("REVIEWED")).status)
            api.updateChecks("r", FraudChecksRequestDto(30, OrderDecimal("50.00"), 5, 30, 2, listOf("HIGH_TIP")))
            assertEquals(listOf(
                "GET /restaurants/r/fraud/alerts",
                "PUT /restaurants/r/fraud/alerts/HIGH_TIP:a/review",
                "PATCH /restaurants/r/settings/fraud-checks"
            ), seen)
        } finally {
            client.close()
        }
    }
}
