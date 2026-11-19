package com.saporini.mobile_desktop.core.session

import android.content.Context

actual object DeviceCurrency {
    private val prefs by lazy { TokenPersistence.appContext.getSharedPreferences("saporini_device", Context.MODE_PRIVATE) }

    actual fun load(): String? = runCatching { prefs.getString("shown_currency", null) }.getOrNull()?.takeIf { it.matches(Regex("[A-Z]{3}")) }

    actual fun save(code: String?) {
        prefs.edit().apply { if (code == null) remove("shown_currency") else putString("shown_currency", code) }.apply()
    }
}
