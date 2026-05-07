package pos.pos.device.entity;

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
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.device.enums.DeviceAssignmentType;
import pos.pos.restaurant.entity.Branch;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "\"device-assignments\"",
        indexes = {
                @Index(name = "idx_device_assignments_device_id", columnList = "device_id"),
                @Index(name = "idx_device_assignments_branch_id", columnList = "branch_id"),
                @Index(name = "idx_device_assignments_user_id", columnList = "user_id"),
                @Index(name = "idx_device_assignments_assigned_by", columnList = "assigned_by"),
                @Index(name = "idx_device_assignments_is_active", columnList = "is_active")
        }
)
@Check(constraints = """
        char_length(btrim(assignment_type)) > 0
        AND (branch_id IS NOT NULL OR user_id IS NOT NULL)
        AND (unassigned_at IS NULL OR unassigned_at > assigned_at)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class DeviceAssignment extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "device_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_device_assignments_device")
    )
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_device_assignments_branch")
    )
    private Branch branch;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    // to whom is this device like to a specific user or to a branch
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 30)
    private DeviceAssignmentType assignmentType;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime assignedAt;

    @Column(name = "unassigned_at", columnDefinition = "timestamptz")
    private OffsetDateTime unassignedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "assigned_by", columnDefinition = "uuid")
    private UUID assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", insertable = false, updatable = false)
    private User assignedByUser;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    protected void normalizeFields() {
        notes = NormalizationUtils.normalize(notes);

        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }

        if (unassignedAt != null) {
            active = false;
        }
    }

    @Override
    protected void validateState() {
        if (branch == null && userId == null) {
            throw new IllegalStateException("device assignment must target a branch or a user");
        }

        if (assignmentType == null) {
            throw new IllegalStateException("assignmentType is required");
        }

        if (unassignedAt != null && !unassignedAt.isAfter(assignedAt)) {
            throw new IllegalStateException("unassignedAt must be after assignedAt");
        }

        if (device != null && branch != null && device.getRestaurant() != null && branch.getRestaurant() != null) {
            if (!Objects.equals(device.getRestaurant().getId(), branch.getRestaurant().getId())) {
                throw new IllegalStateException("assignment branch must belong to the same restaurant as the device");
            }
        }

        if (device != null && user != null && device.getRestaurant() != null) {
            if (!Objects.equals(device.getRestaurant().getId(), user.getRestaurantId())) {
                throw new IllegalStateException("assigned user must belong to the same restaurant as the device");
            }
        }

        if (device != null && assignedByUser != null && device.getRestaurant() != null && assignedByUser.getRestaurantId() != null) {
            if (!Objects.equals(device.getRestaurant().getId(), assignedByUser.getRestaurantId())) {
                throw new IllegalStateException("assignedBy user must belong to the same restaurant as the device");
            }
        }
    }

    public void closeAt(OffsetDateTime closedAt) {
        unassignedAt = closedAt;
        active = false;
    }
}
