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
    SESSION_REVOKED
}
