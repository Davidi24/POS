package pos.pos.device.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.config.properties.DevicePairingProperties;
import pos.pos.device.dto.CreateDeviceAssignmentRequest;
import pos.pos.device.dto.CreateDevicePairingTokenRequest;
import pos.pos.device.dto.DeviceAssignmentResponse;
import pos.pos.device.dto.DevicePairingTokenResponse;
import pos.pos.device.entity.Device;
import pos.pos.device.entity.DeviceAssignment;
import pos.pos.device.entity.DevicePairingToken;
import pos.pos.device.enums.DeviceAssignmentType;
import pos.pos.device.repository.DeviceAssignmentRepository;
import pos.pos.device.repository.DevicePairingTokenRepository;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.device.DeviceNotFoundException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceManagementService {

    private final DeviceRepository deviceRepository;
    private final DeviceAssignmentRepository deviceAssignmentRepository;
    private final DevicePairingTokenRepository devicePairingTokenRepository;
    private final UserRepository userRepository;
    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsAuditService settingsAuditService;
    private final DevicePairingProperties devicePairingProperties;
    private final OpaqueTokenService opaqueTokenService;

    @Transactional(readOnly = true)
    public List<DeviceAssignmentResponse> getAssignments(
            Authentication authentication,
            UUID restaurantId,
            UUID deviceId
    ) {
        Device device = requireManageableDevice(authentication, restaurantId, deviceId);
        return deviceAssignmentRepository.findAllByDevice_IdOrderByAssignedAtDesc(device.getId()).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    @Transactional
    public DeviceAssignmentResponse createAssignment(
            Authentication authentication,
            UUID restaurantId,
            UUID deviceId,
            CreateDeviceAssignmentRequest request
    ) {
        // Assignment is the business-side placement of the device:
        // which branch it belongs to, or which user it is assigned to.
        Device device = requireManageableDevice(authentication, restaurantId, deviceId);
        UUID actorId = settingsDomainSupport.currentActorId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Only one assignment can be active at a time, so replace the current one.
        deviceAssignmentRepository.findByDevice_IdAndActiveTrue(device.getId()).ifPresent(activeAssignment -> {
            activeAssignment.closeAt(now);
            persistAssignment(activeAssignment);
        });

        DeviceAssignment assignment = new DeviceAssignment();
        assignment.setDevice(device);
        assignment.setAssignmentType(request.getAssignmentType());
        assignment.setAssignedAt(now);
        assignment.setAssignedBy(actorId);
        assignment.setNotes(request.getNotes());

        if (request.getAssignmentType() == DeviceAssignmentType.BRANCH) {
            // Branch assignment means the device is placed under a branch operationally.
            Branch branch = resolveAssignmentBranch(restaurantId, request);
            assignment.setBranch(branch);
            assignment.setUserId(null);
            assignment.setUser(null);
            device.setBranch(branch);
            device.setUpdatedBy(actorId);
        } else if (request.getAssignmentType() == DeviceAssignmentType.USER) {
            // User assignment means the device is allocated to a specific staff member.
            User user = resolveAssignmentUser(restaurantId, request);
            assignment.setBranch(null);
            assignment.setUserId(user.getId());
            assignment.setUser(user);
        } else {
            throw new AuthException("Unsupported assignmentType", HttpStatus.BAD_REQUEST);
        }

        DeviceAssignment savedAssignment = persistAssignment(assignment);

        settingsAuditService.log(
                device.getRestaurant(),
                device.getBranch(),
                "DEVICE_ASSIGNMENT",
                savedAssignment.getId(),
                "CREATE",
                "Created device assignment",
                actorId
        );

        return toAssignmentResponse(savedAssignment);
    }

    @Transactional
    public DeviceAssignmentResponse unassign(
            Authentication authentication,
            UUID restaurantId,
            UUID deviceId,
            UUID assignmentId
    ) {
        Device device = requireManageableDevice(authentication, restaurantId, deviceId);
        DeviceAssignment assignment = deviceAssignmentRepository.findByIdAndDevice_Id(assignmentId, device.getId())
                .orElseThrow(() -> new AuthException("Device assignment not found", HttpStatus.NOT_FOUND));

        if (assignment.isActive()) {
            assignment.closeAt(OffsetDateTime.now(ZoneOffset.UTC));
            persistAssignment(assignment);

            settingsAuditService.log(
                    device.getRestaurant(),
                    device.getBranch(),
                    "DEVICE_ASSIGNMENT",
                    assignment.getId(),
                    "UNASSIGN",
                    "Closed device assignment",
                    settingsDomainSupport.currentActorId(authentication)
            );
        }

        return toAssignmentResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<DevicePairingTokenResponse> getPairingTokens(
            Authentication authentication,
            UUID restaurantId,
            UUID deviceId
    ) {
        Device device = requireManageableDevice(authentication, restaurantId, deviceId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return devicePairingTokenRepository.findAllByDevice_IdOrderByCreatedAtDesc(device.getId()).stream()
                .map(token -> toPairingTokenResponse(token, null, now))
                .toList();
    }

    @Transactional
    public DevicePairingTokenResponse createPairingToken(
            Authentication authentication,
            UUID restaurantId,
            UUID deviceId,
            CreateDevicePairingTokenRequest request
    ) {
        // Pairing token is the technical side of onboarding the device.
        // The device/app uses this short-lived token to complete pairing with the backend.
        Device device = requireManageableDevice(authentication, restaurantId, deviceId);
        UUID actorId = settingsDomainSupport.currentActorId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Keep only one currently usable pairing token per device.
        List<DevicePairingToken> openTokens = devicePairingTokenRepository
                .findAllByDevice_IdAndUsedAtIsNullAndRevokedAtIsNull(device.getId());
        if (!openTokens.isEmpty()) {
            for (DevicePairingToken openToken : openTokens) {
                openToken.revokeAt(now);
            }
            devicePairingTokenRepository.saveAllAndFlush(openTokens);
        }

        // Persist only the hash; return the raw token once in the response for the caller/device.
        OpaqueTokenService.IssuedToken issuedToken = opaqueTokenService.issue(devicePairingProperties.getTokenPepper());
        DevicePairingToken token = new DevicePairingToken();
        token.setDevice(device);
        token.setTokenHash(issuedToken.tokenHash());
        token.setExpiresAt(now.plus(resolveTtl(request)));
        token.setRequestedIp(request.getRequestedIp());
        token.setCreatedAt(now);
        token.setCreatedBy(actorId);

        DevicePairingToken savedToken = persistPairingToken(
                token
        );

        settingsAuditService.log(
                device.getRestaurant(),
                device.getBranch(),
                "DEVICE_PAIRING_TOKEN",
                savedToken.getId(),
                "CREATE",
                "Issued device pairing token",
                actorId
        );

        return toPairingTokenResponse(savedToken, issuedToken.rawToken(), now);
    }

    @Transactional
    public DevicePairingTokenResponse revokePairingToken(
            Authentication authentication,
            UUID restaurantId,
            UUID deviceId,
            UUID pairingTokenId
    ) {
        Device device = requireManageableDevice(authentication, restaurantId, deviceId);
        DevicePairingToken token = devicePairingTokenRepository.findByIdAndDevice_Id(pairingTokenId, device.getId())
                .orElseThrow(() -> new AuthException("Device pairing token not found", HttpStatus.NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean changed = token.getUsedAt() == null && token.getRevokedAt() == null;
        if (changed) {
            token.revokeAt(now);
            persistPairingToken(token);

            settingsAuditService.log(
                    device.getRestaurant(),
                    device.getBranch(),
                    "DEVICE_PAIRING_TOKEN",
                    token.getId(),
                    "REVOKE",
                    "Revoked device pairing token",
                    settingsDomainSupport.currentActorId(authentication)
            );
        }

        return toPairingTokenResponse(token, null, now);
    }

    // check if user is in the right restaurant before putting the device
    private Device requireManageableDevice(Authentication authentication, UUID restaurantId, UUID deviceId) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        return deviceRepository.findRestaurantNonPrinterById(deviceId, restaurantId)
                .orElseThrow(DeviceNotFoundException::new);
    }

    private Branch resolveAssignmentBranch(UUID restaurantId, CreateDeviceAssignmentRequest request) {
        if (request.getBranchId() == null) {
            throw new AuthException("branchId is required for BRANCH assignments", HttpStatus.BAD_REQUEST);
        }
        if (request.getUserId() != null) {
            throw new AuthException("userId is not allowed for BRANCH assignments", HttpStatus.BAD_REQUEST);
        }
        return settingsDomainSupport.resolveBranch(restaurantId, request.getBranchId());
    }

    private User resolveAssignmentUser(UUID restaurantId, CreateDeviceAssignmentRequest request) {
        if (request.getUserId() == null) {
            throw new AuthException("userId is required for USER assignments", HttpStatus.BAD_REQUEST);
        }
        if (request.getBranchId() != null) {
            throw new AuthException("branchId is not allowed for USER assignments", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findActiveById(request.getUserId())
                .orElseThrow(UserNotFoundException::new);
        if (!restaurantId.equals(user.getRestaurantId())) {
            throw new AuthException(
                    "Assigned user must belong to the same restaurant as the device",
                    HttpStatus.BAD_REQUEST
            );
        }
        return user;
    }

    private Duration resolveTtl(CreateDevicePairingTokenRequest request) {
        if (request.getTtlMinutes() == null) {
            return devicePairingProperties.getDefaultTtl();
        }

        Duration requestedTtl = Duration.ofMinutes(request.getTtlMinutes());
        if (requestedTtl.compareTo(devicePairingProperties.getMaxTtl()) > 0) {
            throw new AuthException("ttlMinutes exceeds the configured maximum", HttpStatus.BAD_REQUEST);
        }
        return requestedTtl;
    }

    private DeviceAssignment persistAssignment(DeviceAssignment assignment) {
        try {
            return deviceAssignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Device assignment update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private DevicePairingToken persistPairingToken(DevicePairingToken token) {
        try {
            return devicePairingTokenRepository.saveAndFlush(token);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Device pairing token update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private DeviceAssignmentResponse toAssignmentResponse(DeviceAssignment assignment) {
        return DeviceAssignmentResponse.builder()
                .id(assignment.getId())
                .deviceId(assignment.getDevice() == null ? null : assignment.getDevice().getId())
                .branchId(assignment.getBranch() == null ? null : assignment.getBranch().getId())
                .branchName(assignment.getBranch() == null ? null : assignment.getBranch().getName())
                .userId(assignment.getUserId())
                .userEmail(assignment.getUser() == null ? null : assignment.getUser().getEmail())
                .userDisplayName(displayNameOf(assignment.getUser()))
                .assignmentType(assignment.getAssignmentType())
                .assignedAt(assignment.getAssignedAt())
                .unassignedAt(assignment.getUnassignedAt())
                .active(assignment.isActive())
                .assignedBy(assignment.getAssignedBy())
                .assignedByDisplayName(displayNameOf(assignment.getAssignedByUser()))
                .notes(assignment.getNotes())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }

    private DevicePairingTokenResponse toPairingTokenResponse(
            DevicePairingToken token,
            String rawToken,
            OffsetDateTime referenceTime
    ) {
        return DevicePairingTokenResponse.builder()
                .id(token.getId())
                .deviceId(token.getDevice() == null ? null : token.getDevice().getId())
                .state(stateOf(token, referenceTime))
                .active(token.isActiveAt(referenceTime))
                .pairingToken(rawToken)
                .expiresAt(token.getExpiresAt())
                .usedAt(token.getUsedAt())
                .revokedAt(token.getRevokedAt())
                .requestedIp(token.getRequestedIp())
                .createdAt(token.getCreatedAt())
                .createdBy(token.getCreatedBy())
                .createdByDisplayName(displayNameOf(token.getCreatedByUser()))
                .build();
    }

    private String stateOf(DevicePairingToken token, OffsetDateTime referenceTime) {
        if (token.getUsedAt() != null) {
            return "USED";
        }
        if (token.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(referenceTime)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private String displayNameOf(User user) {
        if (user == null) {
            return null;
        }

        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if (StringUtils.hasText(firstName) && StringUtils.hasText(lastName)) {
            return firstName + " " + lastName;
        }
        if (StringUtils.hasText(firstName)) {
            return firstName;
        }
        if (StringUtils.hasText(lastName)) {
            return lastName;
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return user.getEmail();
    }
}
