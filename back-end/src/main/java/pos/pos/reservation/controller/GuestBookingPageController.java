package pos.pos.reservation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import pos.pos.exception.auth.AuthException;
import pos.pos.i18n.PageLanguage;
import pos.pos.i18n.Words;
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

// Simple pages guests use until the website is built: book a table, and their booking page from the email links
// (confirm, running late, pay, cancel with what's refunded). Plain HTML, works on any phone. In four languages (user,
// 2026-11-18): the one picked at the top (?lang=), else the one they booked in, else the browser's; English always offered.
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class GuestBookingPageController {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final GuestBookingService guestBookingService;
    private final ReservationOccasionService reservationOccasionService;
    private final ReservationSupport reservationSupport;

    // Opening hours shown to guests (user, 2026-11-20); not in the constructor (unit tests).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private pos.pos.hours.OpeningHoursService openingHours;

    // ---- Booking form ----

    @GetMapping(value = "/book/{restaurantSlug}/{branchCode}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> bookingForm(@PathVariable String restaurantSlug, @PathVariable String branchCode,
                                              @RequestParam(required = false) String lang,
                                              @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String browser) {
        return bookingFormPage(HttpStatus.OK, restaurantSlug, branchCode, null, FormValues.EMPTY, PageLanguage.pick(lang, null, browser));
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
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String lang
    ) {
        FormValues values = new FormValues(date, time, String.valueOf(guests), name, email, phone, occasion, note);
        String l = PageLanguage.pick(lang, null, null);
        try {
            Branch branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
            ZoneId zone = reservationSupport.restaurantZone(branch.getRestaurant());
            ZonedDateTime start = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone);
            if (name == null || name.isBlank()) throw new IllegalArgumentException(Words.en("Write your name"));
            if (email == null || !email.contains("@")) throw new IllegalArgumentException(Words.en("Write a valid email, so we can confirm the booking"));
            if (guests < 1) throw new IllegalArgumentException(Words.en("Say how many guests"));
            GuestBookingView booked = guestBookingService.book(restaurantSlug, branchCode, OnlineBookingRequest.builder()
                    .partySize(guests)
                    .reservationStart(start.toOffsetDateTime())
                    .contactName(name.trim())
                    .contactEmail(email.trim())
                    .contactPhone(phone)
                    .occasionCode(occasion == null || occasion.isBlank() ? null : occasion)
                    .specialRequests(note)
                    .language(l)
                    .build());
            return redirect("/public/bookings/" + booked.getToken() + "?done=booked&lang=" + l);
        } catch (AuthException error) {
            if (error.getStatus() == HttpStatus.NOT_FOUND) {
                return page(HttpStatus.NOT_FOUND, message(l, Words.t(l, "Not found"), Words.t(l, "This restaurant doesn't take bookings online.")));
            }
            return bookingFormPage(HttpStatus.BAD_REQUEST, restaurantSlug, branchCode, Words.tr(l, error.getMessage()), values, l);
        } catch (RuntimeException error) {
            String problem = error instanceof IllegalArgumentException ? Words.tr(l, error.getMessage()) : Words.t(l, "Check the day and time");
            return bookingFormPage(HttpStatus.BAD_REQUEST, restaurantSlug, branchCode, problem, values, l);
        }
    }

    // ---- The guest's booking ----

    @GetMapping(value = "/bookings/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> booking(@PathVariable String token, @RequestParam(required = false) String done,
                                          @RequestParam(required = false) String lang,
                                          @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String browser) {
        try {
            GuestBookingView booking = guestBookingService.view(token);
            String l = PageLanguage.pick(lang, booking.getLanguage(), browser);
            return page(HttpStatus.OK, bookingHtml(booking, doneMessage(done, l), null, l));
        } catch (AuthException error) {
            return notFound(PageLanguage.pick(lang, null, browser));
        }
    }

    @PostMapping(value = "/bookings/{token}/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirm(@PathVariable String token, @RequestParam(required = false) String lang) {
        return act(token, "confirmed", lang, () -> guestBookingService.confirmAttendance(token));
    }

    @PostMapping(value = "/bookings/{token}/late", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> late(@PathVariable String token, @RequestParam int minutes, @RequestParam(required = false) String lang) {
        return act(token, "late", lang, () -> guestBookingService.runningLate(token, minutes));
    }

    @PostMapping(value = "/bookings/{token}/pay", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pay(@PathVariable String token, @RequestParam(required = false) String lang) {
        return act(token, "paid", lang, () -> guestBookingService.payInTestMode(token));
    }

    @GetMapping(value = "/bookings/{token}/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> cancelPage(@PathVariable String token, @RequestParam(required = false) String lang,
                                             @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String browser) {
        try {
            GuestBookingView booking = guestBookingService.view(token);
            String l = PageLanguage.pick(lang, booking.getLanguage(), browser);
            List<MoneyLineResponse> lines = guestBookingService.cancelPreview(token).stream()
                    .filter(line -> "PAID".equals(line.getStatus())).toList();
            StringBuilder body = new StringBuilder()
                    .append("<h1>").append(Words.t(l, "Cancel your booking?")).append("</h1>")
                    .append("<p>").append(esc(booking.getRestaurantName())).append(" · ").append(esc(when(booking, l))).append("</p>");
            if (lines.isEmpty()) {
                body.append("<p>").append(Words.t(l, "Cancelling is free.")).append("</p>");
            } else {
                body.append("<p>").append(Words.t(l, "What happens to what you paid:")).append("</p><ul>");
                lines.forEach(line -> body.append("<li>").append(esc(Words.tr(l, line.getExplanation()))).append("</li>"));
                body.append("</ul>");
            }
            body.append("<form method='post' action='/public/bookings/").append(esc(token)).append("/cancel?lang=").append(l).append("'>")
                    .append("<button class='danger'>").append(Words.t(l, "Cancel booking")).append("</button></form>")
                    .append("<p><a href='/public/bookings/").append(esc(token)).append("?lang=").append(l).append("'>")
                    .append(Words.t(l, "Keep my booking")).append("</a></p>");
            return page(HttpStatus.OK, shell(l, code -> "/public/bookings/" + token + "/cancel?lang=" + code, Words.t(l, "Cancel booking"), body.toString()));
        } catch (AuthException error) {
            return notFound(PageLanguage.pick(lang, null, browser));
        }
    }

    @PostMapping(value = "/bookings/{token}/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> cancel(@PathVariable String token, @RequestParam(required = false) String lang) {
        return act(token, "cancelled", lang, () -> guestBookingService.cancel(token));
    }

    private ResponseEntity<String> act(String token, String done, String lang, java.util.function.Supplier<GuestBookingView> action) {
        String l = PageLanguage.pick(lang, null, null);
        try {
            action.get();
            return redirect("/public/bookings/" + token + "?done=" + done + "&lang=" + l);
        } catch (AuthException error) {
            if (error.getStatus() == HttpStatus.NOT_FOUND) {
                return notFound(l);
            }
            try {
                return page(HttpStatus.BAD_REQUEST, bookingHtml(guestBookingService.view(token), null, Words.tr(l, error.getMessage()), l));
            } catch (AuthException ignored) {
                return notFound(l);
            }
        }
    }

    private static ResponseEntity<String> notFound(String l) {
        return page(HttpStatus.NOT_FOUND, message(l, Words.t(l, "Booking not found"), Words.t(l, "Check the link in your email.")));
    }

    // ---- HTML ----

    private ResponseEntity<String> bookingFormPage(HttpStatus status, String restaurantSlug, String branchCode,
                                                    String problem, FormValues values, String l) {
        try {
            return page(status, bookingFormHtml(restaurantSlug, branchCode, problem, values, l));
        } catch (AuthException error) {
            if (error.getStatus() != HttpStatus.NOT_FOUND) throw error;
            return page(HttpStatus.NOT_FOUND, message(l, Words.t(l, "Not found"), Words.t(l, "This restaurant doesn't take bookings online.")));
        }
    }

    private String bookingFormHtml(String restaurantSlug, String branchCode, String problem, FormValues values, String l) {
        Branch branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
        List<ReservationOccasionDto> occasions = reservationOccasionService.activeOccasions(branch.getRestaurant().getId());
        ZoneId zone = reservationSupport.restaurantZone(branch.getRestaurant());
        String today = LocalDate.now(zone).toString();
        // Only times the branch is open, date by date (user, 2026-11-21); 12:00–22:30 while its hours were never set.
        java.util.Map<LocalDate, List<LocalTime>> open = guestBookingService.bookingTimes(branch);
        String day = or(values.date(), today);
        List<String> shown = new java.util.ArrayList<>();
        String max = null;
        String script = "";
        if (open == null) {
            for (LocalTime slot = LocalTime.of(12, 0); !slot.isAfter(LocalTime.of(22, 30)); slot = slot.plusMinutes(15)) shown.add(slot.toString());
        } else {
            // No day asked for and nothing left today: start on the next open day.
            if (values.date() == null && open.getOrDefault(LocalDate.parse(today), List.of()).isEmpty()) {
                day = open.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(e -> e.getKey().toString()).findFirst().orElse(today);
            }
            LocalDate chosen = parseDate(day);
            if (chosen != null) open.getOrDefault(chosen, List.of()).forEach(time -> shown.add(time.toString()));
            max = open.keySet().stream().reduce((a, b) -> b).map(LocalDate::toString).orElse(null);
            script = timesScript(open, l);
        }
        String picked = values.time() != null && shown.contains(values.time()) ? values.time() : shown.contains("19:00") ? "19:00" : null;
        StringBuilder times = new StringBuilder();
        for (String value : shown) {
            times.append("<option").append(value.equals(picked) ? " selected" : "").append(">").append(value).append("</option>");
        }
        boolean closedDay = shown.isEmpty();
        StringBuilder occasionOptions = new StringBuilder("<option value=''>" + Words.t(l, "None") + "</option>");
        occasions.forEach(occasion -> occasionOptions.append("<option value='").append(esc(occasion.getCode())).append("'")
                .append(occasion.getCode().equals(values.occasion()) ? " selected" : "").append(">")
                .append(esc(occasion.getIcon() + " " + occasion.getName())).append("</option>"));
        String body = "<h1>" + Words.t(l, "Book a table") + "</h1><p class='muted'>" + esc(branch.getRestaurant().getName()) + "</p>"
                + (problem == null ? "" : "<p class='error'>" + esc(problem) + "</p>")
                + "<form method='post'>"
                + "<input type='hidden' name='lang' value='" + l + "'>"
                + "<label>" + Words.t(l, "Day") + "<input type='date' id='day' name='date' required min='" + today + "'"
                + (max == null ? "" : " max='" + max + "'") + " value='" + esc(day) + "'></label>"
                + "<label id='time-label'" + (closedDay ? " style='display:none'" : "") + ">" + Words.t(l, "Time") + "<select id='time' name='time'>" + times + "</select></label>"
                + "<p id='closed' class='error'" + (closedDay ? "" : " style='display:none'") + ">" + Words.t(l, "Closed that day.")
                + " <a href='#' id='next-open' style='color:#4f7942'></a></p>"
                + "<label>" + Words.t(l, "Guests") + "<input type='number' name='guests' min='1' max='500' required value='" + esc(or(values.guests(), "2")) + "'></label>"
                + "<label>" + Words.t(l, "Name") + "<input name='name' required maxlength='150' value='" + esc(or(values.name(), "")) + "'></label>"
                + "<label>" + Words.t(l, "Email") + "<input type='email' name='email' required maxlength='150' value='" + esc(or(values.email(), "")) + "'></label>"
                + "<label>" + Words.t(l, "Phone (optional)") + "<input name='phone' maxlength='50' value='" + esc(or(values.phone(), "")) + "'></label>"
                + (occasions.isEmpty() ? "" : "<label>" + Words.t(l, "Occasion (optional)") + "<select name='occasion'>" + occasionOptions + "</select></label>")
                + "<label>" + Words.t(l, "Note (optional)") + "<textarea name='note' maxlength='2000'>" + esc(or(values.note(), "")) + "</textarea></label>"
                + "<button id='book'" + (closedDay ? " disabled style='opacity:.5'" : "") + ">" + Words.t(l, "Book") + "</button></form>"
                + script
                + "<p class='muted'>" + Words.t(l, "If no table is free we send your request to the restaurant and email you their answer. Cancelling is always free.") + "</p>"
                + contact(branch.getPhone() != null ? branch.getPhone() : branch.getRestaurant().getPhone(),
                        branch.getEmail() != null ? branch.getEmail() : branch.getRestaurant().getEmail(), l)
                + hoursLine(branch.getId(), l);
        return shell(l, code -> "/public/book/" + restaurantSlug + "/" + branchCode + "?lang=" + code, Words.t(l, "Book a table"), body);
    }

    private static LocalDate parseDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The times for every date, so changing the day changes the times without a reload: each different list of times once
     * ("p"), each date pointing at its list ("d"). A day with none says "Closed that day." with a link to the next open day.
     */
    private static String timesScript(java.util.Map<LocalDate, List<LocalTime>> open, String l) {
        List<List<LocalTime>> patterns = new java.util.ArrayList<>();
        StringBuilder dates = new StringBuilder("{");
        open.forEach((date, list) -> {
            int index = patterns.indexOf(list);
            if (index < 0) {
                patterns.add(list);
                index = patterns.size() - 1;
            }
            if (dates.length() > 1) dates.append(',');
            dates.append('"').append(date).append("\":").append(index);
        });
        dates.append('}');
        StringBuilder lists = new StringBuilder("[");
        for (int i = 0; i < patterns.size(); i++) {
            if (i > 0) lists.append(',');
            lists.append('[');
            List<LocalTime> list = patterns.get(i);
            for (int j = 0; j < list.size(); j++) lists.append(j > 0 ? "," : "").append('"').append(list.get(j)).append('"');
            lists.append(']');
        }
        lists.append(']');
        return "<script>(function(){var P=" + lists + ",D=" + dates + ",lang='" + l + "',nextText=" + js(Words.t(l, "Next open: {}")) + ";"
                + "var day=document.getElementById('day'),sel=document.getElementById('time'),label=document.getElementById('time-label'),"
                + "closed=document.getElementById('closed'),next=document.getElementById('next-open'),book=document.getElementById('book');"
                + "function timesOf(d){var i=D[d];return i===undefined?[]:P[i];}"
                + "function nextOpen(d){var keys=Object.keys(D).sort();for(var i=0;i<keys.length;i++){if(keys[i]>d&&P[D[keys[i]]].length)return keys[i];}return null;}"
                + "function fill(){var list=timesOf(day.value),cur=sel.value;sel.innerHTML='';"
                + "list.forEach(function(t){var o=document.createElement('option');o.textContent=t;sel.appendChild(o);});"
                + "if(list.indexOf(cur)>=0)sel.value=cur;else if(list.indexOf('19:00')>=0)sel.value='19:00';"
                + "var none=list.length===0;label.style.display=none?'none':'';closed.style.display=none?'':'none';book.disabled=none;book.style.opacity=none?'.5':'';"
                + "var n=none?nextOpen(day.value):null;next.style.display=n?'':'none';"
                + "if(n){var dt=new Date(n+'T12:00:00');var text;try{text=dt.toLocaleDateString(lang,{weekday:'short',day:'numeric',month:'short'});}catch(e){text=n;}"
                + "next.textContent=nextText.replace('{}',text);next.onclick=function(e){e.preventDefault();day.value=n;fill();};}}"
                + "day.addEventListener('change',fill);day.addEventListener('input',fill);fill();})();</script>";
    }

    /** A text as a JavaScript string inside the page. */
    private static String js(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\u003c").replace("\n", " ") + "'";
    }

    private String bookingHtml(GuestBookingView booking, String notice, String problem, String l) {
        String token = esc(booking.getToken());
        String q = "?lang=" + l;
        StringBuilder body = new StringBuilder()
                .append(notice == null ? "" : "<p class='notice'>" + esc(notice) + "</p>")
                .append(problem == null ? "" : "<p class='error'>" + esc(problem) + "</p>")
                .append("<h1>").append(esc(booking.getRestaurantName())).append("</h1>")
                .append("<p class='status'>").append(esc(Words.tr(l, booking.getStatusText()))).append("</p>")
                .append("<p>").append(esc(when(booking, l))).append("<br>")
                .append(booking.getPartySize() == 1 ? Words.t(l, "1 guest") : Words.t(l, "{} guests", booking.getPartySize()));
        if (booking.getOccasion() != null) body.append("<br>").append(esc(booking.getOccasion()));
        if (booking.getEventName() != null) body.append("<br>").append(esc(booking.getEventIcon() + " " + booking.getEventName()));
        body.append("<br><span class='muted'>").append(Words.t(l, "Booking code {}", esc(booking.getReservationCode()))).append("</span></p>");

        if (Boolean.TRUE.equals(booking.getCanConfirm())) {
            body.append("<form method='post' action='/public/bookings/").append(token).append("/confirm").append(q).append("'><button>")
                    .append(Words.t(l, "I'm coming ✓")).append("</button></form>");
        } else if (Boolean.TRUE.equals(booking.getAttendanceConfirmed())) {
            body.append("<p class='notice'>").append(Words.t(l, "✓ You confirmed you're coming")).append("</p>");
        }
        if (booking.getLateOptions() != null && !booking.getLateOptions().isEmpty()) {
            body.append("<p>").append(Words.t(l, "Running late? We'll keep your table a little longer (your booking still ends at {}).",
                    esc(clock(booking.getReservationEnd(), booking)))).append("</p><div class='row'>");
            booking.getLateOptions().forEach(minutes -> body.append("<form method='post' action='/public/bookings/").append(token)
                    .append("/late?minutes=").append(minutes).append("&lang=").append(l).append("'><button class='light'>")
                    .append(Words.t(l, "+{} min", minutes)).append("</button></form>"));
            body.append("</div>");
        }
        List<MoneyLineResponse> money = booking.getMoney() == null ? List.of() : booking.getMoney();
        if (!money.isEmpty()) {
            body.append("<h2 id='pay'>").append(Words.t(l, "Payments")).append("</h2><ul>");
            money.forEach(line -> body.append("<li>").append(esc(Words.tr(l, line.getDescription()))).append(" · ")
                    .append(esc(amount(line.getAmount(), line.getCurrency()))).append("<br><span class='muted'>")
                    .append(esc(Words.tr(l, line.getExplanation()))).append("</span></li>"));
            body.append("</ul>");
            if (booking.getAmountDue() != null && booking.getAmountDue().signum() > 0 && Boolean.TRUE.equals(booking.getCanCancel())) {
                body.append("<form method='post' action='/public/bookings/").append(token).append("/pay").append(q).append("'><button>")
                        .append(Words.t(l, "Pay {}", esc(amount(booking.getAmountDue(), booking.getCurrency())))).append("</button></form>");
                if (Boolean.TRUE.equals(booking.getTestPayments())) {
                    body.append("<p class='muted'>").append(Words.t(l, "Test mode: no card is charged.")).append("</p>");
                }
            }
        }
        if (Boolean.TRUE.equals(booking.getCanCancel())) {
            body.append("<p><a class='danger-link' href='/public/bookings/").append(token).append("/cancel").append(q).append("'>")
                    .append(Words.t(l, "Cancel booking")).append("</a></p>");
        }
        body.append(contact(booking.getRestaurantPhone(), booking.getRestaurantEmail(), l));
        return shell(l, code -> "/public/bookings/" + booking.getToken() + "?lang=" + code, booking.getRestaurantName(), body.toString());
    }

    // "Questions? Call … · Write to …" at the bottom of the guest pages (phone and email links work on phones).
    // The branch's opening hours under the contact line (user, 2026-11-20: shown to guests).
    private String hoursLine(java.util.UUID branchId, String l) {
        String week = openingHours == null ? null : openingHours.weekText(branchId, l);
        return week == null ? "" : "<p class='muted contact'>" + Words.t(l, "Opening hours: {}", esc(week)) + "</p>";
    }

    private static String contact(String phone, String email, String l) {
        if (phone == null && email == null) return "";
        String call = phone == null ? null : Words.t(l, "Call {}", "<a href='tel:" + esc(phone.replaceAll("[^+0-9]", "")) + "'>" + esc(phone) + "</a>");
        String write = email == null ? null : Words.t(l, "Write to {}", "<a href='mailto:" + esc(email) + "'>" + esc(email) + "</a>");
        String ways = call != null && write != null ? call + " · " + write : call != null ? call : write;
        return "<p class='muted contact'>" + Words.t(l, "Questions? {}", ways) + "</p>";
    }

    private static String message(String l, String title, String text) {
        return shell(l, code -> "?lang=" + code, title, "<h1>" + esc(title) + "</h1><p>" + esc(text) + "</p>");
    }

    private static String shell(String l, java.util.function.Function<String, String> hrefFor, String title, String body) {
        return "<!doctype html><html lang='" + l + "'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
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
                + ".contact{border-top:1px solid #e3e6e1;padding-top:12px;margin-top:16px}.contact a{color:#4f7942;font-weight:600;text-decoration:none}"
                + PageLanguage.STYLE
                + "</style></head><body><main>" + PageLanguage.switcher(l, hrefFor) + body + "</main></body></html>";
    }

    private static String doneMessage(String done, String l) {
        if (done == null) return null;
        return switch (done) {
            case "booked" -> Words.t(l, "Thank you! We've emailed you the details.");
            case "confirmed" -> Words.t(l, "Thanks for confirming. See you soon!");
            case "late" -> Words.t(l, "Thanks for letting us know. We'll keep your table a little longer.");
            case "paid" -> Words.t(l, "Payment received. Thank you!");
            case "cancelled" -> Words.t(l, "Your booking is cancelled.");
            default -> null;
        };
    }

    private String when(GuestBookingView booking, String l) {
        ZonedDateTime local = booking.getReservationStart().atZoneSameInstant(zoneOf(booking));
        return Words.t(l, "{} at {}", day(local, l), CLOCK.format(local));
    }

    /** "Friday 6 November", "venerdì 6 novembre", "viernes 6 de noviembre", "e premte 6 nëntor" (first letter capital). */
    public static String day(ZonedDateTime local, String l) {
        String pattern = "es".equals(Words.language(l)) ? "EEEE d 'de' MMMM" : "EEEE d MMMM";
        String text = DateTimeFormatter.ofPattern(pattern, Words.locale(l)).format(local);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
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
