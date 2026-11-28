package com.saporini.mobile_desktop.core.printing

import androidx.compose.runtime.Composable

/**
 * Whether this device may use Bluetooth (find, pair and print), a way to ask (a phone or tablet asks the person once), and a
 * way to have it switched on there (the phone's own question).
 */
class BluetoothAccess(val allowed: Boolean, val ask: () -> Unit, val turnOn: () -> Unit = {})

@Composable
expect fun rememberBluetoothAccess(): BluetoothAccess
