package pos.pos.kds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.dto.KdsStationRoutingRequest;
import pos.pos.kds.dto.KdsStationResponse;
import pos.pos.kds.dto.UpsertKdsStationRequest;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.restaurant.entity.Branch;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KdsStationCommandService {

    private final SettingsDomainSupport settingsDomainSupport;
    private final SettingsAuditService settingsAuditService;
    private final KdsSupport kdsSupport;

    @Transactional
    public KdsStationResponse createStation(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpsertKdsStationRequest request
    ) {
        Branch branch = settingsDomainSupport.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = settingsDomainSupport.currentActorId(authentication);

        KdsStation station = new KdsStation();
        station.setRestaurant(branch.getRestaurant());
        station.setBranch(branch);
        station.setCreatedBy(actorId);
        station.setUpdatedBy(actorId);

        applyRequest(branch, station, request, null);
        KdsStation savedStation = kdsSupport.saveStation(station);

        settingsAuditService.log(
                branch.getRestaurant(),
                branch,
                "KDS_STATION",
                savedStation.getId(),
                "CREATE",
                "Created KDS station",
                actorId
        );

        return kdsSupport.mapper().toStationResponse(savedStation);
    }

    @Transactional
    public KdsStationResponse updateStation(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID stationId,
            UpsertKdsStationRequest request
    ) {
        Branch branch = settingsDomainSupport.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = settingsDomainSupport.currentActorId(authentication);
        KdsStation station = kdsSupport.requireStationInBranch(branchId, stationId);

        applyRequest(branch, station, request, stationId);
        station.setUpdatedBy(actorId);
        KdsStation savedStation = kdsSupport.saveStation(station);

        settingsAuditService.log(
                branch.getRestaurant(),
                branch,
                "KDS_STATION",
                savedStation.getId(),
                "UPDATE",
                "Updated KDS station",
                actorId
        );

        return kdsSupport.mapper().toStationResponse(savedStation);
    }

    private void applyRequest(
            Branch branch,
            KdsStation station,
            UpsertKdsStationRequest request,
            UUID stationId
    ) {
        String normalizedCode = request.getCode() == null ? null : request.getCode().trim();
        if (stationId == null) {
            if (normalizedCode != null && kdsSupport.loadBranchStations(branch.getId(), false).stream()
                    .anyMatch(existing -> existing.getCode().equalsIgnoreCase(normalizedCode))) {
                throw new AuthException("KDS station code already exists for this branch", HttpStatus.BAD_REQUEST);
            }
        } else if (normalizedCode != null && kdsSupport.loadBranchStations(branch.getId(), false).stream()
                .filter(existing -> !existing.getId().equals(stationId))
                .anyMatch(existing -> existing.getCode().equalsIgnoreCase(normalizedCode))) {
            throw new AuthException("KDS station code already exists for this branch", HttpStatus.BAD_REQUEST);
        }

        station.setDevice(kdsSupport.resolveKdsDevice(branch.getRestaurant().getId(), request.getDeviceId()));
        station.setCode(request.getCode());
        station.setName(request.getName());
        station.setStationType(request.getStationType());
        station.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        station.setActive(request.getActive() == null || request.getActive());
        station.setAcceptsScheduledOrders(request.getAcceptsScheduledOrders() == null || request.getAcceptsScheduledOrders());
        station.setScreenLabel(request.getScreenLabel());
        station.setNotes(request.getNotes());

        replaceRoutings(branch.getRestaurant().getId(), branch.getId(), station, request.getRoutings(), stationId);
    }

    private void replaceRoutings(
            UUID restaurantId,
            UUID branchId,
            KdsStation station,
            List<KdsStationRoutingRequest> requests,
            UUID stationId
    ) {
        Set<UUID> menuItemIds = new LinkedHashSet<>();
        List<KdsStationRouting> replacements = new ArrayList<>();

        if (requests != null) {
            for (KdsStationRoutingRequest request : requests) {
                if (!menuItemIds.add(request.getMenuItemId())) {
                    throw new AuthException("routings must not contain duplicate menuItemId values", HttpStatus.BAD_REQUEST);
                }

                boolean routingActive = request.getActive() == null || request.getActive();
                if (station.isActive() && routingActive
                        && kdsSupport.hasActiveRoutingConflict(branchId, request.getMenuItemId(), stationId)) {
                    throw new AuthException(
                            "A menu item can only be actively routed to one KDS station in the same branch",
                            HttpStatus.BAD_REQUEST
                    );
                }

                KdsStationRouting routing = new KdsStationRouting();
                routing.setMenuItem(kdsSupport.requireMenuItemInRestaurant(restaurantId, request.getMenuItemId()));
                routing.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
                routing.setPriority(request.getPriority() == null ? KdsPriority.NORMAL : request.getPriority());
                routing.setCourseLabel(request.getCourseLabel());
                routing.setActive(routingActive);
                replacements.add(routing);
            }
        }

        List<KdsStationRouting> existing = new ArrayList<>(station.getRoutings());
        existing.forEach(station::removeRouting);
        replacements.forEach(station::addRouting);
    }
}
