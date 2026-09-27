package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.reservation.entity.Reservation;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// The restaurant's reservation times and limits from Admin Hub → Settings → Reservations, with the agreed defaults
// when a restaurant hasn't saved any. Read often (every booking in a list), so each restaurant's values are kept for
// a few seconds.
@Component
@RequiredArgsConstructor
public class ReservationPolicy {

    // The restaurant's day runs 06:00 to 02:00: a booking at 01:00 belongs to the evening before.
    public static final int SERVICE_DAY_START_HOUR = 6;
    private static final Duration CACHE_FOR = Duration.ofSeconds(15);

    private final SettingsRepository settingsRepository;
    private final ReservationRuleResolver reservationRuleResolver;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public Values values(Restaurant restaurant) {
        if (restaurant == null || restaurant.getId() == null) {
            return Values.from(new Settings());
        }
        Instant now = Instant.now();
        Cached cached = cache.get(restaurant.getId());
        if (cached != null && now.isBefore(cached.until())) {
            return cached.values();
        }
        Values values = Values.from(settingsRepository.findByRestaurant_Id(restaurant.getId()).orElseGet(Settings::new));
        cache.put(restaurant.getId(), new Cached(values, now.plus(CACHE_FOR)));
        return values;
    }

    // 2 h, or 2 h 15 for groups of 5 or more (both from settings).
    public Duration bookingLength(Branch branch, OffsetDateTime start, int partySize) {
        Values values = values(branch == null ? null : branch.getRestaurant());
        Duration length = Duration.ofMinutes(reservationRuleResolver.durationMinutes(branch, start));
        return partySize >= values.largeGroupFrom() ? length.plusMinutes(values.largeGroupExtraMinutes()) : length;
    }

    // When the table stops waiting: what staff set, or the booking time plus the hold.
    public OffsetDateTime holdUntil(Reservation reservation) {
        if (reservation.getHoldUntil() != null) {
            return reservation.getHoldUntil();
        }
        if (reservation.getReservationStart() == null) {
            return null;
        }
        return reservation.getReservationStart().plusMinutes(values(reservation.getRestaurant()).holdMinutes());
    }

    public LocalDate serviceDate(OffsetDateTime moment, ZoneId zone) {
        return moment.atZoneSameInstant(zone).minusHours(SERVICE_DAY_START_HOUR).toLocalDate();
    }

    public record Values(
            int largeGroupFrom,
            int largeGroupExtraMinutes,
            int approvalGroupSize,
            int holdMinutes,
            int holdWarningMinutes,
            int lateAfterMinutes,
            int checkInOpensMinutes,
            LocalTime confirmReminderTime,
            int sameDayConfirmMinutes,
            int attendanceCallMinutes,
            int guestReminderHours,
            int reopenWindowMinutes,
            int undoSeatMinutes,
            int runningLateMaxMinutes,
            int noShowWarningFrom,
            int depositFromGuests
    ) {
        static Values from(Settings settings) {
            return new Values(
                    settings.getLargeGroupFrom(),
                    settings.getLargeGroupExtraMinutes(),
                    settings.getApprovalGroupSize(),
                    settings.getHoldMinutes(),
                    settings.getHoldWarningMinutes(),
                    settings.getLateAfterMinutes(),
                    settings.getCheckInOpensMinutes(),
                    settings.getConfirmReminderTime(),
                    settings.getSameDayConfirmMinutes(),
                    settings.getAttendanceCallMinutes(),
                    settings.getGuestReminderHours(),
                    settings.getReopenWindowMinutes(),
                    settings.getUndoSeatMinutes(),
                    settings.getRunningLateMaxMinutes(),
                    settings.getNoShowWarningFrom(),
                    settings.getDepositFromGuests()
            );
        }
    }

    private record Cached(Values values, Instant until) {
    }
}
