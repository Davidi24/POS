package pos.pos.user.service;

import pos.pos.user.entity.User;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Finding a person by what was typed, close enough (user, 2026-10-30: "the search doesn't need to be the same, even if
 * it's near"): accents don't count ("cela" finds Çela), the name can be typed whole ("anisa cela"), and each word may be
 * a little off ("anisa cella") or just the start of one ("ani"). Every typed word has to fit the person somewhere: their
 * name, username, email or phone.
 */
final class StaffSearch {

    private StaffSearch() {
    }

    /** How well [user] fits [query]: 0 not at all, higher is closer (every word found as written beats near misses). */
    static int score(User user, String query) {
        List<String> words = words(query);
        if (words.isEmpty()) return 1;
        List<String> theirs = new ArrayList<>();
        for (String field : new String[]{user.getFirstName(), user.getLastName(), user.getUsername(), user.getEmail()}) {
            theirs.addAll(words(field));
        }
        String digits = user.getPhone() == null ? "" : user.getPhone().replaceAll("\\D", "");
        int total = 0;
        for (String word : words) {
            int best = 0;
            for (String their : theirs) best = Math.max(best, wordScore(word, their));
            if (best == 0 && word.chars().allMatch(Character::isDigit) && word.length() >= 3 && digits.contains(word)) best = 3;
            if (best == 0) return 0;
            total += best;
        }
        return total;
    }

    // 4: the same word, 3: it starts with it, 2: it holds it, 1: one or two letters off.
    private static int wordScore(String typed, String their) {
        if (their.equals(typed)) return 4;
        if (their.startsWith(typed)) return 3;
        if (typed.length() >= 3 && their.contains(typed)) return 2;
        int allowed = typed.length() >= 7 ? 2 : typed.length() >= 4 ? 1 : 0;
        if (allowed == 0) return 0;
        // Against the whole word and against its start of the same length (a word still being typed).
        String start = their.length() > typed.length() ? their.substring(0, typed.length()) : their;
        return Math.min(distance(typed, their), distance(typed, start)) <= allowed ? 1 : 0;
    }

    private static List<String> words(String text) {
        if (text == null || text.isBlank()) return List.of();
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^\\p{L}\\p{N}]+")).filter(word -> !word.isEmpty()).toList();
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
