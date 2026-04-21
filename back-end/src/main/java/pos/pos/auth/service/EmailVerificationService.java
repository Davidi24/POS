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
    private final EmailVerificationProperties emailVerificationProperties;
    private final FrontendProperties frontendProperties;

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

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
        }

        verificationToken.setUsedAt(now);
        authEmailVerificationTokenRepository.save(verificationToken);
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
                FrontendUrlUtils.buildTokenUrl(
                        frontendProperties.resolveBaseUrl(clientTarget),
                        emailVerificationProperties.getVerifyPath(),
                        issuedToken.rawToken()
                ),
                emailVerificationProperties.getTokenTtl()
        );
    }
}
