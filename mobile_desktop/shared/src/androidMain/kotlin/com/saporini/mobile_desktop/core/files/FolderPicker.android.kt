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

/** The phone keeps the file in the folder it was saved to; opening it from here comes later. */
actual fun openInSystem(path: String): Boolean = false
