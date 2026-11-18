package com.saporini.mobile_desktop.core.session

/** Android: the app's activity reports touches itself (`onUserInteraction`), and dialogs report theirs from `AppDialog`. */
actual fun watchPlatformInput(onInput: () -> Unit) = Unit
