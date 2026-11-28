package pos.pos.auth.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.UUID;

/**
 * Account and sign-in lines in the restaurant's audit log (Admin Hub → Audit log; user, 2026-11-18: "save everything that is
 * done, even the okay sign-ins, so they know when a person signed in or out"). They are listed under the area
 * [AREA]; people without a restaurant (the Super Admin and Co-Super-Admins) write nothing, since there's no restaurant
 * log to put them in.
 */
@Component
@RequiredArgsConstructor
public class AccountAudit {

    /** The audit log's area for these lines ("Accounts and sign-ins" in the app). */
    public static final String AREA = "ACCOUNT";

    private final SettingsAuditService settingsAudit;
    private final UserRepository users;
    private final EntityManager entityManager;

    /** A line about [person]'s account, done by [actorId] (themselves, a manager, or null when nobody signed in did it). */
    public void about(User person, UUID actorId, String action, String message) {
        if (person == null || person.getRestaurantId() == null) return;
        inRestaurant(person.getRestaurantId(), person.getId(), actorId, action, message);
    }

    /** The same, finding the person by id; [message] gets their name. */
    public void about(UUID personId, UUID actorId, String action, java.util.function.Function<String, String> message) {
        if (personId == null) return;
        users.findById(personId).ifPresent(person -> about(person, actorId, action, message.apply(nameOf(person))));
    }

    /** A line in [restaurantId]'s log about [entityId] (a person or a till). */
    public void inRestaurant(UUID restaurantId, UUID entityId, UUID actorId, String action, String message) {
        if (restaurantId == null) return;
        String text = message == null ? action : message;
        settingsAudit.log(entityManager.getReference(Restaurant.class, restaurantId), null, AREA, entityId, action,
                text.length() > 500 ? text.substring(0, 497) + "..." : text, actorId);
    }

    /** "Mira Leka", else the username. */
    public static String nameOf(User person) {
        if (person == null) return "Someone";
        String name = ((person.getFirstName() == null ? "" : person.getFirstName()) + " " + (person.getLastName() == null ? "" : person.getLastName())).trim();
        return name.isEmpty() ? person.getUsername() : name;
    }

    /** What a device is called in a line: its name, or "an unknown device". */
    public static String deviceOf(String deviceName) {
        return StringUtils.hasText(deviceName) && !"UNKNOWN".equalsIgnoreCase(deviceName.trim()) ? deviceName.trim() : "an unknown device";
    }
}
