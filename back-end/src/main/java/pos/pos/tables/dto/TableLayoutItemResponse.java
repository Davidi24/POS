package pos.pos.tables.dto;

import pos.pos.reservation.enums.ReservationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;
import pos.pos.order.enums.OrderFulfillmentStatus;
import pos.pos.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableLayoutItemResponse {

    private UUID tableId;
    private UUID mergedIntoTableId;
    private List<UUID> mergedTableIds;
    private String tableNumber;
    private String name;
    private Integer capacity;
    private Integer effectiveCapacity;
    private String floor;
    private BigDecimal positionX;
    private BigDecimal positionY;
    private BigDecimal rotationDegrees;
    private BigDecimal layoutScale;
    private TableShape shape;
    private TableStatus status;
    private Integer guestCount;
    private OffsetDateTime seatedAt;
    private OffsetDateTime nextReservationStart;
    private OffsetDateTime nextReservationEnd;
    private String nextReservationCode;
    private String nextReservationName;
    // For the floor plan's booking states: "Hold ends in 10 min", "Waiting for table", "3 of 6 arrived".
    private UUID nextReservationId;
    private ReservationStatus nextReservationStatus;
    private OffsetDateTime nextReservationHoldUntil;
    // From then the table shows "Hold ends in N min" (the hold minus the warning, e.g. 10 min before it ends).
    private OffsetDateTime nextReservationHoldWarningAt;
    private Integer nextReservationPartySize;
    private Integer nextReservationArrivedGuests;
    private UUID currentOrderId;
    private String currentOrderNumber;
    private OrderStatus currentOrderStatus;
    private OrderFulfillmentStatus currentOrderFulfillmentStatus;
    private Boolean active;
}
