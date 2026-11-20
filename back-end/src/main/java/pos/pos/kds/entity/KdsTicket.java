package pos.pos.kds.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.order.entity.Order;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "kds_tickets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kds_tickets_restaurant_ticket_number", columnNames = {"restaurant_id", "ticket_number"})
        },
        indexes = {
                @Index(name = "idx_kds_tickets_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_kds_tickets_branch_id", columnList = "branch_id"),
                @Index(name = "idx_kds_tickets_station_id", columnList = "station_id"),
                @Index(name = "idx_kds_tickets_order_id", columnList = "order_id"),
                @Index(name = "idx_kds_tickets_status", columnList = "status"),
                @Index(name = "idx_kds_tickets_due_at", columnList = "due_at"),
                @Index(name = "idx_kds_tickets_created_by", columnList = "created_by"),
                @Index(name = "idx_kds_tickets_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(ticket_number)) > 0
        AND status IN ('PENDING', 'FIRED', 'IN_PROGRESS', 'READY', 'EXPO_READY', 'COMPLETED', 'CANCELLED')
        AND priority IN ('NORMAL', 'RUSH', 'VIP', 'HOLD_FIRE')
        AND (
            completed_at IS NULL
            OR ready_at IS NOT NULL
        )
        AND (
            ready_at IS NULL
            OR fired_at IS NOT NULL
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class KdsTicket extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_tickets_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_tickets_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "station_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_tickets_station")
    )
    private KdsStation station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_tickets_order")
    )
    private Order order;

    @Column(name = "ticket_number", nullable = false, length = 50)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private KdsTicketStatus status = KdsTicketStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private KdsPriority priority = KdsPriority.NORMAL;

    @Column(name = "course_name", length = 50)
    private String courseName;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Column(name = "fired_at", columnDefinition = "timestamptz")
    private OffsetDateTime firedAt;

    @Column(name = "started_at", columnDefinition = "timestamptz")
    private OffsetDateTime startedAt;

    @Column(name = "ready_at", columnDefinition = "timestamptz")
    private OffsetDateTime readyAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private OffsetDateTime completedAt;

    @Column(name = "due_at", columnDefinition = "timestamptz")
    private OffsetDateTime dueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_kds_tickets_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_kds_tickets_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "kdsTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<KdsTicketItem> items = new ArrayList<>();

    public void addItem(KdsTicketItem item) {
        if (item == null) {
            return;
        }

        items.add(item);
        item.setKdsTicket(this);
    }

    public void removeItem(KdsTicketItem item) {
        if (item == null) {
            return;
        }

        items.remove(item);
        item.setKdsTicket(null);
    }

    @Override
    protected void normalizeFields() {
        ticketNumber = NormalizationUtils.normalizeUpper(ticketNumber);
        courseName = NormalizationUtils.normalize(courseName);
        notes = NormalizationUtils.normalize(notes);
        voidReason = NormalizationUtils.normalize(voidReason);
    }

    @Override
    protected void validateState() {
        if (restaurant != null && branch != null && branch.getRestaurant() != null
                && !Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
            throw new IllegalStateException("kds ticket branch must belong to the same restaurant");
        }

        if (restaurant != null && station != null && station.getRestaurant() != null
                && !Objects.equals(station.getRestaurant().getId(), restaurant.getId())) {
            throw new IllegalStateException("kds ticket station must belong to the same restaurant");
        }

        if (branch != null && station != null && station.getBranch() != null
                && !Objects.equals(station.getBranch().getId(), branch.getId())) {
            throw new IllegalStateException("kds ticket station must belong to the same branch");
        }

        if (restaurant != null && order != null && order.getRestaurant() != null
                && !Objects.equals(order.getRestaurant().getId(), restaurant.getId())) {
            throw new IllegalStateException("kds ticket order must belong to the same restaurant");
        }

        if (branch != null && order != null && order.getBranch() != null
                && !Objects.equals(order.getBranch().getId(), branch.getId())) {
            throw new IllegalStateException("kds ticket order must belong to the same branch");
        }

        if (completedAt != null && readyAt == null) {
            throw new IllegalStateException("completedAt requires readyAt");
        }

        if (readyAt != null && firedAt == null) {
            throw new IllegalStateException("readyAt requires firedAt");
        }
    }
}
