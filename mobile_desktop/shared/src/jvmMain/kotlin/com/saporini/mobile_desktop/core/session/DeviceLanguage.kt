package com.saporini.mobile_desktop.core.session

import java.io.File

actual object DeviceLanguage {
    private val file = File(File(System.getProperty("user.home"), ".saporini").apply { mkdirs() }, "language.txt")

    actual fun load(): String? = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.matches(Regex("[a-z]{2}")) }

    actual fun save(code: String) {
        runCatching { file.writeText(code) }
    }
}
