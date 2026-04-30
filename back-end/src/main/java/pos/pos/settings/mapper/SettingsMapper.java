package pos.pos.settings.mapper;

import org.springframework.stereotype.Component;
import pos.pos.settings.dto.BusinessHourResponse;
import pos.pos.settings.dto.OrderRuleSettingsResponse;
import pos.pos.settings.dto.ReceiptSettingsResponse;
import pos.pos.settings.dto.ReservationRuleResponse;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.SpecialHourResponse;
import pos.pos.settings.entity.SettingsBusinessHour;
import pos.pos.settings.entity.SettingsOrderRule;
import pos.pos.settings.entity.SettingsReceipt;
import pos.pos.settings.entity.SettingsReservationRule;
import pos.pos.settings.entity.SettingsSpecialHour;
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

    public ReceiptSettingsResponse toReceiptResponse(SettingsReceipt receiptSettings) {
        if (receiptSettings == null) {
            return null;
        }

        return ReceiptSettingsResponse.builder()
                .id(receiptSettings.getId())
                .settingsId(receiptSettings.getSettings() == null ? null : receiptSettings.getSettings().getId())
                .restaurantId(receiptSettings.getSettings() == null || receiptSettings.getSettings().getRestaurant() == null
                        ? null
                        : receiptSettings.getSettings().getRestaurant().getId())
                .autoPrintCustomerReceipt(receiptSettings.isAutoPrintCustomerReceipt())
                .autoPrintKitchenTicket(receiptSettings.isAutoPrintKitchenTicket())
                .receiptCopies(receiptSettings.getReceiptCopies())
                .showLogo(receiptSettings.isShowLogo())
                .showTaxBreakdown(receiptSettings.isShowTaxBreakdown())
                .showServerName(receiptSettings.isShowServerName())
                .showTableName(receiptSettings.isShowTableName())
                .showOrderNumber(receiptSettings.isShowOrderNumber())
                .showQrCode(receiptSettings.isShowQrCode())
                .printVoidedItems(receiptSettings.isPrintVoidedItems())
                .footerNote(receiptSettings.getFooterNote())
                .createdAt(receiptSettings.getCreatedAt())
                .updatedAt(receiptSettings.getUpdatedAt())
                .build();
    }

    public OrderRuleSettingsResponse toOrderRuleResponse(SettingsOrderRule orderRuleSettings) {
        if (orderRuleSettings == null) {
            return null;
        }

        return OrderRuleSettingsResponse.builder()
                .id(orderRuleSettings.getId())
                .settingsId(orderRuleSettings.getSettings() == null ? null : orderRuleSettings.getSettings().getId())
                .restaurantId(orderRuleSettings.getSettings() == null || orderRuleSettings.getSettings().getRestaurant() == null
                        ? null
                        : orderRuleSettings.getSettings().getRestaurant().getId())
                .autoFireToKitchen(orderRuleSettings.isAutoFireToKitchen())
                .allowItemVoid(orderRuleSettings.isAllowItemVoid())
                .allowDiscountWithoutManager(orderRuleSettings.isAllowDiscountWithoutManager())
                .allowBackdatedOrders(orderRuleSettings.isAllowBackdatedOrders())
                .requireReasonForVoid(orderRuleSettings.isRequireReasonForVoid())
                .requireReasonForDiscount(orderRuleSettings.isRequireReasonForDiscount())
                .mergeOrdersEnabled(orderRuleSettings.isMergeOrdersEnabled())
                .transferOrdersEnabled(orderRuleSettings.isTransferOrdersEnabled())
                .reopenClosedOrdersEnabled(orderRuleSettings.isReopenClosedOrdersEnabled())
                .createdAt(orderRuleSettings.getCreatedAt())
                .updatedAt(orderRuleSettings.getUpdatedAt())
                .build();
    }

    public ReservationRuleResponse toReservationRuleResponse(SettingsReservationRule reservationRule) {
        if (reservationRule == null) {
            return null;
        }

        return ReservationRuleResponse.builder()
                .id(reservationRule.getId())
                .settingsId(reservationRule.getSettings() == null ? null : reservationRule.getSettings().getId())
                .restaurantId(reservationRule.getSettings() == null || reservationRule.getSettings().getRestaurant() == null
                        ? null
                        : reservationRule.getSettings().getRestaurant().getId())
                .branchId(reservationRule.getBranch() == null ? null : reservationRule.getBranch().getId())
                .ruleName(reservationRule.getRuleName())
                .priority(reservationRule.getPriority())
                .active(reservationRule.isActive())
                .effectiveFrom(reservationRule.getEffectiveFrom())
                .effectiveTo(reservationRule.getEffectiveTo())
                .advanceBookingDays(reservationRule.getAdvanceBookingDays())
                .minPartySize(reservationRule.getMinPartySize())
                .maxPartySize(reservationRule.getMaxPartySize())
                .defaultDurationMinutes(reservationRule.getDefaultDurationMinutes())
                .bufferMinutes(reservationRule.getBufferMinutes())
                .allowOnlineReservations(reservationRule.isAllowOnlineReservations())
                .requireDeposit(reservationRule.isRequireDeposit())
                .depositType(reservationRule.getDepositType())
                .depositValue(reservationRule.getDepositValue())
                .autoConfirmReservations(reservationRule.isAutoConfirmReservations())
                .cancellationWindowHours(reservationRule.getCancellationWindowHours())
                .createdAt(reservationRule.getCreatedAt())
                .updatedAt(reservationRule.getUpdatedAt())
                .build();
    }

    public BusinessHourResponse toBusinessHourResponse(SettingsBusinessHour businessHour) {
        if (businessHour == null) {
            return null;
        }

        return BusinessHourResponse.builder()
                .id(businessHour.getId())
                .branchId(businessHour.getBranch() == null ? null : businessHour.getBranch().getId())
                .dayOfWeek(businessHour.getDayOfWeek())
                .openTime(businessHour.getOpenTime())
                .closeTime(businessHour.getCloseTime())
                .closed(businessHour.isClosed())
                .overnight(businessHour.isOvernight())
                .createdAt(businessHour.getCreatedAt())
                .updatedAt(businessHour.getUpdatedAt())
                .build();
    }

    public SpecialHourResponse toSpecialHourResponse(SettingsSpecialHour specialHour) {
        if (specialHour == null) {
            return null;
        }

        return SpecialHourResponse.builder()
                .id(specialHour.getId())
                .branchId(specialHour.getBranch() == null ? null : specialHour.getBranch().getId())
                .specialDate(specialHour.getSpecialDate())
                .openTime(specialHour.getOpenTime())
                .closeTime(specialHour.getCloseTime())
                .closed(specialHour.isClosed())
                .note(specialHour.getNote())
                .createdAt(specialHour.getCreatedAt())
                .updatedAt(specialHour.getUpdatedAt())
                .build();
    }
}
