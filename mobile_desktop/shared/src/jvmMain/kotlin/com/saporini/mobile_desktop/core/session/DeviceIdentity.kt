package com.saporini.mobile_desktop.core.session

import java.io.File
import java.util.UUID

actual object DeviceIdentity {
    private val file = File(File(System.getProperty("user.home"), ".saporini").apply { mkdirs() }, "device-id.txt")

    actual val id: String by lazy {
        runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,100}")) }
            ?: UUID.randomUUID().toString().also { runCatching { file.writeText(it) } }
    }
}
