package com.saporini.mobile_desktop.core.session

import java.io.File
import java.util.UUID

actual object DeviceIdentity {
    private val file = File(File(System.getProperty("user.home"), ".saporini").apply { mkdirs() }, "device-id.txt")

    actual val id: String by lazy {
        runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,100}")) }
            ?: UUID.randomUUID().toString().also { runCatching { file.writeText(it) } }
    }

    actual val name: String by lazy {
        val os = System.getProperty("os.name").orEmpty().let {
            when {
                it.startsWith("Windows", ignoreCase = true) -> "Windows"
                it.startsWith("Mac", ignoreCase = true) -> "Mac"
                it.isBlank() -> "Desktop"
                else -> it
            }
        }
        val host = (System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME")
            ?: runCatching { java.io.File("/etc/hostname").readText().trim() }.getOrNull())?.takeIf { it.isNotBlank() }
        // Plain letters only: a header can't carry "·" or accents.
        (if (host != null) "$os computer ($host)" else "$os computer").take(100)
    }
}
