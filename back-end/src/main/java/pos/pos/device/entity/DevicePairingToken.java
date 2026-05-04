package pos.pos.device.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "\"device-pairing-tokens\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_pairing_tokens_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_device_pairing_tokens_device_id", columnList = "device_id"),
                @Index(name = "idx_device_pairing_tokens_expires_at", columnList = "expires_at"),
                @Index(name = "idx_device_pairing_tokens_created_by", columnList = "created_by")
        }
)
@Check(constraints = """
        expires_at > created_at
        AND (used_at IS NULL OR used_at >= created_at)
        AND (revoked_at IS NULL OR revoked_at >= created_at)
        AND (used_at IS NULL OR revoked_at IS NULL)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DevicePairingToken {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "device_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_device_pairing_tokens_device")
    )
    private Device device;

    @Column(name = "token_hash", nullable = false, columnDefinition = "text")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime expiresAt;

    @Column(name = "used_at", columnDefinition = "timestamptz")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private OffsetDateTime revokedAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "requested_ip", columnDefinition = "inet")
    private String requestedIp;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @PrePersist
    protected void prePersist() {
        normalizeFields();
        validateState();

        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void preUpdate() {
        normalizeFields();
        validateState();
    }

    private void normalizeFields() {
        tokenHash = NormalizationUtils.normalize(tokenHash);
        requestedIp = NormalizationUtils.normalize(requestedIp);
    }

    private void validateState() {
        if (tokenHash == null) {
            throw new IllegalStateException("tokenHash is required");
        }

        if (expiresAt == null) {
            throw new IllegalStateException("expiresAt is required");
        }

        OffsetDateTime referenceCreatedAt = createdAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : createdAt;
        if (!expiresAt.isAfter(referenceCreatedAt)) {
            throw new IllegalStateException("expiresAt must be after createdAt");
        }

        if (usedAt != null && usedAt.isBefore(referenceCreatedAt)) {
            throw new IllegalStateException("usedAt must not be before createdAt");
        }

        if (revokedAt != null && revokedAt.isBefore(referenceCreatedAt)) {
            throw new IllegalStateException("revokedAt must not be before createdAt");
        }

        if (usedAt != null && revokedAt != null) {
            throw new IllegalStateException("pairing token cannot be both used and revoked");
        }
    }

    public boolean isActiveAt(OffsetDateTime now) {
        return usedAt == null
                && revokedAt == null
                && expiresAt != null
                && expiresAt.isAfter(now);
    }

    public void revokeAt(OffsetDateTime revokedAtValue) {
        if (usedAt != null || revokedAt != null) {
            return;
        }
        revokedAt = revokedAtValue;
    }
}
