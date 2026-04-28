package pos.pos.settings.mapper;

import org.springframework.stereotype.Component;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.entity.Settings;

@Component
public class SettingsMapper {

    public SettingsResponse toResponse(Settings settings) {
        if (settings == null) {
            return null;
        }

        return SettingsResponse.builder()
                .id(settings.getId())
                .restaurantId(settings.getRestaurant() == null ? null : settings.getRestaurant().getId())
                .defaultBranchId(settings.getDefaultBranch() == null ? null : settings.getDefaultBranch().getId())
                .defaultLanguage(settings.getDefaultLanguage())
                .dateFormat(settings.getDateFormat())
                .timeFormat(settings.getTimeFormat())
                .weekStartDay(settings.getWeekStartDay())
                .orderSequencePrefix(settings.getOrderSequencePrefix())
                .invoiceSequencePrefix(settings.getInvoiceSequencePrefix())
                .reservationSlotMinutes(settings.getReservationSlotMinutes())
                .defaultTableTurnTimeMinutes(settings.getDefaultTableTurnTimeMinutes())
                .serviceChargeEnabled(settings.isServiceChargeEnabled())
                .serviceChargeType(settings.getServiceChargeType())
                .serviceChargeValue(settings.getServiceChargeValue())
                .cashRoundingEnabled(settings.isCashRoundingEnabled())
                .cashRoundingIncrement(settings.getCashRoundingIncrement())
                .allowSplitBills(settings.isAllowSplitBills())
                .allowOpenTickets(settings.isAllowOpenTickets())
                .requireCustomerForInvoice(settings.isRequireCustomerForInvoice())
                .enableQrOrdering(settings.isEnableQrOrdering())
                .enableTakeaway(settings.isEnableTakeaway())
                .enableDelivery(settings.isEnableDelivery())
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .createdBy(settings.getCreatedBy())
                .updatedBy(settings.getUpdatedBy())
                .build();
    }
}
