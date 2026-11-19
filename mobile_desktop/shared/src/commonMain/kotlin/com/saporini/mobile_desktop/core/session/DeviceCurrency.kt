package com.saporini.mobile_desktop.core.session

/** The currency this device also shows amounts in ("USD"), or null for amounts only as the restaurant charges them. */
expect object DeviceCurrency {
    fun load(): String?
    fun save(code: String?)
}
