package pos.pos.kds.mapper;

import org.springframework.stereotype.Component;
import pos.pos.device.entity.Device;
import pos.pos.kds.dto.KdsAssignableDeviceResponse;
import pos.pos.kds.dto.KdsStationBoardResponse;
import pos.pos.kds.dto.KdsStationResponse;
import pos.pos.kds.dto.KdsStationRoutingResponse;
import pos.pos.kds.dto.KdsTicketItemResponse;
import pos.pos.kds.dto.KdsTicketResponse;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.entity.KdsTicketItem;
import pos.pos.kds.enums.KdsTicketStatus;

import java.util.Comparator;
import java.util.List;

@Component
public class KdsMapper {

    public KdsStationResponse toStationResponse(KdsStation station) {
        if (station == null) {
            return null;
        }

        return KdsStationResponse.builder()
                .id(station.getId())
                .restaurantId(station.getRestaurant() == null ? null : station.getRestaurant().getId())
                .branchId(station.getBranch() == null ? null : station.getBranch().getId())
                .branchName(station.getBranch() == null ? null : station.getBranch().getName())
                .deviceId(station.getDevice() == null ? null : station.getDevice().getId())
                .deviceCode(station.getDevice() == null ? null : station.getDevice().getCode())
                .deviceName(station.getDevice() == null ? null : station.getDevice().getName())
                .code(station.getCode())
                .name(station.getName())
                .stationType(station.getStationType())
                .displayOrder(station.getDisplayOrder())
                .active(station.isActive())
                .acceptsScheduledOrders(station.isAcceptsScheduledOrders())
                .screenLabel(station.getScreenLabel())
                .notes(station.getNotes())
                .createdAt(station.getCreatedAt())
                .updatedAt(station.getUpdatedAt())
                .createdBy(station.getCreatedBy())
                .updatedBy(station.getUpdatedBy())
                .routings(mapRoutingResponses(station.getRoutings()))
                .build();
    }

    public KdsStationRoutingResponse toRoutingResponse(KdsStationRouting routing) {
        if (routing == null) {
            return null;
        }

        return KdsStationRoutingResponse.builder()
                .id(routing.getId())
                .menuItemId(routing.getMenuItem() == null ? null : routing.getMenuItem().getId())
                .menuItemName(routing.getMenuItem() == null ? null : routing.getMenuItem().getName())
                .displayOrder(routing.getDisplayOrder())
                .priority(routing.getPriority())
                .courseLabel(routing.getCourseLabel())
                .active(routing.isActive())
                .createdAt(routing.getCreatedAt())
                .updatedAt(routing.getUpdatedAt())
                .build();
    }

    public KdsAssignableDeviceResponse toAssignableDeviceResponse(Device device, KdsStation assignedStation) {
        return KdsAssignableDeviceResponse.builder()
                .deviceId(device.getId())
                .deviceCode(device.getCode())
                .deviceName(device.getName())
                .status(device.getStatus())
                .active(device.isActive())
                .online(device.isOnline())
                .assignedStationId(assignedStation == null ? null : assignedStation.getId())
                .assignedStationCode(assignedStation == null ? null : assignedStation.getCode())
                .assignedStationName(assignedStation == null ? null : assignedStation.getName())
                .build();
    }

