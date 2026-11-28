package com.saporini.mobile_desktop.core.printing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// Using Bluetooth needs "Nearby devices" on Android 12 and later (find and connect), the location on older ones (to find); a
// phone with Bluetooth off is asked to switch it on.
@Composable
actual fun rememberBluetoothAccess(): BluetoothAccess {
    val context = LocalContext.current
    val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun granted() = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var allowed by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = granted() }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return BluetoothAccess(allowed, { launcher.launch(needed) }) {
        runCatching { enable.launch(android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
    }
}
