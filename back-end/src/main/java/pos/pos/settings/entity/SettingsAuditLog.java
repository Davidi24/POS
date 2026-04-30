package pos.pos.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.util.UUID;

//checked
@Entity
@Table(
        name = "\"settings-audit-logs\"",
        indexes = {
                @Index(name = "idx_settings_audit_logs_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_settings_audit_logs_branch_id", columnList = "branch_id"),
                @Index(name = "idx_settings_audit_logs_entity_type", columnList = "entity_type"),
                @Index(name = "idx_settings_audit_logs_action", columnList = "action"),
                @Index(name = "idx_settings_audit_logs_actor_user_id", columnList = "actor_user_id"),
                @Index(name = "idx_settings_audit_logs_created_at", columnList = "created_at")
        }
)
@Check(constraints = """
        char_length(btrim(entity_type)) > 0
        AND char_length(btrim(action)) > 0
        AND char_length(btrim(message)) > 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SettingsAuditLog extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_audit_logs_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_audit_logs_branch")
    )
    private Branch branch;

    // the id of the object it was changed
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", columnDefinition = "uuid")
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "actor_user_id", columnDefinition = "uuid")
    private UUID actorUserId;

    @Override
    protected void normalizeFields() {
        entityType = NormalizationUtils.normalizeUpper(entityType);
        action = NormalizationUtils.normalizeUpper(action);
        message = NormalizationUtils.normalize(message);
    }

    @Override
    protected void validateState() {
        if (restaurant == null) {
            throw new IllegalStateException("restaurant is required");
        }
    }
}
