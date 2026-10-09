package com.saporini.mobile_desktop.core.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun saveTextFile(fileName: String, mimeType: String, text: String): String = withContext(Dispatchers.IO) {
    val home = File(System.getProperty("user.home"))
    val folder = File(home, "Downloads").takeIf { it.isDirectory || it.mkdirs() } ?: home
    val clean = safeFileName(fileName)
    val dot = clean.lastIndexOf('.')
    val base = if (dot > 0) clean.substring(0, dot) else clean
    val extension = if (dot > 0) clean.substring(dot) else ""
    // Never overwrite an earlier download: "report.csv", "report (2).csv", ...
    var target = File(folder, clean)
    var copy = 2
    while (target.exists()) target = File(folder, "$base ($copy)$extension").also { copy++ }
    target.writeText(text, Charsets.UTF_8)
    target.absolutePath
}
