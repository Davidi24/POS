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
import pos.pos.security.rbac.AppRole;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.UpdateSettingsBillingRequest;
import pos.pos.settings.dto.UpdateSettingsDefaultBranchRequest;
import pos.pos.settings.dto.UpdateSettingsPreOrdersRequest;
import pos.pos.settings.dto.UpdateSettingsReservationPolicyRequest;
import pos.pos.settings.dto.UpdateSettingsStaffPermissionsRequest;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.mapper.SettingsMapper;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;
import pos.pos.settings.service.SettingsService;

import java.math.BigDecimal;
import java.time.LocalTime;
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

    @Mock
    private RoleHierarchyService roleHierarchyService;

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

    @Test
    @DisplayName("Should refuse Admins changing the staff permissions switch")
    void shouldRefuseAdminsChangingStaffPermissions() {
        Authentication authentication = authentication();
        UpdateSettingsStaffPermissionsRequest request = UpdateSettingsStaffPermissionsRequest.builder()
                .adminsCanManageManagers(true)
                .build();

        when(roleHierarchyService.actorRank(authentication)).thenReturn(AppRole.ADMIN.rank());

        assertThatThrownBy(() -> settingsService.updateStaffPermissions(authentication, RESTAURANT_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Only the Owner or a Co-Owner can change staff permissions");

        verify(settingsDomainSupport, never()).saveSettings(any(Settings.class));
    }

    @Test
    @DisplayName("Should let a Co-Owner allow Admins to manage Managers")
    void shouldLetCoOwnerAllowAdminsToManageManagers() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);
        UpdateSettingsStaffPermissionsRequest request = UpdateSettingsStaffPermissionsRequest.builder()
                .adminsCanManageManagers(true)
                .build();

        when(roleHierarchyService.actorRank(authentication)).thenReturn(AppRole.CO_OWNER.rank());
        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsDomainSupport.saveSettings(settings)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        settingsService.updateStaffPermissions(authentication, RESTAURANT_ID, request);

        assertThat(settings.isAdminsCanManageManagers()).isTrue();
        verify(settingsAuditService).log(eq(restaurant), isNull(), eq("SETTINGS"), isNull(), eq("UPDATE_STAFF_PERMISSIONS"), anyString(), eq(ACTOR_ID));
    }

    @Test
    @DisplayName("Should turn pre-orders on with the chosen kitchen lead time")
    void shouldUpdatePreOrderSettings() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsDomainSupport.saveSettings(settings)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        settingsService.updatePreOrders(authentication, RESTAURANT_ID, UpdateSettingsPreOrdersRequest.builder()
                .preOrdersEnabled(true)
                .preOrderLeadMinutes(25)
                .build());

        assertThat(settings.isPreOrdersEnabled()).isTrue();
        assertThat(settings.getPreOrderLeadMinutes()).isEqualTo(25);
        verify(settingsAuditService).log(eq(restaurant), isNull(), eq("SETTINGS"), isNull(), eq("UPDATE_PRE_ORDERS"), anyString(), eq(ACTOR_ID));
    }

    @Test
    @DisplayName("Should save the reservation times and limits")
    void shouldUpdateReservationPolicy() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsDomainSupport.saveSettings(settings)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        settingsService.updateReservationPolicy(authentication, RESTAURANT_ID, reservationPolicy()
                .holdMinutes(45)
                .holdWarningMinutes(35)
                .lateAfterMinutes(10)
                .confirmReminderTime(LocalTime.of(16, 30))
                .guestReminderHours(48)
                .noShowWarningFrom(2)
                .depositFromGuests(10)
                .build());

        assertThat(settings.getHoldMinutes()).isEqualTo(45);
        assertThat(settings.getHoldWarningMinutes()).isEqualTo(35);
        assertThat(settings.getLateAfterMinutes()).isEqualTo(10);
        assertThat(settings.getConfirmReminderTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(settings.getGuestReminderHours()).isEqualTo(48);
        assertThat(settings.getNoShowWarningFrom()).isEqualTo(2);
        assertThat(settings.getDepositFromGuests()).isEqualTo(10);
        assertThat(settings.getUndoSeatMinutes()).isEqualTo(15);
        verify(settingsAuditService).log(eq(restaurant), isNull(), eq("SETTINGS"), isNull(), eq("UPDATE_RESERVATION_POLICY"), anyString(), eq(ACTOR_ID));
    }

    @Test
    @DisplayName("Should refuse a hold warning that is not before the hold ends")
    void shouldRejectHoldWarningAfterHold() {
        UpdateSettingsReservationPolicyRequest request = reservationPolicy().holdMinutes(30).holdWarningMinutes(30).build();

        assertThatThrownBy(() -> settingsService.updateReservationPolicy(authentication(), RESTAURANT_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("The hold warning must come before the hold ends");
        verify(settingsDomainSupport, never()).saveSettings(any());
    }

    @Test
    @DisplayName("Should refuse marking guests late only after the hold ends")
    void shouldRejectLateAfterHold() {
        UpdateSettingsReservationPolicyRequest request = reservationPolicy().holdMinutes(30).lateAfterMinutes(30).build();

        assertThatThrownBy(() -> settingsService.updateReservationPolicy(authentication(), RESTAURANT_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("A guest must count as late before the hold ends");
        verify(settingsDomainSupport, never()).saveSettings(any());
    }

    @Test
    @DisplayName("Should put the reservation times back to the agreed defaults on reset")
    void shouldResetReservationPolicy() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Settings settings = new Settings();
        settings.setRestaurant(restaurant);
        settings.setHoldMinutes(60);
        settings.setHoldWarningMinutes(50);
        settings.setLargeGroupFrom(8);
        settings.setConfirmReminderTime(LocalTime.of(9, 0));
        settings.setNoShowWarningFrom(3);
        settings.setPreOrderLeadMinutes(90);

        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(settingsDomainSupport.loadOrCreateSettings(restaurant, ACTOR_ID)).thenReturn(settings);
        when(settingsDomainSupport.saveSettings(settings)).thenReturn(settings);
        when(settingsMapper.toResponse(settings)).thenReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        settingsService.resetSettings(authentication, RESTAURANT_ID);

        assertThat(settings.getHoldMinutes()).isEqualTo(30);
        assertThat(settings.getHoldWarningMinutes()).isEqualTo(20);
        assertThat(settings.getLargeGroupFrom()).isEqualTo(5);
        assertThat(settings.getConfirmReminderTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(settings.getNoShowWarningFrom()).isEqualTo(1);
        assertThat(settings.getPreOrderLeadMinutes()).isEqualTo(30);
    }

    // The agreed defaults; each test changes what it needs.
    private UpdateSettingsReservationPolicyRequest.UpdateSettingsReservationPolicyRequestBuilder reservationPolicy() {
        return UpdateSettingsReservationPolicyRequest.builder()
                .largeGroupFrom(5)
                .largeGroupExtraMinutes(15)
                .approvalGroupSize(7)
                .holdMinutes(30)
                .holdWarningMinutes(20)
                .checkInOpensMinutes(120)
                .confirmReminderTime(LocalTime.of(15, 0))
                .sameDayConfirmMinutes(120)
                .attendanceCallMinutes(120)
                .reopenWindowMinutes(60)
                .undoSeatMinutes(15)
                .runningLateMaxMinutes(30)
                .lateAfterMinutes(15)
                .guestReminderHours(24)
                .noShowWarningFrom(1)
                .depositFromGuests(7);
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
