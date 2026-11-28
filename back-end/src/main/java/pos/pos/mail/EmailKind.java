package pos.pos.mail;

public enum EmailKind {
    OWNER_INVITE,
    PASSWORD_RESET,
    EMAIL_VERIFICATION,
    PASSWORD_CHANGED,
    // A daily password limit was reached: no more "Forgot password?" links, or no more changes, today.
    PASSWORD_LIMIT,
    // To someone's old address when they ask to change their email (the confirmation goes to the new one as EMAIL_VERIFICATION).
    EMAIL_CHANGE,
    // To someone after they changed their username.
    USERNAME_CHANGED,
    // To someone who applied to open a restaurant: received (with their status code), or rejected (with the reason).
    RESTAURANT_APPLICATION,
    // To a former owner after the Super Admin gave their restaurant to someone else.
    OWNERSHIP_TRANSFERRED,
    TEST
}
