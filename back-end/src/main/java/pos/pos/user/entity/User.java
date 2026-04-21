package pos.pos.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import pos.pos.utils.AuditedEntityLifecycle;
import pos.pos.utils.EntityLifecycleUtils;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_normalized_phone", columnNames = "normalized_phone")
        },
        indexes = {
                @Index(name = "idx_users_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_users_default_branch_id", columnList = "default_branch_id"),
                @Index(name = "idx_users_status", columnList = "status"),
                @Index(name = "idx_users_email_verified", columnList = "email_verified"),
                @Index(name = "idx_users_phone_verified", columnList = "phone_verified"),
                @Index(name = "idx_users_locked_until", columnList = "locked_until"),
                @Index(name = "idx_users_created_by", columnList = "created_by"),
                @Index(name = "idx_users_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = "pin_attempts >= 0 AND failed_login_attempts >= 0")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User implements AuditedEntityLifecycle {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Version
    private Long version;

    @Column(name = "restaurant_id", columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "default_branch_id", columnDefinition = "uuid")
    private UUID defaultBranchId;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "pin_hash", columnDefinition = "text")
    private String pinHash;

    @Builder.Default
    @Column(name = "pin_enabled", nullable = false)
    private boolean pinEnabled = false;

    @Builder.Default
    @Column(name = "pin_attempts", nullable = false)
    private int pinAttempts = 0;

    @Column(name = "pin_locked_until", columnDefinition = "timestamptz")
    private OffsetDateTime pinLockedUntil;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "normalized_phone", length = 50)
    private String normalizedPhone;

    @Builder.Default
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "phone_verified_at", columnDefinition = "timestamptz")
    private OffsetDateTime phoneVerifiedAt;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at", columnDefinition = "timestamptz")
    private OffsetDateTime emailVerifiedAt;

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until", columnDefinition = "timestamptz")
    private OffsetDateTime lockedUntil;

    @Column(name = "password_updated_at", columnDefinition = "timestamptz")
    private OffsetDateTime passwordUpdatedAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "last_login_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastLoginAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    @PrePersist
    public void prePersist() {
        normalizeFields();
        EntityLifecycleUtils.initializeAuditedEntity(this);
    }

    @PreUpdate
    public void preUpdate() {
        normalizeFields();
        EntityLifecycleUtils.touch(this);
    }

    private void normalizeFields() {
        email = NormalizationUtils.normalizeLower(email);
        username = NormalizationUtils.normalizeLower(username);
        firstName = NormalizationUtils.normalize(firstName);
        lastName = NormalizationUtils.normalize(lastName);
        phone = NormalizationUtils.normalize(phone);
        normalizedPhone = NormalizationUtils.normalizePhone(phone);
        avatarUrl = NormalizationUtils.normalize(avatarUrl);
        status = NormalizationUtils.normalizeUpper(status);
    }
}
