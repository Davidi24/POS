package pos.pos.reservation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReservationRequest {

    private UUID branchId;
    private UUID customerId;

    private ReservationSource source;

    @Min(value = 1, message = "partySize must be greater than 0")
    private Integer partySize;

    private OffsetDateTime reservationStart;
    private OffsetDateTime reservationEnd;

    @Size(max = 150, message = "contactName must be at most 150 characters")
    private String contactName;

    @Size(max = 50, message = "contactPhone must be at most 50 characters")
    private String contactPhone;

    @Size(max = 150, message = "contactEmail must be at most 150 characters")
    private String contactEmail;

    @Size(max = 50, message = "seatingPreference must be at most 50 characters")
    private String seatingPreference;

    private String specialRequests;
    private String internalNotes;
    private Boolean depositRequired;

    @DecimalMin(value = "0.0", inclusive = true, message = "depositAmount must not be negative")
    private BigDecimal depositAmount;

    private List<UUID> tableIds;
    private UUID primaryTableId;
}
