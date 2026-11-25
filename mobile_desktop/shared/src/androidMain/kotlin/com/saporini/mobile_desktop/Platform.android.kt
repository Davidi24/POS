package com.saporini.mobile_desktop

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val system: String = "Android"
    override val systemVersion: String? = Build.VERSION.RELEASE
}

actual fun getPlatform(): Platform = AndroidPlatform()