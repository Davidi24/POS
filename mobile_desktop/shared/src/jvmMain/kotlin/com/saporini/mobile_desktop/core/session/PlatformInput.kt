package com.saporini.mobile_desktop.core.session

import java.awt.AWTEvent
import java.awt.Toolkit

private var watching = false

actual fun watchPlatformInput(onInput: () -> Unit) {
    if (watching) return
    watching = true
    // Every window of the app, its dialogs and menus too.
    Toolkit.getDefaultToolkit().addAWTEventListener({ onInput() },
        AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK or AWTEvent.KEY_EVENT_MASK)
}
