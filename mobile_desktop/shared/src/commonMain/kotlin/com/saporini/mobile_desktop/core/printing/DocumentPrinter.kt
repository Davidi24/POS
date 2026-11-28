package com.saporini.mobile_desktop.core.printing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.files.DocPage
import com.saporini.mobile_desktop.core.files.PrintOutcome
import com.saporini.mobile_desktop.core.files.pdfDocument
import com.saporini.mobile_desktop.core.files.printDocument
import com.saporini.mobile_desktop.core.i18n.Text
import com.saporini.mobile_desktop.core.theme.Inter
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CompletableDeferred

/** Where a document prints: one of this computer's printers, a document printer on the network, or the PDF viewer. */
sealed interface DocumentTarget {
    data class OnComputer(val name: String) : DocumentTarget
    data class OnNetwork(val printer: PrintPrinter) : DocumentTarget
    data object Viewer : DocumentTarget
}

/**
 * Printing a document (a report, a page of figures): nothing set up for it, you choose a printer each time (user, 2026-11-27:
 * "for documents no need to configure a specific one; when I click print I choose"): the restaurant's document printers (A4,
 * added in Devices as "Document printer") and this computer's own printers. The last one chosen is first. Null = cancelled.
 */
class DocumentPrinterPicker {
    internal var waiting by mutableStateOf<CompletableDeferred<DocumentTarget?>?>(null)
    internal var choices by mutableStateOf<List<Pair<DocumentTarget, String>>>(emptyList())

    suspend fun choose(): DocumentTarget? {
        val network = PrintHooks.printing?.setup()?.printers.orEmpty()
            .filter { it.role == "DOCUMENT" && it.usable && it.connectionType == "NETWORK" && !it.ip.isNullOrBlank() }
            .map { DocumentTarget.OnNetwork(it) to it.name }
        val computer = PrinterLink.systemPrinters().map { DocumentTarget.OnComputer(it) to it }
        val all = network + computer
        if (all.isEmpty()) return DocumentTarget.Viewer
        choices = all.sortedByDescending { it.second == last }
        val answer = CompletableDeferred<DocumentTarget?>()
        waiting = answer
        return answer.await().also { chosen -> all.firstOrNull { it.first == chosen }?.let { last = it.second } }
    }

    internal fun answer(target: DocumentTarget?) {
        waiting?.complete(target)
        waiting = null
    }

    private companion object {
        var last: String? = null
    }
}

/** A [DocumentPrinterPicker] with its window. */
@Composable
fun rememberDocumentPrinterPicker(): DocumentPrinterPicker {
    val picker = remember { DocumentPrinterPicker() }
    if (picker.waiting != null) {
        AppDialog(title = "Print on which printer?", onDismiss = { picker.answer(null) }, maxWidth = 460.dp,
            buttons = { KitButton("Cancel", { picker.answer(null) }, style = ButtonStyle.SECONDARY) }) {
            Text("The restaurant's document printers and this computer's printers.", fontFamily = Inter(), fontSize = 13.5.sp,
                letterSpacing = 0.sp, color = Kit.Muted)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                picker.choices.forEach { (target, name) ->
                    PrinterChoice(name, (target as? DocumentTarget.OnNetwork)?.printer?.ip ?: "On this computer") { picker.answer(target) }
                }
            }
        }
    }
    return picker
}

/** Prints [pages] on [target]. */
suspend fun printDocumentOn(target: DocumentTarget, title: String, pages: List<DocPage>): PrintOutcome = when (target) {
    is DocumentTarget.OnComputer -> printDocument(title, pages, target.name)
    is DocumentTarget.Viewer -> printDocument(title, pages, null)
    is DocumentTarget.OnNetwork -> if (printPdfOnNetwork(target.printer.ip.orEmpty(), title, pdfDocument(pages))) PrintOutcome.PRINTED else PrintOutcome.NOT_PRINTED
}

/**
 * A PDF to an office printer on the network: through IPP (port 631, what printers that print from phones speak), else
 * straight to port 9100, which many printers read PDFs from too.
 */
suspend fun printPdfOnNetwork(ip: String, title: String, pdf: ByteArray): Boolean {
    if (ip.isBlank()) return false
    val ipp = runCatching {
        HttpClient { install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 } }.use { client ->
            val answer = client.post("http://$ip:631/ipp/print") {
                contentType(ContentType("application", "ipp"))
                setBody(ippPrintJob("ipp://$ip:631/ipp/print", title, pdf))
            }.readRawBytes()
            // Status at bytes 2–3: below 0x0100 is "done".
            answer.size >= 4 && ((answer[2].toInt() and 0xFF) shl 8 or (answer[3].toInt() and 0xFF)) < 0x0100
        }
    }.getOrDefault(false)
    if (ipp) return true
    return runCatching { PrinterLink.sendNetwork(ip, 9100, pdf); true }.getOrDefault(false)
}

/** An IPP Print-Job request carrying [pdf]. */
private fun ippPrintJob(printerUri: String, title: String, pdf: ByteArray): ByteArray {
    val out = mutableListOf<Byte>()
    fun bytes(vararg values: Int) = values.forEach { out.add(it.toByte()) }
    fun short(value: Int) = bytes((value shr 8) and 0xFF, value and 0xFF)
    fun attribute(tag: Int, name: String, value: String) {
        val n = name.encodeToByteArray()
        val v = value.encodeToByteArray()
        bytes(tag); short(n.size); n.forEach { out.add(it) }; short(v.size); v.forEach { out.add(it) }
    }
    bytes(0x02, 0x00)          // IPP 2.0
    short(0x0002)              // Print-Job
    bytes(0, 0, 0, 1)          // request id
    bytes(0x01)                // operation attributes
    attribute(0x47, "attributes-charset", "utf-8")
    attribute(0x48, "attributes-natural-language", "en")
    attribute(0x45, "printer-uri", printerUri)
    attribute(0x42, "requesting-user-name", "POS")
    attribute(0x42, "job-name", title.take(200))
    attribute(0x49, "document-format", "application/pdf")
    bytes(0x03)                // end of attributes
    return out.toByteArray() + pdf
}

/** An A4 test page for a document printer. */
fun documentTestPage(printerName: String, restaurantName: String?): DocPage {
    val left = 56f
    val right = com.saporini.mobile_desktop.core.files.A4_WIDTH - 56f
    return DocPage(listOfNotNull(
        com.saporini.mobile_desktop.core.files.DocItem.Box(left, 60f, 44f, 44f, fill = androidx.compose.ui.graphics.Color(0xFF2E7D57), radius = 8f),
        com.saporini.mobile_desktop.core.files.DocItem.Text(left + 60f, 80f, "Test page", 24f, bold = true),
        restaurantName?.let { com.saporini.mobile_desktop.core.files.DocItem.Text(left + 60f, 100f, it, 12f, color = com.saporini.mobile_desktop.core.files.DocMuted) },
        com.saporini.mobile_desktop.core.files.DocItem.Line(left, 130f, right, 130f),
        com.saporini.mobile_desktop.core.files.DocItem.Text(left, 170f, printerName, 16f, bold = true),
        com.saporini.mobile_desktop.core.files.DocItem.Text(left, 196f, "If you can read this, the printer works.", 12f),
        com.saporini.mobile_desktop.core.files.DocItem.Text(left, 216f, "Reports and documents you print can go to it.", 12f,
            color = com.saporini.mobile_desktop.core.files.DocMuted)
    ))
}
