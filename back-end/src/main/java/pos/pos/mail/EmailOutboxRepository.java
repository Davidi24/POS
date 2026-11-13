package pos.pos.mail;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, UUID> {

    // SKIP LOCKED: two servers never pick the same email.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
        SELECT m FROM EmailOutboxMessage m
        WHERE m.status IN :statuses AND m.nextAttemptAt <= :now
        ORDER BY m.nextAttemptAt
    """)
    List<EmailOutboxMessage> findDueForUpdate(Collection<EmailOutboxStatus> statuses, OffsetDateTime now, Pageable page);

    Optional<EmailOutboxMessage> findTopByUserIdAndKindOrderByCreatedAtDesc(UUID userId, EmailKind kind);

    // Takes one waiting email for sending; 0 when another server (or the regular check) already has it.
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE EmailOutboxMessage m
        SET m.status = pos.pos.mail.EmailOutboxStatus.SENDING, m.attempts = m.attempts + 1,
            m.nextAttemptAt = :leaseUntil, m.updatedAt = :now
        WHERE m.id = :id AND m.status = pos.pos.mail.EmailOutboxStatus.PENDING
    """)
    int claim(UUID id, OffsetDateTime now, OffsetDateTime leaseUntil);

    long countByStatus(EmailOutboxStatus status);

    List<EmailOutboxMessage> findTop20ByStatusOrderByUpdatedAtDesc(EmailOutboxStatus status);

    // A newer email of the same kind replaces the ones still waiting.
    @Modifying
    @Query("""
        UPDATE EmailOutboxMessage m
        SET m.status = pos.pos.mail.EmailOutboxStatus.CANCELLED, m.body = null, m.updatedAt = :now,
            m.lastError = 'Replaced by a newer email'
        WHERE m.userId = :userId AND m.kind = :kind AND m.status = pos.pos.mail.EmailOutboxStatus.PENDING
    """)
    int cancelWaiting(UUID userId, EmailKind kind, OffsetDateTime now);

    @Modifying
    @Query("""
        UPDATE EmailOutboxMessage m
        SET m.status = pos.pos.mail.EmailOutboxStatus.CANCELLED, m.body = null, m.updatedAt = :now, m.lastError = :reason
        WHERE m.userId = :userId AND m.kind IN :kinds AND m.status = pos.pos.mail.EmailOutboxStatus.PENDING
    """)
    int cancelWaitingKinds(UUID userId, java.util.Collection<EmailKind> kinds, OffsetDateTime now, String reason);

    @Modifying
    @Query("""
        DELETE FROM EmailOutboxMessage m
        WHERE m.status IN (pos.pos.mail.EmailOutboxStatus.SENT, pos.pos.mail.EmailOutboxStatus.FAILED,
                           pos.pos.mail.EmailOutboxStatus.CANCELLED)
          AND m.updatedAt < :cutoff
    """)
    int deleteFinishedBefore(OffsetDateTime cutoff);
}
