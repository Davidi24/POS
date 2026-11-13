package pos.pos.mail;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Account emails are saved in the outbox inside the caller's transaction: if the change rolls back, no email goes out.
 * Once it commits, the email is sent right away; if the mail server is down, it waits and is retried instead of undoing
 * the change.
 */
@Service
public class EmailOutboxService {

    // Only the newest of these is worth sending: each one replaces the link in the one before.
    private static final Set<EmailKind> REPLACEABLE = EnumSet.of(EmailKind.OWNER_INVITE, EmailKind.PASSWORD_RESET, EmailKind.EMAIL_VERIFICATION);

    private final EmailOutboxRepository repository;
    private final EmailOutboxDispatcher dispatcher;

    public EmailOutboxService(EmailOutboxRepository repository, EmailOutboxDispatcher dispatcher) {
        this.repository = repository;
        this.dispatcher = dispatcher;
    }

    public record OutgoingEmail(
            EmailKind kind,
            String to,
            String subject,
            String body,
            UUID userId,
            UUID restaurantId,
            OffsetDateTime sendBefore
    ) {
    }

    public record Delivery(
            EmailOutboxStatus status,
            int attempts,
            OffsetDateTime queuedAt,
            OffsetDateTime sentAt,
            OffsetDateTime nextAttemptAt,
            String lastError
    ) {
    }

    @Transactional
    public void enqueue(OutgoingEmail email) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (email.userId() != null && REPLACEABLE.contains(email.kind())) {
            repository.cancelWaiting(email.userId(), email.kind(), now);
        }
        EmailOutboxMessage saved = repository.save(EmailOutboxMessage.builder()
                .kind(email.kind())
                .recipient(email.to())
                .subject(email.subject())
                .body(email.body())
                .status(EmailOutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .sendBefore(email.sendBefore())
                .userId(email.userId())
                .restaurantId(email.restaurantId())
                .build());
        sendAfterCommit(saved.getId());
    }

    /** Drops this person's account emails that haven't gone out yet (their links no longer work), with [reason]. */
    @Transactional
    public int cancelAccountEmails(UUID userId, String reason) {
        return repository.cancelWaitingKinds(userId,
                EnumSet.of(EmailKind.OWNER_INVITE, EmailKind.PASSWORD_RESET, EmailKind.EMAIL_VERIFICATION),
                OffsetDateTime.now(ZoneOffset.UTC), reason);
    }

    /** The latest email of this kind to this person, and how its delivery went. */
    @Transactional(readOnly = true)
    public Optional<Delivery> latest(UUID userId, EmailKind kind) {
        return repository.findTopByUserIdAndKindOrderByCreatedAtDesc(userId, kind)
                .map(message -> new Delivery(
                        message.getStatus(),
                        message.getAttempts(),
                        message.getCreatedAt(),
                        message.getSentAt(),
                        message.getStatus() == EmailOutboxStatus.PENDING ? message.getNextAttemptAt() : null,
                        message.getLastError()
                ));
    }

    // Right after the change commits, rather than at the next regular check.
    private void sendAfterCommit(UUID id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.sendNow(id);
            }
        });
    }
}
