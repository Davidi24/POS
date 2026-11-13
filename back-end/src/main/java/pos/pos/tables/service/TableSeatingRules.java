package pos.pos.tables.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationOperationalPublisher;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.tables.dto.TableLayoutItemResponse;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.event.GuestsSeatedEvent;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * How many guests a table takes (user rules, 2026-10-31; the numbers and who is told are in Settings → Tables).
 * Merged tables need more guests than their biggest table holds: that's why they were merged. A table with 4 seats:
 * up to 6 is normal; 7 and 8 are unusual, so the chosen people are told at once; more than 8 is refused.
 */
@Component
@RequiredArgsConstructor
public class TableSeatingRules {

    public static final String ALERT_EVENT = "TABLE_SEATING_OVER_LIMIT";

    private final SettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final NotificationOperationalPublisher notifications;
    private final pos.pos.tables.specialcase.SpecialCaseActionRepository actionRepository;

    public record Limits(int min, int usual, int max) {
    }

    public Limits limits(UUID restaurantId, Collection<Integer> capacities) {
        return limits(settingsOf(restaurantId), capacities);
    }

    // Refuses a number these tables (one, or merged ones with the main table first) can't take.
    public void check(UUID restaurantId, List<RestaurantTable> group, int guests) {
        Limits limits = limits(restaurantId, group.stream().map(RestaurantTable::getCapacity).toList());
        if (guests < limits.min()) {
            throw new AuthException("Merged tables need at least " + limits.min() + " guests. Unmerge them for fewer.", HttpStatus.BAD_REQUEST);
        }
        if (guests > limits.max()) {
            String tables = group.stream().map(RestaurantTable::getTableNumber).collect(Collectors.joining("-"));
            throw new AuthException(tables + " can take at most " + limits.max() + " guests.", HttpStatus.BAD_REQUEST);
        }
    }

    private Limits limits(Settings settings, Collection<Integer> capacities) {
        int seats = capacities.stream().mapToInt(Integer::intValue).sum();
        int biggest = capacities.stream().mapToInt(Integer::intValue).max().orElse(0);
        return new Limits(
                capacities.size() > 1 ? biggest + 1 : 1,
                seats + settings.getSeatingExtraGuests(),
                seats + settings.getSeatingMostExtraGuests()
        );
    }

    // Each table's limits for the app, so it never works them out itself.
    public void fill(UUID restaurantId, List<TableLayoutItemResponse> tables) {
        Settings settings = settingsOf(restaurantId);
        Map<UUID, Integer> capacityById = tables.stream()
                .collect(Collectors.toMap(TableLayoutItemResponse::getTableId, TableLayoutItemResponse::getCapacity, (first, second) -> first));
        for (TableLayoutItemResponse table : tables) {
            List<Integer> group = new java.util.ArrayList<>(List.of(table.getCapacity()));
            if (table.getMergedTableIds() != null) {
                table.getMergedTableIds().stream().map(capacityById::get).filter(Objects::nonNull).forEach(group::add);
            }
            Limits limits = limits(settings, group);
            table.setGuestsMin(limits.min());
            table.setGuestsUsual(limits.usual());
            table.setGuestsMax(limits.max());
            table.setNextBookingClashAt(clashAt(table, settings));
            table.setNextBookingSoon(bookingSoon(table, settings));
            table.setBookingComing(bookingComing(table, settings));
            if (table.getNextBookingClashAt() != null) {
                table.setMoreTimeOptions(settings.getSpecialCaseMoreTime());
            }
            fillAutoFree(table, tables, settings);
        }
        List<UUID> bookings = tables.stream().map(TableLayoutItemResponse::getNextReservationId).filter(Objects::nonNull).toList();
        if (!bookings.isEmpty()) {
            Map<UUID, String> movedFrom = actionRepository
                    .findAllByReservationIdInAndKindAndReturnedAtIsNull(bookings, pos.pos.tables.specialcase.SpecialCaseAction.Kind.BOOKING_MOVED)
                    .stream()
                    .collect(Collectors.toMap(pos.pos.tables.specialcase.SpecialCaseAction::getReservationId, pos.pos.tables.specialcase.SpecialCaseAction::getTableNumber, (first, second) -> second));
            tables.forEach(table -> table.setNextReservationMovedFrom(movedFrom.get(table.getNextReservationId())));
        }
    }

    // When the system steps in on this table if nobody acts, counted from the last thing done on it or those merged into it.
    private void fillAutoFree(TableLayoutItemResponse table, List<TableLayoutItemResponse> tables, Settings settings) {
        boolean guestsHere = table.getStatus() == pos.pos.tables.enums.TableStatus.OCCUPIED || table.getCurrentOrderId() != null;
        if (!guestsHere || table.getMergedIntoTableId() != null) {
            return;
        }
        int limit = pos.pos.tables.specialcase.SpecialCaseLimits.minutes(settings, table.getCurrentOrderId() != null && table.getCurrentOrderTotalItems() > 0 && !table.isPaid(), table.isPaid());
        OffsetDateTime last = table.getLastActionAt();
        if (table.getMergedTableIds() != null) {
            for (TableLayoutItemResponse other : tables) {
                if (table.getMergedTableIds().contains(other.getTableId())) {
                    last = pos.pos.tables.specialcase.SpecialCaseLimits.latest(last, other.getLastActionAt());
                }
            }
        }
        if (limit <= 0 || last == null) {
            return;
        }
        table.setAutoFreeAt(last.plusMinutes(limit));
        table.setAutoFreeWarningAt(last.plusMinutes(limit).minusMinutes(settings.getSpecialCaseWarningMinutes()));
    }

