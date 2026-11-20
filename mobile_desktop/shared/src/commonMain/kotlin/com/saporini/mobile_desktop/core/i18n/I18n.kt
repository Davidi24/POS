package com.saporini.mobile_desktop.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.saporini.mobile_desktop.core.session.DeviceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mobile_desktop.shared.generated.resources.Res

/** The app's languages (user, 2026-11-18): English, Italian, Spanish and Albanian. */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    IT("it", "Italiano"),
    ES("es", "Español"),
    SQ("sq", "Shqip");

    /** "EN", "IT"… for the language button. */
    val short: String get() = code.uppercase()

    companion object {
        fun of(code: String?): AppLanguage? = entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }
    }
}

/** The language in use, for every [Text] (provided by the app's root). */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

/** Bumped when the translations have loaded, so texts drawn before then are drawn again. */
val LocalTranslations = staticCompositionLocalOf { 0 }

/**
 * The app's own words in four languages (user, 2026-11-18). Every text is written in English in the code; the translations
 * live in one table, `composeResources/files/i18n/strings.tsv` (columns en, it, es, sq), keyed by the English text, so a
 * screen needs no change to be translated: [Text] looks its words up. A text with values in it ("3 people") is matched by
 * its row with `{}` where the values go ("{} people"); a translation may move them with `{1}`, `{2}`. Anything not in the
 * table shows in English.
 */
object I18n {
    private val _language = MutableStateFlow(AppLanguage.of(DeviceLanguage.load()) ?: AppLanguage.EN)
    /** The language in use on this device now. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private class Pattern(val regex: Regex, val chunk: String, val weight: Int, val translations: Array<String?>)

    private var exact: Map<String, Array<String?>> = emptyMap()
    private var patterns: List<Pattern> = emptyList()
    private val cache = HashMap<String, String>()

    /** Switches the app to [language] now and keeps it on this device. */
    fun use(language: AppLanguage) {
        _language.value = language
        DeviceLanguage.save(language.code)
    }

