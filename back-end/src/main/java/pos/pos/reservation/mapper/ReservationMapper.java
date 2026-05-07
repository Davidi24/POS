package pos.pos.reservation.mapper;

import org.springframework.stereotype.Component;
import pos.pos.customer.entity.Customer;
import pos.pos.reservation.dto.PublicReservationResponse;
import pos.pos.reservation.dto.PublicTableLookupResponse;
import pos.pos.reservation.dto.ReservationAuditResponse;
import pos.pos.reservation.dto.ReservationDepositResponse;
import pos.pos.reservation.dto.ReservationNoteResponse;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationStatusHistoryResponse;
import pos.pos.reservation.dto.ReservationTableAssignmentResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationNote;
import pos.pos.reservation.entity.ReservationStatusHistory;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.tables.entity.RestaurantTable;

import java.util.List;

@Component
public class ReservationMapper {

    public ReservationResponse toResponse(Reservation reservation, List<ReservationTableAssignment> assignments) {
        if (reservation == null) {
            return null;
        }

        return ReservationResponse.builder()
                .id(reservation.getId())
                .restaurantId(reservation.getRestaurant() == null ? null : reservation.getRestaurant().getId())
                .branchId(reservation.getBranch() == null ? null : reservation.getBranch().getId())
                .customerId(reservation.getCustomer() == null ? null : reservation.getCustomer().getId())
                .customerCode(reservation.getCustomer() == null ? null : reservation.getCustomer().getCode())
                .customerName(customerName(reservation.getCustomer()))
                .reservationCode(reservation.getReservationCode())
                .source(reservation.getSource())
                .status(reservation.getStatus())
                .partySize(reservation.getPartySize())
                .reservationStart(reservation.getReservationStart())
                .reservationEnd(reservation.getReservationEnd())
                .contactName(reservation.getContactName())
                .contactPhone(reservation.getContactPhone())
                .contactEmail(reservation.getContactEmail())
                .seatingPreference(reservation.getSeatingPreference())
                .specialRequests(reservation.getSpecialRequests())
                .internalNotes(reservation.getInternalNotes())
                .depositRequired(reservation.isDepositRequired())
                .depositAmount(reservation.getDepositAmount())
                .depositStatus(reservation.getDepositStatus())
                .confirmedAt(reservation.getConfirmedAt())
                .cancelledAt(reservation.getCancelledAt())
                .cancellationReason(reservation.getCancellationReason())
                .checkedInAt(reservation.getCheckedInAt())
                .seatedAt(reservation.getSeatedAt())
                .completedAt(reservation.getCompletedAt())
                .noShowAt(reservation.getNoShowAt())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .tableAssignments(assignments.stream().map(this::toTableAssignmentResponse).toList())
                .build();
    }

    public ReservationTableAssignmentResponse toTableAssignmentResponse(ReservationTableAssignment assignment) {
        if (assignment == null) {
            return null;
        }

        RestaurantTable table = assignment.getRestaurantTable();
        return ReservationTableAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                .tableId(table == null ? null : table.getId())
                .tableNumber(table == null ? null : table.getTableNumber())
                .tableName(table == null ? null : table.getName())
                .floor(table == null ? null : table.getFloor())
                .capacity(table == null ? null : table.getCapacity())
                .primary(assignment.isPrimaryAssignment())
                .assignedAt(assignment.getAssignedAt())
                .assignedBy(assignment.getAssignedBy())
                .build();
    }

    public ReservationStatusHistoryResponse toStatusHistoryResponse(ReservationStatusHistory history) {
        if (history == null) {
            return null;
        }

        return ReservationStatusHistoryResponse.builder()
                .id(history.getId())
                .oldStatus(history.getOldStatus())
                .newStatus(history.getNewStatus())
                .reason(history.getReason())
                .changedBy(history.getChangedBy())
                .changedAt(history.getChangedAt())
                .build();
    }

