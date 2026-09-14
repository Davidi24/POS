package com.saporini.mobile_desktop.pos.menu.ui.menu

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saporini.mobile_desktop.core.ui.isPhoneWindow
import com.saporini.mobile_desktop.core.ui.isWidePhoneWindow
import com.saporini.mobile_desktop.core.theme.Inter

private val FormInk = Color(0xFF242522)
private val FormMuted = Color(0xFF71736E)
private val FormBorder = Color(0xFFE2E3DE)
private val FormGreen = Color(0xFF94A27F)

@Composable
internal fun isPhoneMenuWindow(): Boolean = isPhoneWindow()

/** Main editors occupy a page on phones; secondary dialogs use MenuNestedDialog. */
@Composable
internal fun MenuFormDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = "Save Changes",
    canSave: Boolean = true,
    desktopWidth: Float,
    desktopHeight: Float,
    desktopMaxWidth: Dp,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.(isPhone: Boolean) -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                modifier = if (isPhone) {
                    Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().imePadding()
                } else {
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)).padding(24.dp)
                },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = if (isPhone) Modifier.fillMaxSize() else Modifier
                        .fillMaxWidth(desktopWidth)
                        .fillMaxHeight(desktopHeight)
                        .widthIn(max = desktopMaxWidth)
                        .shadow(24.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, FormBorder, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = if (isPhone) {
                            Modifier.fillMaxWidth().heightIn(min = if (isWidePhone) 48.dp else 64.dp).padding(start = 8.dp, end = 20.dp)
                        } else {
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 18.dp, top = 8.dp, bottom = 12.dp)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPhone) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = FormInk)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                fontFamily = Inter(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = FormInk
                            )
                            subtitle?.let {
                                Text(
                                    it,
                                    fontFamily = Inter(),
                                    fontSize = 13.sp,
                                    color = FormMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (!isPhone) {
                            Box(
                                modifier = Modifier.size(34.dp).clip(CircleShape)
                                    .background(Color(0xFFF7F7F5)).border(1.dp, FormBorder, CircleShape)
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Close, "Close editor", Modifier.size(18.dp), tint = FormInk)
                            }
                        }
                    }
                    HorizontalDivider(color = FormBorder)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(
                                horizontal = if (isPhone) 20.dp else 24.dp,
                                vertical = if (isWidePhone) 12.dp else 18.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        content(isPhone)
                    }
                    HorizontalDivider(color = FormBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = if (isPhone) 20.dp else 24.dp,
                            vertical = if (isWidePhone) 6.dp else if (isPhone) 12.dp else 7.dp
                        ),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isPhone) {
                            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                                Text("Cancel", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, color = FormMuted)
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Button(
                            onClick = {
                                if (isPhone) {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                }
                                onSave()
                            },
                            enabled = canSave,
                            modifier = if (isPhone) {
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = if (isWidePhone) 44.dp else 50.dp)
                            } else {
                                Modifier
                            },
                            shape = if (isPhone) RoundedCornerShape(10.dp) else ButtonDefaults.shape,
                            colors = ButtonDefaults.buttonColors(containerColor = FormGreen),
                            contentPadding = PaddingValues(
                                horizontal = 18.dp,
                                vertical = if (isPhone && !isWidePhone) 14.dp else 8.dp
                            )
                        ) {
                            Text(saveLabel, fontFamily = Inter(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** A bounded popup keeps its parent editor visible and its actions above the keyboard. */
@Composable
internal fun MenuNestedDialog(
    onDismissRequest: () -> Unit,
    shape: Shape = RectangleShape,
    containerColor: Color = Color.White,
    titleContentColor: Color = FormInk,
    textContentColor: Color = FormMuted,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    val isPhone = isPhoneMenuWindow()
    val isWidePhone = isWidePhoneWindow()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f))
                .then(if (isPhone) Modifier.safeDrawingPadding().imePadding() else Modifier)
                .padding(if (isPhone) 16.dp else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val dialogWidth = minOf(maxWidth, if (isPhone) 420.dp else 380.dp)
            Column(
                modifier = Modifier.width(dialogWidth).heightIn(max = maxHeight * 0.86f)
                    .shadow(14.dp, shape).clip(shape).background(containerColor)
            ) {
                Box(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = if (isWidePhone) 10.dp else 16.dp, bottom = 10.dp)) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) { title() }
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 2.dp)
                ) {
                    CompositionLocalProvider(LocalContentColor provides textContentColor) { text() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (isWidePhone) 3.dp else 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton()
                    Spacer(Modifier.width(6.dp))
                    confirmButton()
                }
            }
        }
    }
}

@Composable
internal fun MenuFormFieldPair(
    isPhone: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    if (isPhone) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}
