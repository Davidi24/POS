package pos.pos.restaurant.util;

import pos.pos.utils.NormalizationUtils;

import java.time.DateTimeException;
import java.time.ZoneId;

public final class RestaurantFieldNormalizer {

    private static final int CODE_MAX_LENGTH = 100;
    private static final int SLUG_MAX_LENGTH = 150;

    private RestaurantFieldNormalizer() {
    }

    public static String normalizeCode(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? null : truncate(sanitized, CODE_MAX_LENGTH);
    }

    public static String normalizeCodeOrFallback(String value, String fallbackValue) {
        return normalizeCode(selectValue(value, fallbackValue));
    }

    public static String normalizeSlug(String value) {
        String normalized = NormalizationUtils.normalizeLower(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return sanitized.isEmpty() ? null : truncate(sanitized, SLUG_MAX_LENGTH);
    }

    public static String normalizeSlugOrFallback(String value, String fallbackValue) {
        return normalizeSlug(selectValue(value, fallbackValue));
    }

    public static String normalizeTimezone(String value) {
        return NormalizationUtils.normalize(value);
    }

    public static boolean isValidTimezone(String value) {
        if (value == null) {
            return false;
        }

        try {
            ZoneId.of(value);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }

    public static String withCodeSequence(String baseCode, int sequence) {
        return withNumericSequence(baseCode, sequence, "_", CODE_MAX_LENGTH);
    }

    public static String withSlugSequence(String baseSlug, int sequence) {
        return withNumericSequence(baseSlug, sequence, "-", SLUG_MAX_LENGTH);
    }

    private static String selectValue(String value, String fallbackValue) {
        return NormalizationUtils.normalize(value) == null ? fallbackValue : value;
    }

    private static String withNumericSequence(String baseValue, int sequence, String delimiter, int maxLength) {
        if (baseValue == null) {
            return null;
        }

        if (sequence <= 1) {
            return truncate(baseValue, maxLength);
        }

        String suffix = delimiter + sequence;
        String truncatedBase = stripTrailingDelimiter(
                truncate(baseValue, maxLength - suffix.length()),
                delimiter
        );

        return truncatedBase + suffix;
    }

    private static String stripTrailingDelimiter(String value, String delimiter) {
        if (value == null) {
            return null;
        }

        return value.replaceAll(java.util.regex.Pattern.quote(delimiter) + "+$", "");
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
