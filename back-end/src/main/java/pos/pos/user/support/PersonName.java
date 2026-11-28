package pos.pos.user.support;

import pos.pos.user.entity.User;

/**
 * How a person's name shows next to what they did: "Mira Leka", else their username, with " (removed)" once they were
 * removed from the staff (user, 2026-11-19: their orders, shifts and pay stay, marked removed).
 */
public final class PersonName {

    private PersonName() {
    }

    public static String of(User user) {
        if (user == null) return "";
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        String name = full.isEmpty() ? (user.getUsername() == null ? "" : user.getUsername()) : full;
        return user.getDeletedAt() == null ? name : pos.pos.i18n.Words.en("{} (removed)", name);
    }
}
