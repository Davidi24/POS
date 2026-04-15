package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ResendVerificationRequest;
import pos.pos.auth.dto.VerifyEmailRequest;
import pos.pos.auth.entity.AuthEmailVerificationToken;
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

        authEmailVerificationTokenRepository.deleteByUserId(user.getId());

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
                        frontendProperties.getBaseUrl(),
                        emailVerificationProperties.getVerifyPath(),
                        issuedToken.rawToken()
                ),
                emailVerificationProperties.getTokenTtl()
        );
    }

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
}
