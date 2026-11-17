package com.saporini.mobile_desktop.core.files

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** Opens the system's "choose a folder" window; [onPicked] gets the folder, or null when cancelled. Returns what opens it. */
@Composable
expect fun rememberFolderPicker(onPicked: (PlatformFile?) -> Unit): () -> Unit

/** Opens a file or folder with the computer's own app (a PDF in its viewer, a folder in the file manager). False when it couldn't. */
expect fun openInSystem(path: String): Boolean
