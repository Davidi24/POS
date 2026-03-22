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
        name = "auth-email-verification-tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_email_verification_tokens_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_auth_email_verification_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_auth_email_verification_tokens_expires_at", columnList = "expires_at"),
                @Index(name = "idx_auth_email_verification_tokens_used_at", columnList = "used_at")
        }
)
@Check(constraints = "expires_at > created_at")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuthEmailVerificationToken {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "text")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime expiresAt;

    @Column(name = "used_at", columnDefinition = "timestamptz")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}