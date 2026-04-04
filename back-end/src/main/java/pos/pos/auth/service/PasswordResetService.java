package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import pos.pos.auth.dto.ForgotPasswordRequest;
import pos.pos.auth.dto.ResetPasswordRequest;
import pos.pos.auth.entity.AuthPasswordResetToken;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.config.properties.FrontendProperties;
import pos.pos.config.properties.PasswordResetProperties;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final AuthPasswordResetTokenRepository authPasswordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordService passwordService;
    private final OpaqueTokenService opaqueTokenService;
    private final AuthMailService authMailService;
    private final PasswordResetProperties passwordResetProperties;
    private final FrontendProperties frontendProperties;

    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String normalizedEmail = NormalizationUtils.normalizeLower(request.getEmail());

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElse(null);
        if (user == null || !user.isActive() || !user.isEmailVerified()) {
            return;
        }

        OffsetDateTime cooldownCutoff = now.minus(passwordResetProperties.getRequestCooldown());
        if (authPasswordResetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), cooldownCutoff)) {
            return;
        }

        authPasswordResetTokenRepository.deleteByUserId(user.getId());

        String rawToken = opaqueTokenService.generate();
        String tokenHash = opaqueTokenService.hash(rawToken, passwordResetProperties.getTokenPepper());

        authPasswordResetTokenRepository.save(
                AuthPasswordResetToken.builder()
                        .userId(user.getId())
                        .tokenHash(tokenHash)
                        .expiresAt(now.plus(passwordResetProperties.getTokenTtl()))
                        .build()
        );

        authMailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getFirstName(),
                passwordResetProperties.getSubject(),
                buildResetUrl(rawToken),
                passwordResetProperties.getTokenTtl()
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String tokenHash = opaqueTokenService.hash(request.getToken(), passwordResetProperties.getTokenPepper());

        AuthPasswordResetToken resetToken = authPasswordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(tokenHash, now)
                .orElseThrow(InvalidTokenException::new);

        User user = userRepository.findById(resetToken.getUserId())
                .filter(foundUser -> foundUser.getDeletedAt() == null && foundUser.isActive())
                .orElseThrow(InvalidTokenException::new);

        user.setPasswordHash(passwordService.hash(request.getNewPassword()));
        user.setPasswordUpdatedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsedAt(now);
        authPasswordResetTokenRepository.save(resetToken);

        userSessionRepository.revokeAllActiveSessionsByUserId(
                user.getId(),
                now,
                SessionRevocationReason.PASSWORD_RESET.name()
        );
    }

    private String buildResetUrl(String rawToken) {
        return UriComponentsBuilder.fromUriString(frontendProperties.getBaseUrl())
                .path(passwordResetProperties.getResetPath())
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }
}
