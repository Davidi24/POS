package pos.pos.restaurant.util;

import pos.pos.utils.NormalizationUtils;

public final class BranchFieldNormalizer {

    private BranchFieldNormalizer() {
    }

    public static String normalizeCode(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? null : sanitized;
    }

    public static String normalizeCodeOrFallback(String value, String fallbackValue) {
        return normalizeCode(selectValue(value, fallbackValue));
    }

    private static String selectValue(String value, String fallbackValue) {
        return NormalizationUtils.normalize(value) == null ? fallbackValue : value;
    }
}
