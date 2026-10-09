@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.poscompletion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import com.saporini.mobile_desktop.orders.*
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.history.*
import com.saporini.mobile_desktop.pos.orders.OrdersContent
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderStatus
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScope
import com.saporini.mobile_desktop.pos.orders.ui.OrdersScreenModel
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import com.saporini.mobile_desktop.core.theme.SaporiniTheme
import com.saporini.mobile_desktop.kds.model.*
import com.saporini.mobile_desktop.pos.kitchen.KitchenStatusContent
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.sales.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*
import kotlin.time.Instant

class PosReadScreensScreenshotTest {
    @Test fun salesUsesReportValuesInExistingLayout() {
        val totals = SalesTotals("EUR", ordersServed = 3, tablesServed = 2, sales = amount("170"),
            subtotal = amount("150"), tax = amount("20"), averageTicket = amount("56.67"),
            recordedTips = amount("12"), refunds = amount("32"), collected = amount("100"),
            ordersWithoutPayments = 1, paymentCount = 3,
            hourly = listOf(SalesHour("11:00", amount("70")), SalesHour("12:00", amount("100"))),
            paymentMethods = listOf(SalesMethod("CASH", amount("40"), 1), SalesMethod("CARD", amount("60"), 2)),
            topItems = listOf(SalesItem("Vegetable soup", 3, amount("170"))),
            areas = listOf(SalesArea("First floor", 2, amount("170"))),
            recentPayments = listOf(SalesPayment("p", "o", "ORDER-1042", "T03", 2,
                "2026-09-25T12:00:00Z", "CARD", "PARTIALLY_REFUNDED", amount("60"))))
        val report = salesReport().copy(currencies = listOf(totals))
        capture("sales-data", listOf("Vegetable soup", "First floor", "T03", "Tips", "Collected", "€170.00", "€100.00", "€12.00")) {
            MySalesContent(MySalesState(canRead = true, report = report, stale = false), {}, {}, {}, {}, {})
        }
    }

    @Test fun salesEmptyDoesNotShowSampleValues() {
        capture("sales-empty", listOf("My Sales"), absent = listOf("ORDER-1042", "Vegetable soup")) {
            MySalesContent(MySalesState(canRead = true, report = salesReport(), stale = false), {}, {}, {}, {}, {})
        }
    }

    @Test fun kitchenUsesRealTicketDetailsAcrossStages() {
        val tickets = listOf("FIRED", "IN_PROGRESS", "READY").mapIndexed { i, status ->
            KdsTicket("t$i", "r", "b", "s", orderId = "o$i", orderNumber = "ORDER-${1042 + i}",
                ticketNumber = "K-${i + 1}", stationName = "Hot kitchen", tableNumber = "T0${i + 1}",
                guestCount = 2, status = status, firedAt = "2026-09-25T11:55:00Z",
                items = listOf(KdsTicketItem("i$i", itemNameSnapshot = "Vegetable soup", quantity = 2,
                    status = status, variantNameSnapshot = "Large", notes = "No cream")))
        }
        val state = KdsState(scope = KdsScope("u", "r", "b"), permissions = setOf("KDS_READ"),
            now = Instant.parse("2026-09-25T12:00:00Z"), loaded = true, stale = false,
            connection = KdsConnection.LIVE, boards = listOf(KdsBoard("s", stationName = "Hot kitchen", tickets = tickets)))
        capture("kitchen-data", listOf("Vegetable soup", "2×", "No cream", "Hot kitchen", "Ready now", "Cooking", "Waiting"), absent = listOf("1 x")) {
            KitchenStatusContent(state, {}, {})
        }
    }

    @Test fun historyReusesOrdersLayoutWithoutWriteActions() {
        val repository = object : OrderHistoryRepository {
            override suspend fun page(scope: OrdersScope, filter: HistoryFilter, page: Int, size: Int) =
                HistoryPage(listOf(summary("past-order").copy(status = OrderStatus.CLOSED,
                    orderNumber = "HISTORY-2048", total = amount("42.50"))), page, size, 1, false)
            override suspend fun detail(scope: OrdersScope, id: String) =
                order(id).copy(status = OrderStatus.CLOSED, orderNumber = "HISTORY-2048", total = amount("42.50"))
            override fun changes(scope: OrdersScope) = emptyFlow<Unit>()
        }
        capture("history-data", listOf("HISTORY-2048", "All statuses"), absent = listOf("New order", "Mark paid", "Order progress")) {
            val model = remember {
                OrdersScreenModel(unsupportedRepository(), SessionManager().apply {
                    signIn(user(permissions = listOf("ORDER_READ", "ORDER_UPDATE", "ORDER_CREATE")))
                }, historyRepository = repository, historyOnly = true)
            }
            DisposableEffect(model) { model.setActive(true); onDispose { model.onDispose() } }
            OrdersContent(model)
        }
    }

    private fun amount(value: String) = OrderDecimal(if ("." in value) value else "$value.00")

    private fun capture(name: String, required: List<String>, absent: List<String> = emptyList(), content: @Composable () -> Unit) {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val scene = ImageComposeScene(1440, 1000, coroutineContext = dispatcher) { SaporiniTheme { content() } }
        try {
            repeat(6) {
                scheduler.advanceTimeBy(100); scheduler.runCurrent()
                scene.render(scheduler.currentTime * 1_000_000L).close()
                Thread.sleep(25)
            }
            fun labels(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty().joinToString { it.text } + node.children.joinToString { labels(it) }
            val text = scene.semanticsOwners.joinToString { labels(it.unmergedRootSemanticsNode) }
            val output = File("build/reports/pos-completion").apply { mkdirs() }
            val image = scene.render(scheduler.currentTime * 1_000_000L)
            try {
                val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try { File(output, "$name.png").writeBytes(png.bytes) } finally { png.close() }
            } finally { image.close() }
            required.forEach { assertTrue(text.contains(it), "Missing real value: $it") }
            absent.forEach { assertFalse(text.contains(it), "Unexpected sample value: $it") }
        } finally { scene.close(); Dispatchers.resetMain() }
    }
}
