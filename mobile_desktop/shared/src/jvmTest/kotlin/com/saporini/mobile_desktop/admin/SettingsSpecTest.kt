package com.saporini.mobile_desktop.admin

import com.saporini.mobile_desktop.admin.settings.SaveGroups
import com.saporini.mobile_desktop.admin.settings.SettingsApi
import com.saporini.mobile_desktop.admin.settings.SettingsCategory
import com.saporini.mobile_desktop.admin.settings.SettingsScope
import com.saporini.mobile_desktop.admin.settings.SettingsSource
import com.saporini.mobile_desktop.admin.settings.SettingsValues
import com.saporini.mobile_desktop.admin.settings.loadSource
import com.saporini.mobile_desktop.admin.settings.problem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Admin Hub → Settings → Reservations: edits are tracked per value, the page refuses settings that contradict each
// other, and each group of values goes to its own endpoint.
class SettingsSpecTest {
    private val scope = SettingsScope("r1", "b1")
    private val R = SettingsSource.RESERVATION_RULE
    private val G = SettingsSource.GENERAL

    private val general = buildJsonObject {
        put("holdMinutes", 30); put("holdWarningMinutes", 20); put("lateAfterMinutes", 15)
        put("largeGroupFrom", 5); put("largeGroupExtraMinutes", 15); put("approvalGroupSize", 7)
        put("checkInOpensMinutes", 120); put("confirmReminderTime", "15:00:00"); put("sameDayConfirmMinutes", 120)
        put("attendanceCallMinutes", 120); put("reopenWindowMinutes", 60); put("undoSeatMinutes", 15)
        put("runningLateMaxMinutes", 30); put("guestReminderHours", 24); put("noShowWarningFrom", 1); put("depositFromGuests", 7)
        put("serviceChargeEnabled", false)
    }
    private val rule = buildJsonObject {
        put("id", "rule-1"); put("ruleName", "Default"); put("priority", 100); put("active", true)
        put("minPartySize", 1); put("maxPartySize", 20); put("requireDeposit", false); put("depositType", JsonNull)
        put("depositValue", JsonNull); put("createdAt", "2026-09-27T10:00:00Z")
    }
    private fun values() = SettingsValues(mapOf(G to general, R to rule), emptyMap())

    @Test
    fun anEditBackToTheStoredValueIsNoChange() {
        val edited = values().set(G, "holdMinutes", JsonPrimitive(45))
        assertTrue(edited.changed)
        assertEquals("45", edited.text(G, "holdMinutes"))

        assertFalse(edited.set(G, "holdMinutes", JsonPrimitive(30)).changed)
    }

    @Test
    fun theAgreedDefaultsCanBeSaved() {
        assertNull(SettingsCategory.RESERVATIONS.problem(values()))
    }

    @Test
    fun refusesSettingsThatContradictEachOther() {
        val page = SettingsCategory.RESERVATIONS
        assertEquals(
            "The smallest booking can't be bigger than the largest.",
            page.problem(values().set(R, "minPartySize", JsonPrimitive(25)))
        )
        assertEquals(
            "\"Hold ends soon\" must show before the hold ends.",
            page.problem(values().set(G, "holdWarningMinutes", JsonPrimitive(30)))
        )
        assertEquals(
            "A guest must show as late before the hold ends.",
            page.problem(values().set(G, "lateAfterMinutes", JsonPrimitive(40)))
        )
    }

    @Test
    fun aDepositNeedsItsKindAndAmount() {
        val page = SettingsCategory.RESERVATIONS
        val on = values().set(R, "requireDeposit", JsonPrimitive(true))
        assertEquals("Choose the kind of deposit.", page.problem(on))

        val fixed = on.set(R, "depositType", JsonPrimitive("FIXED_AMOUNT"))
        assertEquals("Enter the deposit amount.", page.problem(fixed))
        assertNull(page.problem(fixed.set(R, "depositValue", JsonPrimitive(20.0))))

        val percentage = on.set(R, "depositType", JsonPrimitive("PERCENTAGE")).set(R, "depositValue", JsonPrimitive(120.0))
        assertEquals("A percentage deposit can't be more than 100%.", page.problem(percentage))
    }

    @Test
    fun theReservationTimesGoToTheirOwnEndpoint() = runTest {
        val sent = mutableListOf<Triple<HttpMethod, String, JsonObject>>()
        val api = api { method, path, body -> sent += Triple(method, path, body); body }
        val edited = values().set(G, "holdMinutes", JsonPrimitive(45))
        val group = SaveGroups.single { it.source == G && "holdMinutes" in it.keys }
        val all = edited.current(G)!!

        group.save(api, scope, JsonObject(group.keys.associateWith { all[it] ?: JsonNull }))

        val (method, path, body) = sent.single()
        assertEquals(HttpMethod.Patch, method)
        assertEquals("/restaurants/r1/settings/reservation-policy", path)
        assertEquals(JsonPrimitive(45), body["holdMinutes"])
        assertEquals(JsonPrimitive(1), body["noShowWarningFrom"])
        assertFalse("serviceChargeEnabled" in body)
    }

    @Test
    fun withNoRuleYetTheDefaultsAreLoadedAndCreatedOnSave() = runTest {
        val sent = mutableListOf<Triple<HttpMethod, String, JsonObject>>()
        val api = api { method, path, body ->
            sent += Triple(method, path, body)
            if (method == HttpMethod.Get) JsonObject(emptyMap()) else body
        }

        val loaded = api.loadSource(R, scope)
        assertEquals(JsonNull, loaded["id"])
        assertEquals(JsonPrimitive(120), loaded["defaultDurationMinutes"])
        assertEquals(JsonPrimitive(5), loaded["bufferMinutes"])

        SaveGroups.single { it.source == R }.save(api, scope, loaded)

        val (method, path, body) = sent.last()
        assertEquals(HttpMethod.Post, method)
        assertEquals("/restaurants/r1/settings/reservation-rules", path)
        assertFalse("id" in body)
    }

    @Test
    fun anExistingRuleIsUpdatedWithoutItsServerFields() = runTest {
        val sent = mutableListOf<Triple<HttpMethod, String, JsonObject>>()
        val api = api { method, path, body -> sent += Triple(method, path, body); body }

        SaveGroups.single { it.source == R }.save(api, scope, rule)

        val (method, path, body) = sent.single()
        assertEquals(HttpMethod.Put, method)
        assertEquals("/restaurants/r1/settings/reservation-rules/rule-1", path)
        assertFalse("id" in body)
        assertFalse("createdAt" in body)
    }

    // Answers every call with what [reply] returns; a GET of the rule list gets an empty list.
    private fun api(reply: (HttpMethod, String, JsonObject) -> JsonObject): SettingsApi {
        val client = HttpClient(MockEngine { request ->
            val body = (request.body as? TextContent)?.text?.let { Json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
            val path = request.url.encodedPath
            val answer = if (request.method == HttpMethod.Get && path.endsWith("/reservation-rules")) "[]"
            else reply(request.method, path, body).toString()
            respond(answer, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return SettingsApi(client) { "http://test" }
    }
}
