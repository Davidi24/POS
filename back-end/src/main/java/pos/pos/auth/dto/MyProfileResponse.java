package pos.pos.auth.dto;

/** The signed-in person's own details for Profile; [pendingEmail] = a new address waiting for its confirmation link. */
public record MyProfileResponse(String firstName, String lastName, String username, String email, boolean emailVerified,
                                String pendingEmail, String phone, boolean phoneVerified, String photoUrl,
                                // Username changes left in the last 30 days (3 at most), and when the next one frees up when none is left.
                                int usernameChangesLeft, java.time.OffsetDateTime nextUsernameChangeAt) {
}
