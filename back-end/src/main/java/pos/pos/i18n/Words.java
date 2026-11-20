package pos.pos.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's own words in four languages (user, 2026-11-18): English, Italian, Spanish and Albanian. Like the app, every
 * text is written in English in the code and its translations live in one table, {@code resources/i18n/server.tsv}
 * (columns en, it, es, sq, keyed by the English text, {@code {}} where a value goes; a translation may move values with
 * {@code {1}}, {@code {2}}). Filled in with {@code mobile_desktop/i18n/i18n.py --server}.
 *
 * <ul>
 *   <li>{@link #t} — a text written here, in a language, with its values;</li>
 *   <li>{@link #en} — the same in English, for texts made now and shown later (a booking's money lines);</li>
 *   <li>{@link #tr} — a finished English text (made by {@link #en} or an error message) in a language.</li>
 * </ul>
 * Anything not in the table stays in English.
 */
public final class Words {

    /** The languages, in the table's order. */
    public static final List<String> LANGUAGES = List.of("en", "it", "es", "sq");

    private static final Map<String, String> NAMES = Map.of("en", "English", "it", "Italiano", "es", "Español", "sq", "Shqip");

    private record Template(Pattern regex, String chunk, int weight, String[] cells) { }

    private static final Map<String, String[]> EXACT = new HashMap<>();
    private static final List<Template> PATTERNS = new ArrayList<>();
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    static {
        load();
    }

    private Words() { }

    /** One of the four codes, else "en". */
    public static String language(String code) {
        if (code == null) return "en";
        String clean = code.trim().toLowerCase(Locale.ROOT);
        if (clean.length() > 2) clean = clean.substring(0, 2);
        return LANGUAGES.contains(clean) ? clean : "en";
    }

    /** The best of the four for a browser's Accept-Language ("it-IT,it;q=0.9,en;q=0.8"), else null. */
    public static String fromBrowser(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return null;
        for (String part : acceptLanguage.split(",")) {
            String code = part.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (code.length() >= 2 && LANGUAGES.contains(code.substring(0, 2))) return code.substring(0, 2);
        }
        return null;
    }

    /** "Italiano" for "it". */
    public static String nativeName(String code) {
        return NAMES.getOrDefault(language(code), "English");
    }

    /** Java's locale for dates ("lunedì 3 novembre"). */
    public static Locale locale(String code) {
        return Locale.forLanguageTag(language(code));
    }

    /** [template] in [lang] with its {} filled by [values] in order (or {1}, {2} by position). */
    public static String t(String lang, String template, Object... values) {
        String code = language(lang);
        String shown = template;
        if (!"en".equals(code)) {
            String[] cells = EXACT.get(template);
            String cell = cells == null ? null : cells[LANGUAGES.indexOf(code)];
            if (cell != null && !cell.isEmpty()) shown = cell;
        }
        return fill(shown, values);
    }

    /** [template] in English with its values: a text made now and translated when it's shown ({@link #tr}). */
    public static String en(String template, Object... values) {
        return fill(template, values);
    }

    /** A finished English [text] in [lang]: the whole text, else a template it fits, else its pieces; else as it is. */
    public static String tr(String lang, String text) {
        String code = language(lang);
        if ("en".equals(code) || text == null || text.isBlank()) return text;
        int column = LANGUAGES.indexOf(code);
        String[] cells = EXACT.get(text);
        if (cells != null && cells[column] != null && !cells[column].isEmpty()) return cells[column];
        return CACHE.computeIfAbsent(code + '\u0000' + text, key -> translate(text, column));
    }

    private static String translate(String text, int column) {
        String trimmed = text.strip();
        if (!trimmed.isEmpty() && trimmed.length() != text.length()) {
            String inner = exact(trimmed, column);
            if (inner == null) inner = matchPattern(trimmed, column);
            if (inner != null) {
                int start = text.indexOf(trimmed);
                return text.substring(0, start) + inner + text.substring(start + trimmed.length());
            }
        }
        String matched = matchPattern(text, column);
        if (matched != null) return matched;
        for (Pattern joint : JOINTS) {
            Matcher m = joint.matcher(text);
            if (!m.find()) continue;
            StringBuilder out = new StringBuilder();
            int last = 0;
            boolean changed = false;
            m.reset();
            while (m.find()) {
                String piece = text.substring(last, m.start());
                String done = piece.isBlank() ? piece : pieceOf(piece, column);
                changed |= !done.equals(piece);
                out.append(done).append(m.group());
                last = m.end();
            }
            String tail = text.substring(last);
            String doneTail = tail.isBlank() ? tail : pieceOf(tail, column);
            changed |= !doneTail.equals(tail);
            out.append(doneTail);
            if (changed) return out.toString();
        }
        String dates = dateWords(text, column);
        return dates == null ? text : dates;
    }

    private static String pieceOf(String piece, int column) {
        String exact = exact(piece, column);
        return exact != null ? exact : translate(piece, column);
    }

    private static String exact(String text, int column) {
        String[] cells = EXACT.get(text);
        return cells == null || cells[column] == null || cells[column].isEmpty() ? null : cells[column];
    }

    private static String matchPattern(String text, int column) {
        for (Template template : PATTERNS) {
            if (!template.chunk().isEmpty() && !text.contains(template.chunk())) continue;
            Matcher m = template.regex().matcher(text);
            if (!m.matches()) continue;
            String cell = template.cells()[column];
            if (cell == null || cell.isEmpty()) return null;
            Object[] values = new Object[m.groupCount()];
            for (int i = 0; i < values.length; i++) {
                values[i] = valueIn(m.group(i + 1), column);
            }
            return fill(cell, values);
        }
        return null;
    }

    /** A value inside a template: its own translation when it's a text of the table ("deposit for 4 guests"), else as it is. */
    private static String valueIn(String value, int column) {
        String exact = exact(value, column);
        if (exact != null) return exact;
        String matched = matchPattern(value, column);
        if (matched != null) return matched;
        if (!value.isEmpty() && Character.isLowerCase(value.charAt(0))) {
            String upper = Character.toUpperCase(value.charAt(0)) + value.substring(1);
            String done = exact(upper, column);
            if (done == null) done = matchPattern(upper, column);
            if (done != null) return Character.toLowerCase(done.charAt(0)) + done.substring(1);
        }
        String dates = dateWords(value, column);
        return dates != null ? dates : value;
    }

    private static final List<Pattern> JOINTS = List.of(
            Pattern.compile("\n"), Pattern.compile(" · "), Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9\"“(])"), Pattern.compile(" – "),
            Pattern.compile("(?<=:) "));

    /** "{}" in order, or "{1}", "{2}" by position. */
    static String fill(String template, Object... values) {
        if (values == null || values.length == 0) return template;
        StringBuilder out = new StringBuilder();
        int next = 0;
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int close = template.indexOf('}', i);
                if (close > i) {
                    String inside = template.substring(i + 1, close);
                    Integer index = inside.isEmpty() ? Integer.valueOf(next++) : parse(inside);
                    if (index != null && index >= 0 && index < values.length) {
                        out.append(values[index]);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static Integer parse(String inside) {
        try {
            return Integer.parseInt(inside) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Day and month names inside dates made in English ----

    private static final Map<String, String[]> DATE_NAMES = new LinkedHashMap<>();
    private static final Pattern DATE_WORD;

    static {
        String[] rows = {
                "Monday|lunedì|lunes|e hënë", "Tuesday|martedì|martes|e martë", "Wednesday|mercoledì|miércoles|e mërkurë",
                "Thursday|giovedì|jueves|e enjte", "Friday|venerdì|viernes|e premte", "Saturday|sabato|sábado|e shtunë",
                "Sunday|domenica|domingo|e diel",
                "January|gennaio|enero|janar", "February|febbraio|febrero|shkurt", "March|marzo|marzo|mars", "April|aprile|abril|prill",
                "May|maggio|mayo|maj", "June|giugno|junio|qershor", "July|luglio|julio|korrik", "August|agosto|agosto|gusht",
                "September|settembre|septiembre|shtator", "October|ottobre|octubre|tetor", "November|novembre|noviembre|nëntor",
                "December|dicembre|diciembre|dhjetor",
                "Mon|lun|lun|hën", "Tue|mar|mar|mar", "Wed|mer|mié|mër", "Thu|gio|jue|enj", "Fri|ven|vie|pre", "Sat|sab|sáb|sht",
                "Sun|dom|dom|die", "Jan|gen|ene|jan", "Feb|feb|feb|shk", "Mar|mar|mar|mar", "Apr|apr|abr|pri", "Jun|giu|jun|qer",
                "Jul|lug|jul|kor", "Aug|ago|ago|gus", "Sep|set|sep|sht", "Oct|ott|oct|tet", "Nov|nov|nov|nën", "Dec|dic|dic|dhj"
        };
        for (String row : rows) {
            String[] cells = row.split("\\|");
            DATE_NAMES.put(cells[0], new String[]{cells[0], cells[1], cells[2], cells[3]});
        }
        DATE_WORD = Pattern.compile("(?<![\\p{L}\\-])(" + String.join("|", DATE_NAMES.keySet()) + ")(?![\\p{L}\\-])");
    }

    private static String dateWords(String text, int column) {
        Matcher m = DATE_WORD.matcher(text);
        if (!m.find()) return null;
        m.reset();
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String word = DATE_NAMES.get(m.group())[column];
            if (m.start() == 0) word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            m.appendReplacement(out, Matcher.quoteReplacement(word));
        }
        m.appendTail(out);
        String done = out.toString();
        return done.equals(text) ? null : done;
    }

    // ---- The table ----

    private static void load() {
        try (InputStream in = Words.class.getResourceAsStream("/i18n/server.tsv")) {
            if (in == null) return;
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = text.split("\n");
            for (int n = 1; n < lines.length; n++) {
                String line = lines[n];
                if (line.isBlank()) continue;
                String[] raw = line.split("\t", -1);
                String[] cells = new String[LANGUAGES.size()];
                for (int i = 0; i < cells.length; i++) cells[i] = i < raw.length ? unescape(raw[i]) : null;
                String key = cells[0];
                if (key == null || key.isEmpty()) continue;
                if (key.contains("{}")) {
                    String[] parts = key.split("\\{}", -1);
                    boolean words = false;
                    for (String part : parts) words |= part.chars().anyMatch(Character::isLetter);
                    if (!words) continue;
                    StringBuilder regex = new StringBuilder();
                    String longest = "";
                    int weight = 0;
                    for (int i = 0; i < parts.length; i++) {
                        if (i > 0) regex.append("([\\s\\S]+?)");
                        regex.append(Pattern.quote(parts[i]));
                        if (parts[i].length() > longest.length()) longest = parts[i];
                        weight += parts[i].length();
                    }
                    PATTERNS.add(new Template(Pattern.compile(regex.toString()), longest, weight, cells));
                }
                EXACT.put(key, cells);
            }
            PATTERNS.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        } catch (IOException ignored) {
            // No table: English everywhere.
        }
    }

    private static String unescape(String cell) {
        if (cell.indexOf('\\') < 0) return cell;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cell.length(); i++) {
            char c = cell.charAt(i);
            if (c == '\\' && i + 1 < cell.length()) {
                char next = cell.charAt(i + 1);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case '\\' -> out.append('\\');
                    default -> out.append(c).append(next);
                }
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
