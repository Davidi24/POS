package pos.pos.mail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.config.properties.AppMailProperties;
import pos.pos.exception.auth.AuthException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * For whoever runs the server: check that the mail server really delivers (a test email sent right away, with the
 * server's answer), and see what's waiting in the outbox or has failed.
 */
@Tag(name = "Platform / Email")
@RestController
@RequestMapping("/platform/mail")
@RequiredArgsConstructor
public class MailAdminController {

    private final JavaMailSender mailSender;
    private final AppMailProperties mailProperties;
    private final EmailOutboxRepository repository;

    public record TestEmailRequest(
            @NotBlank @Email @Size(max = 320) String to
    ) {
    }

    public record TestEmailResponse(String to, String from, OffsetDateTime sentAt) {
    }

    public record FailedEmail(UUID id, EmailKind kind, String recipient, String subject, int attempts, String lastError, OffsetDateTime at) {
    }

    public record OutboxSummary(long waiting, long sending, long sent, long failed, long cancelled, List<FailedEmail> recentFailures) {
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Super Admin: send a test email straight to the mail server and report its answer")
    public TestEmailResponse sendTest(@Valid @RequestBody TestEmailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFrom());
        message.setTo(request.to().trim());
        message.setSubject("POS test email");
        message.setText("""
                Hello,

                This is a test email from POS. If you can read it, emails from the POS server reach this inbox.
                """);
        try {
            mailSender.send(message);
        } catch (MailException failure) {
            throw new AuthException("The mail server didn't accept the email: " + failure.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        return new TestEmailResponse(request.to().trim(), mailProperties.getFrom(), OffsetDateTime.now());
    }

    @GetMapping("/outbox")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Super Admin: emails waiting, sent and failed, with the latest failures")
    @Transactional(readOnly = true)
    public OutboxSummary outbox() {
        return new OutboxSummary(
                repository.countByStatus(EmailOutboxStatus.PENDING),
                repository.countByStatus(EmailOutboxStatus.SENDING),
                repository.countByStatus(EmailOutboxStatus.SENT),
                repository.countByStatus(EmailOutboxStatus.FAILED),
                repository.countByStatus(EmailOutboxStatus.CANCELLED),
                repository.findTop20ByStatusOrderByUpdatedAtDesc(EmailOutboxStatus.FAILED).stream()
                        .map(m -> new FailedEmail(m.getId(), m.getKind(), m.getRecipient(), m.getSubject(), m.getAttempts(),
                                m.getLastError(), m.getUpdatedAt()))
                        .toList()
        );
    }
}
