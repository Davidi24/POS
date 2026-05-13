package pos.pos.reservation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.customer.entity.Customer;
import pos.pos.reservation.enums.ReservationDepositStatus;
import pos.pos.reservation.enums.ReservationSource;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "reservations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reservations_restaurant_code", columnNames = {"restaurant_id", "reservation_code"})
        },
        indexes = {
                @Index(name = "idx_reservations_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_reservations_branch_id", columnList = "branch_id"),
                @Index(name = "idx_reservations_customer_id", columnList = "customer_id"),
                @Index(name = "idx_reservations_status", columnList = "status"),
                @Index(name = "idx_reservations_reservation_start", columnList = "reservation_start"),
                @Index(name = "idx_reservations_created_by", columnList = "created_by"),
                @Index(name = "idx_reservations_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(reservation_code)) > 0
        AND party_size > 0
        AND reservation_end > reservation_start
        AND (deposit_amount IS NULL OR deposit_amount >= 0)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Reservation extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservations_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservations_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "customer_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservations_customer")
    )
    private Customer customer;

    // a unique code for each reservation
    @Column(name = "reservation_code", nullable = false, length = 50)
    private String reservationCode;

    // base in when it came from it can handle it different
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private ReservationSource source = ReservationSource.INTERNAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "party_size", nullable = false)
    private int partySize = 2;

    @Column(name = "reservation_start", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime reservationStart;

    @Column(name = "reservation_end", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime reservationEnd;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "seating_preference", length = 50)
    private String seatingPreference;

    @Column(name = "special_requests", columnDefinition = "text")
    private String specialRequests;

    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

    @Column(name = "deposit_required", nullable = false)
    private boolean depositRequired = false;

    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_status", nullable = false, length = 30)
    private ReservationDepositStatus depositStatus = ReservationDepositStatus.NOT_REQUIRED;

    @Column(name = "confirmed_at", columnDefinition = "timestamptz")
    private OffsetDateTime confirmedAt;

    @Column(name = "cancelled_at", columnDefinition = "timestamptz")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "checked_in_at", columnDefinition = "timestamptz")
    private OffsetDateTime checkedInAt;

    @Column(name = "seated_at", columnDefinition = "timestamptz")
    private OffsetDateTime seatedAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private OffsetDateTime completedAt;

    @Column(name = "no_show_at", columnDefinition = "timestamptz")
    private OffsetDateTime noShowAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt DESC")
    private List<ReservationStatusHistory> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("primaryAssignment DESC, assignedAt ASC, createdAt ASC")
    private List<ReservationTableAssignment> tableAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ReservationNote> notes = new ArrayList<>();

    public void addStatusHistory(ReservationStatusHistory entry) {
        if (entry == null) {
            return;
        }

        statusHistory.add(entry);
        entry.setReservation(this);
    }

    public void removeStatusHistory(ReservationStatusHistory entry) {
        if (entry == null) {
            return;
        }

        statusHistory.remove(entry);
        entry.setReservation(null);
    }

    public void addTableAssignment(ReservationTableAssignment assignment) {
        if (assignment == null) {
            return;
        }

        tableAssignments.add(assignment);
        assignment.setReservation(this);
    }

    public void removeTableAssignment(ReservationTableAssignment assignment) {
        if (assignment == null) {
            return;
        }

        tableAssignments.remove(assignment);
        assignment.setReservation(null);
    }

    public void addNote(ReservationNote note) {
        if (note == null) {
            return;
        }

        notes.add(note);
        note.setReservation(this);
    }

    public void removeNote(ReservationNote note) {
        if (note == null) {
            return;
        }

        notes.remove(note);
        note.setReservation(null);
    }

    @Override
    protected void normalizeFields() {
        reservationCode = NormalizationUtils.normalizeCode(reservationCode);
        contactName = NormalizationUtils.normalize(contactName);
        contactPhone = NormalizationUtils.normalizePhone(contactPhone);
        contactEmail = NormalizationUtils.normalizeLower(contactEmail);
        seatingPreference = NormalizationUtils.normalize(seatingPreference);
        specialRequests = NormalizationUtils.normalize(specialRequests);
        internalNotes = NormalizationUtils.normalize(internalNotes);
        cancellationReason = NormalizationUtils.normalize(cancellationReason);
    }

    @Override
    protected void validateState() {
        if (partySize <= 0) {
            throw new IllegalStateException("partySize must be greater than zero");
        }

        if (reservationStart == null || reservationEnd == null) {
            throw new IllegalStateException("reservationStart and reservationEnd are required");
        }

        if (!reservationEnd.isAfter(reservationStart)) {
            throw new IllegalStateException("reservationEnd must be after reservationStart");
        }

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("reservation branch must belong to the same restaurant");
            }
        }

        if (customer != null && restaurant != null && customer.getRestaurant() != null) {
            if (!Objects.equals(customer.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("reservation customer must belong to the same restaurant");
            }
        }

        if (customer == null && contactName == null && contactPhone == null && contactEmail == null) {
            throw new IllegalStateException("reservation requires a customer or snapshot contact details");
        }

        if (depositAmount != null && depositAmount.signum() < 0) {
            throw new IllegalStateException("depositAmount must not be negative");
        }

        if (depositRequired) {
            if (depositAmount == null || depositAmount.signum() <= 0) {
                throw new IllegalStateException("depositAmount is required when depositRequired is true");
            }

            if (depositStatus == ReservationDepositStatus.NOT_REQUIRED) {
                throw new IllegalStateException("depositStatus must not be NOT_REQUIRED when deposit is required");
            }
        } else {
            if (depositAmount != null) {
                throw new IllegalStateException("depositAmount must be null when depositRequired is false");
            }

            if (depositStatus != ReservationDepositStatus.NOT_REQUIRED) {
                throw new IllegalStateException("depositStatus must be NOT_REQUIRED when depositRequired is false");
            }
        }

        if (cancelledAt != null && status != ReservationStatus.CANCELLED) {
            throw new IllegalStateException("cancelledAt requires CANCELLED status");
        }

        if (noShowAt != null && status != ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("noShowAt requires NO_SHOW status");
        }

        if (completedAt != null && status != ReservationStatus.COMPLETED) {
            throw new IllegalStateException("completedAt requires COMPLETED status");
        }

        if (checkedInAt != null && !EnumSet.of(
                ReservationStatus.CHECKED_IN,
                ReservationStatus.SEATED,
                ReservationStatus.COMPLETED
        ).contains(status)) {
            throw new IllegalStateException("checkedInAt requires CHECKED_IN, SEATED, or COMPLETED status");
        }

        if (seatedAt != null && !EnumSet.of(ReservationStatus.SEATED, ReservationStatus.COMPLETED).contains(status)) {
            throw new IllegalStateException("seatedAt requires SEATED or COMPLETED status");
        }

        if (confirmedAt != null && status == ReservationStatus.PENDING) {
            throw new IllegalStateException("confirmedAt cannot be set while reservation is still pending");
        }
    }
}
