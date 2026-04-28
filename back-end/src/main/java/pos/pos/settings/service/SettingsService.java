package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.BranchNotFoundException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.UpdateRestaurantSettingsRequest;
import pos.pos.settings.dto.UpdateSettingsBillingRequest;
import pos.pos.settings.dto.UpdateSettingsDefaultBranchRequest;
import pos.pos.settings.dto.UpdateSettingsLocalizationRequest;
import pos.pos.settings.dto.UpdateSettingsOrderChannelsRequest;
import pos.pos.settings.dto.UpdateSettingsSequencePrefixesRequest;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.entity.SettingsOrderRule;
import pos.pos.settings.entity.SettingsReceipt;
import pos.pos.settings.enums.ServiceChargeType;
import pos.pos.settings.enums.WeekStartDay;
import pos.pos.settings.mapper.SettingsMapper;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final RoleHierarchyService roleHierarchyService;
    private final SettingsMapper settingsMapper;

    @Transactional
    public SettingsResponse getSettings(Authentication authentication, UUID restaurantId) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);
        return settingsMapper.toResponse(settings);
    }

    @Transactional
    public SettingsResponse updateSettings(
            Authentication authentication,
            UUID restaurantId,
            UpdateRestaurantSettingsRequest request
    ) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);

        validateLocalization(request.getDefaultLanguage(), request.getDateFormat(), request.getTimeFormat());
        applyResolvedBranch(settings, restaurantId, request.getDefaultBranchId());
        settings.setDefaultLanguage(request.getDefaultLanguage());
        settings.setDateFormat(request.getDateFormat());
        settings.setTimeFormat(request.getTimeFormat());
        settings.setWeekStartDay(request.getWeekStartDay());
        settings.setOrderSequencePrefix(request.getOrderSequencePrefix());
        settings.setInvoiceSequencePrefix(request.getInvoiceSequencePrefix());
        settings.setReservationSlotMinutes(request.getReservationSlotMinutes());
        settings.setDefaultTableTurnTimeMinutes(request.getDefaultTableTurnTimeMinutes());
        applyBilling(settings,
                request.getServiceChargeEnabled(),
                request.getServiceChargeType(),
                request.getServiceChargeValue(),
                request.getCashRoundingEnabled(),
                request.getCashRoundingIncrement(),
                request.getAllowSplitBills(),
                request.getRequireCustomerForInvoice());
        settings.setAllowOpenTickets(Boolean.TRUE.equals(request.getAllowOpenTickets()));
        settings.setEnableQrOrdering(Boolean.TRUE.equals(request.getEnableQrOrdering()));
        settings.setEnableTakeaway(Boolean.TRUE.equals(request.getEnableTakeaway()));
        settings.setEnableDelivery(Boolean.TRUE.equals(request.getEnableDelivery()));
        settings.setUpdatedBy(actorId);

        return settingsMapper.toResponse(saveSettings(settings));
    }

    @Transactional
    public SettingsResponse updateDefaultBranch(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsDefaultBranchRequest request
    ) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);

        settings.setDefaultBranch(resolveBranch(restaurantId, request.getDefaultBranchId()));
        settings.setUpdatedBy(actorId);

        return settingsMapper.toResponse(saveSettings(settings));
    }

    @Transactional
    public SettingsResponse updateLocalization(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsLocalizationRequest request
    ) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);

        validateLocalization(request.getDefaultLanguage(), request.getDateFormat(), request.getTimeFormat());

        settings.setDefaultLanguage(request.getDefaultLanguage());
        settings.setDateFormat(request.getDateFormat());
        settings.setTimeFormat(request.getTimeFormat());
        settings.setWeekStartDay(request.getWeekStartDay());
        settings.setReservationSlotMinutes(request.getReservationSlotMinutes());
        settings.setDefaultTableTurnTimeMinutes(request.getDefaultTableTurnTimeMinutes());
        settings.setUpdatedBy(actorId);

        return settingsMapper.toResponse(saveSettings(settings));
    }

    @Transactional
    public SettingsResponse updateSequencePrefixes(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsSequencePrefixesRequest request
    ) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);

        settings.setOrderSequencePrefix(request.getOrderSequencePrefix());
        settings.setInvoiceSequencePrefix(request.getInvoiceSequencePrefix());
        settings.setUpdatedBy(actorId);

        return settingsMapper.toResponse(saveSettings(settings));
    }

    @Transactional
    public SettingsResponse updateBilling(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsBillingRequest request
    ) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);

        applyBilling(settings,
                request.getServiceChargeEnabled(),
                request.getServiceChargeType(),
                request.getServiceChargeValue(),
                request.getCashRoundingEnabled(),
                request.getCashRoundingIncrement(),
                request.getAllowSplitBills(),
                request.getRequireCustomerForInvoice());
        settings.setUpdatedBy(actorId);

        return settingsMapper.toResponse(saveSettings(settings));
    }

    @Transactional
    public SettingsResponse updateOrderChannels(
            Authentication authentication,
            UUID restaurantId,
            UpdateSettingsOrderChannelsRequest request
    ) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);

        settings.setAllowOpenTickets(Boolean.TRUE.equals(request.getAllowOpenTickets()));
        settings.setEnableQrOrdering(Boolean.TRUE.equals(request.getEnableQrOrdering()));
        settings.setEnableTakeaway(Boolean.TRUE.equals(request.getEnableTakeaway()));
        settings.setEnableDelivery(Boolean.TRUE.equals(request.getEnableDelivery()));
        settings.setUpdatedBy(actorId);

        return settingsMapper.toResponse(saveSettings(settings));
    }

    @Transactional
    public SettingsResponse resetSettings(Authentication authentication, UUID restaurantId) {
        UUID actorId = roleHierarchyService.currentUserId(authentication);
        Restaurant restaurant = findAccessibleRestaurant(authentication, restaurantId);
        Settings settings = loadOrCreateSettings(restaurant, actorId);

        applyDefaults(settings);
        settings.setUpdatedBy(actorId);

        return settingsMapper.toResponse(saveSettings(settings));
    }

    private Restaurant findAccessibleRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);

        if (roleHierarchyService.isSuperAdmin(authentication)) {
            return restaurant;
        }

        User actor = userRepository.findByIdAndDeletedAtIsNull(roleHierarchyService.currentUserId(authentication))
                .orElseThrow(UserNotFoundException::new);

        if (!restaurantId.equals(actor.getRestaurantId())) {
            throw new AuthException(
                    "You are not allowed to manage settings for this restaurant",
                    HttpStatus.FORBIDDEN
            );
        }

        return restaurant;
    }

    private Settings loadOrCreateSettings(Restaurant restaurant, UUID actorId) {
        Settings settings = settingsRepository.findByRestaurant_Id(restaurant.getId())
                .orElseGet(() -> createDefaultSettings(restaurant, actorId));
        return ensureChildSettings(settings, actorId);
    }

    private Settings createDefaultSettings(Restaurant restaurant, UUID actorId) {
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);
        settings.setCreatedBy(actorId);
        settings.setUpdatedBy(actorId);
        settings.setReceiptSettings(new SettingsReceipt());
        settings.setOrderRuleSettings(new SettingsOrderRule());

        try {
            return settingsRepository.saveAndFlush(settings);
        } catch (DataIntegrityViolationException ex) {
            return settingsRepository.findByRestaurant_Id(restaurant.getId())
                    .orElseThrow(() -> ex);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private Settings ensureChildSettings(Settings settings, UUID actorId) {
        boolean changed = false;

        if (settings.getReceiptSettings() == null) {
            settings.setReceiptSettings(new SettingsReceipt());
            changed = true;
        }

        if (settings.getOrderRuleSettings() == null) {
            settings.setOrderRuleSettings(new SettingsOrderRule());
            changed = true;
        }

        if (!changed) {
            return settings;
        }

        settings.setUpdatedBy(actorId);
        return saveSettings(settings);
    }

    private void applyResolvedBranch(Settings settings, UUID restaurantId, UUID defaultBranchId) {
        if (defaultBranchId == null) {
            settings.setDefaultBranch(null);
            return;
        }

        settings.setDefaultBranch(resolveBranch(restaurantId, defaultBranchId));
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
        settings.setDefaultLanguage(defaults.getDefaultLanguage());
        settings.setDateFormat(defaults.getDateFormat());
        settings.setTimeFormat(defaults.getTimeFormat());
        settings.setWeekStartDay(defaults.getWeekStartDay());
        settings.setOrderSequencePrefix(defaults.getOrderSequencePrefix());
        settings.setInvoiceSequencePrefix(defaults.getInvoiceSequencePrefix());
        settings.setReservationSlotMinutes(defaults.getReservationSlotMinutes());
        settings.setDefaultTableTurnTimeMinutes(defaults.getDefaultTableTurnTimeMinutes());
        settings.setServiceChargeEnabled(defaults.isServiceChargeEnabled());
        settings.setServiceChargeType(defaults.getServiceChargeType());
        settings.setServiceChargeValue(defaults.getServiceChargeValue());
        settings.setCashRoundingEnabled(defaults.isCashRoundingEnabled());
        settings.setCashRoundingIncrement(defaults.getCashRoundingIncrement());
        settings.setAllowSplitBills(defaults.isAllowSplitBills());
        settings.setAllowOpenTickets(defaults.isAllowOpenTickets());
        settings.setRequireCustomerForInvoice(defaults.isRequireCustomerForInvoice());
        settings.setEnableQrOrdering(defaults.isEnableQrOrdering());
        settings.setEnableTakeaway(defaults.isEnableTakeaway());
        settings.setEnableDelivery(defaults.isEnableDelivery());
    }

    private Branch resolveBranch(UUID restaurantId, UUID branchId) {
        return branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId)
                .orElseThrow(BranchNotFoundException::new);
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

    private Settings saveSettings(Settings settings) {
        try {
            return settingsRepository.saveAndFlush(settings);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Settings update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
