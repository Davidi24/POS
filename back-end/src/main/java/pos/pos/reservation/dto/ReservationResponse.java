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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ReservationTableAssignmentResponse> tableAssignments;
}
