package com.saporini.mobile_desktop.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Reading a barcode with the device's camera: the webcam on a desktop, the back camera on a phone or tablet. The live
// picture shows in [modifier]'s box; the first code read goes to onCode once, and the camera stops when it leaves the screen.

/** The barcode kinds shops and suppliers use: EAN/UPC on products, Code 128/39 and ITF on cases, QR codes. */
internal val ScannableBarcodes = listOf("EAN_13", "EAN_8", "UPC_A", "UPC_E", "CODE_128", "CODE_39", "ITF", "QR_CODE")

@Composable
internal expect fun CameraBarcodeScanner(onCode: (String) -> Unit, onProblem: (String) -> Unit, modifier: Modifier)
