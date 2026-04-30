package pos.pos.restaurant.util;

import pos.pos.utils.NormalizationUtils;

public final class BranchFieldNormalizer {

    private BranchFieldNormalizer() {
    }

    public static String normalizeCode(String value) {
        return NormalizationUtils.normalizeCode(value);
    }

    public static String normalizeCodeOrFallback(String value, String fallbackValue) {
        return normalizeCode(selectValue(value, fallbackValue));
    }

    private static String selectValue(String value, String fallbackValue) {
        return NormalizationUtils.normalize(value) == null ? fallbackValue : value;
    }
}
