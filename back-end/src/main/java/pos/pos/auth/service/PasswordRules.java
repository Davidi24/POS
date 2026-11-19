package pos.pos.auth.service;

/** The rules for a password chosen from an emailed link (an owner's first password or a reset). */
public final class PasswordRules {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 100;
    public static final String SUMMARY = "Use at least 8 characters, with an uppercase letter, a number and a special character.";

    private PasswordRules() {
    }

    /** Null when the password is fine, otherwise what's missing. */
    public static String problem(String password) {
        if (password == null || password.length() < MIN_LENGTH) return pos.pos.i18n.Words.en("Use at least 8 characters.");
        if (password.length() > MAX_LENGTH) return pos.pos.i18n.Words.en("Use at most 100 characters.");
        if (password.isBlank() || !password.strip().equals(password)) return pos.pos.i18n.Words.en("The password can't start or end with a space.");
        if (password.chars().noneMatch(Character::isUpperCase)) return pos.pos.i18n.Words.en("Add an uppercase letter.");
        if (password.chars().noneMatch(Character::isDigit)) return pos.pos.i18n.Words.en("Add a number.");
        if (password.chars().allMatch(Character::isLetterOrDigit)) return pos.pos.i18n.Words.en("Add a special character, like ! or #.");
        return null;
    }
}
