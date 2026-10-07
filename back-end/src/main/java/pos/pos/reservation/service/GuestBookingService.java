package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.reservation.dto.GuestBookingView;
import pos.pos.reservation.dto.GuestExtraChoice;
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.dto.OnlineBookingRequest;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.enums.AttendanceConfirmedVia;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationSource;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.RestaurantEventRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.settings.entity.SettingsReservationRule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Bookings guests make and manage themselves (the website and the links in their emails), under the agreed rules:
//  - a table free for the whole visit → confirmed at once; no table, or a big group → a request staff answer;
//  - "Still coming?": the guest confirms (✓ Attendance confirmed) or cancels for free (paid parts: refund minus the
//    card fee before their deadline);
//  - "I'm running late": 10/15/20 min, once, up to the Admin Hub maximum, only the hold moves, never past the end;
//  - paid extras and deposits are paid online (a test payment page until a card provider is connected).
@Service
@RequiredArgsConstructor
public class GuestBookingService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<Integer> LATE_STEPS = List.of(10, 15, 20);

    private final ReservationSupport reservationSupport;
    private final ReservationRepository reservationRepository;
    private final ReservationPolicy reservationPolicy;
    private final ReservationRuleResolver reservationRuleResolver;
    private final ReservationAvailabilitySupport reservationAvailabilitySupport;
    private final ReservationTableAssignmentService reservationTableAssignmentService;
    private final ReservationOccasionService reservationOccasionService;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ReservationNotifications reservationNotifications;
    private final ReservationEventRepository reservationEventRepository;
    private final RestaurantEventRepository restaurantEventRepository;
    private final BookingMoneyService bookingMoneyService;
    private final ReservationMailService reservationMailService;
    private final MenuItemRepository menuItemRepository;
    private final Environment environment;

    // Until a card provider is set up, local payment pages can take a test payment (no real card).
    @Value("${app.payments.provider:disabled}")
    private String paymentProvider;

    @Transactional
    public GuestBookingView book(String restaurantSlug, String branchCode, OnlineBookingRequest request) {
        Branch branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = request.getReservationStart().withOffsetSameInstant(ZoneOffset.UTC);
        int guests = request.getPartySize();
        SettingsReservationRule rule = reservationRuleResolver.activeRule(branch, start).orElse(null);
        if (rule != null && !rule.isAllowOnlineReservations()) {
            throw new AuthException("Online booking isn't available. Please call the restaurant", HttpStatus.FORBIDDEN);
        }
        if (!start.isAfter(now.plusMinutes(15))) {
            throw bad("Pick a time at least 15 minutes from now");
        }
        if (rule != null) {
            if (guests < rule.getMinPartySize() || guests > rule.getMaxPartySize()) {
                throw bad("Online we take bookings for " + rule.getMinPartySize() + " to " + rule.getMaxPartySize() + " guests. Please call us for other sizes");
            }
            ZoneId zone = reservationSupport.restaurantZone(branch.getRestaurant());
            if (rule.getAdvanceBookingDays() > 0 && start.atZoneSameInstant(zone).toLocalDate().isAfter(LocalDate.now(zone).plusDays(rule.getAdvanceBookingDays()))) {
                throw bad("You can book up to " + rule.getAdvanceBookingDays() + " days ahead");
            }
        }
        OffsetDateTime end = start.plus(reservationPolicy.bookingLength(branch, start, guests));

        Reservation reservation = new Reservation();
        reservation.setRestaurant(branch.getRestaurant());
        reservation.setBranch(branch);
        reservation.setSource(ReservationSource.WEB);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setReservationCode(reservationSupport.newReservationCode(branch.getRestaurant().getId()));
        reservation.setPartySize(guests);
        reservation.setReservationStart(start);
        reservation.setReservationEnd(end);
        reservation.setContactName(blankToNull(request.getContactName()));
        reservation.setContactPhone(blankToNull(request.getContactPhone()));
        reservation.setContactEmail(blankToNull(request.getContactEmail()));
        reservation.setSpecialRequests(blankToNull(request.getSpecialRequests()));
        reservationOccasionService.apply(reservation, request.getOccasionCode(), request.getOccasionOptions(), request.getOccasionNote());

        // Big groups and restaurants that answer every booking themselves get a request; otherwise a free table
        // for the whole visit confirms it at once (and holds that table).
        boolean needsApproval = guests >= reservationPolicy.values(branch.getRestaurant()).approvalGroupSize()
                || (rule != null && !rule.isAutoConfirmReservations());
        String reason;
        if (!needsApproval) {
            List<ReservationAvailabilityOptionResponse> options = reservationAvailabilitySupport.availabilityOptionsForBranch(branch, start, end, guests, 1);
            if (!options.isEmpty()) {
                reservationTableAssignmentService.replaceReservationTables(reservation, options.getFirst().getTableIds(), options.getFirst().getPrimaryTableId(), null);
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservation.setConfirmedAt(now);
                reason = "Booked online, table free";
            } else {
                reason = "Request from the website: no table free for the whole booking";
            }
        } else {
            reason = "Request from the website: needs approval";
        }
        reservationSupport.addStatusHistory(reservation, null, reservation.getStatus(), reason, null);
        Reservation saved = reservationSupport.saveReservation(reservation);

        if (request.getExtras() != null) {
            for (OnlineBookingRequest.Extra extra : request.getExtras()) {
                bookingMoneyService.addExtra(saved, extra.getMenuItemId(), extra.getQuantity(), now);
            }
        }
        bookingMoneyService.createDepositIfRequired(saved);
        reservationNotifications.created(saved, null, true);

        List<MoneyLineResponse> due = bookingMoneyService.lines(saved, now).stream().filter(line -> "PENDING".equals(line.getStatus())).toList();
        if (saved.getStatus() == ReservationStatus.CONFIRMED) {
            boolean askNow = !now.isBefore(start.minusHours(reservationPolicy.values(branch.getRestaurant()).guestReminderHours()));
            reservationMailService.confirmed(saved, false, askNow, due);
        } else {
            reservationMailService.requestReceived(saved, due);
        }
        return view(saved, now);
    }

    // Extras guests can add to a booking for an occasion (none picked: extras offered for every occasion).
    @Transactional(readOnly = true)
    public List<GuestExtraChoice> extras(String restaurantSlug, String branchCode, String occasionCode) {
        Branch branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
        String currency = branch.getRestaurant().getCurrency() == null || branch.getRestaurant().getCurrency().isBlank()
                ? "EUR" : branch.getRestaurant().getCurrency().toUpperCase(java.util.Locale.ROOT);
        return menuItemRepository.findSpecialMenuExtras(branch.getRestaurant().getId()).stream()
                .filter(item -> offeredFor(item, occasionCode))
                .map(item -> GuestExtraChoice.builder()
                        .menuItemId(item.getId())
                        .name(item.getName())
                        .description(item.getDescription())
                        .price(item.getBasePrice())
                        .currency(currency)
                        .orderBeforeHours(item.getOrderBeforeHours())
                        .occasionCodes(codes(item))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public GuestBookingView view(String token) {
        return view(requireByToken(token), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public GuestBookingView confirmAttendance(String token) {
        Reservation reservation = requireByToken(token);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw bad("This booking can't be confirmed anymore");
        }
        reservationLifecycleService.confirmAttendance(reservation, AttendanceConfirmedVia.GUEST, null, ReservationActor.system(null));
        return view(reservationSupport.saveReservation(reservation), OffsetDateTime.now(ZoneOffset.UTC));
    }

    // "We'll be 15 minutes late": once, within the Admin Hub maximum, never past the end of the booking.
    @Transactional
    public GuestBookingView runningLate(String token, int minutes) {
        Reservation reservation = requireByToken(token);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!lateOptions(reservation, now).contains(minutes)) {
            throw bad(lateProblem(reservation, now, minutes));
        }
        OffsetDateTime current = reservationPolicy.holdUntil(reservation);
        OffsetDateTime until = current.plusMinutes(minutes);
        reservation.setHoldUntil(until);
        String clock = CLOCK.format(until.atZoneSameInstant(reservationSupport.restaurantZone(reservation.getRestaurant())));
        record(reservation, ReservationEventType.RUNNING_LATE, "Guest running " + minutes + " min late: table held until " + clock, null);
        reservationNotifications.runningLate(reservation, minutes, clock);
        return view(reservationSupport.saveReservation(reservation), now);
    }

    // What cancelling now would do with each paid part.
    @Transactional(readOnly = true)
    public List<MoneyLineResponse> cancelPreview(String token) {
        return bookingMoneyService.lines(requireByToken(token), OffsetDateTime.now(ZoneOffset.UTC));
    }

    // Cancelling is always free; paid parts follow their own deadlines.
    @Transactional
    public GuestBookingView cancel(String token) {
        Reservation reservation = requireByToken(token);
        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw bad("This booking can't be cancelled online anymore. Please call the restaurant");
        }
        reservationLifecycleService.cancel(reservation, "Cancelled by the guest", ReservationActor.system(null));
        Reservation saved = reservationSupport.saveReservation(reservation);
        reservationNotifications.cancelled(saved, null);
        return view(saved, OffsetDateTime.now(ZoneOffset.UTC));
    }

    // Test mode: marks what's due as paid. A real card provider replaces this with its own checkout.
    @Transactional
    public GuestBookingView payInTestMode(String token) {
        if (!testPayments()) {
            throw new AuthException("Payments go through the card provider", HttpStatus.BAD_REQUEST);
        }
        Reservation reservation = requireByToken(token);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var pending = bookingMoneyService.pending(reservation);
        if (pending.isEmpty()) {
            throw bad("Nothing is left to pay");
        }
        pending.forEach(payment -> bookingMoneyService.markPaid(payment, "TEST", "test-" + UUID.randomUUID(), now));
        return view(reservation, now);
    }

    public boolean testPayments() {
        // Never allow a property override to make production report a simulated payment as real.
        return !environment.acceptsProfiles(Profiles.of("prod")) && "test".equalsIgnoreCase(paymentProvider);
    }

    // ---- View ----

    private GuestBookingView view(Reservation reservation, OffsetDateTime now) {
        List<MoneyLineResponse> money = bookingMoneyService.lines(reservation, now);
        BigDecimal due = money.stream().filter(line -> "PENDING".equals(line.getStatus())).map(MoneyLineResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean waiting = reservation.getStatus() == ReservationStatus.PENDING || reservation.getStatus() == ReservationStatus.CONFIRMED;
        ZoneId zone = reservationSupport.restaurantZone(reservation.getRestaurant());
        var event = restaurantEventRepository.findActiveBetween(reservation.getRestaurant().getId(),
                        reservationPolicy.serviceDate(reservation.getReservationStart(), zone), reservationPolicy.serviceDate(reservation.getReservationStart(), zone))
                .stream().findFirst().orElse(null);
        return GuestBookingView.builder()
                .token(reservation.getGuestToken())
                .reservationCode(reservation.getReservationCode())
                .restaurantName(reservation.getRestaurant().getName())
                .timezone(zone.getId())
                .status(reservation.getStatus())
                .statusText(statusText(reservation))
                .partySize(reservation.getPartySize())
                .reservationStart(reservation.getReservationStart())
                .reservationEnd(reservation.getReservationEnd())
                .contactName(reservation.getContactName())
                .occasion(reservation.getOccasionName() == null ? null
                        : (reservation.getOccasionIcon() == null ? "" : reservation.getOccasionIcon() + " ") + reservation.getOccasionName())
                .eventName(event == null ? null : event.getName())
                .eventIcon(event == null ? null : event.getIcon())
                .attendanceConfirmed(reservation.getAttendanceConfirmedAt() != null)
                .canConfirm(reservation.getStatus() == ReservationStatus.CONFIRMED && reservation.getAttendanceConfirmedAt() == null
                        && now.isBefore(reservation.getReservationEnd()))
                .canCancel(waiting && now.isBefore(reservation.getReservationEnd()))
                .lateOptions(lateOptions(reservation, now))
                .holdUntil(waiting ? reservationPolicy.holdUntil(reservation) : null)
                .money(money)
                .amountDue(due)
                .currency(reservation.getRestaurant().getCurrency())
                .payUrl(due.signum() > 0 && waiting ? reservationMailService.bookingPage(reservation) + "#pay" : null)
                .testPayments(testPayments())
                .build();
    }

    private static String statusText(Reservation reservation) {
        return switch (reservation.getStatus()) {
            case PENDING -> "Request sent: we'll let you know soon";
            case CONFIRMED -> reservation.getAttendanceConfirmedAt() != null ? "Confirmed ✓ · you confirmed you're coming" : "Confirmed ✓";
            case CHECKED_IN, SEATED -> "You're here. Enjoy!";
            case COMPLETED -> "Thank you for your visit";
            case CANCELLED -> ReservationLifecycleService.isDeclined(reservation) ? "Sorry, we couldn't take this booking" : "Cancelled";
            case NO_SHOW -> "Missed";
            case EXPIRED -> "Sorry, we couldn't confirm this request";
        };
    }

    // Which "running late" steps are still possible: confirmed, before the hold ends, once, within the maximum.
    List<Integer> lateOptions(Reservation reservation, OffsetDateTime now) {
        if (reservation.getStatus() != ReservationStatus.CONFIRMED || reservationEventRepository.existsByReservation_IdAndType(reservation.getId(), ReservationEventType.RUNNING_LATE)) {
            return List.of();
        }
        OffsetDateTime hold = reservationPolicy.holdUntil(reservation);
        if (hold == null || !now.isBefore(hold) || now.isBefore(reservation.getReservationStart().minusHours(6))) {
            return List.of();
        }
        int max = reservationPolicy.values(reservation.getRestaurant()).runningLateMaxMinutes();
        return LATE_STEPS.stream()
                .filter(step -> step <= max)
                .filter(step -> !hold.plusMinutes(step).isAfter(reservation.getReservationEnd()))
                .toList();
    }

    private String lateProblem(Reservation reservation, OffsetDateTime now, int minutes) {
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            return "This booking isn't waiting for you anymore";
        }
        if (reservationEventRepository.existsByReservation_IdAndType(reservation.getId(), ReservationEventType.RUNNING_LATE)) {
            return "You already told us you're running late";
        }
        OffsetDateTime hold = reservationPolicy.holdUntil(reservation);
        String clock = CLOCK.format(hold.atZoneSameInstant(reservationSupport.restaurantZone(reservation.getRestaurant())));
        return "We can hold your table until " + clock;
    }

    private Reservation requireByToken(String token) {
        if (token == null || token.length() < 32) {
            throw new AuthException("Booking not found", HttpStatus.NOT_FOUND);
        }
        Reservation reservation = reservationRepository.findByGuestToken(token)
                .orElseThrow(() -> new AuthException("Booking not found", HttpStatus.NOT_FOUND));
        reservationSupport.assertPublicAvailability(reservation.getRestaurant(), reservation.getBranch());
        return reservation;
    }

    private void record(Reservation reservation, ReservationEventType type, String detail, String reason) {
        ReservationEvent event = new ReservationEvent();
        event.setReservation(reservation);
        event.setType(type);
        event.setDetail(detail);
        event.setReason(reason);
        reservationEventRepository.save(event);
    }

    private static boolean offeredFor(MenuItem item, String occasionCode) {
        List<String> codes = codes(item);
        return codes.isEmpty() || (occasionCode != null && codes.contains(occasionCode.trim().toUpperCase()));
    }

    private static List<String> codes(MenuItem item) {
        return item.getOccasionCodes() == null || item.getOccasionCodes().isBlank() ? List.of()
                : new ArrayList<>(Arrays.stream(item.getOccasionCodes().split(",")).map(String::trim).filter(code -> !code.isEmpty()).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AuthException bad(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }
}
