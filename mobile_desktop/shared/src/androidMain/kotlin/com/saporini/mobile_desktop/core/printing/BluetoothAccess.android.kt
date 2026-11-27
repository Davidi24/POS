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

// Android 12 and later need "Nearby devices" (BLUETOOTH_CONNECT) to list paired devices and talk to them.
@Composable
actual fun rememberBluetoothAccess(): BluetoothAccess {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return BluetoothAccess(true) {}
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }
    return BluetoothAccess(allowed) { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
}
