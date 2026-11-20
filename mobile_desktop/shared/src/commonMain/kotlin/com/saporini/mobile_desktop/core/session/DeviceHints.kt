package com.saporini.mobile_desktop.core.session

/** The app's tips that someone chose "Don't show again" for, kept on this device. */
expect object DeviceHints {
    fun isHidden(key: String): Boolean
    fun hide(key: String)
}
