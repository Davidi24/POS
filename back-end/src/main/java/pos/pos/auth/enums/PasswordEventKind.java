package pos.pos.auth.enums;

/** What the daily password limits count (table auth_password_events). */
public enum PasswordEventKind {
    // A link (or a fresh invitation) sent from "Forgot password?".
    RESET_LINK_SENT,
    // The "no more links today" email.
    RESET_LIMIT_EMAIL,
    // A password changed while signed in.
    CHANGED,
    // The "no more changes today" email.
    CHANGE_LIMIT_EMAIL
}
