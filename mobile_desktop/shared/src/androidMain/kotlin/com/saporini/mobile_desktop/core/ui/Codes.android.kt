package com.saporini.mobile_desktop.core.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter

actual fun qrModules(text: String): List<BooleanArray>? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    (0 until matrix.height).map { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } }
}.getOrNull()

actual fun barcodeBars(text: String): BooleanArray? = runCatching {
    val matrix = Code128Writer().encode(text, BarcodeFormat.CODE_128, 0, 1, mapOf(EncodeHintType.MARGIN to 0))
    BooleanArray(matrix.width) { x -> matrix.get(x, 0) }
}.getOrNull()
