package com.saporini.mobile_desktop.core.files

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher

@Composable
actual fun rememberFolderPicker(onPicked: (PlatformFile?) -> Unit): () -> Unit {
    val launcher = rememberDirectoryPickerLauncher(null, FileKitDialogSettings.createDefault(), onPicked)
    return { launcher.launch() }
}

actual fun openInSystem(path: String): Boolean = runCatching {
    val file = java.io.File(path)
    // Off the UI thread: the desktop's opener can take a moment to answer.
    Thread { runCatching { java.awt.Desktop.getDesktop().open(file) } }.apply { isDaemon = true }.start()
    true
}.getOrDefault(false)
