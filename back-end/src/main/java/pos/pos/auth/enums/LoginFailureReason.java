package pos.pos.auth.enums;

public enum LoginFailureReason {
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_INACTIVE,
    EMAIL_NOT_VERIFIED,
    ACCOUNT_RATE_LIMITED,
    IP_RATE_LIMITED,
    DEVICE_RATE_LIMITED,
    // PIN sign-in on a connected till: a PIN nobody in the restaurant has, and a till paused after 5 of them.
    INVALID_PIN,
    PIN_PAUSED
}
