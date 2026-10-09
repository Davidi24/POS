package pos.pos.reservation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.GuestBookingView;
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.dto.OnlineBookingRequest;
import pos.pos.reservation.dto.ReservationOccasionDto;
import pos.pos.reservation.service.GuestBookingService;
import pos.pos.reservation.service.ReservationOccasionService;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Branch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Simple pages guests use until the website is built: book a table, and their booking page from the email links
// (confirm, running late, pay, cancel with what's refunded). Plain HTML, works on any phone.
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class GuestBookingPageController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final GuestBookingService guestBookingService;
    private final ReservationOccasionService reservationOccasionService;
    private final ReservationSupport reservationSupport;

    // ---- Booking form ----

    @GetMapping(value = "/book/{restaurantSlug}/{branchCode}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> bookingForm(@PathVariable String restaurantSlug, @PathVariable String branchCode) {
        return page(HttpStatus.OK, bookingFormHtml(restaurantSlug, branchCode, null, FormValues.EMPTY));
    }

    @PostMapping(value = "/book/{restaurantSlug}/{branchCode}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> book(
            @PathVariable String restaurantSlug,
            @PathVariable String branchCode,
            @RequestParam String date,
            @RequestParam String time,
            @RequestParam int guests,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String occasion,
            @RequestParam(required = false) String note
    ) {
        FormValues values = new FormValues(date, time, String.valueOf(guests), name, email, phone, occasion, note);
        try {
            Branch branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
            ZoneId zone = reservationSupport.restaurantZone(branch.getRestaurant());
            ZonedDateTime start = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone);
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Write your name");
            if (email == null || !email.contains("@")) throw new IllegalArgumentException("Write a valid email, so we can confirm the booking");
            if (guests < 1) throw new IllegalArgumentException("Say how many guests");
            GuestBookingView booked = guestBookingService.book(restaurantSlug, branchCode, OnlineBookingRequest.builder()
                    .partySize(guests)
                    .reservationStart(start.toOffsetDateTime())
                    .contactName(name.trim())
                    .contactEmail(email.trim())
                    .contactPhone(phone)
                    .occasionCode(occasion == null || occasion.isBlank() ? null : occasion)
                    .specialRequests(note)
                    .build());
            return redirect("/public/bookings/" + booked.getToken() + "?done=booked");
        } catch (AuthException error) {
            if (error.getStatus() == HttpStatus.NOT_FOUND) {
                return page(HttpStatus.NOT_FOUND, message("Not found", "This restaurant doesn't take bookings online."));
            }
            return page(HttpStatus.BAD_REQUEST, bookingFormHtml(restaurantSlug, branchCode, error.getMessage(), values));
        } catch (RuntimeException error) {
            String problem = error instanceof IllegalArgumentException ? error.getMessage() : "Check the day and time";
            return page(HttpStatus.BAD_REQUEST, bookingFormHtml(restaurantSlug, branchCode, problem, values));
        }
    }

    // ---- The guest's booking ----

    @GetMapping(value = "/bookings/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> booking(@PathVariable String token, @RequestParam(required = false) String done) {
        try {
            return page(HttpStatus.OK, bookingHtml(guestBookingService.view(token), doneMessage(done), null));
        } catch (AuthException error) {
            return page(HttpStatus.NOT_FOUND, message("Booking not found", "Check the link in your email."));
        }
    }

    @PostMapping(value = "/bookings/{token}/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirm(@PathVariable String token) {
        return act(token, "confirmed", () -> guestBookingService.confirmAttendance(token));
    }

    @PostMapping(value = "/bookings/{token}/late", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> late(@PathVariable String token, @RequestParam int minutes) {
        return act(token, "late", () -> guestBookingService.runningLate(token, minutes));
    }

    @PostMapping(value = "/bookings/{token}/pay", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pay(@PathVariable String token) {
        return act(token, "paid", () -> guestBookingService.payInTestMode(token));
    }

    @GetMapping(value = "/bookings/{token}/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> cancelPage(@PathVariable String token) {
        try {
            GuestBookingView booking = guestBookingService.view(token);
            List<MoneyLineResponse> lines = guestBookingService.cancelPreview(token).stream()
                    .filter(line -> "PAID".equals(line.getStatus())).toList();
            StringBuilder body = new StringBuilder()
                    .append("<h1>Cancel your booking?</h1>")
                    .append("<p>").append(esc(booking.getRestaurantName())).append(" · ").append(esc(when(booking))).append("</p>");
            if (lines.isEmpty()) {
                body.append("<p>Cancelling is free.</p>");
            } else {
                body.append("<p>What happens to what you paid:</p><ul>");
                lines.forEach(line -> body.append("<li>").append(esc(line.getExplanation())).append("</li>"));
                body.append("</ul>");
            }
            body.append("<form method='post' action='/public/bookings/").append(esc(token)).append("/cancel'>")
                    .append("<button class='danger'>Cancel booking</button></form>")
                    .append("<p><a href='/public/bookings/").append(esc(token)).append("'>Keep my booking</a></p>");
            return page(HttpStatus.OK, shell("Cancel booking", body.toString()));
        } catch (AuthException error) {
            return page(HttpStatus.NOT_FOUND, message("Booking not found", "Check the link in your email."));
        }
    }

    @PostMapping(value = "/bookings/{token}/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> cancel(@PathVariable String token) {
        return act(token, "cancelled", () -> guestBookingService.cancel(token));
    }

    private ResponseEntity<String> act(String token, String done, java.util.function.Supplier<GuestBookingView> action) {
        try {
            action.get();
            return redirect("/public/bookings/" + token + "?done=" + done);
        } catch (AuthException error) {
            if (error.getStatus() == HttpStatus.NOT_FOUND) {
                return page(HttpStatus.NOT_FOUND, message("Booking not found", "Check the link in your email."));
            }
            try {
                return page(HttpStatus.BAD_REQUEST, bookingHtml(guestBookingService.view(token), null, error.getMessage()));
            } catch (AuthException ignored) {
                return page(HttpStatus.NOT_FOUND, message("Booking not found", "Check the link in your email."));
            }
        }
    }

    // ---- HTML ----

    private String bookingFormHtml(String restaurantSlug, String branchCode, String problem, FormValues values) {
        Branch branch;
        try {
            branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
        } catch (AuthException error) {
            return message("Not found", "This restaurant doesn't take bookings online.");
        }
        List<ReservationOccasionDto> occasions = reservationOccasionService.activeOccasions(branch.getRestaurant().getId());
        ZoneId zone = reservationSupport.restaurantZone(branch.getRestaurant());
        String today = LocalDate.now(zone).toString();
        StringBuilder times = new StringBuilder();
        for (LocalTime slot = LocalTime.of(12, 0); !slot.isAfter(LocalTime.of(22, 30)); slot = slot.plusMinutes(15)) {
            String value = slot.toString();
            times.append("<option").append(value.equals(values.time() == null ? "19:00" : values.time()) ? " selected" : "").append(">").append(value).append("</option>");
        }
        StringBuilder occasionOptions = new StringBuilder("<option value=''>None</option>");
        occasions.forEach(occasion -> occasionOptions.append("<option value='").append(esc(occasion.getCode())).append("'")
                .append(occasion.getCode().equals(values.occasion()) ? " selected" : "").append(">")
                .append(esc(occasion.getIcon() + " " + occasion.getName())).append("</option>"));
        String body = "<h1>Book a table</h1><p class='muted'>" + esc(branch.getRestaurant().getName()) + "</p>"
                + (problem == null ? "" : "<p class='error'>" + esc(problem) + "</p>")
                + "<form method='post'>"
                + "<label>Day<input type='date' name='date' required min='" + today + "' value='" + esc(or(values.date(), today)) + "'></label>"
                + "<label>Time<select name='time'>" + times + "</select></label>"
                + "<label>Guests<input type='number' name='guests' min='1' max='500' required value='" + esc(or(values.guests(), "2")) + "'></label>"
                + "<label>Name<input name='name' required maxlength='150' value='" + esc(or(values.name(), "")) + "'></label>"
                + "<label>Email<input type='email' name='email' required maxlength='150' value='" + esc(or(values.email(), "")) + "'></label>"
                + "<label>Phone (optional)<input name='phone' maxlength='50' value='" + esc(or(values.phone(), "")) + "'></label>"
                + (occasions.isEmpty() ? "" : "<label>Occasion (optional)<select name='occasion'>" + occasionOptions + "</select></label>")
                + "<label>Note (optional)<textarea name='note' maxlength='2000'>" + esc(or(values.note(), "")) + "</textarea></label>"
                + "<button>Book</button></form>"
                + "<p class='muted'>If no table is free we send your request to the restaurant and email you their answer. Cancelling is always free.</p>";
        return shell("Book a table", body);
    }

    private String bookingHtml(GuestBookingView booking, String notice, String problem) {
        String token = esc(booking.getToken());
        StringBuilder body = new StringBuilder()
                .append(notice == null ? "" : "<p class='notice'>" + esc(notice) + "</p>")
                .append(problem == null ? "" : "<p class='error'>" + esc(problem) + "</p>")
                .append("<h1>").append(esc(booking.getRestaurantName())).append("</h1>")
                .append("<p class='status'>").append(esc(booking.getStatusText())).append("</p>")
                .append("<p>").append(esc(when(booking))).append("<br>")
                .append(booking.getPartySize()).append(booking.getPartySize() == 1 ? " guest" : " guests");
        if (booking.getOccasion() != null) body.append("<br>").append(esc(booking.getOccasion()));
        if (booking.getEventName() != null) body.append("<br>").append(esc(booking.getEventIcon() + " " + booking.getEventName()));
        body.append("<br><span class='muted'>Booking code ").append(esc(booking.getReservationCode())).append("</span></p>");

        if (Boolean.TRUE.equals(booking.getCanConfirm())) {
            body.append("<form method='post' action='/public/bookings/").append(token).append("/confirm'><button>I'm coming ✓</button></form>");
        } else if (Boolean.TRUE.equals(booking.getAttendanceConfirmed())) {
            body.append("<p class='notice'>✓ You confirmed you're coming</p>");
        }
        if (booking.getLateOptions() != null && !booking.getLateOptions().isEmpty()) {
            body.append("<p>Running late? We'll keep your table a little longer (your booking still ends at ")
                    .append(esc(clock(booking.getReservationEnd(), booking))).append(").</p><div class='row'>");
            booking.getLateOptions().forEach(minutes -> body.append("<form method='post' action='/public/bookings/").append(token)
                    .append("/late?minutes=").append(minutes).append("'><button class='light'>+").append(minutes).append(" min</button></form>"));
            body.append("</div>");
        }
        List<MoneyLineResponse> money = booking.getMoney() == null ? List.of() : booking.getMoney();
        if (!money.isEmpty()) {
            body.append("<h2 id='pay'>Payments</h2><ul>");
            money.forEach(line -> body.append("<li>").append(esc(line.getDescription())).append(" · ")
                    .append(esc(amount(line.getAmount(), line.getCurrency()))).append("<br><span class='muted'>")
                    .append(esc(line.getExplanation())).append("</span></li>"));
            body.append("</ul>");
            if (booking.getAmountDue() != null && booking.getAmountDue().signum() > 0 && Boolean.TRUE.equals(booking.getCanCancel())) {
                body.append("<form method='post' action='/public/bookings/").append(token).append("/pay'><button>Pay ")
                        .append(esc(amount(booking.getAmountDue(), booking.getCurrency()))).append("</button></form>");
                if (Boolean.TRUE.equals(booking.getTestPayments())) {
                    body.append("<p class='muted'>Test mode: no card is charged.</p>");
                }
            }
        }
        if (Boolean.TRUE.equals(booking.getCanCancel())) {
            body.append("<p><a class='danger-link' href='/public/bookings/").append(token).append("/cancel'>Cancel booking</a></p>");
        }
        return shell(booking.getRestaurantName(), body.toString());
    }

    private static String message(String title, String text) {
        return shell(title, "<h1>" + esc(title) + "</h1><p>" + esc(text) + "</p>");
    }

    private static String shell(String title, String body) {
        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + esc(title) + "</title><style>"
                + "body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#f7f8f6;color:#222426;margin:0;padding:16px}"
                + "main{max-width:520px;margin:0 auto;background:#fff;border:1px solid #e3e6e1;border-radius:14px;padding:20px 20px 12px}"
                + "h1{font-size:22px;margin:0 0 6px}h2{font-size:16px;margin:18px 0 6px}p{line-height:1.45}"
                + "label{display:block;font-size:13px;font-weight:600;margin:10px 0 0}"
                + "input,select,textarea{display:block;width:100%;box-sizing:border-box;margin-top:4px;padding:10px;border:1px solid #d5d8d2;border-radius:8px;font-size:15px}"
                + "button{background:#4f7942;color:#fff;border:0;border-radius:10px;padding:12px 18px;font-size:15px;font-weight:600;margin:12px 0 4px;cursor:pointer}"
                + "button.light{background:#eef3eb;color:#4f7942}button.danger{background:#b13a2f}"
                + ".row{display:flex;gap:8px;flex-wrap:wrap}.row form{margin:0}"
                + ".muted{color:#747572;font-size:13px}.error{color:#b13a2f;font-weight:600}.notice{color:#4f7942;font-weight:600}"
                + ".status{font-weight:700;font-size:16px}.danger-link{color:#b13a2f}ul{padding-left:18px}li{margin-bottom:8px}"
                + "</style></head><body><main>" + body + "</main></body></html>";
    }

    private static String doneMessage(String done) {
        if (done == null) return null;
        return switch (done) {
            case "booked" -> "Thank you! We've emailed you the details.";
            case "confirmed" -> "Thanks for confirming. See you soon!";
            case "late" -> "Thanks for letting us know. We'll keep your table a little longer.";
            case "paid" -> "Payment received. Thank you!";
            case "cancelled" -> "Your booking is cancelled.";
            default -> null;
        };
    }

    private String when(GuestBookingView booking) {
        ZonedDateTime local = booking.getReservationStart().atZoneSameInstant(zoneOf(booking));
        return DAY.format(local) + " at " + CLOCK.format(local);
    }

    private String clock(java.time.OffsetDateTime moment, GuestBookingView booking) {
        return CLOCK.format(moment.atZoneSameInstant(zoneOf(booking)));
    }

    // Times as the restaurant shows them.
    private static ZoneId zoneOf(GuestBookingView booking) {
        try {
            return ZoneId.of(booking.getTimezone());
        } catch (RuntimeException error) {
            return java.time.ZoneOffset.UTC;
        }
    }

    private static String amount(BigDecimal value, String currency) {
        String symbol = switch (currency == null ? "" : currency) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> currency == null ? "" : currency + " ";
        };
        return symbol + (value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static ResponseEntity<String> page(HttpStatus status, String html) {
        return ResponseEntity.status(status).contentType(MediaType.valueOf("text/html;charset=UTF-8")).body(html);
    }

    // After a form, go back to the page (so refreshing doesn't send it again).
    private static ResponseEntity<String> redirect(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, URI.create(location).toString()).build();
    }

    private record FormValues(String date, String time, String guests, String name, String email, String phone, String occasion, String note) {
        static final FormValues EMPTY = new FormValues(null, null, null, null, null, null, null, null);
    }
}
