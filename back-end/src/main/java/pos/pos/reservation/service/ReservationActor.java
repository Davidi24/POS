package pos.pos.reservation.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import pos.pos.security.rbac.AppPermission;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Who is changing a booking, and which of the reservation rules they may override.
// The system (no-show job, a table being cleared) follows the rules without the staff time limits.
public record ReservationActor(UUID id, boolean canApprove, boolean canCorrect, boolean system) {

    public static ReservationActor of(Authentication authentication, UUID id) {
        Set<String> authorities = authentication == null ? Set.of() : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new ReservationActor(
                id,
                authorities.contains(AppPermission.RESERVATION_APPROVE.name()),
                authorities.contains(AppPermission.RESERVATION_CORRECT.name()),
                false
        );
    }

    public static ReservationActor system(UUID id) {
        return new ReservationActor(id, true, true, true);
    }
}
