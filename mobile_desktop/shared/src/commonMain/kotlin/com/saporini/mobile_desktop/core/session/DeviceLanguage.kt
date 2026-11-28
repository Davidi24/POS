package com.saporini.mobile_desktop.core.session

/** The language the app was last used in on this device (en, it, es, sq), for the sign-in screens before anyone signs in. */
expect object DeviceLanguage {
    fun load(): String?
    fun save(code: String)
}
