package pos.pos.menu.util;

import java.util.Locale;
import java.util.Set;

/** Names and ordering shared by menu preservation operations. */
public final class MenuNames {
    public static final String UNCATEGORIZED = "Uncategorized";
    public static final int LAST = Integer.MAX_VALUE;
    private static final int NAME_LIMIT = 150;

    private MenuNames() {}

    public static boolean isUncategorized(String name) {
        return name != null && UNCATEGORIZED.equalsIgnoreCase(name.trim());
    }

    public static int sectionOrder(String name, int requested) {
        return isUncategorized(name) ? LAST : Math.min(requested, LAST - 1);
    }

    public static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** Reserve each result immediately, including names allocated earlier in the same batch. */
    public static String reserveUnique(String name, Set<String> used) {
        if (used.add(key(name))) return name;
        for (int number = 2; ; number++) {
            String suffix = " (" + number + ")";
            int length = Math.min(name.codePointCount(0, name.length()), NAME_LIMIT - suffix.length());
            String candidate = name.substring(0, name.offsetByCodePoints(0, length)).stripTrailing() + suffix;
            if (used.add(key(candidate))) return candidate;
        }
    }
}
