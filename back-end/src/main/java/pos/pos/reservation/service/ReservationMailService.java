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
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.entity.Reservation;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Emails to guests about their booking, each with a link to their booking page (confirm, cancel, running late, pay).
// Sent after the change is saved; a mail problem never undoes or blocks the booking.
@Service
@RequiredArgsConstructor
public class ReservationMailService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationMailService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM 'at' HH:mm", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final AppMailProperties appMailProperties;
    private final ReservationSupport reservationSupport;
    private final ReservationReminderDeliveryService reminderDeliveryService;

    // Where the guest's booking pages live (the server itself until the website takes over).
    @Value("${app.reservations.guest-base-url:http://localhost:8080}")
    private String guestBaseUrl;

    public String bookingPage(Reservation reservation) {
        return guestBaseUrl.replaceAll("/+$", "") + "/public/bookings/" + reservation.getGuestToken();
    }

    // "Your table is confirmed ✓". Booked less than the reminder time ahead, it already asks them to confirm.
    public void confirmed(Reservation reservation, boolean acceptedRequest, boolean askToConfirm, List<MoneyLineResponse> due) {
        String opening = acceptedRequest ? "Good news: your table is confirmed ✓" : "Your table is confirmed ✓";
        StringBuilder body = new StringBuilder(greeting(reservation))
                .append(opening).append("\n\n").append(details(reservation)).append("\n");
        appendDue(body, due, reservation);
        if (askToConfirm) {
            // This email is the "Still coming?" one, so the reminder job won't send another.
            body.append("\nPlease let us know you're coming, or cancel if your plans changed (it's free):\n");
        } else {
            body.append("\nWe'll send a reminder before your visit. Need to change or cancel? It's free:\n");
        }
        body.append(bookingPage(reservation)).append("\n").append(signature(reservation));
        Runnable onDelivered = askToConfirm
                ? () -> reminderDeliveryService.recordGuestReminderSent(reservation.getId())
                : () -> { };
        send(reservation, (acceptedRequest ? "Your table is confirmed" : "Booking confirmed") + " · " + restaurant(reservation), body.toString(), onDelivered);
    }

    // No table free (or a big group): a request staff answer.
    public void requestReceived(Reservation reservation, List<MoneyLineResponse> due) {
        StringBuilder body = new StringBuilder(greeting(reservation))
                .append("Your reservation request has been sent. We'll let you know as soon as we can confirm it.\n\n")
                .append(details(reservation)).append("\n");
        appendDue(body, due, reservation);
        body.append("\nYour request: ").append(bookingPage(reservation)).append("\n").append(signature(reservation));
        send(reservation, "Reservation request received · " + restaurant(reservation), body.toString());
    }

    public void declined(Reservation reservation, List<MoneyLineResponse> money) {
        StringBuilder body = new StringBuilder(greeting(reservation))
                .append("Sorry, we can't take your booking for ").append(when(reservation)).append(": we're full.\n");
        appendRefunds(body, money, reservation);
        body.append(signature(reservation));
        send(reservation, "Sorry, we're full · " + restaurant(reservation), body.toString());
    }

    public void expired(Reservation reservation, List<MoneyLineResponse> money) {
        StringBuilder body = new StringBuilder(greeting(reservation))
                .append("Sorry, we couldn't confirm your request for ").append(when(reservation)).append(".\n");
        appendRefunds(body, money, reservation);
        body.append(signature(reservation));
        send(reservation, "We couldn't confirm your request · " + restaurant(reservation), body.toString());
    }

    public void cancelled(Reservation reservation, List<MoneyLineResponse> money) {
        StringBuilder body = new StringBuilder(greeting(reservation))
                .append("Your booking for ").append(when(reservation)).append(" is cancelled.\n");
        appendRefunds(body, money, reservation);
        body.append(signature(reservation));
        send(reservation, "Booking cancelled · " + restaurant(reservation), body.toString());
    }

    // "See you tomorrow? [Confirm] [Cancel]" (and "I'm running late").
    public void reminder(Reservation reservation) {
        String body = greeting(reservation)
                + "See you soon? Your table is booked for " + when(reservation) + " (" + guests(reservation) + ").\n\n"
                + "Please confirm you're still coming, tell us if you're running late, or cancel for free:\n"
                + bookingPage(reservation) + "\n" + signature(reservation);
        send(reservation, "See you soon? Please confirm · " + restaurant(reservation), body,
                () -> reminderDeliveryService.recordGuestReminderSent(reservation.getId()));
    }

    public void paymentLink(Reservation reservation, List<MoneyLineResponse> due) {
        StringBuilder body = new StringBuilder(greeting(reservation)).append("For your booking on ").append(when(reservation)).append(":\n");
        appendDue(body, due, reservation);
        body.append("\nPay here: ").append(bookingPage(reservation)).append("\n").append(signature(reservation));
        send(reservation, "Payment for your booking · " + restaurant(reservation), body.toString());
    }

    // ---- Text ----

    private void appendDue(StringBuilder body, List<MoneyLineResponse> due, Reservation reservation) {
        if (due == null || due.isEmpty()) {
            return;
        }
        body.append("\nTo pay:\n");
        due.forEach(line -> body.append("  • ").append(line.getDescription()).append(": ").append(money(line, reservation)).append("\n"));
        body.append("If you cancel in time you get it back minus the card fee. After the deadline, or if you don't come, there's no refund.\n");
    }

    private void appendRefunds(StringBuilder body, List<MoneyLineResponse> money, Reservation reservation) {
        if (money == null || money.isEmpty()) {
            body.append("\n");
            return;
        }
        body.append("\n");
        money.forEach(line -> {
            String what = switch (line.getStatus()) {
                case "REFUNDED" -> "refunded " + amount(line.getRefundedAmount(), line.getCurrency());
                case "KEPT" -> "no refund (after the deadline)";
                case "CANCELLED" -> "nothing was charged";
                default -> line.getExplanation();
            };
            body.append("  • ").append(line.getDescription()).append(" ").append(money(line, reservation)).append(": ").append(what).append("\n");
        });
        body.append("\n");
    }

    private String greeting(Reservation reservation) {
        String name = reservation.getContactName();
        return "Hello " + (name == null || name.isBlank() ? "there" : name.trim().split("\\s+")[0]) + ",\n\n";
    }

    private String details(Reservation reservation) {
        StringBuilder text = new StringBuilder()
                .append("  ").append(restaurant(reservation)).append("\n")
                .append("  ").append(when(reservation)).append("\n")
                .append("  ").append(guests(reservation)).append("\n");
        if (reservation.getOccasionName() != null) {
            text.append("  ").append(reservation.getOccasionIcon() == null ? "" : reservation.getOccasionIcon() + " ").append(reservation.getOccasionName()).append("\n");
        }
        text.append("  Booking code ").append(reservation.getReservationCode()).append("\n");
        return text.toString();
    }

    private String when(Reservation reservation) {
        return WHEN.format(reservation.getReservationStart().atZoneSameInstant(reservationSupport.restaurantZone(reservation.getRestaurant())));
    }

    private static String guests(Reservation reservation) {
        return reservation.getPartySize() + (reservation.getPartySize() == 1 ? " guest" : " guests");
    }

    private static String restaurant(Reservation reservation) {
        return reservation.getRestaurant() == null ? "the restaurant" : reservation.getRestaurant().getName();
    }

    private String signature(Reservation reservation) {
        return "\nSee you soon,\n" + restaurant(reservation) + "\n";
    }

    private static String money(MoneyLineResponse line, Reservation reservation) {
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

    private void send(Reservation reservation, String subject, String body) {
        send(reservation, subject, body, () -> { });
    }

    private void send(Reservation reservation, String subject, String body, Runnable onDelivered) {
        String to = reservation.getContactEmail();
        if (to == null || to.isBlank()) {
            return;
        }
        Runnable task = () -> {
            boolean delivered = false;
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(appMailProperties.getFrom());
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
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
