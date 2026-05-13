package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.settings.enums.DepositType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRuleResponse {

    private UUID id;
    private UUID settingsId;
    private UUID restaurantId;
    private UUID branchId;
    private String ruleName;
    private Integer priority;
    private Boolean active;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
    private Integer advanceBookingDays;
    private Integer minPartySize;
    private Integer maxPartySize;
    private Integer defaultDurationMinutes;
    private Integer bufferMinutes;
    private Boolean allowOnlineReservations;
    private Boolean requireDeposit;
    private DepositType depositType;
    private BigDecimal depositValue;
    private Boolean autoConfirmReservations;
    private Integer cancellationWindowHours;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
