package pos.pos.restaurant.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.utils.NormalizationUtils;

import java.util.UUID;

/**
 * Branch is the physical operating location of a restaurant.
 *
 * FUTURE RELATION: orders.branch_id -> branches.id
 * FUTURE RELATION: tables.branch_id -> branches.id
 * FUTURE RELATION: branch_menu_items.branch_id -> branches.id
 * FUTURE RELATION: inventory-locations.branch_id -> branches.id
 * FUTURE RELATION: shifts.branch_id -> branches.id
 * FUTURE RELATION: devices.branch_id -> branches.id
 */
@Entity
@Table(
        name = "branches",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_branches_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_branches_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_branches_status", columnList = "status"),
                @Index(name = "idx_branches_manager_user_id", columnList = "manager_user_id"),
                @Index(name = "idx_branches_created_by", columnList = "created_by"),
                @Index(name = "idx_branches_updated_by", columnList = "updated_by"),
                @Index(name = "idx_branches_deleted_at", columnList = "deleted_at")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND char_length(btrim(code)) > 0
        AND status IN ('ACTIVE', 'INACTIVE', 'TEMPORARILY_CLOSED', 'ARCHIVED')
        """)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Branch extends AbstractAuditedSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_branches_restaurant")
    )
    private Restaurant restaurant;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BranchStatus status = BranchStatus.ACTIVE;

    // FUTURE FK: branches.manager_user_id -> users.id
    @Column(name = "manager_user_id", columnDefinition = "uuid")
    private UUID managerUserId;

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
        code = normalizeCode(code == null ? name : code);
        description = NormalizationUtils.normalize(description);
        email = NormalizationUtils.normalizeLower(email);
        phone = NormalizationUtils.normalize(phone);
    }

    private String normalizeCode(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? null : sanitized;
    }
}