    public KdsTicketResponse toTicketResponse(KdsTicket ticket) {
        if (ticket == null) {
            return null;
        }

        return KdsTicketResponse.builder()
                .id(ticket.getId())
                .restaurantId(ticket.getRestaurant() == null ? null : ticket.getRestaurant().getId())
                .branchId(ticket.getBranch() == null ? null : ticket.getBranch().getId())
                .stationId(ticket.getStation() == null ? null : ticket.getStation().getId())
                .stationCode(ticket.getStation() == null ? null : ticket.getStation().getCode())
                .stationName(ticket.getStation() == null ? null : ticket.getStation().getName())
                .deviceId(ticket.getStation() == null || ticket.getStation().getDevice() == null
                        ? null
                        : ticket.getStation().getDevice().getId())
                .ticketNumber(ticket.getTicketNumber())
                .orderId(ticket.getOrder() == null ? null : ticket.getOrder().getId())
                .orderNumber(ticket.getOrder() == null ? null : ticket.getOrder().getOrderNumber())
                .tableId(ticket.getOrder() == null || ticket.getOrder().getRestaurantTable() == null
                        ? null
                        : ticket.getOrder().getRestaurantTable().getId())
                .tableNumber(ticket.getOrder() == null || ticket.getOrder().getRestaurantTable() == null
                        ? null
                        : ticket.getOrder().getRestaurantTable().getTableNumber())
                .tableName(ticket.getOrder() == null || ticket.getOrder().getRestaurantTable() == null
                        ? null
                        : ticket.getOrder().getRestaurantTable().getName())
                .customerId(ticket.getOrder() == null || ticket.getOrder().getCustomer() == null
                        ? null
                        : ticket.getOrder().getCustomer().getId())
                .customerName(ticket.getOrder() == null || ticket.getOrder().getCustomer() == null
                        ? null
                        : ticket.getOrder().getCustomer().displayName())
                .guestCount(ticket.getOrder() == null ? null : ticket.getOrder().getGuestCount())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .courseName(ticket.getCourseName())
                .notes(ticket.getNotes())
                .voidReason(ticket.getVoidReason())
                .firedAt(ticket.getFiredAt())
                .startedAt(ticket.getStartedAt())
                .readyAt(ticket.getReadyAt())
                .completedAt(ticket.getCompletedAt())
                .dueAt(ticket.getDueAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .createdBy(ticket.getCreatedBy())
                .updatedBy(ticket.getUpdatedBy())
                .items(mapTicketItemResponses(ticket.getItems()))
                .build();
    }

    public KdsTicketItemResponse toTicketItemResponse(KdsTicketItem item) {
        if (item == null) {
            return null;
        }

        return KdsTicketItemResponse.builder()
                .id(item.getId())
                .orderLineItemId(item.getOrderLineItem() == null ? null : item.getOrderLineItem().getId())
                .menuItemId(item.getOrderLineItem() == null || item.getOrderLineItem().getMenuItem() == null
                        ? null
                        : item.getOrderLineItem().getMenuItem().getId())
                .itemNameSnapshot(item.getItemNameSnapshot())
                .quantity(item.getQuantity())
                .status(item.getStatus())
                .priority(item.getPriority())
                .seatLabel(item.getSeatLabel())
                .notes(item.getNotes())
                .firedAt(item.getFiredAt())
                .readyAt(item.getReadyAt())
                .completedAt(item.getCompletedAt())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public KdsStationBoardResponse toBoardResponse(KdsStation station, List<KdsTicket> tickets) {
        List<KdsTicket> orderedTickets = tickets.stream()
                .sorted(Comparator.comparing(KdsTicket::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return KdsStationBoardResponse.builder()
                .stationId(station.getId())
                .stationCode(station.getCode())
                .stationName(station.getName())
                .screenLabel(station.getScreenLabel())
                .deviceId(station.getDevice() == null ? null : station.getDevice().getId())
                .deviceCode(station.getDevice() == null ? null : station.getDevice().getCode())
                .deviceName(station.getDevice() == null ? null : station.getDevice().getName())
                .stationType(station.getStationType())
                .displayOrder(station.getDisplayOrder())
                .active(station.isActive())
                .activeTicketCount((int) orderedTickets.stream()
                        .filter(ticket -> ticket.getStatus() != KdsTicketStatus.COMPLETED)
                        .filter(ticket -> ticket.getStatus() != KdsTicketStatus.CANCELLED)
                        .count())
                .readyTicketCount((int) orderedTickets.stream()
                        .filter(ticket -> ticket.getStatus() == KdsTicketStatus.READY || ticket.getStatus() == KdsTicketStatus.EXPO_READY)
                        .count())
                .completedTicketCount((int) orderedTickets.stream()
                        .filter(ticket -> ticket.getStatus() == KdsTicketStatus.COMPLETED)
                        .count())
                .tickets(orderedTickets.stream().map(this::toTicketResponse).toList())
                .build();
    }

    public List<KdsStationResponse> mapStationResponses(List<KdsStation> stations) {
        return stations.stream()
                .map(this::toStationResponse)
                .toList();
    }

    public List<KdsTicketResponse> mapTicketResponses(List<KdsTicket> tickets) {
        return tickets.stream()
                .map(this::toTicketResponse)
                .toList();
    }

    private List<KdsStationRoutingResponse> mapRoutingResponses(List<KdsStationRouting> routings) {
        return routings == null ? List.of() : routings.stream()
                .sorted(Comparator.comparing(KdsStationRouting::getDisplayOrder)
                        .thenComparing(KdsStationRouting::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toRoutingResponse)
                .toList();
    }

    private List<KdsTicketItemResponse> mapTicketItemResponses(List<KdsTicketItem> items) {
        return items == null ? List.of() : items.stream()
                .sorted(Comparator.comparing(KdsTicketItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toTicketItemResponse)
                .toList();
    }
}
