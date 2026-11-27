package com.saporini.mobile_desktop.core.printing

import androidx.compose.runtime.Composable

/** Whether this device may see and print on its paired Bluetooth printers, and a way to ask (Android 12 and later ask once). */
class BluetoothAccess(val allowed: Boolean, val ask: () -> Unit)

@Composable
expect fun rememberBluetoothAccess(): BluetoothAccess
