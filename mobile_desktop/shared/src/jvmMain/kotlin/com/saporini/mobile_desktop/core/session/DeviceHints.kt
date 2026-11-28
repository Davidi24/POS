package com.saporini.mobile_desktop.core.session

import java.io.File

actual object DeviceHints {
    private val file = File(File(System.getProperty("user.home"), ".saporini").apply { mkdirs() }, "hidden-hints.txt")
    private val hidden: MutableSet<String> by lazy {
        runCatching { file.readLines().map(String::trim).filter(String::isNotEmpty) }.getOrDefault(emptyList()).toMutableSet()
    }

    actual fun isHidden(key: String): Boolean = key in hidden

    actual fun hide(key: String) {
        if (hidden.add(key)) runCatching { file.writeText(hidden.joinToString("\n")) }
    }
}
