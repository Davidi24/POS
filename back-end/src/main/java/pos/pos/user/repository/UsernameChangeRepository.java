package pos.pos.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.user.entity.UsernameChange;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UsernameChangeRepository extends JpaRepository<UsernameChange, UUID> {

    /** A person's changes since [after], oldest first (the 3-in-30-days limit). */
    List<UsernameChange> findByUserIdAndChangedAtAfterOrderByChangedAtAsc(UUID userId, OffsetDateTime after);

    /** Someone else let go of [oldUsername] since [after]: it's still reserved. */
    boolean existsByOldUsernameAndChangedAtAfterAndUserIdNot(String oldUsername, OffsetDateTime after, UUID userId);

    /** The changes of these people since [after], for "was …" in the staff list. */
    List<UsernameChange> findByUserIdInAndChangedAtAfterOrderByChangedAtDesc(Collection<UUID> userIds, OffsetDateTime after);
}
