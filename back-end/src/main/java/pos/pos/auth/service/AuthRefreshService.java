package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.AuthTokensResponse;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthRefreshService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final String TOKEN_TYPE = "Bearer";

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final RefreshTokenSecurityService refreshTokenSecurityService;

    @Transactional
    public AuthTokensResponse refresh(String refreshToken, ClientInfo clientInfo) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClientInfo normalizedClientInfo = normalizeClientInfo(clientInfo);
        RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                refreshTokenSecurityService.validate(refreshToken);

        UserSession session = userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(validatedRefreshToken.tokenId())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));

        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            revokeSession(session, now, SessionRevocationReason.EXPIRED);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (!session.getUserId().equals(validatedRefreshToken.userId())) {
            revokeSession(session, now, SessionRevocationReason.TOKEN_USER_MISMATCH);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (!refreshTokenSecurityService.matchesHash(validatedRefreshToken, session.getRefreshTokenHash())) {
            revokeSession(session, now, SessionRevocationReason.REUSE_DETECTED);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        User user = userRepository.findActiveById(session.getUserId()).orElse(null);

        if (user == null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                || !user.isEmailVerified()) {
            revokeSession(session, now, SessionRevocationReason.USER_NOT_ALLOWED);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        List<String> roles = roleRepository.findActiveRoleCodesByUserId(user.getId());

        UUID newTokenId = UUID.randomUUID();
        String newAccessToken = jwtService.generateAccessToken(user.getId(), roles, newTokenId);
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), newTokenId);

        session.setTokenId(newTokenId);
        session.setRefreshTokenHash(refreshTokenSecurityService.hash(newRefreshToken));
        session.setLastUsedAt(now);
        session.setIpAddress(normalizedClientInfo != null ? normalizedClientInfo.ipAddress() : null);
        session.setUserAgent(normalizedClientInfo != null ? normalizedClientInfo.userAgent() : null);

        userSessionRepository.save(session);

        return AuthTokensResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtProperties.getAccessExpiration().getSeconds())
                .user(userMapper.toUserResponse(user, roles))
                .build();
    }

    private void revokeSession(UserSession session, OffsetDateTime now, SessionRevocationReason reason) {
        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(reason.name());
        userSessionRepository.save(session);
    }

    private ClientInfo normalizeClientInfo(ClientInfo clientInfo) {
        if (clientInfo == null) {
            return null;
        }

        return new ClientInfo(
                normalizeIpAddress(clientInfo.ipAddress()),
                normalizeUserAgent(clientInfo.userAgent())
        );
    }

    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        return ipAddress.trim();
    }

    private String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.trim();
    }
}
