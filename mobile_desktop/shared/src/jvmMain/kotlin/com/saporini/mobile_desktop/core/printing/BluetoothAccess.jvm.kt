package com.saporini.mobile_desktop.core.printing

import androidx.compose.runtime.Composable

// On a computer, paired Bluetooth printers are in its own printer list; nothing to ask.
@Composable
actual fun rememberBluetoothAccess(): BluetoothAccess = BluetoothAccess(true) {}