    // A free table whose booking comes within Settings → Tables → "Show a coming booking" shows a yellow ring, so nobody
    // seats walk-ins there (user, 2026-11-01). It lasts until the booking's hold ends; guests at the table go half purple instead.
    private boolean bookingComing(TableLayoutItemResponse table, Settings settings) {
        if (settings.getBookingComingMinutes() <= 0 || table.getNextReservationStart() == null) {
            return false;
        }
        if (table.getStatus() != pos.pos.tables.enums.TableStatus.AVAILABLE || table.getCurrentOrderId() != null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        OffsetDateTime ends = table.getNextReservationHoldUntil() != null ? table.getNextReservationHoldUntil() : table.getNextReservationEnd();
        return table.getNextReservationStart().isBefore(now.plusMinutes(settings.getBookingComingMinutes())) && (ends == null || ends.isAfter(now));
    }

    // A booking this close is worth a question when its table is joined to others (user, 2026-11-01).
    private boolean bookingSoon(TableLayoutItemResponse table, Settings settings) {
        return settings.getJoinBookingWarningMinutes() > 0 && table.getNextReservationStart() != null
                && table.getNextReservationStart().isBefore(OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(settings.getJoinBookingWarningMinutes()));
    }

    // Guests still here as the next booking nears (user, 2026-11-01). Guests seated once the warning had begun are most
    // likely that booking's own, sat down from the table's panel, so they don't count.
    private OffsetDateTime clashAt(TableLayoutItemResponse table, Settings settings) {
        boolean guestsHere = table.getStatus() == pos.pos.tables.enums.TableStatus.OCCUPIED || table.getCurrentOrderId() != null;
        boolean expected = table.getNextReservationStatus() == pos.pos.reservation.enums.ReservationStatus.CONFIRMED
                || table.getNextReservationStatus() == pos.pos.reservation.enums.ReservationStatus.CHECKED_IN;
        if (!guestsHere || !expected || table.getNextReservationStart() == null) {
            return null;
        }
        OffsetDateTime clashAt = table.getNextReservationStart().minusMinutes(settings.getNextBookingWarningMinutes());
        if (table.getSeatedAt() != null && !table.getSeatedAt().isBefore(clashAt)) {
            return null;
        }
        // Its guests have already come, early or not, so it's now.
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        OffsetDateTime from = table.getNextReservationStatus() == pos.pos.reservation.enums.ReservationStatus.CHECKED_IN && now.isBefore(clashAt) ? now : clashAt;
        // "Give more time": it comes back when that's over.
        return pos.pos.tables.specialcase.SpecialCaseLimits.latest(from, table.getMoreTimeUntil());
    }

    @EventListener
    public void onGuestsSeated(GuestsSeatedEvent event) {
        Settings settings = settingsOf(event.restaurantId());
        if (event.guests() <= event.capacity() + settings.getSeatingExtraGuests() || event.guests() <= event.previousGuests()) {
            return;
        }
        Set<String> roles = Arrays.stream(settings.getSeatingAlertRoles().split(","))
                .map(String::trim).filter(code -> !code.isEmpty()).collect(Collectors.toSet());
        if (roles.isEmpty()) {
            return;
        }
        String seatedBy = event.actorId() == null ? null : userRepository.findActiveById(event.actorId())
                .map(user -> (user.getFirstName() + " " + user.getLastName()).trim())
                .orElse(null);
        String body = event.guests() + " guests at " + event.tables() + " (" + event.branchName() + "), which has "
                + event.capacity() + " seats." + (seatedBy == null ? "" : " Seated by " + seatedBy + ".");
        // No branch on the notification: an owner working in another branch must see it too, so the body names it.
        for (UUID recipient : userRepository.findActiveStaffIdsWithRoles(event.restaurantId(), event.branchId(), roles)) {
            if (recipient.equals(event.actorId())) {
                continue;
            }
            notifications.publishCustom(
                    NotificationTopic.TABLE,
                    NotificationMutationType.STATE_CHANGE,
                    NotificationChannel.IN_APP,
                    NotificationPriority.HIGH,
                    ALERT_EVENT,
                    event.restaurantId(),
                    null,
                    recipient,
                    "TABLE",
                    event.tableId(),
                    event.actorId(),
                    "Unusual seating at " + event.tables(),
                    body
            );
        }
    }

    private Settings settingsOf(UUID restaurantId) {
        return settingsRepository.findByRestaurant_Id(restaurantId).orElseGet(Settings::new);
    }
}
