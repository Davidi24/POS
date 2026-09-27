package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationDepositStatus;
import pos.pos.reservation.enums.ReservationSource;
import pos.pos.reservation.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private UUID customerId;
    private String customerCode;
    private String customerName;
    private String reservationCode;
    private ReservationSource source;
    private ReservationStatus status;
    private Integer partySize;
    private OffsetDateTime reservationStart;
    private OffsetDateTime reservationEnd;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String seatingPreference;
    private String specialRequests;
    private String internalNotes;
    private Boolean depositRequired;
    private BigDecimal depositAmount;
    private ReservationDepositStatus depositStatus;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    private String cancellationReason;
    private OffsetDateTime checkedInAt;
    private OffsetDateTime seatedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime noShowAt;
    private OffsetDateTime expiredAt;
    // When the table stops waiting for the guests: the booking time plus the hold, or later if staff held it longer.
    private OffsetDateTime holdUntil;
    // "3 of 6 arrived"; empty until check-in.
    private Integer arrivedGuests;
    // A visit staff must look at: the booking time is over but the guests were never seated, or it's still open
    // from an earlier day. Never ended automatically.
    private Boolean needsReview;
    private String reviewReason;
    // "✓ Attendance confirmed" and by whom (STAFF after a call, GUEST from the reminder).
    private OffsetDateTime attendanceConfirmedAt;
    private pos.pos.reservation.enums.AttendanceConfirmedVia attendanceConfirmedVia;
    // For confirmed bookings: CONFIRMED, WAITING (not due yet), CONFIRM_NOW (booked less than 2 h ahead),
    // NOT_CONFIRMED (the deadline passed). Empty for other statuses.
    private String attendance;
    // The guest's no-shows counted for the warning (after the last time a manager cleared it).
    private Integer guestNoShows;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ReservationTableAssignmentResponse> tableAssignments;
}
