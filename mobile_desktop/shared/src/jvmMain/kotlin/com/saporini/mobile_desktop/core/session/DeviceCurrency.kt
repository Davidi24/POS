package com.saporini.mobile_desktop.core.session

import java.io.File

actual object DeviceCurrency {
    private val file = File(File(System.getProperty("user.home"), ".saporini").apply { mkdirs() }, "shown-currency.txt")

    actual fun load(): String? = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.matches(Regex("[A-Z]{3}")) }

    actual fun save(code: String?) {
        runCatching { if (code == null) file.delete() else file.writeText(code) }
    }
}
