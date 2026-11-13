package pos.pos.tables.specialcase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecialCaseActionRepository extends JpaRepository<SpecialCaseAction, UUID> {

    List<SpecialCaseAction> findTop50ByBranchIdAndHappenedAtAfterAndReturnedAtIsNullAndKindInOrderByHappenedAtDesc(
            UUID branchId, OffsetDateTime after, Collection<SpecialCaseAction.Kind> kinds);

    boolean existsByReservationIdAndKindIn(UUID reservationId, Collection<SpecialCaseAction.Kind> kinds);

    boolean existsByReservationIdAndReturnedAtIsNullAndKindIn(UUID reservationId, Collection<SpecialCaseAction.Kind> kinds);

    List<SpecialCaseAction> findAllByReservationIdInAndKindAndReturnedAtIsNull(Collection<UUID> reservationIds, SpecialCaseAction.Kind kind);

    Optional<SpecialCaseAction> findByIdAndBranchId(UUID id, UUID branchId);
}
