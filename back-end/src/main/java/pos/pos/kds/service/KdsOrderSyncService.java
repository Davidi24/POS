package pos.pos.kds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.service.OrderSupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KdsOrderSyncService {

    private final KdsSupport kdsSupport;
    private final OrderSupport orderSupport;

    public List<KdsTicket> syncFromCurrentOrderState(Order order, UUID actorId) {
        return syncFromCurrentOrderState(order, actorId, null);
    }

    public List<KdsTicket> syncFromCurrentOrderState(Order order, UUID actorId, String voidReason) {
        if (order == null || order.getId() == null || order.getBranch() == null) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<UUID, KdsStationRouting> routingByMenuItemId = loadRoutingMap(order.getBranch().getId());
        List<KdsTicket> modifiedTickets = new ArrayList<>();

        for (OrderLineItem lineItem : order.getLineItems()) {
            if (lineItem.getId() == null) {
                continue;
            }

            if (lineItem.getStatus() == OrderLineItemStatus.CANCELLED || lineItem.getStatus() == OrderLineItemStatus.VOIDED) {
                for (KdsTicketItem ticketItem : kdsSupport.findActiveTicketItems(lineItem.getId())) {
                    kdsSupport.applyItemStatus(ticketItem, KdsTicketStatus.CANCELLED, now);
                    ticketItem.setNotes(lineItem.getNotes());
                    KdsTicket ticket = ticketItem.getKdsTicket();
                    ticket.setUpdatedBy(actorId);
                    if (voidReason != null && !voidReason.isBlank()) {
                        ticket.setVoidReason(voidReason);
                    }
                    addTicket(modifiedTickets, ticket);
                }
                continue;
            }

            KdsTicketStatus targetStatus = toKdsStatus(lineItem.getStatus());
            if (targetStatus == null) {
                continue;
            }

            Optional<KdsTicketItem> activeTicketItem = kdsSupport.findActiveTicketItem(lineItem.getId());
            KdsStationRouting routing = routingByMenuItemId.get(lineItem.getMenuItem().getId());
            if (activeTicketItem.isEmpty() && routing == null) {
                continue;
            }

            KdsTicket ticket;
            KdsTicketItem ticketItem;
            if (activeTicketItem.isPresent()) {
                ticketItem = activeTicketItem.get();
                ticket = ticketItem.getKdsTicket();
            } else {
                KdsStation station = routing.getStation();
                if (!stationAcceptsOrder(station, order, now)) {
                    continue;
                }
                ticket = kdsSupport.findActiveTicket(order.getId(), station.getId())
                        .orElseGet(() -> newTicket(order, station, actorId));
                ticketItem = new KdsTicketItem();
                ticket.addItem(ticketItem);
            }

            KdsStationRouting effectiveRouting = routing;
            if (effectiveRouting == null && ticket.getStation() != null) {
                effectiveRouting = routingByMenuItemId.get(lineItem.getMenuItem().getId());
            }

            populateTicketItem(ticketItem, lineItem, effectiveRouting);
            if (targetStatus == KdsTicketStatus.IN_PROGRESS && ticket.getStartedAt() == null) {
                ticket.setStartedAt(now);
            }
            kdsSupport.applyItemStatus(ticketItem, targetStatus, now);
            ticket.setUpdatedBy(actorId);
            refreshTicketMetadata(ticket, routingByMenuItemId);
            addTicket(modifiedTickets, ticket);
        }

        modifiedTickets.forEach(kdsSupport::refreshTicketAggregate);
        if (!modifiedTickets.isEmpty()) {
            kdsSupport.saveTickets(modifiedTickets);
        }

        return kdsSupport.loadOrderTickets(order.getId());
    }

    public void syncLineItemNotes(OrderLineItem lineItem, UUID actorId) {
        if (lineItem == null || lineItem.getId() == null) {
            return;
        }

        List<KdsTicket> modifiedTickets = new ArrayList<>();
        for (KdsTicketItem ticketItem : kdsSupport.findActiveTicketItems(lineItem.getId())) {
            ticketItem.setNotes(lineItem.getNotes());
            ticketItem.setQuantity(lineItem.getQuantity());
            ticketItem.setItemNameSnapshot(lineItem.getItemNameSnapshot());
            ticketItem.getKdsTicket().setUpdatedBy(actorId);
            addTicket(modifiedTickets, ticketItem.getKdsTicket());
        }

        if (!modifiedTickets.isEmpty()) {
            kdsSupport.saveTickets(modifiedTickets);
        }
    }

    public void assertNoTicketHistory(Order order, String message) {
        if (order != null && order.getId() != null && kdsSupport.orderHasTicketHistory(order.getId())) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    public void assertNoLineItemHistory(Order order, String message) {
        if (order != null && order.getId() != null && kdsSupport.orderHasLineItemHistory(order.getId())) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    public void assertLineItemMutable(OrderLineItem lineItem, String message) {
        if (lineItem != null && lineItem.getId() != null && kdsSupport.lineItemHasHistory(lineItem.getId())) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    private Map<UUID, KdsStationRouting> loadRoutingMap(UUID branchId) {
        Map<UUID, KdsStationRouting> routingByMenuItemId = new LinkedHashMap<>();
        for (KdsStationRouting routing : kdsSupport.loadActiveBranchRoutings(branchId)) {
            UUID menuItemId = routing.getMenuItem().getId();
            if (!routingByMenuItemId.containsKey(menuItemId)) {
                routingByMenuItemId.put(menuItemId, routing);
            }
        }
        return routingByMenuItemId;
    }

    private KdsTicket newTicket(Order order, KdsStation station, UUID actorId) {
        KdsTicket ticket = new KdsTicket();
        ticket.setRestaurant(order.getRestaurant());
        ticket.setBranch(order.getBranch());
        ticket.setStation(station);
        ticket.setOrder(order);
        ticket.setTicketNumber(kdsSupport.nextTicketNumber(order.getRestaurant()));
        ticket.setStatus(KdsTicketStatus.PENDING);
        ticket.setPriority(KdsPriority.NORMAL);
        ticket.setNotes(order.getNotes());
        ticket.setCreatedBy(actorId);
        ticket.setUpdatedBy(actorId);
        return ticket;
    }

    private void populateTicketItem(KdsTicketItem ticketItem, OrderLineItem lineItem, KdsStationRouting routing) {
        ticketItem.setOrderLineItem(lineItem);
        ticketItem.setItemNameSnapshot(lineItem.getItemNameSnapshot());
        ticketItem.setQuantity(lineItem.getQuantity());
        ticketItem.setPriority(routing == null || routing.getPriority() == null ? KdsPriority.NORMAL : routing.getPriority());
        ticketItem.setNotes(lineItem.getNotes());
    }

    private void refreshTicketMetadata(KdsTicket ticket, Map<UUID, KdsStationRouting> routingByMenuItemId) {
        List<KdsPriority> priorities = new ArrayList<>();
        List<String> courseLabels = new ArrayList<>();
        for (KdsTicketItem item : ticket.getItems()) {
            if (item.getPriority() != null) {
                priorities.add(item.getPriority());
            }

            if (item.getOrderLineItem() != null && item.getOrderLineItem().getMenuItem() != null) {
                KdsStationRouting routing = routingByMenuItemId.get(item.getOrderLineItem().getMenuItem().getId());
                if (routing != null
                        && routing.getStation() != null
                        && ticket.getStation() != null
                        && Objects.equals(routing.getStation().getId(), ticket.getStation().getId())
                        && routing.getCourseLabel() != null) {
                    courseLabels.add(routing.getCourseLabel());
                }
            }
        }

        ticket.setPriority(kdsSupport.maxPriority(priorities));
        String commonCourse = courseLabels.stream().distinct().count() == 1 ? courseLabels.get(0) : null;
        if (commonCourse != null || ticket.getCourseName() == null) {
            ticket.setCourseName(commonCourse);
        }
    }

    private boolean stationAcceptsOrder(KdsStation station, Order order, OffsetDateTime now) {
        return station.isAcceptsScheduledOrders()
                || order.getOpenedAt() == null
                || !order.getOpenedAt().isAfter(now);
    }

    private KdsTicketStatus toKdsStatus(OrderLineItemStatus status) {
        if (status == null) {
            return null;
        }

        return switch (status) {
            case PENDING -> null;
            case FIRED -> KdsTicketStatus.FIRED;
            case PREPARING -> KdsTicketStatus.IN_PROGRESS;
            case READY -> KdsTicketStatus.READY;
            case FULFILLED -> KdsTicketStatus.COMPLETED;
            case CANCELLED, VOIDED -> KdsTicketStatus.CANCELLED;
        };
    }

    private void addTicket(List<KdsTicket> tickets, KdsTicket candidate) {
        boolean exists = tickets.stream().anyMatch(existing -> existing == candidate);
        if (!exists) {
            tickets.add(candidate);
        }
    }
}
