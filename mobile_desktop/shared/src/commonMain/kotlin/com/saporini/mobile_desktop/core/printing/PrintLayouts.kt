package com.saporini.mobile_desktop.core.printing

import com.saporini.mobile_desktop.core.format.centsOrNull
import com.saporini.mobile_desktop.core.format.dateTimeText
import com.saporini.mobile_desktop.core.format.moneyText
import com.saporini.mobile_desktop.core.format.timeText
import com.saporini.mobile_desktop.core.i18n.tr
import com.saporini.mobile_desktop.kds.model.KdsTicket
import com.saporini.mobile_desktop.kds.model.KdsTicketItem
import com.saporini.mobile_desktop.platform.BlockAlign
import com.saporini.mobile_desktop.platform.BlockKind
import com.saporini.mobile_desktop.platform.ReceiptBlock
import com.saporini.mobile_desktop.platform.ReceiptDesign
import com.saporini.mobile_desktop.platform.ReceiptKind
import com.saporini.mobile_desktop.platform.ReceiptLineStyle
import com.saporini.mobile_desktop.platform.TextSize
import com.saporini.mobile_desktop.pos.payment.data.ReceiptDto
import kotlinx.datetime.TimeZone

// What goes on the paper (user, 2026-11-27): a test page, kitchen tickets (big and plain: where it goes, the dishes, what's
// special about them) and the two receipts as the Receipt designer lays them out.

/** A test page: the printer's name, its paper and letters in every size, then the cut. */
fun testPage(printerName: String, paperWidthMm: Int, restaurant: String?, autoCut: Boolean): ByteArray {
    val p = EscPos(paperWidthMm)
    p.align(EscPos.Align.CENTER).size(2, 2).bold(true).line(tr("Test page")).size().bold(false)
    p.line(printerName)
    restaurant?.let { p.line(it) }
    p.rule()
    p.align(EscPos.Align.LEFT).line(tr("Paper: $paperWidthMm mm, ${p.columns} letters a line"))
    p.line("ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789")
    p.line("à è é ì ò ù ç ë ñ € ¿ ¡")
    p.bold(true).line(tr("Bold")).bold(false)
    p.size(1, 2).line(tr("Tall")).size(2, 2).line(tr("Big")).size()
    p.rule()
    p.align(EscPos.Align.CENTER).line(tr("If you can read this, it works."))
    if (autoCut) p.cut() else p.feed(4)
    return p.bytes()
}

/**
 * A kitchen ticket for [items] of [ticket]: the station big, where it goes (table and guests, or takeaway and the name), the
 * order and the time, then each dish big with its choices and notes, the ticket's note and Rush / VIP.
 */
fun kitchenTicket(ticket: KdsTicket, items: List<KdsTicketItem>, paperWidthMm: Int, autoCut: Boolean, zone: TimeZone,
                  // The station's look (decided 2026-11-28): the dishes' letter size, and notes in red on two-colour printers.
                  textSize: String = "NORMAL", notesRed: Boolean = false): ByteArray {
    fun dishSize(p: EscPos) = when (textSize) {
        "HUGE" -> p.size(2, 3)
        "BIG" -> p.size(2, 2)
        else -> p.size(1, 2)
    }
    val p = EscPos(paperWidthMm)
    p.align(EscPos.Align.CENTER).size(2, 2).bold(true).line((ticket.stationName ?: tr("Kitchen")).uppercase()).size().bold(false)
    when {
        ticket.priority == "RUSH" -> p.bold(true).size(2, 1).line(tr("RUSH")).size().bold(false)
        ticket.priority == "VIP" -> p.bold(true).size(2, 1).line("VIP").size().bold(false)
    }
    val where = when {
        ticket.orderType == "TAKEAWAY" -> listOfNotNull(tr("Takeaway"), ticket.customerName).joinToString(" · ")
        ticket.orderType == "DELIVERY" -> listOfNotNull(tr("Delivery"), ticket.customerName).joinToString(" · ")
        ticket.tableNumber != null -> listOfNotNull(tr("Table ${ticket.tableNumber}"),
            ticket.guestCount?.takeIf { it > 0 }?.let { tr("$it guests") }).joinToString(" · ")
        else -> ticket.customerName ?: ""
    }
    if (where.isNotBlank()) p.size(1, 2).bold(true).line(where).size().bold(false)
    p.line(listOfNotNull("#${ticket.orderNumber.ifBlank { ticket.ticketNumber }}", timeText(ticket.firedAt ?: ticket.createdAt, zone)).joinToString(" · "))
    p.rule('=')
    p.align(EscPos.Align.LEFT)
    items.forEach { item ->
        val name = listOfNotNull(item.itemNameSnapshot, item.variantNameSnapshot?.takeIf { it.isNotBlank() }).joinToString(" · ")
        dishSize(p).bold(true).line("${item.quantity} x $name").size().bold(false)
        item.seatLabel?.takeIf { it.isNotBlank() }?.let { p.line("   " + tr("Seat $it")) }
        item.modifiers.forEach { modifier ->
            p.line("   + " + (if (modifier.quantity > 1) "${modifier.quantity} x " else "") + modifier.name)
            modifier.notes?.takeIf { it.isNotBlank() }?.let { p.line("     $it") }
        }
        item.notes?.takeIf { it.isNotBlank() }?.let { p.red(notesRed).bold(true).line("   ! $it").bold(false).red(false) }
    }
    ticket.notes?.takeIf { it.isNotBlank() }?.let { p.rule().red(notesRed).bold(true).line(it).bold(false).red(false) }
    ticket.occasion?.takeIf { it.isNotBlank() }?.let { p.rule().line(it) }
    if (autoCut) p.cut() else p.feed(4)
    return p.bytes()
}

