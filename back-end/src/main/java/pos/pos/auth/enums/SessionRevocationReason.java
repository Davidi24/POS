package pos.pos.auth.enums;

public enum SessionRevocationReason {
    EXPIRED,
    LOGOUT,
    LOGOUT_ALL,
    REUSE_DETECTED,
    SESSION_LIMIT,
    TOKEN_USER_MISMATCH,
    USER_NOT_ALLOWED
}
