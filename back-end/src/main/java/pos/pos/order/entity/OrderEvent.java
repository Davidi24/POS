package pos.pos.order.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.order.enums.OrderEventType;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "order_events",
        indexes = {
                @Index(name = "idx_order_events_order_id", columnList = "order_id"),
                @Index(name = "idx_order_events_created_by", columnList = "created_by"),
                @Index(name = "idx_order_events_created_at", columnList = "created_at"),
                @Index(name = "idx_order_events_event_type", columnList = "event_type")
        }
)
@Check(constraints = "event_type IS NOT NULL")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderEvent {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_order_events_order")
    )
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private OrderEventType eventType;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_order_events_created_by_user")
    )
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

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

    private void normalizeFields() {
        note = NormalizationUtils.normalize(note);
    }

    private void validateState() {
        if (eventType == null) {
            throw new IllegalStateException("eventType is required");
        }
    }
}
