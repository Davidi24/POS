package pos.pos.auth.enums;

/** Why a password link was emailed: an owner's invitation to set a first password, or a reset. */
public enum PasswordLinkPurpose {
    RESET,
    INVITE
}
