package com.saporini.mobile_desktop.core.files

/**
 * Saves a text file the person asked for (a CSV report) where they can find it: the Downloads folder on desktop
 * and Android. Returns where it went, for the screen to say so.
 */
expect suspend fun saveTextFile(fileName: String, mimeType: String, text: String): String

/** Keeps a file name to letters, digits, dots, dashes and underscores. */
internal fun safeFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-', '.').take(120).ifEmpty { "export" }

/**
 * One CSV line. Cells with commas, quotes or line breaks are quoted, and cells starting like a formula get a leading
 * apostrophe so a spreadsheet shows them as text instead of running them.
 */
internal fun csvRow(cells: List<String>): String = cells.joinToString(",") { raw ->
    val cell = if (raw.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r') && raw.toDoubleOrNull() == null) "'$raw" else raw
    if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${cell.replace("\"", "\"\"")}\"" else cell
}
