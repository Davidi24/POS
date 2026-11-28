package com.saporini.mobile_desktop.gallery

import com.saporini.mobile_desktop.admin.settings.AdminSettings
import com.saporini.mobile_desktop.admin.settings.SettingsApi
import com.saporini.mobile_desktop.admin.settings.SettingsCategory
import com.saporini.mobile_desktop.pos.menu.domain.repository.MenuRepository
import com.saporini.mobile_desktop.pos.reservations.data.api.ReservationApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.Test

// Look check only (screens round): Admin Hub → Settings at phone, tablet and desktop widths: the categories, the
// Payments page (every kind of control) and Reservations (with the occasions and event nights), into
// build/reports/gallery/size-settings-*.png.

private const val GalleryGeneral = """{"orderTaxRate":20.0,"orderTaxInclusive":true,"serviceChargeEnabled":true,"serviceChargeType":"PERCENTAGE",
"serviceChargeValue":10.0,"allowSplitBills":true,"cashRoundingEnabled":true,"cashRoundingIncrement":0.5,"requireCustomerForInvoice":false,
"invoiceSequencePrefix":"INV","tipsEnabled":true,"tipSuggestionsText":"5,10,15","maxTipPercent":30,"autoClosePaidOrders":true,"refundWindowDays":30,
"largeGroupFrom":8,"largeGroupExtraMinutes":30,"approvalGroupSize":12,"holdMinutes":15,"lateAfterMinutes":10}"""

private const val GalleryReceipt = """{"autoPrintCustomerReceipt":true,"autoPrintKitchenTicket":true,"receiptCopies":1,"showLogo":true,"showTaxBreakdown":true,
"showServerName":true,"showTableName":true,"showOrderNumber":true,"showQrCode":false,"printVoidedItems":false,"footerNote":"Grazie e arrivederci!"}"""

private const val GalleryOccasions = """[{"code":"BIRTHDAY","name":"Birthday","icon":"🎂","options":["Cake","Candles","A song"],"active":true},
{"code":"ANNIVERSARY","name":"Anniversary","icon":"💍","options":["Flowers","A quiet table"],"active":true}]"""

private const val GalleryEvents = """[{"id":"e1","name":"Valentine's night","icon":"❤️","startDate":"2027-02-14","endDate":"2027-02-14",
"menuId":"m1","menuName":"Valentine's menu","specialMenuOnly":true,"active":true}]"""

private fun settingsClient() = HttpClient(MockEngine { request ->
    val path = request.url.encodedPath
    val body = when {
        path.endsWith("/settings") -> GalleryGeneral
        path.endsWith("/settings/receipt") -> GalleryReceipt
        path.endsWith("/settings/reservation-rules") -> "[]"
        path.endsWith("/reservation-occasions") -> GalleryOccasions
        path.endsWith("/events") -> GalleryEvents
        else -> "{}"
    }
    respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
}) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

class AdminSettingsGalleryTest {
    @Test
    fun settingsAtEverySize() = gallery {
        val client = settingsClient()
        val settings = SettingsApi(client) { "http://test" }
        // The first call sets up the JSON machinery, so the pages' own loads answer within the render.
        runBlocking { settings.getObject("restaurant-1", "") }
        // Event nights list the menus to pick from; the look check has none.
        val menus = Proxy.newProxyInstance(MenuRepository::class.java.classLoader, arrayOf(MenuRepository::class.java)) { _, method, _ ->
            error("No menus in the look check: ${method.name}")
        } as MenuRepository
        val session = gallerySession()
        startKoin {
            modules(module {
                single { settings }
                single { ReservationApi(client) { "http://test" } }
                single<MenuRepository> { menus }
                single { session }
            })
        }
        try {
            listOf(360 to 1400, 412 to 1400, 600 to 1100, 768 to 1000, 1024 to 900, 1440 to 900).forEach { (width, height) ->
                render("size-settings-$width", width, height, required = listOf("Settings", "Reservations", "Payments & receipts", "Online booking")) {
                    AdminSettings()
                }
            }
            listOf(360 to 2700, 412 to 2700, 768 to 2300, 1440 to 2100).forEach { (width, height) ->
                render("size-settings-payments-$width", width, height, required = listOf("Payments & receipts", "Tax rate", "Tip buttons", "Service charge")) {
                    AdminSettings(startCategory = SettingsCategory.PAYMENTS)
                }
            }
            listOf(360 to 3600, 1024 to 2800).forEach { (width, height) ->
                render("size-settings-reservations-$width", width, height, required = listOf("Booking length", "Occasions", "Valentine's night")) {
                    AdminSettings(startCategory = SettingsCategory.RESERVATIONS)
                }
            }
        } finally {
            stopKoin()
        }
    }
}