    public ReservationNoteResponse toNoteResponse(ReservationNote note) {
        if (note == null) {
            return null;
        }

        return ReservationNoteResponse.builder()
                .id(note.getId())
                .note(note.getNote())
                .createdBy(note.getCreatedBy())
                .updatedBy(note.getUpdatedBy())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    public ReservationDepositResponse toDepositResponse(Reservation reservation) {
        if (reservation == null) {
            return null;
        }

        return ReservationDepositResponse.builder()
                .reservationId(reservation.getId())
                .depositRequired(reservation.isDepositRequired())
                .depositAmount(reservation.getDepositAmount())
                .depositStatus(reservation.getDepositStatus())
                .build();
    }

    public ReservationAuditResponse toAuditResponse(
            Reservation reservation,
            List<ReservationStatusHistoryResponse> statusHistory,
            List<ReservationNoteResponse> notes,
            List<ReservationTableAssignmentResponse> tableAssignments
    ) {
        if (reservation == null) {
            return null;
        }

        return ReservationAuditResponse.builder()
                .reservationId(reservation.getId())
                .createdAt(reservation.getCreatedAt())
                .createdBy(reservation.getCreatedBy())
                .updatedAt(reservation.getUpdatedAt())
                .updatedBy(reservation.getUpdatedBy())
                .statusHistory(statusHistory)
                .notes(notes)
                .tableAssignments(tableAssignments)
                .build();
    }

    public PublicReservationResponse toPublicResponse(Reservation reservation, List<ReservationTableAssignment> assignments) {
        if (reservation == null) {
            return null;
        }

        return PublicReservationResponse.builder()
                .id(reservation.getId())
                .reservationCode(reservation.getReservationCode())
                .restaurantId(reservation.getRestaurant() == null ? null : reservation.getRestaurant().getId())
                .restaurantName(reservation.getRestaurant() == null ? null : reservation.getRestaurant().getName())
                .restaurantSlug(reservation.getRestaurant() == null ? null : reservation.getRestaurant().getSlug())
                .branchId(reservation.getBranch() == null ? null : reservation.getBranch().getId())
                .branchName(reservation.getBranch() == null ? null : reservation.getBranch().getName())
                .branchCode(reservation.getBranch() == null ? null : reservation.getBranch().getCode())
                .status(reservation.getStatus())
                .partySize(reservation.getPartySize())
                .reservationStart(reservation.getReservationStart())
                .reservationEnd(reservation.getReservationEnd())
                .contactName(reservation.getContactName())
                .contactPhone(reservation.getContactPhone())
                .contactEmail(reservation.getContactEmail())
                .seatingPreference(reservation.getSeatingPreference())
                .specialRequests(reservation.getSpecialRequests())
                .depositRequired(reservation.isDepositRequired())
                .depositAmount(reservation.getDepositAmount())
                .depositStatus(reservation.getDepositStatus())
                .tableAssignments(assignments.stream().map(this::toTableAssignmentResponse).toList())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }

    public PublicTableLookupResponse toPublicTableLookupResponse(
            RestaurantTable table,
            int effectiveCapacity
    ) {
        if (table == null) {
            return null;
        }

        return PublicTableLookupResponse.builder()
                .restaurantId(table.getRestaurant() == null ? null : table.getRestaurant().getId())
                .restaurantName(table.getRestaurant() == null ? null : table.getRestaurant().getName())
                .restaurantSlug(table.getRestaurant() == null ? null : table.getRestaurant().getSlug())
                .branchId(table.getBranch() == null ? null : table.getBranch().getId())
                .branchName(table.getBranch() == null ? null : table.getBranch().getName())
                .branchCode(table.getBranch() == null ? null : table.getBranch().getCode())
                .tableId(table.getId())
                .tableNumber(table.getTableNumber())
                .tableName(table.getName())
                .floor(table.getFloor())
                .capacity(table.getCapacity())
                .effectiveCapacity(effectiveCapacity)
                .status(table.getStatus())
                .active(table.isActive())
                .build();
    }

    private String customerName(Customer customer) {
        if (customer == null) {
            return null;
        }
        if (customer.getFirstName() == null && customer.getLastName() == null) {
            return null;
        }
        if (customer.getFirstName() == null) {
            return customer.getLastName();
        }
        if (customer.getLastName() == null) {
            return customer.getFirstName();
        }
        return customer.getFirstName() + " " + customer.getLastName();
    }
}
