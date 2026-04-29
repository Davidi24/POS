package pos.pos.menu.util;

import pos.pos.utils.NormalizationUtils;

public final class MenuCodeNormalizer {

    private MenuCodeNormalizer() {
    }

    public static String normalize(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? null : sanitized;
    }
}
