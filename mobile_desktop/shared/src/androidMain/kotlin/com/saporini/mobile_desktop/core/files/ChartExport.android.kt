package com.saporini.mobile_desktop.core.files

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

actual fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray =
    ByteArrayOutputStream().use { out -> image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }

actual fun encodePng(image: ImageBitmap): ByteArray =
    ByteArrayOutputStream().use { out -> image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }

/** Printing from the phone comes later. */
actual suspend fun printDocument(title: String, pages: List<DocPage>): PrintOutcome = PrintOutcome.NOT_PRINTED
