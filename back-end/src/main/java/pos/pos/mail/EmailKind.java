package pos.pos.mail;

public enum EmailKind {
    OWNER_INVITE,
    PASSWORD_RESET,
    EMAIL_VERIFICATION,
    PASSWORD_CHANGED,
    // To someone who applied to open a restaurant: received (with their status code), or rejected (with the reason).
    RESTAURANT_APPLICATION,
    // To a former owner after the Super Admin gave their restaurant to someone else.
    OWNERSHIP_TRANSFERRED,
    TEST
}
