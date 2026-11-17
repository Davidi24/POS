package com.saporini.mobile_desktop.core.files

/** Printing from the phone comes later; the preview there offers Download only. */
actual val canPrintTables: Boolean = false

actual suspend fun printTable(title: String, header: List<String>, rows: List<List<String>>): Boolean = false
