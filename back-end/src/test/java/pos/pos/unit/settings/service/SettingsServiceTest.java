package pos.pos.unit.settings.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.UpdateSettingsBillingRequest;
import pos.pos.settings.dto.UpdateSettingsDefaultBranchRequest;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.mapper.SettingsMapper;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;
import pos.pos.settings.service.SettingsService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingsService")
class SettingsServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Mock
    private SettingsDomainSupport settingsDomainSupport;

    @Mock
    private SettingsMapper settingsMapper;

    @Mock
    private SettingsAuditService settingsAuditService;

    @InjectMocks
    private SettingsService settingsService;

    @Test
    @DisplayName("Should map settings returned by domain support on read")
    void shouldMapSettingsReturnedByDomainSupportOnRead() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000304"))
                .restaurantId(RESTAURANT_ID)
                .build());

        SettingsResponse response = settingsService.getSettings(authentication, RESTAURANT_ID);

        assertThat(response.getRestaurantId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    @DisplayName("Should surface access errors from domain support")
    void shouldSurfaceAccessErrorsFromDomainSupport() {
        Authentication authentication = authentication();

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID))
                .thenThrow(new AuthException("You are not allowed to manage settings for this restaurant", HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> settingsService.getSettings(authentication, RESTAURANT_ID))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to manage settings for this restaurant");

        verify(settingsDomainSupport, never()).loadOrCreateSettings(any(Restaurant.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should clear service charge and cash rounding fields when disabled")
    void shouldClearServiceChargeAndCashRoundingFieldsWhenDisabled() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);
        settings.setServiceChargeEnabled(true);
        settings.setServiceChargeValue(BigDecimal.valueOf(12.5));
        settings.setCashRoundingEnabled(true);
        settings.setCashRoundingIncrement(BigDecimal.valueOf(0.05));

        UpdateSettingsBillingRequest request = UpdateSettingsBillingRequest.builder()
                .serviceChargeEnabled(false)
                .serviceChargeType(pos.pos.settings.enums.ServiceChargeType.PERCENTAGE)
                .serviceChargeValue(BigDecimal.valueOf(10))
                .cashRoundingEnabled(false)
                .cashRoundingIncrement(BigDecimal.valueOf(0.05))
                .allowSplitBills(false)
                .requireCustomerForInvoice(true)
                .build();

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsDomainSupport.saveSettings(settings)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        settingsService.updateBilling(authentication, RESTAURANT_ID, request);

        assertThat(settings.isServiceChargeEnabled()).isFalse();
        assertThat(settings.getServiceChargeType()).isNull();
        assertThat(settings.getServiceChargeValue()).isNull();
        assertThat(settings.isCashRoundingEnabled()).isFalse();
        assertThat(settings.getCashRoundingIncrement()).isNull();
        assertThat(settings.isAllowSplitBills()).isFalse();
        assertThat(settings.isRequireCustomerForInvoice()).isTrue();
        assertThat(settings.getUpdatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("Should reject percentage service charge values above 100")
    void shouldRejectPercentageServiceChargeAbove100() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);

        UpdateSettingsBillingRequest request = UpdateSettingsBillingRequest.builder()
                .serviceChargeEnabled(true)
                .serviceChargeType(pos.pos.settings.enums.ServiceChargeType.PERCENTAGE)
                .serviceChargeValue(BigDecimal.valueOf(150))
                .cashRoundingEnabled(false)
                .allowSplitBills(true)
                .requireCustomerForInvoice(false)
                .build();

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);

        assertThatThrownBy(() -> settingsService.updateBilling(authentication, RESTAURANT_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("serviceChargeValue must not exceed 100 for percentage service charge");
    }

    @Test
    @DisplayName("Should reset core settings back to defaults")
    void shouldResetCoreSettingsBackToDefaults() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);
        settings.setDefaultLanguage("fr");
        settings.setDateFormat("dd/MM/yyyy");
        settings.setTimeFormat("hh:mm a");
        settings.setOrderSequencePrefix("SALE");
        settings.setInvoiceSequencePrefix("BILL");
        settings.setServiceChargeEnabled(true);
        settings.setServiceChargeValue(BigDecimal.TEN);
        settings.setEnableDelivery(true);
        settings.setAllowSplitBills(false);

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsDomainSupport.saveSettings(settings)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        settingsService.resetSettings(authentication, RESTAURANT_ID);

        assertThat(settings.getDefaultLanguage()).isEqualTo("en");
        assertThat(settings.getDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(settings.getTimeFormat()).isEqualTo("HH:mm");
        assertThat(settings.getOrderSequencePrefix()).isEqualTo("ORD");
        assertThat(settings.getInvoiceSequencePrefix()).isEqualTo("INV");
        assertThat(settings.isServiceChargeEnabled()).isFalse();
        assertThat(settings.getServiceChargeValue()).isNull();
        assertThat(settings.isEnableDelivery()).isFalse();
        assertThat(settings.isAllowSplitBills()).isTrue();
    }

    @Test
    @DisplayName("Should resolve and save default branch through domain support")
    void shouldResolveAndSaveDefaultBranchThroughDomainSupport() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);
        Branch branch = new Branch();
        branch.setId(UUID.fromString("00000000-0000-0000-0000-000000000305"));

        UpdateSettingsDefaultBranchRequest request = UpdateSettingsDefaultBranchRequest.builder()
                .defaultBranchId(branch.getId())
                .build();

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsDomainSupport.resolveBranch(RESTAURANT_ID, branch.getId())).thenReturn(branch);
        when(settingsDomainSupport.saveSettings(settings)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        settingsService.updateDefaultBranch(authentication, RESTAURANT_ID, request);

        assertThat(settings.getDefaultBranch()).isEqualTo(branch);
        assertThat(settings.getUpdatedBy()).isEqualTo(ACTOR_ID);
        verify(settingsDomainSupport).resolveBranch(RESTAURANT_ID, branch.getId());
        verify(settingsAuditService).log(eq(restaurant), eq(branch), eq("SETTINGS"), isNull(), eq("UPDATE_DEFAULT_BRANCH"), anyString(), eq(ACTOR_ID));
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("settings.admin@pos.example")
                        .username("settings.admin")
                        .active(true)
                        .build(),
                null
        );
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Settings Restaurant");
        restaurant.setLegalName("Settings Restaurant LLC");
        restaurant.setCode("SETTINGS_RESTAURANT");
        restaurant.setSlug("settings-restaurant");
        restaurant.setCurrency("USD");
        restaurant.setTimezone("Europe/Berlin");
        return restaurant;
    }
}