/**
 * A receipt as the Receipt designer lays it out ([design], the preliminary bill or the fiscal invoice), filled with the order's
 * [data]: every block in its order, size, alignment and boldness; the logo and pictures as dots ([pictures] gives them, loaded
 * before). The letters are the printer's own. DGII isn't connected yet: the fiscal numbers are test ones.
 */
fun receipt(design: ReceiptDesign, kind: ReceiptKind, data: ReceiptDto, paperWidthMm: Int, autoCut: Boolean, openDrawer: Boolean, zone: TimeZone,
            pictures: (url: String, width: Int, opacity: Float) -> MonoImage? = { _, _, _ -> null }): ByteArray {
    // Pictures behind the words: the whole receipt is drawn as one picture (decided 2026-11-28); else the printer's own letters.
    val p: ReceiptSink = if (design.backdrops.isEmpty()) EscPos(paperWidthMm) else ReceiptPicture(paperWidthMm)
    val currency = data.currency
    fun money(value: com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal): String =
        value.centsOrNull()?.let { moneyText(it, currency) } ?: value.value
    design.blocks.filter { it.visible }.forEach { block ->
        p.align(block.align.esc()).bold(block.bold)
        block.size.apply(p)
        when (block.kind) {
            BlockKind.TEXT -> block.text.takeIf { it.isNotBlank() }?.let { p.line(it) }
            BlockKind.LINE -> { p.size(); p.rule(block.line.char()) }
            BlockKind.SPACE -> p.feed((block.space / 14).coerceIn(1, 6))
            // As wide as the designer says, in its place (user, 2026-11-27: "the receipt prints exactly as I configure it").
            BlockKind.PICTURE, BlockKind.LOGO -> {
                val url = if (block.kind == BlockKind.LOGO) data.logoUrl else block.imageUrl
                val width = (p.dots * block.width.coerceIn(0.05f, 1f)).toInt()
                url?.let { pictures(it, width, block.opacity) }?.let { p.size(); p.image(it); p.line() }
            }
            BlockKind.BARCODE -> { p.size(); p.align(EscPos.Align.CENTER).barcode(if (block.ownCode) block.code else data.orderNumber.orEmpty()) }
            BlockKind.QR -> block.link.takeIf { it.isNotBlank() }?.let {
                p.size(); p.align(EscPos.Align.CENTER).qr(it, qrModule(block.width, paperWidthMm))
                block.text.takeIf { t -> t.isNotBlank() }?.let { t -> p.line(t) }
            }
            BlockKind.RESTAURANT -> {
                val lines = block.labels.filter { it.isNotBlank() }.ifEmpty {
                    listOfNotNull(data.restaurantName, data.branchName?.takeIf { it != data.restaurantName }) + data.addressLines +
                        listOfNotNull(data.phone, data.vatNumber?.let { "VAT $it" } ?: data.taxNumber?.let { "RNC $it" })
                }
                lines.forEachIndexed { index, line ->
                    if (index == 0) p.size(2, 2).bold(true).line(line).size().bold(block.bold) else p.line(line)
                }
            }
            BlockKind.TOP_TEXT -> block.text.takeIf { it.isNotBlank() }?.let { p.line(it) }
            BlockKind.BOTTOM_TEXT -> (block.text.ifBlank { data.footerNote.orEmpty() }).takeIf { it.isNotBlank() }?.let { p.line(it) }
            BlockKind.ORDER -> {
                val labels = block.labels
                fun label(index: Int, fallback: String) = labels.getOrNull(index)?.takeIf { it.isNotBlank() } ?: tr(fallback)
                p.line(listOfNotNull("${label(0, "Receipt")} #${data.orderNumber.orEmpty()}", dateTimeText(data.closedAt ?: data.printedAt ?: data.openedAt, zone))
                    .joinToString(" · "))
                val table = listOfNotNull(data.tableNumber?.let { "${label(1, "Table")} $it" },
                    data.guestCount?.takeIf { it > 0 }?.let { "$it ${label(2, "guests")}" }).joinToString(" · ")
                if (table.isNotBlank()) p.line(table)
                data.serverName?.let { p.line("${label(3, "Waiter")} $it") }
            }
            BlockKind.ITEMS -> {
                p.align(EscPos.Align.LEFT)
                block.text.takeIf { it.isNotBlank() }?.let { p.bold(true).line(it).bold(false) }
                data.lines.filterNot { it.removed }.forEach { line ->
                    val name = "${line.quantity} x " + listOfNotNull(line.name, line.variant?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    if (block.prices) p.columns(name, money(line.lineTotal)) else p.line(name)
                    if (block.options) line.options.forEach { p.line("   + $it") }
                }
            }
            BlockKind.SUBTOTAL -> {
                p.align(EscPos.Align.LEFT).columns(block.text.ifBlank { tr("Subtotal") }, money(data.subtotal))
                data.discounts.forEach { p.columns(it.name, "-" + money(it.amount)) }
                if ((data.serviceChargeTotal.centsOrNull() ?: 0) > 0) p.columns(tr("Service"), money(data.serviceChargeTotal))
            }
            BlockKind.VAT -> data.tax?.let { tax ->
                val rate = tax.rate.value.trimEnd('0').trimEnd('.')
                val label = block.text.ifBlank { tr("VAT") } + " $rate%" + if (tax.inclusive) " (" + tr("included") + ")" else ""
                p.align(EscPos.Align.LEFT).columns(label, money(tax.amount))
            }
            BlockKind.TOTAL -> p.align(EscPos.Align.LEFT).columns(block.text.ifBlank { tr("TOTAL") }, money(data.total))
            BlockKind.PAYMENT -> {
                p.align(EscPos.Align.LEFT)
                data.payments.filter { it.status != "VOIDED" && it.status != "CANCELLED" }.forEach { payment ->
                    val method = tr(payment.method.lowercase().replaceFirstChar { it.uppercase() }) +
                        (payment.cardLast4?.let { " ****$it" } ?: "")
                    p.columns(method, money(payment.tenderedAmount ?: payment.amount))
                    payment.changeAmount?.takeIf { (it.centsOrNull() ?: 0) > 0 }?.let { p.columns(block.text.ifBlank { tr("Change") }, money(it)) }
                }
                if ((data.tipTotal.centsOrNull() ?: 0) > 0) p.columns(tr("Tip"), money(data.tipTotal))
            }
            BlockKind.THANKS -> block.text.takeIf { it.isNotBlank() }?.let { p.line(it) }
            BlockKind.PRELIM_NOTICE -> p.line(block.text.ifBlank { "PRECUENTA - NO VALIDA PARA FINES FISCALES" })
            BlockKind.FISCAL -> {
                p.line(if (kind == ReceiptKind.FISCAL) "FACTURA DE CONSUMO ELECTRONICA" else "")
                p.line("e-NCF: E320000000001 (TEST)")
                data.taxNumber?.let { p.line("RNC: $it") }
                p.line(dateTimeText(data.closedAt ?: data.printedAt, zone))
            }
            BlockKind.DGII_QR -> {
                p.size(); p.align(EscPos.Align.CENTER)
                p.qr("https://ecf.dgii.gov.do/testecf/consultatimbre?RncEmisor=${data.taxNumber.orEmpty()}&ENCF=E320000000001", qrModule(block.width, paperWidthMm))
                p.line("DGII (TEST)")
            }
        }
        p.size().bold(false)
    }
    val out = when (p) {
        is EscPos -> p
        is ReceiptPicture -> EscPos(paperWidthMm).align(EscPos.Align.CENTER).image(p.render(design.backdrops, pictures))
        else -> EscPos(paperWidthMm)
    }
    if (openDrawer) out.openDrawer()
    if (autoCut) out.cut() else out.feed(4)
    return out.bytes()
}

private fun BlockAlign.esc(): EscPos.Align = when (this) {
    BlockAlign.LEFT -> EscPos.Align.LEFT
    BlockAlign.CENTER -> EscPos.Align.CENTER
    BlockAlign.RIGHT -> EscPos.Align.RIGHT
}

private fun TextSize.apply(p: ReceiptSink) {
    when (this) {
        TextSize.SMALL, TextSize.NORMAL -> p.size(1, 1)
        TextSize.LARGE -> p.size(1, 2)
        TextSize.HUGE -> p.size(2, 2)
    }
}

private fun ReceiptLineStyle.char(): Char = when (this) {
    ReceiptLineStyle.DASHED -> '-'
    ReceiptLineStyle.DOTTED -> '.'
    ReceiptLineStyle.SOLID -> '_'
    ReceiptLineStyle.DOUBLE, ReceiptLineStyle.EQUALS -> '='
    ReceiptLineStyle.STARS -> '*'
}

/** A QR code's dot size so it takes about [share] of the paper. */
private fun qrModule(share: Float, paperWidthMm: Int): Int {
    val dots = if (paperWidthMm in 1..60) 384 else 576
    return ((dots * share.coerceIn(0.2f, 0.9f)) / 33).toInt().coerceIn(3, 12)
}
