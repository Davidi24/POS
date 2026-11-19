package com.saporini.mobile_desktop.core.i18n

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Material's Text, with the app's own words in the language in use (user, 2026-11-18). Every screen imports this one
 * instead of `androidx.compose.material3.Text`, so its English words are looked up in the translations ([I18n.tr]).
 * Words someone typed (names, dishes) aren't in the table and stay as they are.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    // false for words that must stay as they are (a language's own name).
    translate: Boolean = true
) {
    val language = LocalAppLanguage.current
    val loaded = LocalTranslations.current
    // Amounts also in the currency chosen on this device ("≈ $13.74"; user, 2026-11-19), never inside RealMoney.
    val money = com.saporini.mobile_desktop.core.money.shownMoney()
    val shown = remember(text, language, loaded, translate, money) {
        if (!translate) text else I18n.tr(text, language).let { words -> money?.show(words) ?: words }
    }
    androidx.compose.material3.Text(
        text = shown, modifier = modifier, color = color, autoSize = autoSize, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}

/**
 * Styled text, its pieces in the language in use: each run of one style is looked up on its own ("You're seeing the orders
 * from " + the supplier in bold). Texts with links or tags stay as they are, since their places would move.
 */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
    translate: Boolean = true
) {
    val language = LocalAppLanguage.current
    val loaded = LocalTranslations.current
    val money = com.saporini.mobile_desktop.core.money.shownMoney()
    val shown = remember(text, language, loaded, translate, money) { if (translate) translated(text, language, money) else text }
    androidx.compose.material3.Text(
        text = shown, modifier = modifier, color = color, autoSize = autoSize, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, inlineContent = inlineContent, onTextLayout = onTextLayout, style = style
    )
}

private fun translated(text: AnnotatedString, language: AppLanguage, money: com.saporini.mobile_desktop.core.money.MoneyRates?): AnnotatedString {
    if ((language == AppLanguage.EN && money == null) || text.text.isBlank()) return text
    val end = text.length
    if (text.paragraphStyles.isNotEmpty() || text.getStringAnnotations(0, end).isNotEmpty() || text.getLinkAnnotations(0, end).isNotEmpty()) return text
    fun piece(words: String): String = I18n.tr(words, language).let { money?.show(it) ?: it }
    val spans = text.spanStyles
    if (spans.isEmpty()) return AnnotatedString(piece(text.text))
    val cuts = (spans.flatMap { listOf(it.start, it.end) } + 0 + end).filter { it in 0..end }.distinct().sorted()
    return buildAnnotatedString {
        cuts.zipWithNext().forEach { (from, to) ->
            if (from == to) return@forEach
            val styles = spans.filter { it.start <= from && it.end >= to }
            styles.forEach { pushStyle(it.item) }
            append(piece(text.text.substring(from, to)))
            repeat(styles.size) { pop() }
        }
    }
}
