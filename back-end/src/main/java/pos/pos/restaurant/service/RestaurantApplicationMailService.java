package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.mail.EmailKind;
import pos.pos.mail.EmailOutboxService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

/**
 * Emails to the person who applied to open a restaurant, sent through the email outbox (after the change commits,
 * retried while the mail server is down).
 */
@Service
@RequiredArgsConstructor
public class RestaurantApplicationMailService {

    private final EmailOutboxService outbox;

    /** "We received your application", with the private code for checking on it. */
    public void received(Restaurant restaurant, String statusCode) {
        String body = """
                Hello %s,

                Thank you for applying to open %s on the POS. We will review your application and let you know.

                Your application code is: %s

                Keep this code. In the app, choose "Check an application" on the sign-in screen and enter it to see
                whether your application was approved. Don't share it: anyone with the code can see the status.

                If you did not apply, you can ignore this email.
                """.formatted(name(restaurant), restaurant.getName(), statusCode);
        send(restaurant, "We received your restaurant application", body);
    }

    /** "Your application was not approved", with the reason the reviewer gave. */
    public void rejected(Restaurant restaurant) {
        String body = """
                Hello %s,

                We reviewed your application to open %s on the POS and could not approve it.

                Reason: %s

                You are welcome to apply again once this is sorted out.
                """.formatted(name(restaurant), restaurant.getName(), restaurant.getRejectionReason());
        send(restaurant, "Your restaurant application", body);
    }

    private void send(Restaurant restaurant, String subject, String body) {
        String to = restaurant.getPendingOwnerEmail();
        if (to == null) {
            return;
        }
        outbox.enqueue(new EmailOutboxService.OutgoingEmail(
                EmailKind.RESTAURANT_APPLICATION, to, subject, body, null, restaurant.getId(), null));
    }

    private static String name(Restaurant restaurant) {
        String first = NormalizationUtils.normalize(restaurant.getPendingOwnerFirstName());
        return first == null ? "there" : first;
    }
}
