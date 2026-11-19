package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pos.pos.config.properties.AppMailProperties;
import pos.pos.i18n.Words;
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.entity.Reservation;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

// Emails to guests about their booking, each with a link to their booking page (confirm, cancel, running late, pay).
// Sent after the change is saved; a mail problem never undoes or blocks the booking.
@Service
@RequiredArgsConstructor
public class ReservationMailService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationMailService.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;
    private final AppMailProperties appMailProperties;
    private final ReservationSupport reservationSupport;
    private final ReservationReminderDeliveryService reminderDeliveryService;

    // Guest emails are switched off for now (user, 2026-11-18: "use real email but don't send anything, we'll come back to it");
    // when on, they go out through the same real sender as account emails.
    @Value("${app.mail.guest.enabled:false}")
    private boolean guestEmailsOn;

    private pos.pos.mail.AccountMailSender accountMail;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccountMail(pos.pos.mail.AccountMailSender accountMail) { this.accountMail = accountMail; }

    // Where the guest's booking pages live (the server itself until the website takes over).
    @Value("${app.reservations.guest-base-url:http://localhost:8080}")
    private String guestBaseUrl;

    public String bookingPage(Reservation reservation) {
        return guestBaseUrl.replaceAll("/+$", "") + "/public/bookings/" + reservation.getGuestToken();
    }

    // "Your table is confirmed ✓". Booked less than the reminder time ahead, it already asks them to confirm.
    public void confirmed(Reservation reservation, boolean acceptedRequest, boolean askToConfirm, List<MoneyLineResponse> due) {
        Runnable onDelivered = askToConfirm
                ? () -> reminderDeliveryService.recordGuestReminderSent(reservation.getId())
                : () -> { };
        send(reservation, l -> Words.t(l, acceptedRequest ? "Your table is confirmed" : "Booking confirmed") + " · " + restaurant(reservation, l), l -> {
            StringBuilder body = new StringBuilder(greeting(reservation, l))
                    .append(Words.t(l, acceptedRequest ? "Good news: your table is confirmed ✓" : "Your table is confirmed ✓"))
                    .append("\n\n").append(details(reservation, l)).append("\n");
            appendDue(body, due, l);
            if (askToConfirm) {
                // This email is the "Still coming?" one, so the reminder job won't send another.
                body.append("\n").append(Words.t(l, "Please let us know you're coming, or cancel if your plans changed (it's free):")).append("\n");
            } else {
                body.append("\n").append(Words.t(l, "We'll send a reminder before your visit. Need to change or cancel? It's free:")).append("\n");
            }
            return body.append(bookingPage(reservation)).append("\n").append(signature(reservation, l)).toString();
        }, onDelivered);
    }

    // No table free (or a big group): a request staff answer.
    public void requestReceived(Reservation reservation, List<MoneyLineResponse> due) {
        send(reservation, l -> Words.t(l, "Reservation request received") + " · " + restaurant(reservation, l), l -> {
            StringBuilder body = new StringBuilder(greeting(reservation, l))
                    .append(Words.t(l, "Your reservation request has been sent. We'll let you know as soon as we can confirm it.")).append("\n\n")
                    .append(details(reservation, l)).append("\n");
            appendDue(body, due, l);
            return body.append("\n").append(Words.t(l, "Your request: {}", bookingPage(reservation))).append("\n").append(signature(reservation, l)).toString();
        });
    }

    public void declined(Reservation reservation, List<MoneyLineResponse> money) {
        send(reservation, l -> Words.t(l, "Sorry, we're full") + " · " + restaurant(reservation, l), l -> {
            StringBuilder body = new StringBuilder(greeting(reservation, l))
                    .append(Words.t(l, "Sorry, we can't take your booking for {}: we're full.", when(reservation, l))).append("\n");
            appendRefunds(body, money, reservation, l);
            return body.append(signature(reservation, l)).toString();
        });
    }

    public void expired(Reservation reservation, List<MoneyLineResponse> money) {
        send(reservation, l -> Words.t(l, "We couldn't confirm your request") + " · " + restaurant(reservation, l), l -> {
            StringBuilder body = new StringBuilder(greeting(reservation, l))
                    .append(Words.t(l, "Sorry, we couldn't confirm your request for {}.", when(reservation, l))).append("\n");
            appendRefunds(body, money, reservation, l);
            return body.append(signature(reservation, l)).toString();
        });
    }

    public void cancelled(Reservation reservation, List<MoneyLineResponse> money) {
        send(reservation, l -> Words.t(l, "Booking cancelled") + " · " + restaurant(reservation, l), l -> {
            StringBuilder body = new StringBuilder(greeting(reservation, l))
                    .append(Words.t(l, "Your booking for {} is cancelled.", when(reservation, l))).append("\n");
            appendRefunds(body, money, reservation, l);
            return body.append(signature(reservation, l)).toString();
        });
    }

    // "See you tomorrow? [Confirm] [Cancel]" (and "I'm running late").
    public void reminder(Reservation reservation) {
        send(reservation, l -> Words.t(l, "See you soon? Please confirm") + " · " + restaurant(reservation, l), l -> greeting(reservation, l)
                + Words.t(l, "See you soon? Your table is booked for {} ({}).", when(reservation, l), guests(reservation, l)) + "\n\n"
                + Words.t(l, "Please confirm you're still coming, tell us if you're running late, or cancel for free:") + "\n"
                + bookingPage(reservation) + "\n" + signature(reservation, l),
                () -> reminderDeliveryService.recordGuestReminderSent(reservation.getId()));
    }

    // No answer to the first one: asked once more, a few hours before (Settings → Reservations).
    public void reminderAgain(Reservation reservation) {
        send(reservation, l -> Words.t(l, "Still coming today? Please confirm") + " · " + restaurant(reservation, l), l -> greeting(reservation, l)
                + Words.t(l, "We haven't heard from you yet. Your table is booked for {} ({}).", when(reservation, l), guests(reservation, l)) + "\n\n"
                + Words.t(l, "Please confirm you're still coming, or cancel for free:") + "\n"
                + bookingPage(reservation) + "\n" + signature(reservation, l),
                () -> reminderDeliveryService.recordGuestReminderSent(reservation.getId(), pos.pos.reservation.enums.ReservationEventType.GUEST_REMINDED_AGAIN));
    }

    public void paymentLink(Reservation reservation, List<MoneyLineResponse> due) {
        send(reservation, l -> Words.t(l, "Payment for your booking") + " · " + restaurant(reservation, l), l -> {
            StringBuilder body = new StringBuilder(greeting(reservation, l)).append(Words.t(l, "For your booking on {}:", when(reservation, l))).append("\n");
            appendDue(body, due, l);
            return body.append("\n").append(Words.t(l, "Pay here: {}", bookingPage(reservation))).append("\n").append(signature(reservation, l)).toString();
        });
    }

    // ---- Text (each email is written for a language; see send) ----

    private void appendDue(StringBuilder body, List<MoneyLineResponse> due, String l) {
        if (due == null || due.isEmpty()) {
            return;
        }
        body.append("\n").append(Words.t(l, "To pay:")).append("\n");
        due.forEach(line -> body.append("  • ").append(Words.tr(l, line.getDescription())).append(": ").append(money(line)).append("\n"));
        body.append(Words.t(l, "If you cancel in time you get it back minus the card fee. After the deadline, or if you don't come, there's no refund.")).append("\n");
    }

    private void appendRefunds(StringBuilder body, List<MoneyLineResponse> money, Reservation reservation, String l) {
        if (money == null || money.isEmpty()) {
            body.append("\n");
            return;
        }
        body.append("\n");
        money.forEach(line -> {
            String what = switch (line.getStatus()) {
                case "REFUNDED" -> Words.t(l, "refunded {}", amount(line.getRefundedAmount(), line.getCurrency()));
                case "KEPT" -> Words.t(l, "no refund (after the deadline)");
                case "CANCELLED" -> Words.t(l, "nothing was charged");
                default -> Words.tr(l, line.getExplanation());
            };
            body.append("  • ").append(Words.tr(l, line.getDescription())).append(" ").append(money(line)).append(": ").append(what).append("\n");
        });
        body.append("\n");
    }

    private String greeting(Reservation reservation, String l) {
        String name = reservation.getContactName();
        return Words.t(l, "Hello {},", name == null || name.isBlank() ? Words.t(l, "there") : name.trim().split("\\s+")[0]) + "\n\n";
    }

    private String details(Reservation reservation, String l) {
        StringBuilder text = new StringBuilder()
                .append("  ").append(restaurant(reservation, l)).append("\n")
                .append("  ").append(when(reservation, l)).append("\n")
                .append("  ").append(guests(reservation, l)).append("\n");
        if (reservation.getOccasionName() != null) {
            text.append("  ").append(reservation.getOccasionIcon() == null ? "" : reservation.getOccasionIcon() + " ").append(reservation.getOccasionName()).append("\n");
        }
        text.append("  ").append(Words.t(l, "Booking code {}", reservation.getReservationCode())).append("\n");
        return text.toString();
    }

    // "Friday 6 November at 19:00", in the email's language.
    private String when(Reservation reservation, String l) {
        java.time.ZonedDateTime local = reservation.getReservationStart().atZoneSameInstant(reservationSupport.restaurantZone(reservation.getRestaurant()));
        return Words.t(l, "{} at {}", pos.pos.reservation.controller.GuestBookingPageController.day(local, l), CLOCK.format(local));
    }

    private static String guests(Reservation reservation, String l) {
        return reservation.getPartySize() == 1 ? Words.t(l, "1 guest") : Words.t(l, "{} guests", reservation.getPartySize());
    }

    private static String restaurant(Reservation reservation, String l) {
        return reservation.getRestaurant() == null ? Words.t(l, "the restaurant") : reservation.getRestaurant().getName();
    }

    // The restaurant's name and how to reach it (the branch's phone/email, otherwise the restaurant's).
    private String signature(Reservation reservation, String l) {
        StringBuilder text = new StringBuilder("\n").append(Words.t(l, "See you soon,")).append("\n").append(restaurant(reservation, l)).append("\n");
        var branch = reservation.getBranch();
        var owner = reservation.getRestaurant();
        String phone = branch != null && branch.getPhone() != null ? branch.getPhone() : owner == null ? null : owner.getPhone();
        String email = branch != null && branch.getEmail() != null ? branch.getEmail() : owner == null ? null : owner.getEmail();
        if (phone != null) text.append(Words.t(l, "Phone: {}", phone)).append("\n");
        if (email != null) text.append(Words.t(l, "Email: {}", email)).append("\n");
        return text.toString();
    }

    private static String money(MoneyLineResponse line) {
        return amount(line.getAmount(), line.getCurrency());
    }

    private static String amount(java.math.BigDecimal value, String currency) {
        String symbol = switch (currency == null ? "" : currency) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> (currency == null ? "" : currency + " ");
        };
        return symbol + (value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    // ---- Sending ----

    private void send(Reservation reservation, Function<String, String> subjectIn, Function<String, String> bodyIn) {
        send(reservation, subjectIn, bodyIn, () -> { });
    }

    /**
     * In the language the guest booked in, with the English under it (user, 2026-11-18: "English is always included");
     * only English when they booked in English or staff made the booking.
     */
    private void send(Reservation reservation, Function<String, String> subjectIn, Function<String, String> bodyIn, Runnable onDelivered) {
        String lang = Words.language(reservation.getLanguage());
        String subject = subjectIn.apply(lang);
        String body = "en".equals(lang) ? bodyIn.apply("en")
                : bodyIn.apply(lang) + "\n\n———— English ————\n\n" + bodyIn.apply("en");
        send(reservation, subject, body, onDelivered);
    }

    private void send(Reservation reservation, String subject, String body, Runnable onDelivered) {
        String to = reservation.getContactEmail();
        if (to == null || to.isBlank()) {
            return;
        }
        if (!guestEmailsOn) {
            logger.debug("Guest emails are switched off; not sending \"{}\" for booking {}", subject, reservation.getReservationCode());
            return;
        }
        Runnable task = () -> {
            boolean delivered = false;
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                if (accountMail != null) {
                    accountMail.send(message);
                } else {
                    message.setFrom(appMailProperties.getFrom());
                    mailSender.send(message);
                }
                delivered = true;
            } catch (RuntimeException error) {
                logger.warn("Could not email {} about booking {} ({})", to, reservation.getReservationCode(), subject, error);
            }
            if (delivered) {
                try {
                    onDelivered.run();
                } catch (RuntimeException error) {
                    // The email has already left the process. Log the missing marker so operations can detect
                    // a possible duplicate on the next scheduled reminder attempt.
                    logger.error("Email was accepted but its delivery event could not be recorded for booking {}",
                            reservation.getReservationCode(), error);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
