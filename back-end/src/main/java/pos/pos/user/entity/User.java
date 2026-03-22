package pos.pos.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "users_email_idx", columnList = "email", unique = true)
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "pin_hash")
    private String pinHash;

    @Builder.Default
    @Column(name = "pin_enabled", nullable = false)
    private Boolean pinEnabled = false;

    @Builder.Default
    @Column(name = "pin_attempts", nullable = false)
    private Integer pinAttempts = 0;

    @Column(name = "pin_locked_until", columnDefinition = "timestamptz")
    private OffsetDateTime pinLockedUntil;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String phone;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "password_updated_at", columnDefinition = "timestamptz")
    private OffsetDateTime passwordUpdatedAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    @Column(name = "last_login", columnDefinition = "timestamptz")
    private OffsetDateTime lastLogin;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until", columnDefinition = "timestamptz")
    private OffsetDateTime lockedUntil;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}