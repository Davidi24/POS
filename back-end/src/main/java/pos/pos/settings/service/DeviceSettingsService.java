package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.device.entity.Device;
import pos.pos.device.entity.DevicePrinterProfile;
import pos.pos.device.enums.PrinterConnectionType;
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
    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsAuditService settingsAuditService;

    @Transactional(readOnly = true)
    public List<DeviceResponse> getPrinters(Authentication authentication, UUID restaurantId) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        return deviceRepository.findAllByRestaurant_IdOrderByNameAsc(restaurantId).stream()
                .filter(this::isPrinter)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeviceResponse createPrinter(Authentication authentication, UUID restaurantId, UpsertDeviceRequest request) {
        Restaurant restaurant = settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        Device device = new Device();
        device.setRestaurant(restaurant);
        applyDevice(device, restaurantId, request, true);
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
        Device device = requireDevice(restaurantId, printerId);
        ensurePrinter(device);
        applyDevice(device, restaurantId, request, true);
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
        Device device = requireDevice(restaurantId, printerId);
        ensurePrinter(device);
        deleteAndAudit(authentication, device, "DEVICE_PRINTER", "DELETE", "Deleted printer settings");
    }

    @Transactional(readOnly = true)
    public PrinterRouteResponse getPrinterRoutes(Authentication authentication, UUID restaurantId, UUID branchId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        List<Device> printers = deviceRepository.findAllByRestaurant_IdAndBranch_IdOrderByNameAsc(restaurantId, branchId).stream()
                .filter(this::isPrinter)
                .toList();
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

        List<Device> restaurantPrinters = deviceRepository.findAllByRestaurant_IdOrderByNameAsc(restaurantId).stream()
                .filter(this::isPrinter)
                .toList();

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

        if (requestedPrinterIds.size() > restaurantPrinters.stream().map(Device::getId).filter(requestedPrinterIds::contains).count()) {
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
        return deviceRepository.findAllByRestaurant_IdAndBranch_IdOrderByNameAsc(restaurantId, branchId).stream()
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
        Device device = new Device();
        device.setRestaurant(branch.getRestaurant());
        device.setBranch(branch);
        applyDevice(device, restaurantId, request, false);
        device.setBranch(branch);
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
        Device device = requireBranchDevice(authentication, restaurantId, branchId, deviceId);
        applyDevice(device, restaurantId, request, false);
        device.setBranch(settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId));
        return saveAuditAndRespond(
                authentication,
                device,
                "Device settings update violates a data constraint",
                "DEVICE",
                "UPDATE",
                "Updated branch device"
        );
    }

    @Transactional
    public DeviceResponse updateDeviceStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID deviceId,
            UpdateDeviceStatusRequest request
    ) {
        Device device = requireBranchDevice(authentication, restaurantId, branchId, deviceId);
        device.setStatus(request.getStatus());
        device.setActive(Boolean.TRUE.equals(request.getActive()));
        device.setOnline(Boolean.TRUE.equals(request.getOnline()));
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
        deleteAndAudit(authentication, device, "DEVICE", "DELETE", "Deleted branch device");
    }

    private void applyDevice(Device device, UUID restaurantId, UpsertDeviceRequest request, boolean printerMode) {
        Branch branch = request.getBranchId() == null ? null : settingsDomainSupport.resolveBranch(restaurantId, request.getBranchId());
        device.setBranch(branch);
        device.setCode(request.getCode());
        device.setName(request.getName());
        device.setDeviceType(printerMode ? "PRINTER" : request.getDeviceType());
        device.setManufacturer(request.getManufacturer());
        device.setModel(request.getModel());
        device.setSerialNumber(request.getSerialNumber());
        device.setPlatform(request.getPlatform());
        device.setOsVersion(request.getOsVersion());
        device.setAppVersion(request.getAppVersion());
        device.setStatus(request.getStatus());
        device.setActive(Boolean.TRUE.equals(request.getActive()));
        device.setOnline(Boolean.TRUE.equals(request.getOnline()));
        device.setIpAddress(request.getIpAddress());
        device.setMacAddress(request.getMacAddress());
        device.setNotes(request.getNotes());

        if (printerMode) {
            ensurePrinterProfile(device, request);
            return;
        }

        if (device.getPrinterProfile() != null && isPrinter(device)) {
            applyPrinterProfile(device.getPrinterProfile(), request);
        } else if (device.getPrinterProfile() != null) {
            device.setPrinterProfile(null);
        }
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
    }

    private Device requireDevice(UUID restaurantId, UUID deviceId) {
        return deviceRepository.findByIdAndRestaurant_Id(deviceId, restaurantId)
                .orElseThrow(DeviceNotFoundException::new);
    }

    private Device requireBranchDevice(Authentication authentication, UUID restaurantId, UUID branchId, UUID deviceId) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        Device device = requireDevice(restaurantId, deviceId);
        if (device.getBranch() == null || !branchId.equals(device.getBranch().getId())) {
            throw new DeviceNotFoundException();
        }
        return device;
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

    private boolean isPrinter(Device device) {
        return device.getPrinterProfile() != null
                || "PRINTER".equals(normalizeToken(device.getDeviceType()));
    }

    private void ensurePrinter(Device device) {
        if (!isPrinter(device)) {
            throw new DeviceNotFoundException();
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
                .deviceType(device.getDeviceType())
                .manufacturer(device.getManufacturer())
                .model(device.getModel())
                .serialNumber(device.getSerialNumber())
                .platform(device.getPlatform())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .status(device.getStatus())
                .active(device.isActive())
                .online(device.isOnline())
                .lastSeenAt(device.getLastSeenAt())
                .ipAddress(device.getIpAddress())
                .macAddress(device.getMacAddress())
                .notes(device.getNotes())
                .printerConnectionType(printerProfile == null ? null : printerProfile.getConnectionType())
                .paperWidthMm(printerProfile == null ? null : printerProfile.getPaperWidthMm())
                .printerIp(printerProfile == null ? null : printerProfile.getPrinterIp())
                .printerPort(printerProfile == null ? null : printerProfile.getPrinterPort())
                .autoCut(printerProfile == null ? null : printerProfile.isAutoCut())
                .cashDrawerKickEnabled(printerProfile == null ? null : printerProfile.isCashDrawerKickEnabled())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
}
