package pos.pos.kds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.device.entity.Device;
import pos.pos.kds.dto.KdsAssignableDeviceResponse;
import pos.pos.kds.dto.KdsStationResponse;
import pos.pos.kds.entity.KdsStation;
import pos.pos.settings.service.SettingsDomainSupport;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KdsStationQueryService {

    private final SettingsDomainSupport settingsDomainSupport;
    private final KdsSupport kdsSupport;

    @Transactional(readOnly = true)
    public List<KdsStationResponse> getStations(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            boolean activeOnly
    ) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        return kdsSupport.mapper().mapStationResponses(kdsSupport.loadBranchStations(branchId, activeOnly));
    }

    @Transactional(readOnly = true)
    public KdsStationResponse getStation(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID stationId
    ) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        return kdsSupport.mapper().toStationResponse(kdsSupport.requireStationInBranch(branchId, stationId));
    }

    @Transactional(readOnly = true)
    public List<KdsAssignableDeviceResponse> getAssignableDevices(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        settingsDomainSupport.requireAccessibleBranch(authentication, restaurantId, branchId);
        List<KdsStation> stations = kdsSupport.loadBranchStations(branchId, false);
        Map<UUID, KdsStation> stationByDeviceId = stations.stream()
                .filter(station -> station.getDevice() != null)
                .collect(Collectors.toMap(station -> station.getDevice().getId(), Function.identity()));

        return kdsSupport.loadBranchKdsDevices(restaurantId, branchId).stream()
                .map(device -> kdsSupport.mapper().toAssignableDeviceResponse(device, stationByDeviceId.get(device.getId())))
                .toList();
    }
}
