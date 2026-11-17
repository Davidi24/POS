package com.saporini.mobile_desktop.core.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.MessageFormat
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

actual val canPrintTables: Boolean = true

/** The computer prints through Swing's table printing: the system print window, the title on top, page numbers below. */
actual suspend fun printTable(title: String, header: List<String>, rows: List<List<String>>): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val model = DefaultTableModel(rows.map { it.toTypedArray<Any>() }.toTypedArray(), header.toTypedArray<Any>())
        val table = JTable(model).apply {
            rowHeight = 22
            // A table that's never on screen needs a size before it can be printed.
            setSize(preferredSize)
            tableHeader.setSize(tableHeader.preferredSize)
            doLayout()
        }
        table.print(JTable.PrintMode.FIT_WIDTH, MessageFormat(title.replace("'", "''")), MessageFormat("Page {0}"))
    }.getOrDefault(false)
}
