package pos.pos.tables.specialcase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Something the system did by itself because nobody acted; Return puts it back.
@Entity
@Table(name = "special_case_actions")
@Getter
@Setter
@NoArgsConstructor
public class SpecialCaseAction {

    public enum Kind { TABLE_FREED, ORDER_CLOSED, BOOKING_MOVED, BOOKING_NOT_HANDLED, BOOKING_NO_ACTION }

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private Kind kind;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "table_number", length = 30)
    private String tableNumber;

    @Column(name = "moved_to", length = 30)
    private String movedTo;

    // The orders closed with the table, comma separated.
    @Column(name = "order_ids", length = 400)
    private String orderIds;

    @Column(name = "reservation_id")
    private UUID reservationId;

    // "tableId=guests;tableId=guests", the table and those merged into it.
    @Column(name = "guests_by_table", length = 2000)
    private String guestsByTable;

    @Column(name = "seated_at", columnDefinition = "timestamptz")
    private OffsetDateTime seatedAt;

    // Its two lines in the list: what happened, and the details.
    @Column(name = "summary", nullable = false, length = 300)
    private String summary;

    @Column(name = "detail", length = 300)
    private String detail;

    // The guests who were at the table, or the booking's size.
    @Column(name = "guests")
    private Integer guests;

    @Column(name = "happened_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime happenedAt;

    @Column(name = "returned_at", columnDefinition = "timestamptz")
    private OffsetDateTime returnedAt;

    @Column(name = "returned_by")
    private UUID returnedBy;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (happenedAt == null) {
            happenedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
