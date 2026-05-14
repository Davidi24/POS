package pos.pos.kds.entity;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "kds_ticket_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kds_ticket_items_ticket_order_line_item", columnNames = {"kds_ticket_id", "order_line_item_id"})
        },
        indexes = {
                @Index(name = "idx_kds_ticket_items_kds_ticket_id", columnList = "kds_ticket_id"),
                @Index(name = "idx_kds_ticket_items_order_line_item_id", columnList = "order_line_item_id"),
                @Index(name = "idx_kds_ticket_items_status", columnList = "status")
        }
)
@Check(constraints = """
        char_length(btrim(item_name_snapshot)) > 0
        AND quantity > 0
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
public class KdsTicketItem extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "kds_ticket_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_ticket_items_kds_ticket")
    )
    private KdsTicket kdsTicket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_line_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_ticket_items_order_line_item")
    )
    private OrderLineItem orderLineItem;

    @Column(name = "item_name_snapshot", nullable = false, length = 150)
    private String itemNameSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private KdsTicketStatus status = KdsTicketStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private KdsPriority priority = KdsPriority.NORMAL;

    @Column(name = "seat_label", length = 30)
    private String seatLabel;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "fired_at", columnDefinition = "timestamptz")
    private OffsetDateTime firedAt;

    @Column(name = "ready_at", columnDefinition = "timestamptz")
    private OffsetDateTime readyAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private OffsetDateTime completedAt;

    @Override
    protected void normalizeFields() {
        if (itemNameSnapshot == null && orderLineItem != null) {
            itemNameSnapshot = orderLineItem.getItemNameSnapshot();
        }

        if (quantity <= 0 && orderLineItem != null) {
            quantity = orderLineItem.getQuantity();
        }

        itemNameSnapshot = NormalizationUtils.normalize(itemNameSnapshot);
        seatLabel = NormalizationUtils.normalize(seatLabel);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (quantity <= 0) {
            throw new IllegalStateException("quantity must be greater than zero");
        }

        if (kdsTicket != null && orderLineItem != null
                && kdsTicket.getOrder() != null
                && orderLineItem.getOrder() != null
                && !Objects.equals(kdsTicket.getOrder().getId(), orderLineItem.getOrder().getId())) {
            throw new IllegalStateException("kds ticket item must belong to the same order as the ticket");
        }

        if (completedAt != null && readyAt == null) {
            throw new IllegalStateException("completedAt requires readyAt");
        }

        if (readyAt != null && firedAt == null) {
            throw new IllegalStateException("readyAt requires firedAt");
        }
    }
}
