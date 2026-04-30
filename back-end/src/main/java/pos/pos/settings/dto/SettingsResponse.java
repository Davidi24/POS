package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.settings.enums.ServiceChargeType;
import pos.pos.settings.enums.WeekStartDay;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID defaultBranchId;
    private String defaultLanguage;
    private String dateFormat;
    private String timeFormat;
    private WeekStartDay weekStartDay;
    private String orderSequencePrefix;
    private String invoiceSequencePrefix;
    private Integer reservationSlotMinutes;
    private Integer defaultTableTurnTimeMinutes;
    private Boolean serviceChargeEnabled;
    private ServiceChargeType serviceChargeType;
    private BigDecimal serviceChargeValue;
    private Boolean cashRoundingEnabled;
    private BigDecimal cashRoundingIncrement;
    private Boolean allowSplitBills;
    private Boolean allowOpenTickets;
    private Boolean requireCustomerForInvoice;
    private Boolean enableQrOrdering;
    private Boolean enableTakeaway;
    private Boolean enableDelivery;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
}
