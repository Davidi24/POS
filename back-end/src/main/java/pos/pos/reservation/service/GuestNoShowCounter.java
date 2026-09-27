package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.reservation.entity.GuestNoShowClear;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.GuestNoShowClearRepository;
import pos.pos.reservation.repository.ReservationRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// A guest's no-shows for the warning on their bookings: the bookings of the same guest (same customer, phone or
// email) that ended as no-show, counted only after a manager last cleared the warning. Derived every time, so
// correcting a no-show (reopen or "guest arrived") takes it out of the count on its own.
@Component
@RequiredArgsConstructor
public class GuestNoShowCounter {

    // Stands in for an empty list in "in (...)" so the query stays valid and matches nothing.
    private static final UUID NO_ID = new UUID(0, 0);
    private static final String NO_TEXT = "\u0000";

    private final ReservationRepository reservationRepository;
    private final GuestNoShowClearRepository guestNoShowClearRepository;

    // Reservation id → the guest's counted no-shows (other bookings only). Two queries for the whole list.
    public Map<UUID, Integer> counts(Collection<Reservation> reservations) {
        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, List<Reservation>> byRestaurant = reservations.stream()
                .filter(reservation -> reservation.getRestaurant() != null && reservation.getId() != null)
                .collect(Collectors.groupingBy(reservation -> reservation.getRestaurant().getId()));
        byRestaurant.forEach((restaurantId, bookings) -> {
            Keys keys = Keys.of(bookings);
            if (keys.isEmpty()) {
                return;
            }
            List<Reservation> noShows = reservationRepository.findGuestNoShows(
                    restaurantId, ReservationStatus.NO_SHOW, keys.customers(), keys.phones(), keys.emails());
            if (noShows.isEmpty()) {
                return;
            }
            List<GuestNoShowClear> clears = guestNoShowClearRepository.findForGuests(
                    restaurantId, keys.customers(), keys.phones(), keys.emails());
            for (Reservation booking : bookings) {
                int count = noShowsOf(booking, noShows, clears).size();
                if (count > 0) {
                    counts.put(booking.getId(), count);
                }
            }
        });
        return counts;
    }

    // The guest's counted no-shows (newest first), other than this booking itself.
    public List<Reservation> noShowsOf(Reservation booking) {
        if (booking.getRestaurant() == null) {
            return List.of();
        }
        Keys keys = Keys.of(List.of(booking));
        if (keys.isEmpty()) {
            return List.of();
        }
        UUID restaurantId = booking.getRestaurant().getId();
        List<Reservation> noShows = reservationRepository.findGuestNoShows(
                restaurantId, ReservationStatus.NO_SHOW, keys.customers(), keys.phones(), keys.emails());
        List<GuestNoShowClear> clears = noShows.isEmpty() ? List.of() : guestNoShowClearRepository.findForGuests(
                restaurantId, keys.customers(), keys.phones(), keys.emails());
        return noShowsOf(booking, noShows, clears);
    }

    public List<GuestNoShowClear> clearsOf(Reservation booking) {
        if (booking.getRestaurant() == null) {
            return List.of();
        }
        Keys keys = Keys.of(List.of(booking));
        return keys.isEmpty() ? List.of() : guestNoShowClearRepository.findForGuests(
                booking.getRestaurant().getId(), keys.customers(), keys.phones(), keys.emails());
    }

    private static List<Reservation> noShowsOf(Reservation booking, List<Reservation> noShows, List<GuestNoShowClear> clears) {
        OffsetDateTime clearedAt = clears.stream()
                .filter(clear -> sameGuest(booking, clear.getCustomerId(), clear.getContactPhone(), clear.getContactEmail()))
                .map(GuestNoShowClear::getClearedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        return noShows.stream()
                .filter(noShow -> !Objects.equals(noShow.getId(), booking.getId()))
                .filter(noShow -> sameGuest(booking,
                        noShow.getCustomer() == null ? null : noShow.getCustomer().getId(),
                        noShow.getContactPhone(),
                        noShow.getContactEmail()))
                .filter(noShow -> clearedAt == null || markedAt(noShow).isAfter(clearedAt))
                .toList();
    }

    private static OffsetDateTime markedAt(Reservation noShow) {
        return noShow.getNoShowAt() != null ? noShow.getNoShowAt() : noShow.getReservationStart();
    }

    private static boolean sameGuest(Reservation booking, UUID customerId, String phone, String email) {
        UUID bookingCustomer = booking.getCustomer() == null ? null : booking.getCustomer().getId();
        return (bookingCustomer != null && bookingCustomer.equals(customerId))
                || (booking.getContactPhone() != null && booking.getContactPhone().equals(phone))
                || (booking.getContactEmail() != null && booking.getContactEmail().equals(email));
    }

    private record Keys(Set<UUID> customers, Set<String> phones, Set<String> emails) {
        static Keys of(Collection<Reservation> bookings) {
            Set<UUID> customers = new HashSet<>();
            Set<String> phones = new HashSet<>();
            Set<String> emails = new HashSet<>();
            for (Reservation booking : bookings) {
                if (booking.getCustomer() != null && booking.getCustomer().getId() != null) {
                    customers.add(booking.getCustomer().getId());
                }
                if (booking.getContactPhone() != null) {
                    phones.add(booking.getContactPhone());
                }
                if (booking.getContactEmail() != null) {
                    emails.add(booking.getContactEmail());
                }
            }
            return new Keys(
                    customers.isEmpty() ? Set.of(NO_ID) : customers,
                    phones.isEmpty() ? Set.of(NO_TEXT) : phones,
                    emails.isEmpty() ? Set.of(NO_TEXT) : emails
            );
        }

        boolean isEmpty() {
            return customers.equals(Set.of(NO_ID)) && phones.equals(Set.of(NO_TEXT)) && emails.equals(Set.of(NO_TEXT));
        }
    }
}
