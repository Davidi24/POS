package pos.pos.auth.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "\"user-sessions\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_sessions_token_id", columnNames = "token_id")
        },
        indexes = {
                @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_user_sessions_expires_at", columnList = "expires_at"),
                @Index(name = "idx_user_sessions_refresh_token_hash", columnList = "refresh_token_hash"),
                @Index(name = "idx_user_sessions_token_id", columnList = "token_id"),
                @Index(name = "idx_user_sessions_revoked", columnList = "revoked")
        }
)
@Check(constraints = "(revoked = false) OR (revoked = true AND revoked_at IS NOT NULL)")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserSession {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "token_id", nullable = false, columnDefinition = "uuid")
    private UUID tokenId;

    @Column(name = "session_type", nullable = false, length = 30)
    private String sessionType;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "refresh_token_hash", nullable = false, columnDefinition = "text")
    private String refreshTokenHash;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "last_used_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime expiresAt;

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (createdAt == null) {
            createdAt = now;
        }
    }
}