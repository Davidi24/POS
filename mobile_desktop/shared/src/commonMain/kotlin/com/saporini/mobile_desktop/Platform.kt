package com.saporini.mobile_desktop

interface Platform {
    val name: String
    // What the device runs, for Admin Hub → Devices (2026-11-25): "Linux", "Windows 11", "Android".
    val system: String get() = name
    val systemVersion: String? get() = null
}

expect fun getPlatform(): Platform