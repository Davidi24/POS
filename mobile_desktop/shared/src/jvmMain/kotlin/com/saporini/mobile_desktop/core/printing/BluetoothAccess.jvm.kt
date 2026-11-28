package com.saporini.mobile_desktop.core.printing

import androidx.compose.runtime.Composable

// A computer needs no permission from the app; switching Bluetooth on is done by PrinterLink.turnOnBluetooth.
@Composable
actual fun rememberBluetoothAccess(): BluetoothAccess = BluetoothAccess(allowed = true, ask = {})
