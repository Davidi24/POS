package com.saporini.mobile_desktop.core.files

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.saporini.mobile_desktop.core.session.TokenPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun saveTextFile(fileName: String, mimeType: String, text: String): String = withContext(Dispatchers.IO) {
    val context = TokenPersistence.appContext
    val clean = safeFileName(fileName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // The shared Downloads folder needs no permission through MediaStore.
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, clean)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create the file")
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Could not write the file")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Downloads/$clean"
    } else {
        // Older Android: the app's own Downloads folder (no storage permission needed).
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(folder, clean)
        file.writeText(text, Charsets.UTF_8)
        file.absolutePath
    }
}
