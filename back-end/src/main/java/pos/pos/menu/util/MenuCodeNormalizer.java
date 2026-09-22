package pos.pos.menu.util;

import pos.pos.utils.NormalizationUtils;

public final class MenuCodeNormalizer {

    /**
     * Every entity that derives a code from a name (menus, option items,
     * option group types) stores it in a varchar(50). Names are allowed to be
     * far longer, so the derived code must be truncated here -- otherwise the
     * insert fails at the database and surfaces as an HTTP 500.
     */
    private static final int MAX_CODE_LENGTH = 50;

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

        if (sanitized.length() > MAX_CODE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_CODE_LENGTH).replaceAll("_+$", "");
        }

        return sanitized.isEmpty() ? null : sanitized;
    }
}
