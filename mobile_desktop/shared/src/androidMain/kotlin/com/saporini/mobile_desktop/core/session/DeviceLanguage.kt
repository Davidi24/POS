package com.saporini.mobile_desktop.core.session

import android.content.Context

actual object DeviceLanguage {
    private val prefs by lazy { TokenPersistence.appContext.getSharedPreferences("saporini_device", Context.MODE_PRIVATE) }

    actual fun load(): String? = runCatching { prefs.getString("language", null) }.getOrNull()?.takeIf { it.matches(Regex("[a-z]{2}")) }

    actual fun save(code: String) {
        prefs.edit().putString("language", code).apply()
    }
}
