package pos.pos.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.audit.enums.AuditSeverity;
import pos.pos.audit.enums.AuditSource;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_audit_logs_branch_id", columnList = "branch_id"),
                @Index(name = "idx_audit_logs_actor_user_id", columnList = "actor_user_id"),
                @Index(name = "idx_audit_logs_entity_type", columnList = "entity_type"),
                @Index(name = "idx_audit_logs_action", columnList = "action"),
                @Index(name = "idx_audit_logs_source", columnList = "source"),
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
        }
)
@Check(constraints = """
        source IN ('POS', 'BACK_OFFICE', 'API', 'WEBHOOK', 'SYSTEM', 'KDS', 'DEVICE')
        AND severity IN ('INFO', 'WARNING', 'CRITICAL')
        AND char_length(btrim(entity_type)) > 0
        AND char_length(btrim(action)) > 0
        AND char_length(btrim(summary)) > 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class AuditLog extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_audit_logs_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_audit_logs_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "actor_user_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_audit_logs_actor_user")
    )
    private User actorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AuditSource source = AuditSource.API;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AuditSeverity severity = AuditSeverity.INFO;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", columnDefinition = "uuid")
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "summary", nullable = false, length = 255)
    private String summary;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;

    @Column(name = "before_state", columnDefinition = "text")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "text")
    private String afterState;

    @Column(name = "metadata_payload", columnDefinition = "text")
    private String metadataPayload;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Override
    protected void normalizeFields() {
        entityType = NormalizationUtils.normalizeUpper(entityType);
        action = NormalizationUtils.normalizeUpper(action);
        summary = NormalizationUtils.normalize(summary);
        referenceType = NormalizationUtils.normalizeUpper(referenceType);
        beforeState = NormalizationUtils.normalize(beforeState);
        afterState = NormalizationUtils.normalize(afterState);
        metadataPayload = NormalizationUtils.normalize(metadataPayload);
        ipAddress = NormalizationUtils.normalize(ipAddress);
        userAgent = NormalizationUtils.normalize(userAgent);
    }

    @Override
    protected void validateState() {
        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("audit log branch must belong to the same restaurant");
            }
        }
    }
}