    /** Reads the table (once, at start). */
    suspend fun load() {
        if (exact.isNotEmpty()) return
        val text = runCatching { Res.readBytes("files/i18n/strings.tsv").decodeToString() }.getOrNull() ?: return
        val rows = HashMap<String, Array<String?>>()
        val found = ArrayList<Pattern>()
        text.lineSequence().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val cells = line.split('\t').map(::unescape)
            val key = cells[0]
            if (key.isEmpty()) return@forEach
            val translations = Array(AppLanguage.entries.size) { i -> cells.getOrNull(i)?.takeIf { it.isNotEmpty() } }
            if ("{}" in key) {
                val parts = key.split("{}")
                if (parts.none { part -> part.any(Char::isLetter) }) return@forEach
                val regex = Regex(parts.joinToString("([\\s\\S]+?)") { Regex.escape(it) })
                found += Pattern(regex, parts.maxBy { it.length }, parts.sumOf { it.length }, translations)
            } else {
                rows[key] = translations
            }
        }
        exact = rows
        patterns = found.sortedByDescending { it.weight }
        cache.clear()
        _revision.value++
    }

    /** [text] in [language] (the language in use when left out); itself when there's no translation. */
    fun tr(text: String, language: AppLanguage = _language.value): String {
        if (language == AppLanguage.EN || text.isBlank()) return text
        val column = language.ordinal
        exact[text]?.get(column)?.let { return it }
        val cacheKey = "${language.code}\u0000$text"
        cache[cacheKey]?.let { return it }
        val result = translate(text, column)
        if (cache.size > 20_000) cache.clear()
        cache[cacheKey] = result
        return result
    }

    private fun translate(text: String, column: Int): String {
        // Words with spaces around them (pieces joined in the code): translate the words, keep the spaces.
        val trimmed = text.trim()
        if (trimmed.length != text.length && trimmed.isNotEmpty()) {
            val inner = exact[trimmed]?.get(column) ?: matchPattern(trimmed, column)
            if (inner != null) return text.substring(0, text.indexOf(trimmed[0])) + inner + text.substring(text.lastIndexOf(trimmed.last()) + 1)
        }
        matchPattern(text, column)?.let { return it }
        // Pieces joined in the code ("2 guests · Terrace", lines under each other, one sentence after another): each
        // piece on its own.
        for (joint in Joints) {
            if (!joint.containsMatchIn(text)) continue
            val pieces = text.split(joint)
            val seps = joint.findAll(text).map { it.value }.toList()
            val done = pieces.mapIndexed { i, piece -> if (piece.isBlank()) piece else piece(piece, i > 0 && joint == Dot, column) }
            if (done != pieces) return buildString { done.forEachIndexed { i, p -> append(p); if (i < seps.size) append(seps[i]) } }
        }
        return dateWords(text, column) ?: text
    }

    /** A piece of a joined text; after a " · ", also as the code wrote it ("· by {}"). */
    private fun piece(piece: String, afterDot: Boolean, column: Int): String {
        exact[piece]?.get(column)?.let { return it }
        val own = translate(piece, column)
        if (own != piece || !afterDot) return own
        val dotted = translate("· $piece", column)
        return if (dotted != "· $piece" && dotted.startsWith("· ")) dotted.removePrefix("· ") else piece
    }

    private val Dot = Regex(" · ")
    private val Joints = listOf(Regex("\n"), Dot, Regex("(?<=[.!?])\\s+(?=[A-Z0-9\"“(])"), Regex(" – "), Regex("(?<=:) "))

    private fun matchPattern(text: String, column: Int): String? {
        for (pattern in patterns) {
            if (pattern.chunk.isNotEmpty() && !text.contains(pattern.chunk)) continue
            val match = pattern.regex.matchEntire(text) ?: continue
            val template = pattern.translations[column] ?: return null
            val values = match.groupValues.drop(1).map { value -> exact[value]?.get(column) ?: dateWords(value, column) ?: value }
            return fill(template, values)
        }
        return null
    }

    /**
     * Dates are put together in the code from English day and month names ("Fri 6 Nov", "25 September 2026", "Today, 19:00"):
     * those words in the language in use, the rest as it is. Null when there are none.
     */
    private fun dateWords(text: String, column: Int): String? {
        if (!DateWord.containsMatchIn(text)) return null
        val language = AppLanguage.entries[column]
        val out = DateWord.replace(text) { m ->
            val word = DateNames[m.value]?.get(language) ?: m.value
            if (m.range.first == 0) word.replaceFirstChar { it.uppercase() } else word
        }
        return out.takeIf { it != text }
    }

    private val DateWord = Regex("(?<![\\p{L}\\-])(" + listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
        "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Jan", "Feb", "Mar", "Apr", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        "Today", "Yesterday", "Tomorrow"
    ).joinToString("|") + ")(?![\\p{L}\\-])")

    /** Day and month names: Italian, Spanish, Albanian (lower case; the first word of a text gets a capital). */
    private val DateNames: Map<String, Map<AppLanguage, String>> = listOf(
        "Monday|lunedì|lunes|e hënë", "Tuesday|martedì|martes|e martë", "Wednesday|mercoledì|miércoles|e mërkurë",
        "Thursday|giovedì|jueves|e enjte", "Friday|venerdì|viernes|e premte", "Saturday|sabato|sábado|e shtunë",
        "Sunday|domenica|domingo|e diel",
        "January|gennaio|enero|janar", "February|febbraio|febrero|shkurt", "March|marzo|marzo|mars", "April|aprile|abril|prill",
        "May|maggio|mayo|maj", "June|giugno|junio|qershor", "July|luglio|julio|korrik", "August|agosto|agosto|gusht",
        "September|settembre|septiembre|shtator", "October|ottobre|octubre|tetor", "November|novembre|noviembre|nëntor",
        "December|dicembre|diciembre|dhjetor",
        "Mon|lun|lun|hën", "Tue|mar|mar|mar", "Wed|mer|mié|mër", "Thu|gio|jue|enj", "Fri|ven|vie|pre", "Sat|sab|sáb|sht",
        "Sun|dom|dom|die",
        "Jan|gen|ene|jan", "Feb|feb|feb|shk", "Mar|mar|mar|mar", "Apr|apr|abr|pri", "Jun|giu|jun|qer", "Jul|lug|jul|kor",
        "Aug|ago|ago|gus", "Sep|set|sep|sht", "Oct|ott|oct|tet", "Nov|nov|nov|nën", "Dec|dic|dic|dhj",
        "Today|oggi|hoy|sot", "Yesterday|ieri|ayer|dje", "Tomorrow|domani|mañana|nesër"
    ).associate { row ->
        val cells = row.split('|')
        cells[0] to mapOf(AppLanguage.IT to cells[1], AppLanguage.ES to cells[2], AppLanguage.SQ to cells[3])
    }

    /** "{}" in order, or "{1}", "{2}" by position. */
    private fun fill(template: String, values: List<String>): String {
        val out = StringBuilder()
        var next = 0
        var i = 0
        while (i < template.length) {
            if (template[i] == '{') {
                val close = template.indexOf('}', i)
                if (close > i) {
                    val inside = template.substring(i + 1, close)
                    val index = if (inside.isEmpty()) next++ else inside.toIntOrNull()?.minus(1)
                    if (index != null && index in values.indices) {
                        out.append(values[index]); i = close + 1; continue
                    }
                }
            }
            out.append(template[i]); i++
        }
        return out.toString()
    }

    private fun unescape(cell: String): String {
        if ('\\' !in cell) return cell
        val out = StringBuilder()
        var i = 0
        while (i < cell.length) {
            val c = cell[i]
            if (c == '\\' && i + 1 < cell.length) {
                when (cell[i + 1]) {
                    'n' -> out.append('\n'); 't' -> out.append('\t'); '\\' -> out.append('\\')
                    else -> { out.append(c); out.append(cell[i + 1]) }
                }
                i += 2
            } else { out.append(c); i++ }
        }
        return out.toString()
    }
}

/** The app's own words in the language in use (for texts that aren't drawn by [Text], like a window title or a chart label). */
fun tr(text: String): String = I18n.tr(text)
