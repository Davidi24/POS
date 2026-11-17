package com.saporini.mobile_desktop.core.session

import android.content.Context
import java.util.UUID

actual object DeviceIdentity {
    private val prefs by lazy { TokenPersistence.appContext.getSharedPreferences("saporini_device", Context.MODE_PRIVATE) }

    actual val id: String by lazy {
        prefs.getString("device_id", null)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,100}")) }
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    }
}
