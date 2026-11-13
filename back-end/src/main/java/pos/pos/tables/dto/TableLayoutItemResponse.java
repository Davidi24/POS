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
    // Seated with more guests still to come: the half ring.
    private boolean moreComing;
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
    // e.g. 🎂 for a birthday booking at this table.
    private String nextReservationOccasionIcon;
    private String nextReservationOccasionName;
    // The booking whose guests sit here now: what they celebrate shows while they're here (user, 2026-11-02).
    private UUID seatedReservationId;
    private String seatedReservationName;
    private OffsetDateTime seatedReservationStart;
    private Integer seatedReservationPartySize;
    private String seatedReservationOccasionIcon;
    private String seatedReservationOccasionName;
    private UUID currentOrderId;
    private String currentOrderNumber;
    private OrderStatus currentOrderStatus;
    private OrderFulfillmentStatus currentOrderFulfillmentStatus;
    private boolean currentOrderBillRequested;
    // The guests have paid and are still at the table (user, 2026-11-01): its open order is paid, or an order closed and paid
    // since they sat down.
    private boolean paid;
    private int currentOrderServedItems;
    private int currentOrderTotalItems;
    // How many guests can be seated here (with its merged tables), see TableSeatingRules.
    private Integer guestsMin;
    private Integer guestsUsual;
    private Integer guestsMax;
    // From then the guests here are in the way of the next booking (see TableSeatingRules); null when they aren't.
    private OffsetDateTime nextBookingClashAt;
    // Joining this table asks what to do with its booking.
    private boolean nextBookingSoon;
    // A free table whose booking is coming: the yellow ring.
    private boolean bookingComing;
    private OffsetDateTime moreTimeUntil;
    // When the booking's guests came, and the table it was moved from automatically.
    private OffsetDateTime nextReservationCheckedInAt;
    private String nextReservationMovedFrom;
    // The "Give more time" choices (Settings → Tables → Special cases), minutes separated by commas.
    private String moreTimeOptions;
    // Nothing done on this table or its order since then.
    private OffsetDateTime lastActionAt;
    // When the system steps in if nobody acts, and when it starts saying so; null when it won't.
    private OffsetDateTime autoFreeAt;
    private OffsetDateTime autoFreeWarningAt;
    private Boolean active;
}
