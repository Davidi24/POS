package com.saporini.mobile_desktop.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saporini.mobile_desktop.core.theme.Inter

// The look of Admin Hub → Settings pages (Reservations and the rest), shared with screens that set things the same way
// (Users → Permissions, the roles in the person editor): a soft page, a white card per topic, one row per choice with
// its name and a short line on the left and the control on the right, and a floating bar while changes aren't saved.

internal val SettingsPageBg = Color(0xFFF7F8F6)
internal val SettingsBorder = Color(0xFFE3E6E1)
private val SettingsInk = Color(0xFF222426)
private val SettingsMuted = Color(0xFF747572)
private val SettingsDivider = Color(0xFFF0F1EE)
private val SettingsGreen = Color(0xFF4F7942)
private val SettingsGreenSoft = Color(0xFFEEF3EB)
private val SettingsDanger = Color(0xFFB13A2F)

/** One topic: its title (with an icon and a control on the right when given), a short note, then its rows. */
@Composable
internal fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = SettingsGreen,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, SettingsBorder)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(iconTint.copy(alpha = 0.13f)), Alignment.Center) {
                        Icon(it, null, Modifier.size(17.dp), tint = iconTint)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SettingsInk)
                    note?.let { Text(it, Modifier.padding(top = 2.dp), fontFamily = Inter(), fontSize = 12.sp, color = SettingsMuted) }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

/** One choice: its name and a short line on the left, the control on the right; a thin line above all but the first. */
@Composable
internal fun SettingsRow(
    label: String,
    hint: String? = null,
    divider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    if (divider) HorizontalDivider(color = SettingsDivider)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null && enabled) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (enabled) SettingsInk else SettingsMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            hint?.let { Text(it, fontFamily = Inter(), fontSize = 11.sp, lineHeight = 15.sp, color = SettingsMuted) }
        }
        trailing()
    }
}

/** The settings pages' on/off switch. */
@Composable
internal fun SettingsSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = SettingsGreen, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFE6E8E4),
            uncheckedBorderColor = Color(0xFFD5D8D2))
    )
}

/** A soft green note across the page ("Saved.", "You can only look at these"). */
@Composable
internal fun SettingsInfoBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SettingsGreenSoft).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = SettingsGreen)
        Text(text, fontFamily = Inter(), fontSize = 12.sp, color = SettingsInk)
    }
}

/** Floats over the bottom of a page while changes aren't saved: what's wrong (or that there are changes), Discard and Save. */
@Composable
internal fun UnsavedChangesBar(
    saving: Boolean,
    problem: String?,
    error: String?,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "You have unsaved changes",
    maxWidth: Dp = 820.dp
) {
    val shown = problem ?: error
    Surface(
        modifier.widthIn(max = maxWidth).fillMaxWidth().shadow(14.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, SettingsBorder)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown ?: message,
                Modifier.weight(1f), fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = if (shown != null) SettingsDanger else SettingsInk, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onDiscard, enabled = !saving) { Text("Discard", fontFamily = Inter(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.sp, color = SettingsMuted) }
            Spacer(Modifier.width(6.dp))
            Surface(onClick = onSave, enabled = !saving && problem == null, shape = RoundedCornerShape(10.dp),
                color = if (problem == null) SettingsGreen else SettingsGreen.copy(alpha = 0.4f)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (saving) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(if (saving) "Saving…" else "Save changes", fontFamily = Inter(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}
