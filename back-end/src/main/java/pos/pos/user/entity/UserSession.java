package pos.pos.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;

@Entity
@Table(
        name = "\"user-sessions\"",
        indexes = {
                @Index(name = "user_sessions_user_idx", columnList = "user_id"),
                @Index(name = "user_sessions_expires_idx", columnList = "expires_at"),
                @Index(name = "user_sessions_refresh_token_idx", columnList = "refresh_token_hash")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "refresh_token_hash", nullable = false)
    private String refreshTokenHash;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_used_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private Boolean revoked = false;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
    }
}