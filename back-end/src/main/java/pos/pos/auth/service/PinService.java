package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.entity.PinEvent;
import pos.pos.auth.repository.PinEventRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * PINs for the restaurant's connected tills (user, 2026-11-18): 5 digits, never typed by the person but generated (so
 * "taken" never tells anyone a colleague's PIN), never an easy one (11111, 12345), unique in the restaurant; 10
 * suggestions and 3 saves a day; a manager can reset it. Stored as a peppered hash of restaurant + PIN, so the server can
 * find the person by it at sign-in.
 */
@Service
@RequiredArgsConstructor
public class PinService {

    public static final int LENGTH = 5;
    private static final int SUGGESTIONS_PER_DAY = 10;
    private static final int SAVES_PER_DAY = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PinEventRepository pinEvents;
    private final AccountAudit accountAudit;

    @Value("${app.security.pin.pepper:${app.security.refresh-token.pepper}}")
    private String pepper;

    /** What Profile shows: whether there's a PIN, when it was set, and what's left today. */
    public record PinStatus(boolean hasPin, OffsetDateTime setAt, int suggestionsLeft, int changesLeft) {
    }

    public record PinSuggestion(String pin, int suggestionsLeft) {
    }

    @Transactional(readOnly = true)
    public PinStatus status(UUID userId) {
        User user = requireUser(userId);
        OffsetDateTime day = dayStart(userId, OffsetDateTime.now(ZoneOffset.UTC));
        OffsetDateTime setAt = user.getPinHash() == null ? null : pinEvents.findTopByUserIdAndKindInOrderByHappenedAtDesc(userId,
                List.of(PinEvent.Kind.SET)).map(PinEvent::getHappenedAt).orElse(null);
        return new PinStatus(user.getPinHash() != null && user.isPinEnabled(), setAt,
                Math.max(0, SUGGESTIONS_PER_DAY - (int) pinEvents.countByUserIdAndKindAndHappenedAtAfter(userId, PinEvent.Kind.SUGGESTED, day)),
                Math.max(0, SAVES_PER_DAY - (int) pinEvents.countByUserIdAndKindAndHappenedAtAfter(userId, PinEvent.Kind.SET, day)));
    }

    /** A new random PIN nobody in the restaurant has; at most 10 a day. */
    @Transactional
    public PinSuggestion suggest(UUID userId) {
        User user = requireStaff(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long shown = pinEvents.countByUserIdAndKindAndHappenedAtAfter(userId, PinEvent.Kind.SUGGESTED, dayStart(userId, now));
        if (shown >= SUGGESTIONS_PER_DAY) {
            throw new TooManyRequestsException("You've had 10 PIN suggestions today. Try again tomorrow.");
        }
        for (int i = 0; i < 200; i++) {
            String pin = String.format("%05d", RANDOM.nextInt(100_000));
            if (weak(pin)) continue;
            String hash = hash(user.getRestaurantId(), pin);
            if (userRepository.existsByRestaurantIdAndPinHashAndDeletedAtIsNull(user.getRestaurantId(), hash)) continue;
            pinEvents.save(PinEvent.builder().userId(userId).kind(PinEvent.Kind.SUGGESTED).pinHash(hash).happenedAt(now).build());
            return new PinSuggestion(pin, (int) (SUGGESTIONS_PER_DAY - shown - 1));
        }
        throw new AuthException("No PIN could be found right now. Try again.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Saves a PIN suggested to this person in the last 30 minutes; at most 3 saves a day. */
    @Transactional
    public PinStatus save(UUID userId, String pin) {
        User user = requireStaff(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (pin == null || !pin.matches("\\d{" + LENGTH + "}")) {
            throw new AuthException("Generate a PIN first.", HttpStatus.BAD_REQUEST);
        }
        String hash = hash(user.getRestaurantId(), pin);
        if (!pinEvents.existsByUserIdAndKindAndPinHashAndHappenedAtAfter(userId, PinEvent.Kind.SUGGESTED, hash, now.minusMinutes(30))) {
            throw new AuthException("Generate a PIN first.", HttpStatus.BAD_REQUEST);
        }
        if (pinEvents.countByUserIdAndKindAndHappenedAtAfter(userId, PinEvent.Kind.SET, dayStart(userId, now)) >= SAVES_PER_DAY) {
            throw new TooManyRequestsException("You can change your PIN 3 times a day. Try again tomorrow.");
        }
        if (!hash.equals(user.getPinHash()) && userRepository.existsByRestaurantIdAndPinHashAndDeletedAtIsNull(user.getRestaurantId(), hash)) {
            throw new AuthException("That PIN was just taken. Generate another one.", HttpStatus.CONFLICT);
        }
        user.setPinHash(hash);
        user.setPinEnabled(true);
        user.setPinAttempts(0);
        user.setPinLockedUntil(null);
        userRepository.saveAndFlush(user);
        pinEvents.save(PinEvent.builder().userId(userId).kind(PinEvent.Kind.SET).actorId(userId).happenedAt(now).build());
        audit(user, user, "PIN_CHANGED", displayName(user) + " chose a new PIN");
        return status(userId);
    }

    /** A manager takes someone's PIN away; they generate a new one in Profile. */
    @Transactional
    public void reset(UUID actorId, UUID userId) {
        User user = requireUser(userId);
        user.setPinHash(null);
        user.setPinEnabled(false);
        user.setPinAttempts(0);
        user.setPinLockedUntil(null);
        userRepository.saveAndFlush(user);
        pinEvents.save(PinEvent.builder().userId(userId).kind(PinEvent.Kind.RESET).actorId(actorId)
                .happenedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        User actor = userRepository.findById(actorId).orElse(null);
        audit(user, actor, "PIN_RESET", "Reset " + displayName(user) + "'s PIN");
    }

    /**
     * Where today's counts start: 24 hours back, or the last time a manager reset the PIN, whichever is later, so a person
     * who used up their tries and forgot the PIN can get a new one at once after the reset.
     */
    private OffsetDateTime dayStart(UUID userId, OffsetDateTime now) {
        OffsetDateTime day = now.minusHours(24);
        return pinEvents.findTopByUserIdAndKindInOrderByHappenedAtDesc(userId, List.of(PinEvent.Kind.RESET))
                .map(PinEvent::getHappenedAt).filter(day::isBefore).orElse(day);
    }

    /** The PIN's peppered hash within its restaurant (the same PIN in two restaurants gives two hashes). */
    public String hash(UUID restaurantId, String pin) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((restaurantId + ":" + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("PIN hashing failed", failure);
        }
    }

    /** 11111, 12345, 54321, 12121: too easy to guess. */
    static boolean weak(String pin) {
        boolean same = pin.chars().distinct().count() == 1;
        boolean up = true, down = true;
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) != pin.charAt(i - 1) + 1) up = false;
            if (pin.charAt(i) != pin.charAt(i - 1) - 1) down = false;
        }
        boolean twoDigits = pin.chars().distinct().count() <= 2;
        return same || up || down || twoDigits;
    }

    private void audit(User user, User actor, String action, String summary) {
        accountAudit.about(user, actor == null ? null : actor.getId(), action, summary);
    }

    private static String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }

    private User requireUser(UUID userId) {
        return userRepository.findActiveById(userId).orElseThrow(UserNotFoundException::new);
    }

    private User requireStaff(UUID userId) {
        User user = requireUser(userId);
        if (user.getRestaurantId() == null) {
            throw new AuthException("A PIN is for a restaurant's staff.", HttpStatus.BAD_REQUEST);
        }
        return user;
    }
}
