package com.saporini.mobile_desktop.core.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.GlobalHistogramBinarizer
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import android.util.Size
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// The back camera through CameraX, read with ZXing on the brightness plane of each frame.

@Composable
internal actual fun CameraBarcodeScanner(onCode: (String) -> Unit, onProblem: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val found by rememberUpdatedState(onCode)
    val failed by rememberUpdatedState(onProblem)
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (!granted) failed("The camera isn't allowed. Allow it in the phone's settings to scan.")
    }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }
    if (!allowed) {
        Box(modifier.background(Color.Black), Alignment.Center) { Text("Waiting for the camera…", color = Color.White, fontSize = 13.sp) }
        return
    }
    val done = remember { AtomicBoolean(false) }
    val analyzer = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            analyzer.shutdown()
        }
    }
    AndroidView(factory = { viewContext ->
        // A TextureView-backed preview: the scanner sits in a dialog, where a SurfaceView can end up hidden behind it (black).
        val preview = PreviewView(viewContext).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        val providerFuture = ProcessCameraProvider.getInstance(viewContext)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val shown = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val reader = MultiFormatReader().apply {
                    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to ScannableBarcodes.map { BarcodeFormat.valueOf(it) }, DecodeHintType.TRY_HARDER to true))
                }
                // About 1280 × 720: sharp enough for the thin bars of a product barcode.
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(analyzer) { image ->
                    try {
                        if (!done.get()) {
                            val plane = image.planes[0]
                            val width = image.width
                            val height = image.height
                            val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                            // The brightness plane without its row padding.
                            val luma = ByteArray(width * height)
                            for (row in 0 until height) System.arraycopy(bytes, row * plane.rowStride, luma, row * width, width)
                            // The sensor lies sideways in a phone held upright, so a barcode held level reaches it standing up:
                            // read it as it comes and turned a quarter.
                            val text = readBarcode(reader, luma, width, height) ?: readBarcode(reader, turnQuarter(luma, width, height), height, width)
                            if (text != null && done.compareAndSet(false, true)) ContextCompat.getMainExecutor(viewContext).execute { found(text) }
                        }
                    } catch (_: Exception) {
                        // A frame that can't be read is skipped; the next one is tried.
                    } finally {
                        image.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, shown, analysis)
            } catch (e: Exception) {
                failed("The camera couldn't start: ${e.message ?: "unknown problem"}")
            }
        }, ContextCompat.getMainExecutor(viewContext))
        preview
    }, modifier = modifier.background(Color.Black))
}

/** Reads a code from a brightness picture, first with the usual light-and-dark split, then with one made for blurred bars. */
private fun readBarcode(reader: MultiFormatReader, luma: ByteArray, width: Int, height: Int): String? {
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

