package pos.pos.settings.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lists the settings table keeps as short comma-separated text (tip suggestions, switched-off fraud rules). */
public final class SettingsCodes {

    private SettingsCodes() {
    }

    public static List<Integer> parsePercents(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Integer> percents = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    percents.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException ignored) {
                    // The database check keeps this from happening; skip rather than fail a read.
                }
            }
        }
        return List.copyOf(percents);
    }

    public static String joinPercents(List<Integer> percents) {
        if (percents == null) {
            return "";
        }
        return String.join(",", new LinkedHashSet<>(percents).stream().map(String::valueOf).toList());
    }

    public static List<String> parseCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> code.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public static String joinCodes(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        return String.join(",", codes.stream().map(code -> code.trim().toUpperCase(Locale.ROOT)).sorted().distinct().toList());
    }
}
