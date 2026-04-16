package pos.pos.auth.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import pos.pos.auth.enums.LoginFailureReason;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * ENTITY PURPOSE:
 * <p>
 * This entity records every login attempt made to the system.
 * <p>
 * It is used for:
 * - Security monitoring (detect brute-force attacks, suspicious activity)
 * - Account protection (tracking failed attempts and lock conditions)
 * - Auditing (who attempted to log in, from where, and when)
 * - Debugging authentication issues
 * <p>
 * WHAT IS STORED:
 * - userId: the user (if exists), otherwise null
 * - identifier: the username or email entered during login
 * - ipAddress: source IP of the request
 * - userAgent: device/browser information
 * - success: whether login was successful
 * - failureReason: reason for failure (if any)
 * - attemptedAt: timestamp of the attempt (UTC)
 * <p>
 * NOTES:
 * - The submitted identifier is stored even if the user does not exist
 * - IP and User-Agent come from ClientInfo (proxy-aware extraction)
 * - This entity should NEVER be exposed directly via API
 * <p>
 * PRODUCTION IMPORTANCE:
 * This is a critical security component for tracking authentication behavior
 * and detecting potential attacks or misuse of the system.
 */
@Entity
@Table(name = "auth_login_attempts", indexes = {@Index(name = "idx_auth_login_attempts_identifier", columnList = "identifier"), @Index(name = "idx_auth_login_attempts_ip_address", columnList = "ip_address"), @Index(name = "idx_auth_login_attempts_attempted_at", columnList = "attempted_at"), @Index(name = "idx_auth_login_attempts_success", columnList = "success"), @Index(name = "idx_auth_login_attempts_user_id", columnList = "user_id")})
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuthLoginAttempt {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "identifier", length = 150)
    private String identifier;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Builder.Default
    @Column(name = "success", nullable = false)
    private boolean success = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 50)
    private LoginFailureReason failureReason;

    @Column(name = "attempted_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime attemptedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
        if (attemptedAt == null) {
            attemptedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
