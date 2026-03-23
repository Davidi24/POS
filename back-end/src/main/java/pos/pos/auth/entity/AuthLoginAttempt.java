package pos.pos.auth.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "auth_login_attempts",
        indexes = {
                @Index(name = "idx_auth_login_attempts_email", columnList = "email"),
                @Index(name = "idx_auth_login_attempts_ip_address", columnList = "ip_address"),
                @Index(name = "idx_auth_login_attempts_attempted_at", columnList = "attempted_at"),
                @Index(name = "idx_auth_login_attempts_success", columnList = "success"),
                @Index(name = "idx_auth_login_attempts_user_id", columnList = "user_id")
        }
)
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

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Builder.Default
    @Column(name = "success", nullable = false)
    private boolean success = false;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

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
