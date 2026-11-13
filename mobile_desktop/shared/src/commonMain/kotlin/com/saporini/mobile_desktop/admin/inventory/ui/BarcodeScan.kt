package com.saporini.mobile_desktop.admin.inventory.ui

import androidx.compose.foundation.layout.fillMaxSize
import com.saporini.mobile_desktop.core.ui.CameraBarcodeScanner
import com.saporini.mobile_desktop.core.components.OverviewTabs
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.components.AppDialog
import com.saporini.mobile_desktop.core.components.ButtonStyle
import com.saporini.mobile_desktop.core.components.Kit
import com.saporini.mobile_desktop.core.components.KitButton
import com.saporini.mobile_desktop.core.theme.Inter

// Scanning a barcode: a USB or Bluetooth scanner types the code like a keyboard and presses Enter, so the dialog keeps a
// focused field ready for it. People without a scanner type the code, or switch to the camera (webcam or phone camera).

/** The cleaned-up code: spaces gone, letters upper case (the server stores barcodes the same way). */
internal fun normalizeBarcode(text: String): String = text.trim().replace(" ", "").uppercase()

@Composable
internal fun ScanBarcodeDialog(title: String, hint: String, onCode: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    // A scanner (or typing) by default; the camera when someone has no scanner at hand.
    var camera by remember { mutableStateOf(false) }
    var cameraProblem by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(camera) { if (!camera) runCatching { focus.requestFocus() } }
    val submit = { normalizeBarcode(code).takeIf { it.isNotEmpty() }?.let(onCode) }
    AppDialog(title, onDismiss, subtitle = hint, maxWidth = 480.dp, buttons = {
        KitButton("Cancel", onDismiss, style = ButtonStyle.SECONDARY)
        if (!camera) KitButton("Use this code", { submit() }, icon = Icons.Outlined.Check, enabled = code.isNotBlank())
    }) {
        OverviewTabs(listOf("Scanner", "Camera"), if (camera) "Camera" else "Scanner", { camera = it == "Camera"; cameraProblem = null },
            Modifier.fillMaxWidth(), height = 40.dp)
        if (camera) {
            Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(14.dp))) {
                CameraBarcodeScanner(onCode = { read -> normalizeBarcode(read).takeIf { it.isNotEmpty() }?.let(onCode) },
                    onProblem = { cameraProblem = it }, modifier = Modifier.fillMaxSize())
                ViewFinder(Modifier.align(Alignment.Center))
            }
            Text(cameraProblem ?: "Hold the barcode inside the frame. It's read by itself.", Modifier.fillMaxWidth(), fontFamily = Inter(), fontSize = 12.sp,
                lineHeight = 18.sp, color = if (cameraProblem != null) Kit.Danger else Kit.Muted, textAlign = TextAlign.Center)
            return@AppDialog
        }
        ScannerArt()
        Text("Scan the barcode now: the scanner types it in by itself. No scanner? Type the numbers printed under the stripes " +
            "(or the code saved on the item) and press Enter, or use the camera.",
            Modifier.fillMaxWidth(), fontFamily = Inter(), fontSize = 12.sp, lineHeight = 18.sp, color = Kit.Muted, textAlign = TextAlign.Center)
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                .border(1.5.dp, if (code.isNotBlank()) Kit.Green else Kit.Border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.QrCode, null, Modifier.size(20.dp), tint = Kit.Muted)
            Box(Modifier.weight(1f)) {
                if (code.isEmpty()) Text("Scan or type, e.g. 8001234567890", fontFamily = Inter(), fontSize = 15.sp, color = Kit.Faint, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    code, { code = it.take(80) },
                    Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) { submit(); true } else false
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp, color = Kit.Ink),
                    cursorBrush = SolidColor(Kit.Green),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
            }
        }
    }
}

/** The frame drawn over the camera picture, with the sweeping laser line. */
@Composable
private fun ViewFinder(modifier: Modifier) {
    val sweep by rememberInfiniteTransition(label = "finder").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "finder-line")
    Box(modifier.size(width = 260.dp, height = 150.dp).border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))) {
        Box(Modifier.align(Alignment.TopCenter).offset(y = 12.dp + 124.dp * sweep).size(width = 236.dp, height = 2.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Kit.Danger, Color.Transparent))))
    }
}

/** A scanner icon in a soft box with a laser line sweeping over it. */
@Composable
private fun ScannerArt() {
    val sweep by rememberInfiniteTransition(label = "scanner").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "scanner-line")
    Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.verticalGradient(listOf(Kit.GreenSoft, Color.White)))) {
        Icon(Icons.Outlined.QrCodeScanner, null, Modifier.align(Alignment.Center).size(64.dp), tint = Kit.Green)
        Box(Modifier.align(Alignment.TopCenter).offset(y = 22.dp + 80.dp * sweep).size(width = 150.dp, height = 2.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Kit.Danger, Color.Transparent))))
    }
}

/** What the scan found (or didn't), with the next step. Shared by the stock-change dialog and the Stock tab. */
@Composable
internal fun ScanMissBox(code: String, canEdit: Boolean, onCreate: () -> Unit, onScanAgain: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Kit.Amber.copy(alpha = 0.08f))
        .border(1.dp, Kit.Amber.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("No item has the barcode $code yet.", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Kit.Ink)
        Text(if (canEdit) "Create it now: the barcode is filled in for you." else "Ask someone who can add items to create it.",
            fontFamily = Inter(), fontSize = 12.sp, color = Kit.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canEdit) KitButton("Create item", onCreate, icon = Icons.Outlined.Check)
            KitButton("Scan again", onScanAgain, icon = Icons.Outlined.QrCodeScanner, style = ButtonStyle.SECONDARY)
        }
    }
}
