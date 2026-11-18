package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ResendVerificationRequest;
import pos.pos.auth.dto.VerifyEmailRequest;
import pos.pos.auth.entity.AuthEmailVerificationToken;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.config.properties.EmailVerificationProperties;
import pos.pos.config.properties.FrontendProperties;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.FrontendUrlUtils;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// checked
// tested
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;
    private final OpaqueTokenService opaqueTokenService;
    private final AuthMailService authMailService;
    private final AccountAudit accountAudit;
    private final EmailVerificationProperties emailVerificationProperties;
    private final FrontendProperties frontendProperties;

    // The server's own address for emailed links (as for password links); links go to its page /public/account/email.
    private pos.pos.config.properties.PasswordResetProperties linkSettings;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLinkSettings(pos.pos.config.properties.PasswordResetProperties linkSettings) { this.linkSettings = linkSettings; }

    /** What a confirmation link is (for its page): still works, used, expired or unknown; the new address for an email change. */
    public record EmailLinkView(String status, String email, String newEmail, String firstName) {
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String normalizedEmail = NormalizationUtils.normalizeLower(request.getEmail());

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElse(null);
        if (user == null || !user.isActive() || user.isEmailVerified()) {
            return;
        }

        OffsetDateTime cooldownCutoff = now.minus(emailVerificationProperties.getRequestCooldown());
        if (authEmailVerificationTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), cooldownCutoff)) {
            return;
        }

        // delete all tokes for this user so they can not be used anymore cos a new one will be created
        authEmailVerificationTokenRepository.deleteByUserId(user.getId());
        sendVerificationEmail(user, request.getClientTarget(), now);
    }


    //  1. Hashes the token from the request
    //  2. Looks it up in the DB — must exist, usedAt is null, and expiresAt is in the future
    //  3. If not found → throws InvalidTokenException
    //  4. Finds the user and checks they are active and not deleted
    //  5. Marks email as verified on the user
    //  6. Marks the token as used so it can't be reused
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String tokenHash = opaqueTokenService.hash(request.getToken(), emailVerificationProperties.getTokenPepper());

        AuthEmailVerificationToken verificationToken = authEmailVerificationTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(tokenHash, now)
                .orElseThrow(InvalidTokenException::new);

        User user = userRepository.findById(verificationToken.getUserId())
                .filter(foundUser -> foundUser.getDeletedAt() == null && foundUser.isActive())
                .orElseThrow(InvalidTokenException::new);

        if (verificationToken.getNewEmail() != null) {
            // An email change: the new address replaces the old one now, confirmed by this link.
            String newEmail = verificationToken.getNewEmail();
            userRepository.findByEmailAndDeletedAtIsNull(newEmail).filter(other -> !other.getId().equals(user.getId())).ifPresent(other -> {
                throw new pos.pos.exception.auth.AuthException("That email is now used by another account.", org.springframework.http.HttpStatus.CONFLICT);
            });
            String oldEmail = user.getEmail();
            user.setEmail(newEmail);
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
            accountAudit.about(user, user.getId(), "EMAIL_CHANGED", AccountAudit.nameOf(user) + " changed their email from " + oldEmail + " to " + newEmail);
        } else if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
            accountAudit.about(user, user.getId(), "EMAIL_CONFIRMED", AccountAudit.nameOf(user) + " confirmed their email " + user.getEmail());
        }

        verificationToken.setUsedAt(now);
        authEmailVerificationTokenRepository.save(verificationToken);
    }

    /** The link's state for its page, without using it (link scanners open links; only the page's button confirms). */
    @Transactional(readOnly = true)
    public EmailLinkView checkLink(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return new EmailLinkView("INVALID", null, null, null);
        AuthEmailVerificationToken token = authEmailVerificationTokenRepository
                .findByTokenHash(opaqueTokenService.hash(rawToken.trim(), emailVerificationProperties.getTokenPepper())).orElse(null);
        if (token == null) return new EmailLinkView("INVALID", null, null, null);
        User user = userRepository.findById(token.getUserId()).filter(u -> u.getDeletedAt() == null && u.isActive()).orElse(null);
        if (user == null) return new EmailLinkView("INVALID", null, null, null);
        String status = token.getUsedAt() != null ? "USED" : !token.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)) ? "EXPIRED" : "VALID";
        return new EmailLinkView(status, user.getEmail(), token.getNewEmail(), user.getFirstName());
    }

    /**
     * Profile → change email (user, 2026-11-18): a link to [newEmail] that makes it the account's email once used, and a
     * notice to the current address. Earlier waiting links are replaced. The caller checked the password and the address.
     */
    public void requestEmailChange(User user, String newEmail, OffsetDateTime now) {
        authEmailVerificationTokenRepository.deleteByUserId(user.getId());
        OpaqueTokenService.IssuedToken issuedToken = opaqueTokenService.issue(emailVerificationProperties.getTokenPepper());
        authEmailVerificationTokenRepository.save(AuthEmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(issuedToken.tokenHash())
                .newEmail(newEmail)
                .expiresAt(now.plus(emailVerificationProperties.getTokenTtl()))
                .build());
        authMailService.sendEmailChangeConfirmation(user, newEmail, linkFor(issuedToken.rawToken(), null), emailVerificationProperties.getTokenTtl());
        authMailService.sendEmailChangeNotice(user, masked(newEmail));
        accountAudit.about(user, user.getId(), "EMAIL_CHANGE_ASKED", AccountAudit.nameOf(user) + " asked to change their email to " + newEmail
                + " (it changes once they open the link)");
    }

    /** "an***@mail.com": enough for the owner of the old address to recognise it, not the whole address. */
    static String masked(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "a new address";
        String name = email.substring(0, at);
        return name.substring(0, Math.min(2, name.length())) + "***" + email.substring(at);
    }

    /** The server's page for the link (PUBLIC_BASE_URL / PASSWORD_LINK_BASE_URL), else the configured front end's path. */
    private String linkFor(String rawToken, ClientLinkTarget clientTarget) {
        String base = linkSettings == null ? null : linkSettings.getLinkBaseUrl();
        if (base != null && !base.isBlank()) {
            return FrontendUrlUtils.buildTokenUrl(base.trim(), "/public/account/email", rawToken);
        }
        return FrontendUrlUtils.buildTokenUrl(frontendProperties.resolveBaseUrl(clientTarget), emailVerificationProperties.getVerifyPath(), rawToken);
    }

    public void issueVerificationForUser(User user, ClientLinkTarget clientTarget) {
        if (user == null || !user.isActive() || user.isEmailVerified()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // delete all tokens for this suer cos he is already verified or user does not exist or is not active
        authEmailVerificationTokenRepository.deleteByUserId(user.getId());
        sendVerificationEmail(user, clientTarget, now);
    }


    // it saves in the db the email that was sends and then sends the email with all severeness info
    private void sendVerificationEmail(User user, ClientLinkTarget clientTarget, OffsetDateTime now) {
        OpaqueTokenService.IssuedToken issuedToken = opaqueTokenService.issue(emailVerificationProperties.getTokenPepper());

        authEmailVerificationTokenRepository.save(
                AuthEmailVerificationToken.builder()
                        .userId(user.getId())
                        .tokenHash(issuedToken.tokenHash())
                        .expiresAt(now.plus(emailVerificationProperties.getTokenTtl()))
                        .build()
        );

        authMailService.sendEmailVerificationEmail(
                user.getEmail(),
                user.getFirstName(),
                emailVerificationProperties.getSubject(),
                linkFor(issuedToken.rawToken(), clientTarget),
                emailVerificationProperties.getTokenTtl()
        );
    }
}
