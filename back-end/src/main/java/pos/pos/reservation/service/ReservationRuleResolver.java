package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.restaurant.entity.Branch;
import pos.pos.settings.entity.SettingsReservationRule;
import pos.pos.settings.repository.SettingsReservationRuleRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

// Finds the reservation rule that applies to a branch at a given moment.
// Staff bookings use it for defaults and the buffer; its limits are only advice for staff.
@Component
@RequiredArgsConstructor
public class ReservationRuleResolver {

    private final SettingsReservationRuleRepository settingsReservationRuleRepository;

    public Optional<SettingsReservationRule> activeRule(Branch branch, OffsetDateTime at) {
        if (branch == null || branch.getRestaurant() == null) {
            return Optional.empty();
        }
        return settingsReservationRuleRepository
                .findAllBySettings_Restaurant_IdOrderByPriorityAscCreatedAtAsc(branch.getRestaurant().getId())
                .stream()
                .filter(SettingsReservationRule::isActive)
                .filter(rule -> rule.getBranch() == null || Objects.equals(rule.getBranch().getId(), branch.getId()))
                .filter(rule -> rule.getEffectiveFrom() == null || !at.isBefore(rule.getEffectiveFrom()))
                .filter(rule -> rule.getEffectiveTo() == null || at.isBefore(rule.getEffectiveTo()))
                // Lowest priority number wins; a rule for this branch beats a restaurant-wide one.
                .min(Comparator.comparingInt(SettingsReservationRule::getPriority)
                        .thenComparingInt(rule -> rule.getBranch() == null ? 1 : 0));
    }

    // Agreed defaults while the restaurant hasn't saved a rule: 2 h bookings, 5 min cleaning.
    public static final int DEFAULT_DURATION_MINUTES = 120;
    public static final int DEFAULT_BUFFER_MINUTES = 5;

    // Cleaning time kept between two bookings on the same table.
    public Duration buffer(Branch branch, OffsetDateTime at) {
        return Duration.ofMinutes(activeRule(branch, at)
                .map(rule -> Math.max(0, rule.getBufferMinutes()))
                .orElse(DEFAULT_BUFFER_MINUTES));
    }

    public int durationMinutes(Branch branch, OffsetDateTime at) {
        return activeRule(branch, at)
                .map(SettingsReservationRule::getDefaultDurationMinutes)
                .orElse(DEFAULT_DURATION_MINUTES);
    }
}
