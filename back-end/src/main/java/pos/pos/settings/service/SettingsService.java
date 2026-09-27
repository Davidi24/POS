package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.security.rbac.AppRole;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.UpdateRestaurantSettingsRequest;
import pos.pos.settings.dto.UpdateSettingsBillingRequest;
import pos.pos.settings.dto.UpdateSettingsDefaultBranchRequest;
import pos.pos.settings.dto.UpdateSettingsLocalizationRequest;
import pos.pos.settings.dto.UpdateSettingsOrderChannelsRequest;
import pos.pos.settings.dto.UpdateSettingsSequencePrefixesRequest;
import pos.pos.settings.dto.UpdateSettingsPreOrdersRequest;
import pos.pos.settings.dto.UpdateSettingsReservationPolicyRequest;
import pos.pos.settings.dto.UpdateSettingsStaffPermissionsRequest;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.enums.ServiceChargeType;
import pos.pos.settings.enums.WeekStartDay;
import pos.pos.settings.mapper.SettingsMapper;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsMapper settingsMapper;
    private final SettingsAuditService settingsAuditService;
    private final RoleHierarchyService roleHierarchyService;

    @Transactional
    public SettingsResponse getSettings(Authentication authentication, UUID restaurantId) {
        return settingsMapper.toResponse(loadSettingsContext(authentication, restaurantId).settings());
    }

    @Transactional
    public SettingsResponse updateSettings(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantSettingsRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();

        validateLocalization(request.getDefaultLanguage(), request.getDateFormat(), request.getTimeFormat());
        applyResolvedBranch(settings, restaurantId, request.getDefaultBranchId());
        applyCoreFields(settings, request);

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_CORE",
                "Replaced restaurant settings core fields"
        );
    }

    @Transactional
    public SettingsResponse updateDefaultBranch(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsDefaultBranchRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();

        settings.setDefaultBranch(settingsDomainSupport.resolveBranch(restaurantId, request.getDefaultBranchId()));
        return saveSettingsAndAudit(
                context,
                settings,
                settings.getDefaultBranch(),
                "UPDATE_DEFAULT_BRANCH",
                "Updated default branch setting"
        );
    }

    @Transactional
    public SettingsResponse updateLocalization(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsLocalizationRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();

        validateLocalization(request.getDefaultLanguage(), request.getDateFormat(), request.getTimeFormat());
        applyLocalizationFields(
                settings,
                request.getDefaultLanguage(),
                request.getDateFormat(),
                request.getTimeFormat(),
                request.getWeekStartDay(),
                request.getReservationSlotMinutes(),
                request.getDefaultTableTurnTimeMinutes()
        );

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_LOCALIZATION",
                "Updated localization settings"
        );
    }

    @Transactional
    public SettingsResponse updateSequencePrefixes(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsSequencePrefixesRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();

        applySequencePrefixFields(settings, request.getOrderSequencePrefix(), request.getInvoiceSequencePrefix());

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_SEQUENCE_PREFIXES",
                "Updated settings sequence prefixes"
        );
    }

    @Transactional
    public SettingsResponse updateBilling(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsBillingRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();

        if (request.getOrderTaxRate() != null) settings.setOrderTaxRate(request.getOrderTaxRate());
        if (request.getOrderTaxInclusive() != null) settings.setOrderTaxInclusive(request.getOrderTaxInclusive());

        applyBilling(
                settings,
                request.getServiceChargeEnabled(),
                request.getServiceChargeType(),
                request.getServiceChargeValue(),
                request.getCashRoundingEnabled(),
                request.getCashRoundingIncrement(),
                request.getAllowSplitBills(),
                request.getRequireCustomerForInvoice()
        );

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_BILLING",
                "Updated billing settings"
        );
    }

    @Transactional
    public SettingsResponse updateOrderChannels(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsOrderChannelsRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();

        applyOrderChannelFields(
                settings,
                request.getAllowOpenTickets(),
                request.getEnableQrOrdering(),
                request.getEnableTakeaway(),
                request.getEnableDelivery()
        );

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_ORDER_CHANNELS",
                "Updated order channel settings"
        );
    }

    @Transactional
    public SettingsResponse updateStaffPermissions(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsStaffPermissionsRequest request
    ) {
        // Admins hold SETTINGS_UPDATE too, so this switch needs an explicit Owner/Co-Owner check or they could grant it to themselves.
        if (roleHierarchyService.actorRank(authentication) < AppRole.CO_OWNER.rank()) {
            throw new AuthException("Only the Owner or a Co-Owner can change staff permissions", HttpStatus.FORBIDDEN);
        }

        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();
        settings.setAdminsCanManageManagers(request.getAdminsCanManageManagers());

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_STAFF_PERMISSIONS",
                "Updated staff permissions"
        );
    }

    @Transactional
    public SettingsResponse updatePreOrders(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsPreOrdersRequest request
    ) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();
        settings.setPreOrdersEnabled(request.getPreOrdersEnabled());
        settings.setPreOrderLeadMinutes(request.getPreOrderLeadMinutes());

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_PRE_ORDERS",
                "Updated reservation pre-order settings"
        );
    }

    @Transactional
    public SettingsResponse updateReservationPolicy(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsReservationPolicyRequest request
    ) {
        // The "hold ends" warning has to come before the hold itself runs out.
        if (request.getHoldWarningMinutes() >= request.getHoldMinutes()) {
            throw new AuthException("The hold warning must come before the hold ends", HttpStatus.BAD_REQUEST);
        }
        if (request.getLateAfterMinutes() >= request.getHoldMinutes()) {
            throw new AuthException("A guest must count as late before the hold ends", HttpStatus.BAD_REQUEST);
        }
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();
        settings.setLargeGroupFrom(request.getLargeGroupFrom());
        settings.setLargeGroupExtraMinutes(request.getLargeGroupExtraMinutes());
        settings.setApprovalGroupSize(request.getApprovalGroupSize());
        settings.setHoldMinutes(request.getHoldMinutes());
        settings.setHoldWarningMinutes(request.getHoldWarningMinutes());
        settings.setCheckInOpensMinutes(request.getCheckInOpensMinutes());
        settings.setConfirmReminderTime(request.getConfirmReminderTime());
        settings.setSameDayConfirmMinutes(request.getSameDayConfirmMinutes());
        settings.setAttendanceCallMinutes(request.getAttendanceCallMinutes());
        settings.setReopenWindowMinutes(request.getReopenWindowMinutes());
        settings.setUndoSeatMinutes(request.getUndoSeatMinutes());
        settings.setRunningLateMaxMinutes(request.getRunningLateMaxMinutes());
        settings.setLateAfterMinutes(request.getLateAfterMinutes());
        settings.setGuestReminderHours(request.getGuestReminderHours());
        settings.setNoShowWarningFrom(request.getNoShowWarningFrom());
        settings.setDepositFromGuests(request.getDepositFromGuests());

        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "UPDATE_RESERVATION_POLICY",
                "Updated reservation policy settings"
        );
    }

    @Transactional
    public SettingsResponse resetSettings(Authentication authentication, UUID restaurantId) {
        SettingsContext context = loadSettingsContext(authentication, restaurantId);
        Settings settings = context.settings();

        applyDefaults(settings);
        return saveSettingsAndAudit(
                context,
                settings,
                null,
                "RESET",
                "Reset restaurant settings to defaults"
        );
    }

    private void applyResolvedBranch(Settings settings, UUID restaurantId, UUID defaultBranchId) {
        if (defaultBranchId == null) {
            settings.setDefaultBranch(null);
            return;
        }

        settings.setDefaultBranch(settingsDomainSupport.resolveBranch(restaurantId, defaultBranchId));
    }

    private void applyCoreFields(Settings settings, UpdateRestaurantSettingsRequest request) {
        applyLocalizationFields(
                settings,
                request.getDefaultLanguage(),
                request.getDateFormat(),
                request.getTimeFormat(),
                request.getWeekStartDay(),
                request.getReservationSlotMinutes(),
                request.getDefaultTableTurnTimeMinutes()
        );
        applySequencePrefixFields(settings, request.getOrderSequencePrefix(), request.getInvoiceSequencePrefix());
        applyBilling(
                settings,
                request.getServiceChargeEnabled(),
                request.getServiceChargeType(),
                request.getServiceChargeValue(),
                request.getCashRoundingEnabled(),
                request.getCashRoundingIncrement(),
                request.getAllowSplitBills(),
                request.getRequireCustomerForInvoice()
        );
        applyOrderChannelFields(
                settings,
                request.getAllowOpenTickets(),
                request.getEnableQrOrdering(),
                request.getEnableTakeaway(),
                request.getEnableDelivery()
        );
    }

    private void applyLocalizationFields(
            Settings settings,
            String defaultLanguage,
            String dateFormat,
            String timeFormat,
            WeekStartDay weekStartDay,
            int reservationSlotMinutes,
            int defaultTableTurnTimeMinutes
    ) {
        settings.setDefaultLanguage(defaultLanguage);
        settings.setDateFormat(dateFormat);
        settings.setTimeFormat(timeFormat);
        settings.setWeekStartDay(weekStartDay);
        settings.setReservationSlotMinutes(reservationSlotMinutes);
        settings.setDefaultTableTurnTimeMinutes(defaultTableTurnTimeMinutes);
    }

    private void applySequencePrefixFields(Settings settings, String orderSequencePrefix, String invoiceSequencePrefix) {
        settings.setOrderSequencePrefix(orderSequencePrefix);
        settings.setInvoiceSequencePrefix(invoiceSequencePrefix);
    }

    private void applyOrderChannelFields(
            Settings settings,
            Boolean allowOpenTickets,
            Boolean enableQrOrdering,
            Boolean enableTakeaway,
            Boolean enableDelivery
    ) {
        settings.setAllowOpenTickets(Boolean.TRUE.equals(allowOpenTickets));
        settings.setEnableQrOrdering(Boolean.TRUE.equals(enableQrOrdering));
        settings.setEnableTakeaway(Boolean.TRUE.equals(enableTakeaway));
        settings.setEnableDelivery(Boolean.TRUE.equals(enableDelivery));
    }

    private void applyBilling(
            Settings settings,
            Boolean serviceChargeEnabled,
            ServiceChargeType serviceChargeType,
            BigDecimal serviceChargeValue,
            Boolean cashRoundingEnabled,
            BigDecimal cashRoundingIncrement,
            Boolean allowSplitBills,
            Boolean requireCustomerForInvoice
    ) {
        validateBilling(serviceChargeEnabled, serviceChargeType, serviceChargeValue, cashRoundingEnabled, cashRoundingIncrement);

        settings.setServiceChargeEnabled(Boolean.TRUE.equals(serviceChargeEnabled));
        settings.setServiceChargeType(Boolean.TRUE.equals(serviceChargeEnabled) ? serviceChargeType : null);
        settings.setServiceChargeValue(Boolean.TRUE.equals(serviceChargeEnabled) ? serviceChargeValue : null);
        settings.setCashRoundingEnabled(Boolean.TRUE.equals(cashRoundingEnabled));
        settings.setCashRoundingIncrement(Boolean.TRUE.equals(cashRoundingEnabled) ? cashRoundingIncrement : null);
        settings.setAllowSplitBills(Boolean.TRUE.equals(allowSplitBills));
        settings.setRequireCustomerForInvoice(Boolean.TRUE.equals(requireCustomerForInvoice));
    }

    private void applyDefaults(Settings settings) {
        Settings defaults = new Settings();

        settings.setDefaultBranch(defaults.getDefaultBranch());
        applyLocalizationFields(
                settings,
                defaults.getDefaultLanguage(),
                defaults.getDateFormat(),
                defaults.getTimeFormat(),
                defaults.getWeekStartDay(),
                defaults.getReservationSlotMinutes(),
                defaults.getDefaultTableTurnTimeMinutes()
        );
        applySequencePrefixFields(settings, defaults.getOrderSequencePrefix(), defaults.getInvoiceSequencePrefix());
        applyBilling(
                settings,
                defaults.isServiceChargeEnabled(),
                defaults.getServiceChargeType(),
                defaults.getServiceChargeValue(),
                defaults.isCashRoundingEnabled(),
                defaults.getCashRoundingIncrement(),
                defaults.isAllowSplitBills(),
                defaults.isRequireCustomerForInvoice()
        );
        applyOrderChannelFields(
                settings,
                defaults.isAllowOpenTickets(),
                defaults.isEnableQrOrdering(),
                defaults.isEnableTakeaway(),
                defaults.isEnableDelivery()
        );
        applyReservationPolicyDefaults(settings, defaults);
    }

    private void applyReservationPolicyDefaults(Settings settings, Settings defaults) {
        settings.setPreOrdersEnabled(defaults.isPreOrdersEnabled());
        settings.setPreOrderLeadMinutes(defaults.getPreOrderLeadMinutes());
        settings.setLargeGroupFrom(defaults.getLargeGroupFrom());
        settings.setLargeGroupExtraMinutes(defaults.getLargeGroupExtraMinutes());
        settings.setApprovalGroupSize(defaults.getApprovalGroupSize());
        settings.setHoldMinutes(defaults.getHoldMinutes());
        settings.setHoldWarningMinutes(defaults.getHoldWarningMinutes());
        settings.setCheckInOpensMinutes(defaults.getCheckInOpensMinutes());
        settings.setConfirmReminderTime(defaults.getConfirmReminderTime());
        settings.setSameDayConfirmMinutes(defaults.getSameDayConfirmMinutes());
        settings.setAttendanceCallMinutes(defaults.getAttendanceCallMinutes());
        settings.setReopenWindowMinutes(defaults.getReopenWindowMinutes());
        settings.setUndoSeatMinutes(defaults.getUndoSeatMinutes());
        settings.setRunningLateMaxMinutes(defaults.getRunningLateMaxMinutes());
        settings.setLateAfterMinutes(defaults.getLateAfterMinutes());
        settings.setGuestReminderHours(defaults.getGuestReminderHours());
        settings.setNoShowWarningFrom(defaults.getNoShowWarningFrom());
        settings.setDepositFromGuests(defaults.getDepositFromGuests());
    }

    private void validateLocalization(String defaultLanguage, String dateFormat, String timeFormat) {
        String normalizedLanguage = NormalizationUtils.normalize(defaultLanguage);
        if (normalizedLanguage == null) {
            throw new AuthException("defaultLanguage is required", HttpStatus.BAD_REQUEST);
        }

        Locale locale = Locale.forLanguageTag(normalizedLanguage.replace('_', '-'));
        if (locale.getLanguage().isBlank()) {
            throw new AuthException("defaultLanguage must be a valid BCP 47 language tag", HttpStatus.BAD_REQUEST);
        }

        validatePattern(dateFormat, "dateFormat");
        validatePattern(timeFormat, "timeFormat");
    }

    private void validatePattern(String pattern, String fieldName) {
        try {
            DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException ex) {
            throw new AuthException(fieldName + " must be a valid date/time pattern", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateBilling(
            Boolean serviceChargeEnabled,
            ServiceChargeType serviceChargeType,
            BigDecimal serviceChargeValue,
            Boolean cashRoundingEnabled,
            BigDecimal cashRoundingIncrement
    ) {
        if (Boolean.TRUE.equals(serviceChargeEnabled)) {
            if (serviceChargeType == null || serviceChargeValue == null) {
                throw new AuthException(
                        "serviceChargeType and serviceChargeValue are required when serviceChargeEnabled is true",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (serviceChargeType == ServiceChargeType.PERCENTAGE
                    && serviceChargeValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new AuthException(
                        "serviceChargeValue must not exceed 100 for percentage service charge",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        if (serviceChargeValue != null && serviceChargeValue.signum() < 0) {
            throw new AuthException("serviceChargeValue must not be negative", HttpStatus.BAD_REQUEST);
        }

        if (Boolean.TRUE.equals(cashRoundingEnabled) && cashRoundingIncrement == null) {
            throw new AuthException(
                    "cashRoundingIncrement is required when cashRoundingEnabled is true",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (cashRoundingIncrement != null && cashRoundingIncrement.signum() <= 0) {
            throw new AuthException("cashRoundingIncrement must be greater than 0", HttpStatus.BAD_REQUEST);
        }
    }

    private SettingsContext loadSettingsContext(Authentication authentication, UUID restaurantId) {
        UUID actorId = settingsDomainSupport.currentActorId(authentication);
        Restaurant restaurant = settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        Settings settings = settingsDomainSupport.loadOrCreateSettings(restaurant, actorId);
        return new SettingsContext(actorId, restaurant, settings);
    }

    private SettingsResponse saveSettingsAndAudit(
            SettingsContext context,
            Settings settings,
            Branch branch,
            String action,
            String message
    ) {
        settings.setUpdatedBy(context.actorId());
        Settings savedSettings = settingsDomainSupport.saveSettings(settings);
        settingsAuditService.log(
                context.restaurant(),
                branch,
                "SETTINGS",
                savedSettings.getId(),
                action,
                message,
                context.actorId()
        );
        return settingsMapper.toResponse(savedSettings);
    }

    private record SettingsContext(UUID actorId, Restaurant restaurant, Settings settings) {
    }
}
