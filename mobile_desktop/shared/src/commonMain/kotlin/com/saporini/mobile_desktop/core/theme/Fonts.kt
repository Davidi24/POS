package com.saporini.mobile_desktop.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.anton_latin
import mobile_desktop.shared.generated.resources.belleza_latin
import mobile_desktop.shared.generated.resources.burgues_script_regular
import mobile_desktop.shared.generated.resources.cormorant_garamond_bold
import mobile_desktop.shared.generated.resources.cormorant_garamond_bold_italic
import mobile_desktop.shared.generated.resources.cormorant_garamond_italic
import mobile_desktop.shared.generated.resources.cormorant_garamond_regular
import mobile_desktop.shared.generated.resources.geist_latin_variable
import mobile_desktop.shared.generated.resources.geist_mono_latin_variable
import mobile_desktop.shared.generated.resources.im_fell_english_italic
import mobile_desktop.shared.generated.resources.im_fell_english_regular
import mobile_desktop.shared.generated.resources.inter_opsz_wght
import mobile_desktop.shared.generated.resources.le_jour_script_personal_use_only
import mobile_desktop.shared.generated.resources.lora_bold
import mobile_desktop.shared.generated.resources.lora_bold_italic
import mobile_desktop.shared.generated.resources.lora_italic
import mobile_desktop.shared.generated.resources.lora_regular
import mobile_desktop.shared.generated.resources.parfumerie_script_old_style
import mobile_desktop.shared.generated.resources.perandory_condensed
import mobile_desktop.shared.generated.resources.simple_serenity
import mobile_desktop.shared.generated.resources.slight_regular
import mobile_desktop.shared.generated.resources.sloop_script_regular
import mobile_desktop.shared.generated.resources.suranna_regular
import mobile_desktop.shared.generated.resources.tangerine_bold
import mobile_desktop.shared.generated.resources.tangerine_regular
import org.jetbrains.compose.resources.Font

@Composable
fun Anton() = FontFamily(
    Font(Res.font.anton_latin, FontWeight.Normal)
)

@Composable
fun Belleza() = FontFamily(
    Font(Res.font.belleza_latin, FontWeight.Normal)
)

@Composable
fun BurguesScript() = FontFamily(
    Font(Res.font.burgues_script_regular, FontWeight.Normal)
)

@Composable
fun Geist() = FontFamily(
    Font(Res.font.geist_latin_variable, FontWeight.Normal)
)

@Composable
fun GeistMono() = FontFamily(
    Font(Res.font.geist_mono_latin_variable, FontWeight.Normal)
)

@Composable
fun ImFellEnglish() = FontFamily(
    Font(Res.font.im_fell_english_regular, FontWeight.Normal),
    Font(Res.font.im_fell_english_italic, FontWeight.Normal)
)

@Composable
fun Inter() = FontFamily(
    Font(Res.font.inter_opsz_wght, FontWeight.Normal),
    Font(Res.font.inter_opsz_wght, FontWeight.Medium),
    Font(Res.font.inter_opsz_wght, FontWeight.SemiBold),
    Font(Res.font.inter_opsz_wght, FontWeight.Bold)
)

@Composable
fun LeJourScript() = FontFamily(
    Font(Res.font.le_jour_script_personal_use_only, FontWeight.Normal)
)

@Composable
fun ParfumerieScript() = FontFamily(
    Font(Res.font.parfumerie_script_old_style, FontWeight.Normal)
)

@Composable
fun PerandoryCondensed() = FontFamily(
    Font(Res.font.perandory_condensed, FontWeight.Normal)
)

@Composable
fun SimpleSerenity() = FontFamily(
    Font(Res.font.simple_serenity, FontWeight.Normal)
)

@Composable
fun Slight() = FontFamily(
    Font(Res.font.slight_regular, FontWeight.Normal)
)

@Composable
fun SloopScript() = FontFamily(
    Font(Res.font.sloop_script_regular, FontWeight.Normal)
)

@Composable
fun Suranna() = FontFamily(
    Font(Res.font.suranna_regular, FontWeight.Normal)
)

@Composable
fun Tangerine() = FontFamily(
    Font(Res.font.tangerine_regular, FontWeight.Normal),
    Font(Res.font.tangerine_bold, FontWeight.Bold)
)

@Composable
fun Lora() = FontFamily(
    Font(Res.font.lora_regular, FontWeight.Normal),
    Font(Res.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.lora_bold, FontWeight.Bold),
    Font(Res.font.lora_bold_italic, FontWeight.Bold, FontStyle.Italic)
)

@Composable
fun CormorantGaramond() = FontFamily(
    Font(Res.font.cormorant_garamond_regular, FontWeight.Normal),
    Font(Res.font.cormorant_garamond_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.cormorant_garamond_bold, FontWeight.Bold),
    Font(Res.font.cormorant_garamond_bold_italic, FontWeight.Bold, FontStyle.Italic)
)
