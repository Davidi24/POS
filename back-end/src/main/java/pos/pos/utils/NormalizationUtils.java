package pos.pos.utils;

import java.util.Locale;

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
