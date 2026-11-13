package pos.pos.auth.enums;

/** What an emailed password link can still do. */
public enum PasswordLinkStatus {
    VALID,
    EXPIRED,
    USED,
    // A newer email replaced this link (the invitation was sent again, or another reset was asked for).
    REPLACED,
    // The account behind the link was switched off or removed.
    UNAVAILABLE,
    // Unknown link: mistyped, cut short, or so old it was cleaned up.
    INVALID
}
