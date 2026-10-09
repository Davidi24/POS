package com.saporini.mobile_desktop.core.network

actual fun platformBaseUrl(): String = "http://127.0.0.1:8080"

actual fun platformFallbackBaseUrls(): List<String> = listOf("http://127.0.0.1:8080")

private fun configuredBaseUrl(): String? {
    return System.getProperty("saporini.api.baseUrl")
        ?: System.getenv("SAPORINI_API_BASE_URL")
}