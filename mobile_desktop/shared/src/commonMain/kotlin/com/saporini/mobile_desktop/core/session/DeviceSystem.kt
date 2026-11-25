package com.saporini.mobile_desktop.core.session

import com.saporini.mobile_desktop.getPlatform

/**
 * What this device runs, sent with each sign-in for the lists of devices (user, 2026-11-25: "show the operating system and the
 * location of the devices so I know who it is"): "Linux 6.17", "Windows 11", "macOS 14.5", "Android 14". Plain ASCII, for a
 * header.
 */
fun deviceSystem(): String {
    val platform = getPlatform()
    val system = platform.system.trim()
    val version = platform.systemVersion?.trim().orEmpty()
    val text = when {
        system.startsWith("Windows", ignoreCase = true) -> system
        system.startsWith("Mac", ignoreCase = true) -> "macOS $version"
        system.equals("Linux", ignoreCase = true) -> "Linux " + version.split('.').take(2).joinToString(".")
        else -> "$system $version"
    }
    return text.filter { it.code in 32..126 }.trim().take(80)
}

/**
 * A device's second line in the lists: what it runs and where it is ("Linux 6.17 · Tirana, Albania"), "This network" when it's
 * on the network of the device you're on; null when nothing is known.
 */
fun deviceWhere(system: String?, place: String?, sameNetwork: Boolean): String? =
    listOfNotNull(system?.takeIf { it.isNotBlank() }, if (sameNetwork) "This network" else place?.takeIf { it.isNotBlank() })
        .joinToString(" · ").ifBlank { null }
