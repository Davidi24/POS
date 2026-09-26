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

    // Free time kept between two bookings on the same table; none when no rule is set.
    public Duration buffer(Branch branch, OffsetDateTime at) {
        return activeRule(branch, at)
                .map(rule -> Duration.ofMinutes(Math.max(0, rule.getBufferMinutes())))
                .orElse(Duration.ZERO);
    }
}
