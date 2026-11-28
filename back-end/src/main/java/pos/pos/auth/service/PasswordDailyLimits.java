package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pos.pos.auth.entity.AuthPasswordEvent;
import pos.pos.auth.enums.PasswordEventKind;
import pos.pos.auth.repository.AuthPasswordEventRepository;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.user.entity.User;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Daily password limits (user, 2026-11-17): a person changes their password while signed in at most 30 times a day
 * ("so I know, but it's suspicious"), and "Forgot password?" sends them at most 7 links a day. Past a limit they get
 * one email that day saying so. A day is the last 24 hours. Runs inside the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class PasswordDailyLimits {

    private static final Duration DAY = Duration.ofHours(24);

    private final AuthPasswordEventRepository events;
    private final AuthMailService authMailService;

    @Value("${app.security.password.max-changes-per-day:30}")
    private int maxChangesPerDay;

    @Value("${app.security.password.max-reset-links-per-day:7}")
    private int maxResetLinksPerDay;

    /**
     * Whether "Forgot password?" may send this person another link now. Past the limit it sends nothing, and the person
     * gets one email that day saying the limit was reached. The screen's answer stays the same either way.
     */
    public boolean maySendResetLink(User user, OffsetDateTime now) {
        if (count(user, PasswordEventKind.RESET_LINK_SENT, now) < maxResetLinksPerDay) {
            return true;
        }
        if (!toldToday(user, PasswordEventKind.RESET_LIMIT_EMAIL, now)) {
            authMailService.sendResetLinkLimitEmail(user, maxResetLinksPerDay);
            record(user, PasswordEventKind.RESET_LIMIT_EMAIL, now);
        }
        return false;
    }

    public void resetLinkSent(User user, OffsetDateTime now) {
        record(user, PasswordEventKind.RESET_LINK_SENT, now);
    }

    /**
     * Refuses a change past today's limit (429). The first refusal of the day emails the person, since that many
     * changes may mean someone else is in their account; callers must not roll that back.
     */
    public void checkChange(User user, OffsetDateTime now) {
        if (count(user, PasswordEventKind.CHANGED, now) < maxChangesPerDay) {
            return;
        }
        if (!toldToday(user, PasswordEventKind.CHANGE_LIMIT_EMAIL, now)) {
            authMailService.sendPasswordChangeLimitEmail(user, maxChangesPerDay);
            record(user, PasswordEventKind.CHANGE_LIMIT_EMAIL, now);
        }
        throw new TooManyRequestsException("Your password was changed " + maxChangesPerDay + " times today. Try again tomorrow.");
    }

    public void changed(User user, OffsetDateTime now) {
        record(user, PasswordEventKind.CHANGED, now);
    }

    private long count(User user, PasswordEventKind kind, OffsetDateTime now) {
        return events.countByUserIdAndKindAndHappenedAtAfter(user.getId(), kind, now.minus(DAY));
    }

    private boolean toldToday(User user, PasswordEventKind kind, OffsetDateTime now) {
        return events.existsByUserIdAndKindAndHappenedAtAfter(user.getId(), kind, now.minus(DAY));
    }

    private void record(User user, PasswordEventKind kind, OffsetDateTime now) {
        events.save(AuthPasswordEvent.builder().userId(user.getId()).kind(kind).happenedAt(now).build());
    }
}
