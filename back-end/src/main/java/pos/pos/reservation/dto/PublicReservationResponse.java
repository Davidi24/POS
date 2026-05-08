package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationDepositStatus;
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
public class PublicReservationResponse {

    private UUID id;
    private String reservationCode;
    private UUID restaurantId;
    private String restaurantName;
    private String restaurantSlug;
    private UUID branchId;
    private String branchName;
    private String branchCode;
    private ReservationStatus status;
    private Integer partySize;
    private OffsetDateTime reservationStart;
    private OffsetDateTime reservationEnd;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String seatingPreference;
    private String specialRequests;
    private Boolean depositRequired;
    private BigDecimal depositAmount;
    private ReservationDepositStatus depositStatus;
    private List<ReservationTableAssignmentResponse> tableAssignments;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
