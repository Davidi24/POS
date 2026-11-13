package com.saporini.mobile_desktop.core.network

// The computer running the local backend, on the same Wi-Fi as the phone, so the app doesn't depend on `adb reverse`, which
// dies whenever Wireless debugging goes off (user, 2026-11-02). If the computer's address changes, change it here.
actual fun platformBaseUrl(): String = "http://192.168.178.22:8080"

actual fun platformFallbackBaseUrls(): List<String> = listOf("http://127.0.0.1:8080")

private fun configuredBaseUrl(): String? {
    return System.getProperty("saporini.api.baseUrl")
        ?: System.getenv("SAPORINI_API_BASE_URL")
}