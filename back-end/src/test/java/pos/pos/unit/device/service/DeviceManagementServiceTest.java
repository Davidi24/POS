package pos.pos.unit.device.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.config.properties.DevicePairingProperties;
import pos.pos.device.dto.DevicePairingTokenResponse;
import pos.pos.device.entity.Device;
import pos.pos.device.entity.DevicePairingToken;
import pos.pos.device.repository.DeviceAssignmentRepository;
import pos.pos.device.repository.DevicePairingTokenRepository;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.device.service.DeviceManagementService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceManagementService")
class DeviceManagementServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");
    private static final UUID PAIRING_TOKEN_ID = UUID.fromString("00000000-0000-0000-0000-000000000804");

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceAssignmentRepository deviceAssignmentRepository;

    @Mock
    private DevicePairingTokenRepository devicePairingTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SettingsDomainSupport settingsDomainSupport;

    @Mock
    private SettingsAuditService settingsAuditService;

    @Mock
    private DevicePairingProperties devicePairingProperties;

    @Mock
    private OpaqueTokenService opaqueTokenService;

    private DeviceManagementService deviceManagementService;

    @BeforeEach
    void setUp() {
        deviceManagementService = new DeviceManagementService(
                deviceRepository,
                deviceAssignmentRepository,
                devicePairingTokenRepository,
                userRepository,
                settingsDomainSupport,
                settingsAuditService,
                devicePairingProperties,
                opaqueTokenService
        );
    }

    @Test
    @DisplayName("Should use accessible restaurant scope for assignment history reads")
    void shouldUseAccessibleRestaurantScopeForAssignmentHistoryReads() {
        Authentication authentication = authentication();
        Device device = device();

        when(settingsDomainSupport.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(new Restaurant());
        when(deviceRepository.findRestaurantNonPrinterById(DEVICE_ID, RESTAURANT_ID)).thenReturn(Optional.of(device));
        when(deviceAssignmentRepository.findAllByDevice_IdOrderByAssignedAtDesc(DEVICE_ID)).thenReturn(List.of());

        assertThat(deviceManagementService.getAssignments(authentication, RESTAURANT_ID, DEVICE_ID)).isEmpty();

        verify(settingsDomainSupport).requireAccessibleRestaurant(authentication, RESTAURANT_ID);
        verify(settingsDomainSupport, never()).requireManageableRestaurant(authentication, RESTAURANT_ID);
    }

    @Test
    @DisplayName("Should use manageable restaurant scope for pairing token revocation")
    void shouldUseManageableRestaurantScopeForPairingTokenRevocation() {
        Authentication authentication = authentication();
        Device device = device();
        DevicePairingToken token = pairingToken(device);

        when(settingsDomainSupport.requireManageableRestaurant(authentication, RESTAURANT_ID)).thenReturn(new Restaurant());
        when(settingsDomainSupport.currentActorId(authentication)).thenReturn(ACTOR_ID);
        when(deviceRepository.findRestaurantNonPrinterById(DEVICE_ID, RESTAURANT_ID)).thenReturn(Optional.of(device));
        when(devicePairingTokenRepository.findByIdAndDevice_Id(PAIRING_TOKEN_ID, DEVICE_ID)).thenReturn(Optional.of(token));
        when(devicePairingTokenRepository.saveAndFlush(token)).thenReturn(token);

        DevicePairingTokenResponse response = deviceManagementService.revokePairingToken(
                authentication,
                RESTAURANT_ID,
                DEVICE_ID,
                PAIRING_TOKEN_ID
        );

        assertThat(response.getId()).isEqualTo(PAIRING_TOKEN_ID);
        verify(settingsDomainSupport).requireManageableRestaurant(authentication, RESTAURANT_ID);
        verify(settingsDomainSupport, never()).requireAccessibleRestaurant(authentication, RESTAURANT_ID);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("devices.admin@pos.example")
                        .username("devices.admin")
                        .active(true)
                        .build(),
                null
        );
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        return device;
    }

    private DevicePairingToken pairingToken(Device device) {
        DevicePairingToken token = new DevicePairingToken();
        token.setId(PAIRING_TOKEN_ID);
        token.setDevice(device);
        token.setExpiresAt(OffsetDateTime.parse("2026-05-08T10:00:00Z"));
        token.setCreatedAt(OffsetDateTime.parse("2026-05-07T10:00:00Z"));
        return token;
    }
}
