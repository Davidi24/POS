package com.saporini.mobile_desktop.core.files

/** Whether this device can print a table (the computer can; the phone not yet). */
expect val canPrintTables: Boolean

/**
 * Prints a table with the system's print window (the person picks the printer there). Answers false when it couldn't be
 * printed (no printer, cancelled, or not possible on this device).
 */
expect suspend fun printTable(title: String, header: List<String>, rows: List<List<String>>): Boolean
