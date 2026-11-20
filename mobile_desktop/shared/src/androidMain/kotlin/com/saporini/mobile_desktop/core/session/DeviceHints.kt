package com.saporini.mobile_desktop.core.session

import android.content.Context

actual object DeviceHints {
    private val prefs by lazy { TokenPersistence.appContext.getSharedPreferences("saporini_hints", Context.MODE_PRIVATE) }

    actual fun isHidden(key: String): Boolean = prefs.getBoolean(key, false)

    actual fun hide(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }
}
