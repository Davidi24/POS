package com.saporini.mobile_desktop.core.session

/**
 * This device's own id, made once and kept on the device (user, 2026-11-17): sign-in sends it, so the server can pause only
 * the device that typed too many wrong passwords and not the other tills and phones on the same Wi-Fi.
 */
expect object DeviceIdentity {
    val id: String

    /** What this device is called in the person's list of signed-in devices ("Samsung SM-G998U", "Linux computer (kitchen-pc)"). */
    val name: String
}
