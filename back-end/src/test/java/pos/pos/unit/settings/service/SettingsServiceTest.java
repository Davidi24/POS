package pos.pos.unit.settings.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.UpdateSettingsBillingRequest;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.mapper.SettingsMapper;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.settings.service.SettingsService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingsService")
class SettingsServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID OTHER_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @Mock
    private SettingsMapper settingsMapper;

    @InjectMocks
    private SettingsService settingsService;

    @Test
    @DisplayName("Should create default settings on first read when none exist")
    void shouldCreateDefaultSettingsOnFirstReadWhenNoneExist() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();

        when(roleHierarchyService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(true);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurant_Id(RESTAURANT_ID)).thenReturn(Optional.empty());
        when(settingsRepository.saveAndFlush(any(Settings.class))).thenAnswer(invocation -> {
            Settings saved = invocation.getArgument(0);
            saved.setId(UUID.fromString("00000000-0000-0000-0000-000000000304"));
            return saved;
        });
        when(settingsMapper.toResponse(any(Settings.class))).thenReturn(SettingsResponse.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000304"))
                .restaurantId(RESTAURANT_ID)
                .build());

        SettingsResponse response = settingsService.getSettings(authentication, RESTAURANT_ID);

        ArgumentCaptor<Settings> settingsCaptor = ArgumentCaptor.forClass(Settings.class);
        verify(settingsRepository).saveAndFlush(settingsCaptor.capture());
        Settings createdSettings = settingsCaptor.getValue();

        assertThat(createdSettings.getRestaurant()).isEqualTo(restaurant);
        assertThat(createdSettings.getCreatedBy()).isEqualTo(ACTOR_ID);
        assertThat(createdSettings.getUpdatedBy()).isEqualTo(ACTOR_ID);
        assertThat(createdSettings.getReceiptSettings()).isNotNull();
        assertThat(createdSettings.getOrderRuleSettings()).isNotNull();
        assertThat(response.getRestaurantId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    @DisplayName("Should reject access when a non-super-admin targets another restaurant")
    void shouldRejectAccessWhenActorTargetsAnotherRestaurant() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        User actor = User.builder()
                .id(ACTOR_ID)
                .restaurantId(OTHER_RESTAURANT_ID)
                .email("manager@pos.example")
                .passwordHash("hash")
                .firstName("Manager")
                .lastName("User")
                .build();

        when(roleHierarchyService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(false);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(userRepository.findByIdAndDeletedAtIsNull(ACTOR_ID)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> settingsService.getSettings(authentication, RESTAURANT_ID))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to manage settings for this restaurant");

        verify(settingsRepository, never()).findByRestaurant_Id(RESTAURANT_ID);
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

        when(roleHierarchyService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(true);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurant_Id(RESTAURANT_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.saveAndFlush(settings)).thenReturn(settings);
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

        when(roleHierarchyService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(true);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurant_Id(RESTAURANT_ID)).thenReturn(Optional.of(settings));

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

        when(roleHierarchyService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(roleHierarchyService.isSuperAdmin(authentication)).thenReturn(true);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(settingsRepository.findByRestaurant_Id(RESTAURANT_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.saveAndFlush(settings)).thenReturn(settings);
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
