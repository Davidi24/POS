package pos.pos.auth.enums;

public enum SessionRevocationReason {
    EXPIRED,
    LOGOUT,
    LOGOUT_ALL,
    PASSWORD_RESET,
    REUSE_DETECTED,
    SESSION_LIMIT,
    TOKEN_USER_MISMATCH,
    USER_NOT_ALLOWED,
    SESSION_REVOKED,
    PASSWORD_CHANGED,
    // The person's restaurant was suspended, closed or removed.
    RESTAURANT_CLOSED,
    // The Super Admin gave the person's restaurant to a new owner.
    OWNERSHIP_TRANSFERRED
}
