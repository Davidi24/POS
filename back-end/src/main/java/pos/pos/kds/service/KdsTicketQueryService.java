package pos.pos.kds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.dto.KdsStationBoardResponse;
import pos.pos.kds.dto.KdsTicketResponse;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.order.service.OrderSupport;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KdsTicketQueryService {

    private final RestaurantScopeService restaurantScopeService;
    private final OrderSupport orderSupport;
    private final KdsSupport kdsSupport;

    @Transactional(readOnly = true)
    public List<KdsStationBoardResponse> getBoard(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID stationId,
            UUID deviceId,
            boolean includeCompleted
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        List<KdsStation> stations = resolveStations(restaurantId, branchId, stationId, deviceId);
        Map<UUID, List<KdsTicket>> ticketsByStationId = new LinkedHashMap<>();
        List<KdsTicket> tickets = kdsSupport.loadBranchTickets(branchId, includeCompleted);
        for (KdsStation station : stations) {
            List<KdsTicket> stationTickets = tickets.stream()
                    .filter(ticket -> ticket.getStation() != null && station.getId().equals(ticket.getStation().getId()))
                    .toList();
            ticketsByStationId.put(station.getId(), stationTickets);
        }

        return stations.stream()
                .map(station -> kdsSupport.mapper().toBoardResponse(
                        station,
                        ticketsByStationId.getOrDefault(station.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public KdsStationBoardResponse getDisplay(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID deviceId,
            boolean includeCompleted
    ) {
        if (deviceId == null) {
            throw new AuthException("deviceId is required", HttpStatus.BAD_REQUEST);
        }

        List<KdsStationBoardResponse> board = getBoard(
                authentication,
                restaurantId,
                branchId,
                null,
                deviceId,
                includeCompleted
        );
        if (board.isEmpty()) {
            throw new AuthException("No KDS station is assigned to the selected device", HttpStatus.NOT_FOUND);
        }
        return board.get(0);
    }

    @Transactional(readOnly = true)
    public KdsTicketResponse getTicket(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID ticketId
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return kdsSupport.mapper().toTicketResponse(kdsSupport.requireTicketInBranch(branchId, ticketId));
    }

    @Transactional(readOnly = true)
    public List<KdsTicketResponse> getOrderTickets(
            Authentication authentication,
            UUID restaurantId,
            UUID orderId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return kdsSupport.mapper().mapTicketResponses(kdsSupport.loadOrderTickets(
                orderSupport.requireOrder(restaurantId, orderId).getId()
        ));
    }

    private List<KdsStation> resolveStations(
            UUID restaurantId,
            UUID branchId,
            UUID stationId,
            UUID deviceId
    ) {
        if (deviceId != null) {
            KdsStation station = kdsSupport.requireStationForDevice(restaurantId, deviceId);
            if (!branchId.equals(station.getBranch().getId())) {
                throw new AuthException("deviceId does not belong to the selected branch", HttpStatus.BAD_REQUEST);
            }
            if (stationId != null && !stationId.equals(station.getId())) {
                throw new AuthException("stationId does not match the device-assigned station", HttpStatus.BAD_REQUEST);
            }
            return List.of(station);
        }

        if (stationId != null) {
            return List.of(kdsSupport.requireStationInBranch(branchId, stationId));
        }

        return kdsSupport.loadBranchStations(branchId, true);
    }
}
