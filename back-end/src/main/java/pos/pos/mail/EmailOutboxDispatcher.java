package pos.pos.mail;

import jakarta.mail.SendFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pos.pos.config.properties.AppMailProperties;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Sends what's waiting in the email outbox. Each email is claimed for a short lease (so two servers never send the
 * same one, and one that died mid-send is picked up again), sent outside any transaction, then marked sent, or
 * retried later with growing waits, or given up.
 */
@Component
public class EmailOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private static final List<EmailOutboxStatus> DUE = List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.SENDING);
    private static final long MAX_WAIT_MINUTES = 120;

    private final EmailOutboxRepository repository;
    private final JavaMailSender mailSender;
    private final AppMailProperties mailProperties;
    private final EmailOutboxProperties properties;
    private final TransactionTemplate transaction;

    public EmailOutboxDispatcher(
            EmailOutboxRepository repository,
            JavaMailSender mailSender,
            AppMailProperties mailProperties,
            EmailOutboxProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        // Also called right after another transaction commits, so always in a fresh one.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Claimed(UUID id, EmailKind kind, String to, String subject, String body, int attempts, OffsetDateTime sendBefore) {
        static Claimed of(EmailOutboxMessage m) {
            return new Claimed(m.getId(), m.getKind(), m.getRecipient(), m.getSubject(), m.getBody(), m.getAttempts(), m.getSendBefore());
        }
    }

    @Scheduled(scheduler = "emailOutboxScheduler", fixedDelayString = "${app.mail.outbox.poll-ms:5000}",
            initialDelayString = "${app.mail.outbox.initial-delay-ms:10000}")
    public void dispatchDue() {
        // A fixed number of rounds, so a flood of emails can't keep this thread busy forever.
        for (int round = 0; round < 10; round++) {
            List<Claimed> batch = claim();
            batch.forEach(this::deliver);
            if (batch.size() < properties.getBatchSize()) return;
        }
    }

    /**
     * Sends one email right away (called once the change that queued it has committed). If the mail server is down it
     * stays queued for the regular retries; this never throws.
     */
    public void sendNow(UUID id) {
        try {
            Claimed claimed = transaction.execute(status -> {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                if (repository.claim(id, now, now.plus(properties.getSendingLease())) == 0) return null;
                return repository.findById(id).map(Claimed::of).orElse(null);
            });
            if (claimed != null) deliver(claimed);
        } catch (RuntimeException failure) {
            log.warn("Couldn't send email {} right away; the regular check will: {}", id, failure.getMessage());
        }
    }

    @Scheduled(scheduler = "emailOutboxScheduler", cron = "0 17 * * * *")
    public void removeOld() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getRetention());
        transaction.executeWithoutResult(status -> repository.deleteFinishedBefore(cutoff));
    }

    private List<Claimed> claim() {
        List<Claimed> claimed = transaction.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<EmailOutboxMessage> due = repository.findDueForUpdate(DUE, now, PageRequest.of(0, properties.getBatchSize()));
            for (EmailOutboxMessage message : due) {
                message.setStatus(EmailOutboxStatus.SENDING);
                message.setAttempts(message.getAttempts() + 1);
                message.setNextAttemptAt(now.plus(properties.getSendingLease()));
            }
            repository.saveAll(due);
            return due.stream().map(Claimed::of).toList();
        });
        return claimed == null ? List.of() : claimed;
    }

    private void deliver(Claimed email) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (email.body() == null) {
            finish(email.id(), EmailOutboxStatus.FAILED, "The email's text is missing", null);
            return;
        }
        if (email.sendBefore() != null && now.isAfter(email.sendBefore())) {
            finish(email.id(), EmailOutboxStatus.FAILED, "The link in this email expired before it could be sent", null);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getFrom());
            message.setTo(email.to());
            message.setSubject(email.subject());
            message.setText(email.body());
            mailSender.send(message);
            finish(email.id(), EmailOutboxStatus.SENT, null, null);
        } catch (RuntimeException failure) {
            String reason = describe(failure);
            if (isPermanent(failure) || email.attempts() >= properties.getMaxAttempts()) {
                log.warn("Gave up on email {} ({}) after {} tries: {}", email.id(), email.kind(), email.attempts(), reason);
                finish(email.id(), EmailOutboxStatus.FAILED, reason, null);
            } else {
                OffsetDateTime retryAt = now.plus(waitAfter(email.attempts()));
                log.warn("Email {} ({}) failed on try {} of {}, trying again at {}: {}",
                        email.id(), email.kind(), email.attempts(), properties.getMaxAttempts(), retryAt, reason);
                finish(email.id(), EmailOutboxStatus.PENDING, reason, retryAt);
            }
        }
    }

    private void finish(UUID id, EmailOutboxStatus status, String error, OffsetDateTime retryAt) {
        transaction.executeWithoutResult(tx -> repository.findById(id).ifPresent(message -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            message.setStatus(status);
            message.setLastError(error);
            if (status == EmailOutboxStatus.SENT) message.setSentAt(now);
            if (status == EmailOutboxStatus.PENDING) {
                message.setNextAttemptAt(retryAt);
            } else {
                // Finished either way: the one-time link inside isn't kept.
                message.setBody(null);
                message.setNextAttemptAt(now);
            }
            repository.save(message);
        }));
    }

    // 1, 3, 9, 27, 81 minutes, then every 2 hours.
    static Duration waitAfter(int attempts) {
        long minutes = 1;
        for (int i = 1; i < attempts && minutes < MAX_WAIT_MINUTES; i++) minutes *= 3;
        return Duration.ofMinutes(Math.min(minutes, MAX_WAIT_MINUTES));
    }

    // Trying again won't help: the message is malformed or the server refused the address.
    private static boolean isPermanent(RuntimeException failure) {
        if (failure instanceof MailParseException || failure instanceof MailPreparationException) return true;
        if (failure instanceof MailSendException send) {
            return send.getFailedMessages().values().stream().anyMatch(cause ->
                    cause instanceof SendFailedException refused
                            && refused.getInvalidAddresses() != null && refused.getInvalidAddresses().length > 0);
        }
        return false;
    }

    private static String describe(RuntimeException failure) {
        String reason = failure instanceof MailException
                ? failure.getMessage()
                : failure.getClass().getSimpleName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        if (reason == null || reason.isBlank()) reason = "The mail server didn't accept the email";
        reason = reason.replaceAll("\\s+", " ").trim();
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }
}
