package com.saporini.mobile_desktop.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.openpnp.capture.OpenPnpCapture
import java.awt.image.BufferedImage
import kotlin.math.abs

// The desktop's webcam through openpnp-capture (its native part ships inside the library), read with ZXing.

@Composable
internal actual fun CameraBarcodeScanner(onCode: (String) -> Unit, onProblem: (String) -> Unit, modifier: Modifier) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val found by rememberUpdatedState(onCode)
    val failed by rememberUpdatedState(onProblem)
    LaunchedEffect(Unit) {
        val code = withContext(Dispatchers.IO) {
            val capture = runCatching { OpenPnpCapture() }.getOrElse { return@withContext Result.failure<String>(it) }
            try {
                val device = capture.devices.firstOrNull() ?: return@withContext Result.failure(IllegalStateException("No camera found on this computer"))
                // About 1280 pixels wide: sharp enough for small barcodes, light enough to read many times a second.
                val format = device.formats.minByOrNull { abs(it.formatInfo.width - 1280) }
                    ?: return@withContext Result.failure(IllegalStateException("The camera offers no picture size"))
                val stream = device.openStream(format)
                try {
                    val reader = barcodeReader()
                    val latest = AtomicReference<BufferedImage?>(null)
                    coroutineScope {
                        // Reading runs beside the picture, on the newest frame each time, so the picture stays smooth.
                        val reading = async(Dispatchers.Default) {
                            var text: String? = null
                            while (isActive && text == null) {
                                val image = latest.getAndSet(null)
                                if (image == null) delay(20) else text = read(reader, image)
                            }
                            text
                        }
                        while (isActive && !reading.isCompleted) {
                            if (stream.hasNewFrame()) {
                                val image = stream.capture()
                                frame = image.toComposeImageBitmap()
                                latest.set(image)
                            }
                            delay(25)
                        }
                        reading.await()?.let { Result.success(it) } ?: Result.failure(IllegalStateException("stopped"))
                    }
                } finally {
                    stream.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                capture.close()
            }
        }
        code.fold({ found(it) }, { error -> if (error.message != "stopped") failed("The camera couldn't start: ${error.message ?: "unknown problem"}") })
    }
    Box(modifier.background(Color.Black), Alignment.Center) {
        val picture = frame
        if (picture != null) Image(picture, "Camera", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("Starting the camera…", color = Color.White, fontSize = 13.sp)
    }
}

private fun barcodeReader() = MultiFormatReader().apply {
    setHints(mapOf(
        DecodeHintType.POSSIBLE_FORMATS to ScannableBarcodes.map { BarcodeFormat.valueOf(it) },
        DecodeHintType.TRY_HARDER to true
    ))
}

/**
 * Reads a code from one camera picture: as it comes and turned a quarter (a barcode held upright), each first with the usual
 * light-and-dark split and then with one made for blurred bars (webcams often can't focus close up).
 */
private fun read(reader: MultiFormatReader, image: BufferedImage): String? {
    val width = image.width
    val height = image.height
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    val luma = ByteArray(width * height) { i ->
        val p = pixels[i]
        (((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000).toByte()
    }
    return readLuma(reader, luma, width, height) ?: readLuma(reader, turnQuarter(luma, width, height), height, width)
}

private fun readLuma(reader: MultiFormatReader, luma: ByteArray, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
    for (bitmap in listOf(BinaryBitmap(HybridBinarizer(source)), BinaryBitmap(GlobalHistogramBinarizer(source)))) {
        try {
            return reader.decodeWithState(bitmap).text
        } catch (_: Exception) {
            // Not found in this picture (or an odd frame); the next try or frame follows.
        } finally {
            reader.reset()
        }
    }
    return null
}

/** The picture turned a quarter clockwise (the result is [height] wide and [width] high). */
private fun turnQuarter(luma: ByteArray, width: Int, height: Int): ByteArray {
    val turned = ByteArray(luma.size)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) turned[x * height + (height - 1 - y)] = luma[row + x]
    }
    return turned
}
