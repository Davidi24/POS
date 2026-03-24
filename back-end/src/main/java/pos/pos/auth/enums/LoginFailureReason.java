package pos.pos.auth.enums;

public enum LoginFailureReason {
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_INACTIVE,
    EMAIL_NOT_VERIFIED,
    IP_RATE_LIMITED
}