package pos.pos.kds.service;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.device.entity.Device;
import pos.pos.device.enums.DeviceType;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.device.DeviceNotFoundException;
import pos.pos.exception.kds.KdsStationNotFoundException;
import pos.pos.exception.kds.KdsTicketItemNotFoundException;
import pos.pos.exception.kds.KdsTicketNotFoundException;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.kds.mapper.KdsMapper;
import pos.pos.kds.repository.KdsStationRepository;
import pos.pos.kds.repository.KdsStationRoutingRepository;
import pos.pos.kds.repository.KdsTicketItemRepository;
import pos.pos.kds.repository.KdsTicketRepository;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KdsSupport {

    public static final EnumSet<KdsTicketStatus> ACTIVE_STATUSES = EnumSet.of(
            KdsTicketStatus.PENDING,
            KdsTicketStatus.FIRED,
            KdsTicketStatus.IN_PROGRESS,
            KdsTicketStatus.READY,
            KdsTicketStatus.EXPO_READY
    );

    private static final int TICKET_NUMBER_ATTEMPTS = 16;

    private final KdsStationRepository kdsStationRepository;
    private final KdsStationRoutingRepository kdsStationRoutingRepository;
    private final KdsTicketRepository kdsTicketRepository;
    private final KdsTicketItemRepository kdsTicketItemRepository;
    private final DeviceRepository deviceRepository;
    private final MenuItemRepository menuItemRepository;
    private final KdsMapper kdsMapper;

    public List<KdsStation> loadBranchStations(UUID branchId, boolean activeOnly) {
        return activeOnly
                ? kdsStationRepository.findAllByBranch_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(branchId)
                : kdsStationRepository.findAllByBranch_IdOrderByDisplayOrderAscNameAsc(branchId);
    }

    public List<KdsStationRouting> loadActiveBranchRoutings(UUID branchId) {
        return kdsStationRoutingRepository.findAllActiveByBranchId(branchId);
    }

    public KdsStation requireStationInBranch(UUID branchId, UUID stationId) {
        return kdsStationRepository.findByIdAndBranch_Id(stationId, branchId)
                .orElseThrow(KdsStationNotFoundException::new);
    }

    public KdsStation requireStationForDevice(UUID restaurantId, UUID deviceId) {
        return kdsStationRepository.findByDevice_IdAndRestaurant_Id(deviceId, restaurantId)
                .orElseThrow(KdsStationNotFoundException::new);
    }

    public KdsTicket requireTicketInBranch(UUID branchId, UUID ticketId) {
        return kdsTicketRepository.findByIdAndBranch_Id(ticketId, branchId)
                .orElseThrow(KdsTicketNotFoundException::new);
    }

    public KdsTicketItem requireTicketItem(KdsTicket ticket, UUID ticketItemId) {
        return kdsTicketItemRepository.findByIdAndKdsTicket_Id(ticketItemId, ticket.getId())
                .orElseThrow(KdsTicketItemNotFoundException::new);
    }

    public Device resolveKdsDevice(UUID restaurantId, UUID deviceId) {
        if (deviceId == null) {
            return null;
        }

        Device device = deviceRepository.findByIdAndRestaurant_Id(deviceId, restaurantId)
                .orElseThrow(DeviceNotFoundException::new);
        if (device.getDeviceType() != DeviceType.KDS) {
            throw new AuthException("deviceId must reference a KDS device", HttpStatus.BAD_REQUEST);
        }
        if (!device.isActive()) {
            throw new AuthException("Selected KDS device is not active", HttpStatus.BAD_REQUEST);
        }
        return device;
    }

    public List<Device> loadBranchKdsDevices(UUID restaurantId, UUID branchId) {
        return deviceRepository.findBranchNonPrinterDevices(restaurantId, branchId).stream()
                .filter(device -> device.getDeviceType() == DeviceType.KDS)
                .sorted(Comparator.comparing(Device::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public MenuItem requireMenuItemInRestaurant(UUID restaurantId, UUID menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new AuthException("menuItemId references a missing menu item", HttpStatus.BAD_REQUEST));

        if (menuItem.getSection() == null
                || menuItem.getSection().getMenu() == null
                || menuItem.getSection().getMenu().getRestaurant() == null
                || !Objects.equals(menuItem.getSection().getMenu().getRestaurant().getId(), restaurantId)) {
            throw new AuthException("menuItemId must belong to the same restaurant", HttpStatus.BAD_REQUEST);
        }

        return menuItem;
    }

    public boolean hasActiveRoutingConflict(UUID branchId, UUID menuItemId, UUID stationId) {
        return kdsStationRoutingRepository.countActiveBranchRoutingsByMenuItem(branchId, menuItemId, stationId) > 0;
    }

    public List<KdsTicket> loadOrderTickets(UUID orderId) {
        return kdsTicketRepository.findAllByOrder_IdOrderByCreatedAtAsc(orderId);
    }

    public List<KdsTicket> loadBranchTickets(UUID branchId, boolean includeCompleted) {
        return includeCompleted
                ? kdsTicketRepository.findAllByBranch_IdOrderByCreatedAtAsc(branchId)
                : kdsTicketRepository.findAllByBranch_IdAndStatusInOrderByCreatedAtAsc(branchId, ACTIVE_STATUSES);
    }

    public Optional<KdsTicket> findActiveTicket(UUID orderId, UUID stationId) {
        return kdsTicketRepository.findTopByOrder_IdAndStation_IdAndStatusInOrderByCreatedAtDesc(
                orderId,
                stationId,
                ACTIVE_STATUSES
        );
    }

    public Optional<KdsTicketItem> findActiveTicketItem(UUID lineItemId) {
        return kdsTicketItemRepository.findTopByOrderLineItem_IdAndKdsTicket_StatusInOrderByCreatedAtDesc(
                lineItemId,
                ACTIVE_STATUSES
        );
    }

    public List<KdsTicketItem> findActiveTicketItems(UUID lineItemId) {
        return kdsTicketItemRepository.findAllByOrderLineItem_IdAndKdsTicket_StatusIn(lineItemId, ACTIVE_STATUSES);
    }

    public boolean orderHasTicketHistory(UUID orderId) {
        return kdsTicketRepository.existsByOrder_Id(orderId);
    }

    public boolean orderHasLineItemHistory(UUID orderId) {
        return kdsTicketItemRepository.existsByOrderLineItem_Order_Id(orderId);
    }

    public boolean lineItemHasHistory(UUID lineItemId) {
        return kdsTicketItemRepository.existsByOrderLineItem_Id(lineItemId);
    }

    public String nextTicketNumber(Restaurant restaurant) {
        String prefix = "KDS";
        if (restaurant != null) {
            String candidatePrefix = NormalizationUtils.normalizeCode(restaurant.getCode(), 12);
            if (candidatePrefix != null) {
                prefix = candidatePrefix + "_KDS";
            }
        }

        for (int attempt = 0; attempt < TICKET_NUMBER_ATTEMPTS; attempt++) {
            String suffix = UuidCreator.getTimeOrdered().toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase();
            String candidate = prefix + "-" + suffix;
            boolean exists = restaurant != null
                    && restaurant.getId() != null
                    && kdsTicketRepository.existsByRestaurant_IdAndTicketNumber(restaurant.getId(), candidate);
            if (!exists) {
                return candidate;
            }
        }

        throw new AuthException("KDS ticket number could not be generated", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public KdsStation saveStation(KdsStation station) {
        try {
            return kdsStationRepository.saveAndFlush(station);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("KDS station update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public KdsTicket saveTicket(KdsTicket ticket) {
        try {
            return kdsTicketRepository.saveAndFlush(ticket);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("KDS ticket update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public void saveTickets(Collection<KdsTicket> tickets) {
        try {
            kdsTicketRepository.saveAllAndFlush(tickets);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("KDS ticket update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public void applyItemStatus(KdsTicketItem item, KdsTicketStatus status, OffsetDateTime now) {
        if (item == null || status == null) {
            return;
        }

        if (item.getStatus() == KdsTicketStatus.COMPLETED) {
            return;
        }
        if (item.getStatus() == KdsTicketStatus.CANCELLED && status != KdsTicketStatus.CANCELLED) {
            return;
        }

        switch (status) {
            case PENDING -> {
                item.setStatus(KdsTicketStatus.PENDING);
                item.setFiredAt(null);
                item.setReadyAt(null);
                item.setCompletedAt(null);
            }
            case FIRED -> {
                if (phaseOf(item.getStatus()) > phaseOf(KdsTicketStatus.FIRED)) {
                    return;
                }
                item.setStatus(KdsTicketStatus.FIRED);
                if (item.getFiredAt() == null) {
                    item.setFiredAt(now);
                }
                item.setReadyAt(null);
                item.setCompletedAt(null);
            }
            case IN_PROGRESS -> {
                if (phaseOf(item.getStatus()) > phaseOf(KdsTicketStatus.IN_PROGRESS)) {
                    return;
                }
                item.setStatus(KdsTicketStatus.IN_PROGRESS);
                if (item.getFiredAt() == null) {
                    item.setFiredAt(now);
                }
                item.setReadyAt(null);
                item.setCompletedAt(null);
            }
            case READY, EXPO_READY -> {
                if (phaseOf(item.getStatus()) > phaseOf(KdsTicketStatus.READY)) {
                    return;
                }
                item.setStatus(KdsTicketStatus.READY);
                if (item.getFiredAt() == null) {
                    item.setFiredAt(now);
                }
                if (item.getReadyAt() == null) {
                    item.setReadyAt(now);
                }
                item.setCompletedAt(null);
            }
            case COMPLETED -> {
                item.setStatus(KdsTicketStatus.COMPLETED);
                if (item.getFiredAt() == null) {
                    item.setFiredAt(now);
                }
                if (item.getReadyAt() == null) {
                    item.setReadyAt(now);
                }
                if (item.getCompletedAt() == null) {
                    item.setCompletedAt(now);
                }
            }
            case CANCELLED -> {
                if (item.getStatus() == KdsTicketStatus.COMPLETED) {
                    return;
                }
                item.setStatus(KdsTicketStatus.CANCELLED);
                if (item.getFiredAt() == null && phaseOf(status) > phaseOf(KdsTicketStatus.PENDING)) {
                    item.setFiredAt(now);
                }
                item.setReadyAt(null);
                item.setCompletedAt(null);
            }
        }
    }

    public void refreshTicketAggregate(KdsTicket ticket) {
        List<KdsTicketItem> items = ticket.getItems();
        if (items.isEmpty()) {
            ticket.setStatus(KdsTicketStatus.PENDING);
            ticket.setPriority(KdsPriority.NORMAL);
            ticket.setFiredAt(null);
            ticket.setReadyAt(null);
            ticket.setCompletedAt(null);
            return;
        }

        List<KdsTicketItem> liveItems = items.stream()
                .filter(item -> item.getStatus() != KdsTicketStatus.CANCELLED)
                .toList();

        ticket.setPriority(maxPriority(items.stream()
                .map(KdsTicketItem::getPriority)
                .filter(Objects::nonNull)
                .toList()));

        ticket.setFiredAt(items.stream()
                .map(KdsTicketItem::getFiredAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null));

        if (liveItems.isEmpty()) {
            ticket.setStatus(KdsTicketStatus.CANCELLED);
            ticket.setReadyAt(null);
            ticket.setCompletedAt(null);
            return;
        }

        boolean allCompleted = liveItems.stream().allMatch(item -> item.getStatus() == KdsTicketStatus.COMPLETED);
        boolean allReadyOrCompleted = liveItems.stream().allMatch(item ->
                item.getStatus() == KdsTicketStatus.READY
                        || item.getStatus() == KdsTicketStatus.EXPO_READY
                        || item.getStatus() == KdsTicketStatus.COMPLETED
        );
        boolean anyInProgress = liveItems.stream().anyMatch(item -> item.getStatus() == KdsTicketStatus.IN_PROGRESS);
        boolean anyFired = liveItems.stream().anyMatch(item ->
                item.getStatus() == KdsTicketStatus.FIRED
                        || item.getStatus() == KdsTicketStatus.IN_PROGRESS
                        || item.getStatus() == KdsTicketStatus.READY
                        || item.getStatus() == KdsTicketStatus.EXPO_READY
                        || item.getStatus() == KdsTicketStatus.COMPLETED
        );

        if (allCompleted) {
            ticket.setStatus(KdsTicketStatus.COMPLETED);
            ticket.setReadyAt(liveItems.stream()
                    .map(KdsTicketItem::getReadyAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(ticket.getReadyAt()));
            ticket.setCompletedAt(liveItems.stream()
                    .map(KdsTicketItem::getCompletedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(OffsetDateTime.now(ZoneOffset.UTC)));
            return;
        }

        if (allReadyOrCompleted) {
            ticket.setStatus(readyStatusFor(ticket.getStation()));
            ticket.setReadyAt(liveItems.stream()
                    .map(item -> item.getReadyAt() == null ? item.getCompletedAt() : item.getReadyAt())
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(ticket.getReadyAt()));
            ticket.setCompletedAt(null);
            return;
        }

        if (anyInProgress) {
            ticket.setStatus(KdsTicketStatus.IN_PROGRESS);
            ticket.setReadyAt(null);
            ticket.setCompletedAt(null);
            return;
        }

        if (anyFired) {
            ticket.setStatus(KdsTicketStatus.FIRED);
            ticket.setReadyAt(null);
            ticket.setCompletedAt(null);
            return;
        }

        ticket.setStatus(KdsTicketStatus.PENDING);
        ticket.setReadyAt(null);
        ticket.setCompletedAt(null);
    }

    public KdsPriority maxPriority(Collection<KdsPriority> priorities) {
        return priorities.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(this::priorityRank))
                .orElse(KdsPriority.NORMAL);
    }

    public KdsTicketStatus readyStatusFor(KdsStation station) {
        return station != null && station.getStationType() != null && station.getStationType().name().equals("EXPO")
                ? KdsTicketStatus.EXPO_READY
                : KdsTicketStatus.READY;
    }

    public KdsMapper mapper() {
        return kdsMapper;
    }

    private int priorityRank(KdsPriority priority) {
        return switch (priority) {
            case NORMAL -> 0;
            case HOLD_FIRE -> 1;
            case RUSH -> 2;
            case VIP -> 3;
        };
    }

    private int phaseOf(KdsTicketStatus status) {
        if (status == null) {
            return -1;
        }

        return switch (status) {
            case PENDING -> 0;
            case FIRED -> 1;
            case IN_PROGRESS -> 2;
            case READY, EXPO_READY -> 3;
            case COMPLETED -> 4;
            case CANCELLED -> 5;
        };
    }
}
