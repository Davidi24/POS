package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.device.entity.Device;
import pos.pos.device.entity.DevicePrinterProfile;
import pos.pos.device.enums.DeviceStatus;
import pos.pos.device.enums.DeviceType;
import pos.pos.device.enums.PrinterConnectionType;
import pos.pos.device.repository.DevicePairingTokenRepository;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.device.DeviceNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.dto.DeviceResponse;
import pos.pos.settings.dto.PrinterRouteResponse;
import pos.pos.settings.dto.PrinterRouteTestResponse;
import pos.pos.settings.dto.UpdateDeviceStatusRequest;
import pos.pos.settings.dto.UpdatePrinterRoutesRequest;
import pos.pos.settings.dto.UpsertDeviceRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceSettingsService {

    private final DeviceRepository deviceRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final DevicePairingTokenRepository devicePairingTokenRepository;
    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsAuditService settingsAuditService;

    @Transactional(readOnly = true)
    public List<DeviceResponse> getPrinters(Authentication authentication, UUID restaurantId) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);

        return deviceRepository.findRestaurantPrinters(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeviceResponse createPrinter(Authentication authentication, UUID restaurantId, UpsertDeviceRequest request) {
        Restaurant restaurant = settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);

        Device device = new Device();
        device.setRestaurant(restaurant);

        applyDevice(device, restaurantId, request, true);
        stampAuditFields(device, settingsDomainSupport.currentActorId(authentication));

        return saveAuditAndRespond(
                authentication,
                device,
                "Printer settings update violates a data constraint",
                "DEVICE_PRINTER",
                "CREATE",
                "Created printer settings"
        );
    }

    @Transactional
    public DeviceResponse updatePrinter(
            Authentication authentication,
            UUID restaurantId,
            UUID printerId,
            UpsertDeviceRequest request
    ) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);

        Device device = requirePrinter(restaurantId, printerId);

        applyDevice(device, restaurantId, request, true);
        stampAuditFields(device, settingsDomainSupport.currentActorId(authentication));

        return saveAuditAndRespond(
                authentication,
                device,
                "Printer settings update violates a data constraint",
                "DEVICE_PRINTER",
                "UPDATE",
                "Updated printer settings"
        );
    }

    @Transactional
    public void deletePrinter(Authentication authentication, UUID restaurantId, UUID printerId) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);

        Device device = requirePrinter(restaurantId, printerId);
        // A cash drawer plugged into it isn't in use any more (it waits for another printer).
        entityManager.createNativeQuery("update {h-schema}devices set status = 'PROVISIONING' where linked_device_id = :id and status = 'ACTIVE'")
                .setParameter("id", device.getId()).executeUpdate();

        deleteAndAudit(authentication, device, "DEVICE_PRINTER", "DELETE", "Deleted printer settings");
    }

    @Transactional(readOnly = true)
    public PrinterRouteResponse getPrinterRoutes(Authentication authentication, UUID restaurantId, UUID branchId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);

        List<Device> printers = deviceRepository.findBranchPrinters(restaurantId, branchId);

        return PrinterRouteResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .printerIds(printers.stream().map(Device::getId).toList())
                .printers(printers.stream().map(this::toResponse).toList())
                .build();
    }

    @Transactional
    public PrinterRouteResponse updatePrinterRoutes(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdatePrinterRoutesRequest request
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);

        Set<UUID> requestedPrinterIds = request.getPrinterIds() == null
                ? Set.of()
                : new LinkedHashSet<>(request.getPrinterIds());

        List<Device> restaurantPrinters = deviceRepository.findRestaurantPrinters(restaurantId);

        List<Device> toUpdate = new ArrayList<>();

        for (Device printer : restaurantPrinters) {
            if (requestedPrinterIds.contains(printer.getId())) {
                printer.setBranch(branch);
                toUpdate.add(printer);
                continue;
            }

            if (printer.getBranch() != null && branchId.equals(printer.getBranch().getId())) {
                printer.setBranch(null);
                toUpdate.add(printer);
            }
        }

        long foundRequestedPrinters = restaurantPrinters.stream()
                .map(Device::getId)
                .filter(requestedPrinterIds::contains)
                .count();

        if (requestedPrinterIds.size() > foundRequestedPrinters) {
            throw new DeviceNotFoundException();
        }

        if (!toUpdate.isEmpty()) {
            deviceRepository.saveAllAndFlush(toUpdate);
        }

        settingsAuditService.log(
                branch.getRestaurant(),
                branch,
                "DEVICE_PRINTER_ROUTE",
                null,
                "UPDATE",
                "Updated branch printer routes",
                settingsDomainSupport.currentActorId(authentication)
        );

        return getPrinterRoutes(authentication, restaurantId, branchId);
    }

    @Transactional(readOnly = true)
    public PrinterRouteTestResponse testPrinterRoutes(Authentication authentication, UUID restaurantId, UUID branchId) {
        PrinterRouteResponse routes = getPrinterRoutes(authentication, restaurantId, branchId);

        return PrinterRouteTestResponse.builder()
                .restaurantId(restaurantId)
                .branchId(branchId)
                .printerCount(routes.getPrinterIds().size())
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .payload("TEST_PRINT_ROUTE:" + routes.getPrinterIds().size())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevices(Authentication authentication, UUID restaurantId, UUID branchId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);

        return deviceRepository.findBranchNonPrinterDevices(restaurantId, branchId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeviceResponse createDevice(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpsertDeviceRequest request
    ) {
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);

        ensureMatchingBranchPath(request, branchId);

        Device device = new Device();
        device.setRestaurant(branch.getRestaurant());
        device.setBranch(branch);

        applyDevice(device, restaurantId, request, false);
        stampAuditFields(device, settingsDomainSupport.currentActorId(authentication));

        return saveAuditAndRespond(
                authentication,
                device,
                "Device settings update violates a data constraint",
                "DEVICE",
                "CREATE",
                "Created branch device"
        );
    }

    @Transactional
    public DeviceResponse updateDevice(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID deviceId,
            UpsertDeviceRequest request
    ) {
        ensureMatchingBranchPath(request, branchId);

        Device device = requireBranchDevice(authentication, restaurantId, branchId, deviceId);
        Branch branch = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);

        applyDevice(device, restaurantId, request, false);
        device.setBranch(branch);
        stampAuditFields(device, settingsDomainSupport.currentActorId(authentication));

        return saveAuditAndRespond(
                authentication,
                device,
                "Device settings update violates a data constraint",
                "DEVICE",
                "UPDATE",
                "Updated branch device"
        );
    }

    /** Moves a device to another branch of the same restaurant; its connection stays, the new branch's network rule applies. */
    @Transactional
    public DeviceResponse moveDevice(Authentication authentication, UUID restaurantId, UUID branchId, UUID deviceId, UUID toBranchId) {
        // Only someone who works in every branch (user, 2026-11-25: "the owner has only one branch, why does it say Change?").
        boolean everyBranch = authentication.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("BRANCHES_ALL") || a.equals("ROLE_SUPER_ADMIN") || a.equals("ROLE_CO_SUPER_ADMIN"));
        if (!everyBranch) {
            throw new pos.pos.exception.auth.AuthException("Only someone who works in every branch moves a device to another one.",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        Device device = requireBranchDeviceForUpdate(authentication, restaurantId, branchId, deviceId);
        Branch to = settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, toBranchId);
        if (device.getBranch() != null && to.getId().equals(device.getBranch().getId())) return toResponse(device);
        device.setBranch(to);
        stampAuditFields(device, settingsDomainSupport.currentActorId(authentication));
        return saveAuditAndRespond(authentication, device, "Moving the device violates a data constraint", "DEVICE", "UPDATE",
                "Moved " + device.getName() + " to " + to.getName());
    }

    @Transactional
    public DeviceResponse updateDeviceStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID deviceId,
            UpdateDeviceStatusRequest request
    ) {
        // Pair-token issuance takes this same row lock. This serializes deactivation
        // with pairing so a token cannot remain usable after the device is disabled.
        Device device = requireBranchDeviceForUpdate(authentication, restaurantId, branchId, deviceId);

        if (request.getStatus() == DeviceStatus.ACTIVE && notReady(device) != null) {
            throw new AuthException(notReady(device), HttpStatus.BAD_REQUEST);
        }
        if (request.getStatus() != null) {
            device.setStatus(request.getStatus());
        }

        if (request.getActive() != null) {
            device.setActive(request.getActive());
        }

        if (request.getOnline() != null) {
            device.setOnline(request.getOnline());
        }

        if (!isPairable(device)) {
            revokeOpenPairingTokens(device.getId());
            // A disabled/retired/maintenance device must not regain access with a previously
            // issued secret if an administrator later changes its status back to ACTIVE.
            device.setAuthSecretHash(null);
            device.setAuthSecretRotatedAt(null);
            device.setOnline(false);
        }

        stampAuditFields(device, settingsDomainSupport.currentActorId(authentication));

        return saveAuditAndRespond(
                authentication,
                device,
                "Device status update violates a data constraint",
                "DEVICE",
                "UPDATE_STATUS",
                "Updated branch device status"
        );
    }

    @Transactional
    public void deleteDevice(Authentication authentication, UUID restaurantId, UUID branchId, UUID deviceId) {
        Device device = requireBranchDevice(authentication, restaurantId, branchId, deviceId);
        // A till or tablet keeps a little history that mustn't stop it from going (2026-11-25: one that ever had a pairing code
        // couldn't be removed): its codes and assignments go with it; a kitchen station and the shifts clocked in on it keep
        // their rows without it. Who connected it stays in the Audit log.
        for (String sql : List.of(
                "delete from {h-schema}\"device-pairing-tokens\" where device_id = :id",
                "delete from {h-schema}\"device-assignments\" where device_id = :id",
                "update {h-schema}kds_stations set device_id = null where device_id = :id",
                "update {h-schema}shifts set device_id = null where device_id = :id",
                // A card terminal connected to it isn't in use any more (it waits for another till or tablet).
                "update {h-schema}devices set status = 'PROVISIONING' where linked_device_id = :id and status = 'ACTIVE'")) {
            entityManager.createNativeQuery(sql).setParameter("id", device.getId()).executeUpdate();
        }

        deleteAndAudit(authentication, device, "DEVICE", "DELETE", "Deleted branch device");
    }

    private void applyDevice(Device device, UUID restaurantId, UpsertDeviceRequest request, boolean printerMode) {
        if (printerMode) {
            Branch branch = request.getBranchId() == null
                    ? null
                    : settingsDomainSupport.resolveBranch(restaurantId, request.getBranchId());

            device.setBranch(branch);
            device.setDeviceType(DeviceType.PRINTER);
        } else {
            ensureNonPrinterPayload(request);
            device.setDeviceType(parseRequiredDeviceType(request.getDeviceType()));
        }

        device.setCode(request.getCode());
        device.setName(request.getName());
        device.setManufacturer(request.getManufacturer());
        device.setModel(request.getModel());
        device.setSerialNumber(request.getSerialNumber());
        device.setPlatform(request.getPlatform());
        device.setOsVersion(request.getOsVersion());
        device.setAppVersion(request.getAppVersion());

        if (request.getStatus() != null) {
            device.setStatus(parseDeviceStatus(request.getStatus()));
        }

        if (request.getActive() != null) {
            device.setActive(request.getActive());
        }

        if (request.getOnline() != null) {
            device.setOnline(request.getOnline());
        }

        device.setIpAddress(request.getIpAddress());
        device.setMacAddress(request.getMacAddress());
        device.setNotes(request.getNotes());

        if (printerMode) {
            ensurePrinterProfile(device, request);
            return;
        }

        if (device.getPrinterProfile() != null) {
            device.setPrinterProfile(null);
        }
        applyLink(device, restaurantId, request);
    }

    /**
     * What a card terminal or a cash drawer is connected to (user, 2026-11-27): a card terminal works only through a till or
     * tablet of the restaurant, by Bluetooth or cable (none chosen = not connected, so it can't take payments); a cash drawer is
     * plugged into one of the restaurant's printers. Other devices are connected to nothing.
     */
    private void applyLink(Device device, UUID restaurantId, UpsertDeviceRequest request) {
        UUID linked = request.getLinkedDeviceId();
        DeviceType type = device.getDeviceType();
        if (type != DeviceType.PAYMENT_TERMINAL && type != DeviceType.CASH_DRAWER) {
            if (linked != null || request.getLinkType() != null || StringUtils.hasText(request.getLinkAddress())) {
                throw new AuthException("Only card terminals and cash drawers are connected to another device.", HttpStatus.BAD_REQUEST);
            }
            clearLink(device);
            return;
        }
        if (linked == null) {
            clearLink(device);
            // Not connected: not in use (it waits to be confirmed once it's connected again).
            if (device.getStatus() == DeviceStatus.ACTIVE) device.setStatus(DeviceStatus.PROVISIONING);
            return;
        }
        Device target = deviceRepository.findById(linked)
                .filter(candidate -> candidate.getRestaurant() != null && restaurantId.equals(candidate.getRestaurant().getId()))
                .filter(candidate -> candidate.getStatus() != DeviceStatus.RETIRED)
                .orElseThrow(() -> new AuthException("Choose one of this restaurant's devices.", HttpStatus.BAD_REQUEST));
        if (type == DeviceType.PAYMENT_TERMINAL) {
            if (target.getDeviceType() != DeviceType.POS_TERMINAL && target.getDeviceType() != DeviceType.TABLET) {
                throw new AuthException("A card terminal is connected to a till or a tablet.", HttpStatus.BAD_REQUEST);
            }
            if (!"USB".equals(request.getLinkType()) && !"BLUETOOTH".equals(request.getLinkType())) {
                throw new AuthException("Choose Bluetooth or cable.", HttpStatus.BAD_REQUEST);
            }
            if ("BLUETOOTH".equals(request.getLinkType()) && !StringUtils.hasText(request.getLinkAddress())) {
                throw new AuthException("Choose the paired card terminal.", HttpStatus.BAD_REQUEST);
            }
            device.setLinkType(request.getLinkType());
        } else {
            if (target.getDeviceType() != DeviceType.PRINTER) {
                throw new AuthException("A cash drawer is plugged into a printer.", HttpStatus.BAD_REQUEST);
            }
            device.setLinkType("PRINTER");
        }
        device.setLinkedDeviceId(target.getId());
        device.setLinkAddress(StringUtils.hasText(request.getLinkAddress()) ? request.getLinkAddress().trim() : null);
    }

    /**
     * Why a card terminal or a cash drawer can't be in use yet, or null (user, 2026-11-28: "in use means everything has passed,
     * so it's ready"): a terminal needs its till or tablet, a drawer its printer.
     */
    private static String notReady(Device device) {
        if (device.getLinkedDeviceId() != null) return null;
        if (device.getDeviceType() == DeviceType.PAYMENT_TERMINAL) return "Connect it to a till or tablet first.";
        if (device.getDeviceType() == DeviceType.CASH_DRAWER) return "Plug it into a printer first.";
        return null;
    }

    private static void clearLink(Device device) {
        device.setLinkedDeviceId(null);
        device.setLinkType(null);
        device.setLinkAddress(null);
    }

    private void ensurePrinterProfile(Device device, UpsertDeviceRequest request) {
        PrinterConnectionType connectionType = request.getPrinterConnectionType();
        Integer paperWidthMm = request.getPaperWidthMm();

        if (connectionType == null || paperWidthMm == null) {
            throw new AuthException("printerConnectionType and paperWidthMm are required for printers", HttpStatus.BAD_REQUEST);
        }

        DevicePrinterProfile printerProfile = device.getPrinterProfile();

        if (printerProfile == null) {
            printerProfile = new DevicePrinterProfile();
            device.setPrinterProfile(printerProfile);
        }

        applyPrinterProfile(printerProfile, request);
    }

    private void applyPrinterProfile(DevicePrinterProfile printerProfile, UpsertDeviceRequest request) {
        printerProfile.setConnectionType(request.getPrinterConnectionType());
        printerProfile.setPaperWidthMm(request.getPaperWidthMm());
        printerProfile.setPrinterIp(request.getPrinterIp());
        printerProfile.setPrinterPort(request.getPrinterPort());
        printerProfile.setAutoCut(Boolean.TRUE.equals(request.getAutoCut()));
        printerProfile.setCashDrawerKickEnabled(Boolean.TRUE.equals(request.getCashDrawerKickEnabled()));
        printerProfile.setSystemPrinterName(request.getSystemPrinterName());
        printerProfile.setHostDeviceId(request.getHostDeviceId());
        if (request.getPrinterRole() != null) printerProfile.setPrinterRole(request.getPrinterRole());
    }

    private Device requirePrinter(UUID restaurantId, UUID deviceId) {
        return deviceRepository.findPrinterByIdAndRestaurantId(deviceId, restaurantId)
                .orElseThrow(DeviceNotFoundException::new);
    }

    private Device requireBranchDevice(Authentication authentication, UUID restaurantId, UUID branchId, UUID deviceId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);

        return deviceRepository.findBranchNonPrinterById(deviceId, restaurantId, branchId)
                .orElseThrow(DeviceNotFoundException::new);
    }

    private Device requireBranchDeviceForUpdate(Authentication authentication, UUID restaurantId, UUID branchId, UUID deviceId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);

        Device device = deviceRepository.findForUpdateByIdAndRestaurantId(deviceId, restaurantId)
                .filter(candidate -> candidate.getBranch() != null
                        && branchId.equals(candidate.getBranch().getId())
                        && candidate.getDeviceType() != DeviceType.PRINTER)
                .orElseThrow(DeviceNotFoundException::new);
        return device;
    }

    private boolean isPairable(Device device) {
        return device.isActive()
                && (device.getStatus() == DeviceStatus.PROVISIONING || device.getStatus() == DeviceStatus.ACTIVE);
    }

    private void revokeOpenPairingTokens(UUID deviceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var openTokens = devicePairingTokenRepository
                .findAllByDevice_IdAndUsedAtIsNullAndRevokedAtIsNull(deviceId);
        if (openTokens.isEmpty()) {
            return;
        }
        openTokens.forEach(token -> token.revokeAt(now));
        devicePairingTokenRepository.saveAllAndFlush(openTokens);
    }

    private Device saveDevice(Device device, String constraintMessage) {
        try {
            return deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException(constraintMessage, HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private DeviceResponse saveAuditAndRespond(
            Authentication authentication,
            Device device,
            String constraintMessage,
            String entityType,
            String action,
            String message
    ) {
        Device savedDevice = saveDevice(device, constraintMessage);

        auditDevice(authentication, savedDevice, entityType, action, message);

        return toResponse(savedDevice);
    }

    private void deleteAndAudit(
            Authentication authentication,
            Device device,
            String entityType,
            String action,
            String message
    ) {
        deviceRepository.delete(device);
        deviceRepository.flush();

        auditDevice(authentication, device, entityType, action, message);
    }

    private void auditDevice(
            Authentication authentication,
            Device device,
            String entityType,
            String action,
            String message
    ) {
        settingsAuditService.log(
                device.getRestaurant(),
                device.getBranch(),
                entityType,
                device.getId(),
                action,
                message,
                settingsDomainSupport.currentActorId(authentication)
        );
    }

    private void ensureMatchingBranchPath(UpsertDeviceRequest request, UUID branchId) {
        if (request.getBranchId() != null && !branchId.equals(request.getBranchId())) {
            throw new AuthException("branchId must match the branch path", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureNonPrinterPayload(UpsertDeviceRequest request) {
        if (DeviceType.PRINTER.name().equals(normalizeToken(request.getDeviceType()))) {
            throw new AuthException("deviceType PRINTER is only allowed on printer endpoints", HttpStatus.BAD_REQUEST);
        }

        if (request.getPrinterConnectionType() != null
                || request.getPaperWidthMm() != null
                || request.getPrinterIp() != null
                || request.getPrinterPort() != null
                || request.getAutoCut() != null
                || request.getCashDrawerKickEnabled() != null
                || request.getSystemPrinterName() != null
                || request.getHostDeviceId() != null
                || request.getPrinterRole() != null) {
            throw new AuthException("printer-specific fields are not allowed on branch device endpoints", HttpStatus.BAD_REQUEST);
        }
    }

    private void stampAuditFields(Device device, UUID actorId) {
        if (device.getCreatedBy() == null) {
            device.setCreatedBy(actorId);
        }

        device.setUpdatedBy(actorId);
    }

    private DeviceType parseRequiredDeviceType(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthException("deviceType is required", HttpStatus.BAD_REQUEST);
        }

        String normalized = normalizeToken(value);
        if ("TERMINAL".equals(normalized)) {
            return DeviceType.POS_TERMINAL;
        }

        try {
            return DeviceType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid deviceType", HttpStatus.BAD_REQUEST);
        }
    }

    private DeviceStatus parseDeviceStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new AuthException("status is required", HttpStatus.BAD_REQUEST);
        }

        try {
            return DeviceStatus.valueOf(normalizeToken(value));
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid status", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeToken(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private DeviceResponse toResponse(Device device) {
        DevicePrinterProfile printerProfile = device.getPrinterProfile();

        return DeviceResponse.builder()
                .id(device.getId())
                .restaurantId(device.getRestaurant() == null ? null : device.getRestaurant().getId())
                .branchId(device.getBranch() == null ? null : device.getBranch().getId())
                .code(device.getCode())
                .name(device.getName())
                .deviceType(toApiDeviceType(device.getDeviceType()))
                .manufacturer(device.getManufacturer())
                .model(device.getModel())
                .serialNumber(device.getSerialNumber())
                .platform(device.getPlatform())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .status(device.getStatus() == null ? null : device.getStatus().name())
                .active(device.isActive())
                .online(isOnline(device))
                .connected(org.springframework.util.StringUtils.hasText(device.getAuthSecretHash()))
                .connectedAt(device.getAuthSecretRotatedAt())
                .connectedDeviceName(device.getConnectedDeviceName())
                .connectedDeviceSystem(device.getConnectedDeviceSystem())
                .lastSeenAt(device.getLastSeenAt())
                .ipAddress(device.getIpAddress())
                .place(geoPlaces == null ? null : geoPlaces.placeOf(device.getIpAddress()))
                .macAddress(device.getMacAddress())
                .notes(device.getNotes())
                .printerConnectionType(printerProfile == null ? null : printerProfile.getConnectionType())
                .paperWidthMm(printerProfile == null ? null : printerProfile.getPaperWidthMm())
                .printerIp(printerProfile == null ? null : printerProfile.getPrinterIp())
                .printerPort(printerProfile == null ? null : printerProfile.getPrinterPort())
                .systemPrinterName(printerProfile == null ? null : printerProfile.getSystemPrinterName())
                .hostDeviceId(printerProfile == null ? null : printerProfile.getHostDeviceId())
                .printerRole(printerProfile == null ? null : printerProfile.getPrinterRole())
                .printerState(printerProfile == null ? null : printerState(printerProfile))
                .printerReachedAt(printerProfile == null ? null : printerProfile.getReachedAt())
                .printerCheckedAt(printerProfile == null ? null : printerProfile.getCheckedAt())
                .printerCheckedBy(printerProfile == null || printerProfile.getCheckedBy() == null ? null
                        : deviceRepository.findById(printerProfile.getCheckedBy()).map(Device::getName).orElse(null))
                .printerProblem(printerProfile == null ? null : printerProfile.getCheckProblem())
                .linkedDeviceId(device.getLinkedDeviceId())
                .linkedDeviceName(device.getLinkedDeviceId() == null ? null
                        : deviceRepository.findById(device.getLinkedDeviceId()).map(Device::getName).orElse(null))
                .linkType(device.getLinkType())
                .linkAddress(device.getLinkAddress())
                .autoCut(printerProfile == null ? null : printerProfile.isAutoCut())
                .cashDrawerKickEnabled(printerProfile == null ? null : printerProfile.isCashDrawerKickEnabled())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }

    /**
     * A device that signs in is online while it's heard from (its heartbeat every minute, `app.devices.online-minutes`, 3 by
     * default); a printer while a till reached it lately (tills check every minute).
     */
    private boolean isOnline(Device device) {
        if (device.getDeviceType() == DeviceType.PRINTER) {
            return device.getPrinterProfile() != null && "ONLINE".equals(printerState(device.getPrinterProfile()));
        }
        java.time.OffsetDateTime seen = device.getLastSeenAt();
        return device.isOnline() && seen != null
                && seen.isAfter(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(onlineMinutes));
    }

    /** ONLINE: a till reached it lately; OFFLINE: one checked lately and nobody reached it; UNCHECKED: no till checked lately. */
    private String printerState(DevicePrinterProfile profile) {
        java.time.OffsetDateTime since = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(onlineMinutes);
        if (profile.getReachedAt() != null && profile.getReachedAt().isAfter(since)) return "ONLINE";
        if (profile.getCheckedAt() != null && profile.getCheckedAt().isAfter(since)) return "OFFLINE";
        return "UNCHECKED";
    }

    @org.springframework.beans.factory.annotation.Value("${app.devices.online-minutes:3}")
    private int onlineMinutes = 3;

    // Where a device's address is (set by Spring; unit tests may leave it out).
    private pos.pos.security.geo.GeoPlaces geoPlaces;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setGeoPlaces(pos.pos.security.geo.GeoPlaces geoPlaces) {
        this.geoPlaces = geoPlaces;
    }

    private String toApiDeviceType(DeviceType deviceType) {
        if (deviceType == null) {
            return null;
        }

        return deviceType == DeviceType.POS_TERMINAL ? "TERMINAL" : deviceType.name();
    }
}
