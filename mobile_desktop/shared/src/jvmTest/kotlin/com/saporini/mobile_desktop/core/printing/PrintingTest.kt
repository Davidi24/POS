package com.saporini.mobile_desktop.core.printing

import com.saporini.mobile_desktop.kds.model.KdsModifier
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.KdsTicketItem
import com.saporini.mobile_desktop.platform.ReceiptKind
import com.saporini.mobile_desktop.platform.ReceiptLook
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.payment.data.ReceiptDto
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What goes to the printers (2026-11-27): the bytes of tickets and receipts, and sending them over the network. */
class PrintingTest {

    /** The printable text of ESC/POS bytes (commands left out), as Windows-1252. */
    private fun textOf(bytes: ByteArray): String = String(bytes, charset("windows-1252"))

    @Test
    fun aKitchenTicketSaysWhereItGoesAndEveryDish() {
        val ticket = KdsTicket(id = "t1", restaurantId = "r", branchId = "b", stationId = "s", orderId = "o", orderNumber = "1041",
            stationName = "Grill", tableNumber = "T05", guestCount = 4, orderType = "DINE_IN", priority = "RUSH", firedAt = "2026-11-27T20:40:00Z",
            notes = "Birthday table")
        val items = listOf(
            KdsTicketItem(id = "i1", itemNameSnapshot = "Burger", quantity = 2, variantNameSnapshot = "Double",
                modifiers = listOf(KdsModifier("Medium"), KdsModifier("No onions")), notes = "Allergy: nuts"),
            KdsTicketItem(id = "i2", itemNameSnapshot = "Fries", quantity = 1)
        )
        val text = textOf(kitchenTicket(ticket, items, 80, autoCut = true, zone = TimeZone.of("Europe/Rome")))
        assertContains(text, "GRILL")
        assertContains(text, "RUSH")
        assertContains(text, "T05")
        assertContains(text, "4 guests")
        assertContains(text, "#1041")
        assertContains(text, "21:40")
        assertContains(text, "2 x Burger · Double")
        assertContains(text, "+ Medium")
        assertContains(text, "+ No onions")
        assertContains(text, "! Allergy: nuts")
        assertContains(text, "1 x Fries")
        assertContains(text, "Birthday table")
        // Ends with the cut.
        val bytes = kitchenTicket(ticket, items, 80, autoCut = true, zone = TimeZone.UTC)
        assertEquals(listOf(0x1D, 0x56, 66, 0), bytes.takeLast(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun aReceiptFollowsTheDesign() {
        val data = ReceiptDto(restaurantName = "Bistroo", addressLines = listOf("Rruga e Durrësit 5, Tirana"), phone = "+355 69 000 0000",
            taxNumber = "L12345678A", orderNumber = "1041", tableNumber = "T05", guestCount = 2, serverName = "Ana",
            closedAt = "2026-11-27T20:40:00Z", currency = "EUR",
            lines = listOf(ReceiptDto.Line(name = "Pizza", quantity = 2, unitPrice = OrderDecimal("9.00"), options = listOf("Extra cheese"),
                lineTotal = OrderDecimal("18.00")), ReceiptDto.Line(name = "Old line", quantity = 1, unitPrice = OrderDecimal("5.00"),
                lineTotal = OrderDecimal("5.00"), removed = true)),
            subtotal = OrderDecimal("18.00"), tax = ReceiptDto.Tax(OrderDecimal("20.00"), true, OrderDecimal("3.00")), total = OrderDecimal("18.00"),
            payments = listOf(ReceiptDto.Payment(method = "CASH", amount = OrderDecimal("18.00"), tenderedAmount = OrderDecimal("20.00"),
                changeAmount = OrderDecimal("2.00"), status = "COMPLETED")))
        val look = ReceiptLook()
        val fiscal = textOf(receipt(look.of(ReceiptKind.FISCAL), ReceiptKind.FISCAL, data, 80, autoCut = true, openDrawer = true, zone = TimeZone.UTC))
        assertContains(fiscal, "Bistroo")
        assertContains(fiscal, "Rruga e Durrësit 5, Tirana")
        assertContains(fiscal, "#1041")
        assertContains(fiscal, "T05")
        assertContains(fiscal, "2 x Pizza")
        assertContains(fiscal, "€18.00")
        assertTrue("Old line" !in fiscal, "removed dishes don't print")
        assertContains(fiscal, "Cash")
        assertContains(fiscal, "€20.00")
        assertContains(fiscal, "€2.00")
        assertContains(fiscal, "e-NCF")
        val preliminary = textOf(receipt(look.of(ReceiptKind.PRELIMINARY), ReceiptKind.PRELIMINARY, data, 58, autoCut = false, openDrawer = false, zone = TimeZone.UTC))
        assertContains(preliminary, "PRECUENTA")
        assertTrue("e-NCF" !in preliminary, "no fiscal number on a preliminary bill")
        // The drawer opens with the fiscal receipt when asked.
        val bytes = receipt(look.of(ReceiptKind.FISCAL), ReceiptKind.FISCAL, data, 80, autoCut = true, openDrawer = true, zone = TimeZone.UTC)
        assertTrue(bytes.toList().windowed(3).any { it.map { b -> b.toInt() and 0xFF } == listOf(0x1B, 0x70, 0) })
    }

    @Test
    fun theLogoAndPicturesPrintAsDots() {
        // A black square on a see-through background: black stays black, see-through is white paper.
        val size = 40
        val argb = IntArray(size * size) { i -> val x = i % size; val y = i / size; if (x in 10..29 && y in 10..29) 0xFF000000.toInt() else 0x00000000 }
        val image = MonoImage.of(argb, size, size, 80)
        assertEquals(80, image.width)
        assertEquals(80, image.height)
        assertTrue(image.black(40, 40))
        assertTrue(!image.black(2, 2))
        // A mid grey turns into about half black dots (error diffusion keeps the shade).
        val grey = MonoImage.of(IntArray(100 * 100) { 0xFF808080.toInt() }, 100, 100, 100)
        val dark = (0 until 100).sumOf { y -> (0 until 100).count { x -> grey.black(x, y) } }
        assertTrue(dark in 4000..6000, "about half: $dark")
        // The raster command, with the width in bytes and the rows.
        val bytes = EscPos(80).image(image).bytes().map { it.toInt() and 0xFF }
        val at = bytes.windowed(4).indexOfFirst { it == listOf(0x1D, 0x76, 0x30, 0) }
        assertTrue(at >= 0)
        assertEquals(listOf(10, 0, 80, 0), bytes.subList(at + 4, at + 8))
        // A receipt's logo block prints the picture it's given, as wide as the designer says.
        val data = ReceiptDto(restaurantName = "Bistroo", logoUrl = "/logo.png", orderNumber = "1", currency = "EUR")
        val look = ReceiptLook()
        var asked = 0
        val receiptBytes = receipt(look.of(ReceiptKind.PRELIMINARY), ReceiptKind.PRELIMINARY, data, 80, autoCut = true, openDrawer = false,
            zone = TimeZone.UTC) { url, width, _ -> asked = width; assertEquals("/logo.png", url); image }
        assertTrue(asked in 100..200, "the logo is 22% of 576 dots: $asked")
        assertTrue(receiptBytes.toList().windowed(3).any { it.map { b -> b.toInt() and 0xFF } == listOf(0x1D, 0x76, 0x30) })
    }

    @Test
    fun linesFitThePaper() {
        val p58 = EscPos(58)
        val p80 = EscPos(80)
        assertEquals(32, p58.columns)
        assertEquals(48, p80.columns)
        assertTrue(EscPos.wrap("A long dish name that keeps going past the edge of the paper", 20).all { it.length <= 20 })
        assertEquals(0x80.toByte(), EscPos.encode("€")[0])
        assertEquals(0xEB.toByte(), EscPos.encode("ë")[0])
    }

    @Test
    fun aTillFindsWhichPrintersAnswer() = runBlocking {
        ServerSocket(0).use { server ->
            val answering = PrinterWatch.ToCheck("p1", "Kitchen printer", "KITCHEN", "NETWORK", "127.0.0.1", server.localPort)
            val found = PrinterWatch.check(answering)
            assertTrue(found.reachable)
            assertEquals(null, found.problem)
        }
        // A port nobody listens on: no answer, and it says where it looked.
        val closed = ServerSocket(0).use { it.localPort }
        val silent = PrinterWatch.check(PrinterWatch.ToCheck("p2", "Bar printer", "BAR", "NETWORK", "127.0.0.1", closed))
        assertTrue(!silent.reachable)
        assertEquals("No answer at 127.0.0.1", silent.problem)
        // A printer installed on a computer that isn't here.
        val missing = PrinterWatch.check(PrinterWatch.ToCheck("p3", "USB printer", "RECEIPT", "USB", systemPrinterName = "No such printer 42"))
        assertTrue(!missing.reachable)
        assertEquals("No such printer 42 isn't on this computer any more.", missing.problem)
    }

    @Test
    fun aNetworkPrinterGetsTheBytes() = runBlocking {
        ServerSocket(0).use { server ->
            var received = ByteArray(0)
            val reader = thread { server.accept().use { received = it.getInputStream().readBytes() } }
            val bytes = testPage("Front printer", 80, "Bistroo", autoCut = true)
            PrinterLink.sendNetwork("127.0.0.1", server.localPort, bytes)
            reader.join(5_000)
            assertTrue(received.contentEquals(bytes))
            assertContains(textOf(received), "Front printer")
        }
    }
}
