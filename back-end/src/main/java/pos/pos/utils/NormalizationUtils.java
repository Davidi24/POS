package pos.pos.utils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility class for safe string normalization.
 * <p>
 * normalize(value):
 * - removes leading and trailing spaces
 * - returns null if the result is empty
 * - returns null if input is null
 * <p>
 * Example:
 * "  test  " → "test"
 * "   " → null
 * null → null
 * <p>
 * normalizeLower(value):
 * - first cleans the value using normalize()
 * - then converts it to lowercase (safe for all languages)
 * <p>
 * Example:
 * "  TEST  " → "test"
 * <p>
 * normalizeUpper(value):
 * - first cleans the value using normalize()
 * - then converts it to uppercase (safe for all languages)
 * <p>
 * Example:
 * "  test  " → "TEST"
 */
public final class NormalizationUtils {

    private static final Pattern NON_ALPHANUMERIC_CODE = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");

    private NormalizationUtils() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizeLower(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public static String normalizeLowerLike(String value) {
        String normalized = normalizeLower(value);
        return normalized == null ? null : "%" + normalized + "%";
    }

    public static String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalizeCode(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = NON_ALPHANUMERIC_CODE.matcher(normalized).replaceAll("_");
        sanitized = EDGE_UNDERSCORES.matcher(sanitized).replaceAll("");
        return sanitized.isEmpty() ? null : sanitized;
    }

    public static String normalizeCode(String value, int maxLength) {
        String normalized = normalizeCode(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength);
    }

    public static String normalizePhone(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        return normalized.replaceAll("[\\s().-]", "");
    }

    public static String normalizePhoneLike(String value) {
        String normalized = normalizePhone(value);
        return normalized == null ? null : "%" + normalized + "%";
    }
}
