package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.AuthTokensResponse;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
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

    @Value("${app.security.refresh-token.pepper}")
    private String refreshTokenPepper;

    @Transactional
    public AuthTokensResponse refresh(String refreshToken, ClientInfo clientInfo) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClientInfo normalizedClientInfo = normalizeClientInfo(clientInfo);

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        String normalizedRefreshToken = refreshToken.trim();

        if (!jwtService.isValid(normalizedRefreshToken) || !jwtService.isRefreshToken(normalizedRefreshToken)) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        UUID tokenId = jwtService.extractTokenId(normalizedRefreshToken);
        UUID userId = jwtService.extractUserId(normalizedRefreshToken);

        UserSession session = userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));

        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            revokeSession(session, now, "EXPIRED");
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (!session.getUserId().equals(userId)) {
            revokeSession(session, now, "TOKEN_USER_MISMATCH");
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        String presentedTokenHash = hashRefreshToken(normalizedRefreshToken);

        if (!presentedTokenHash.equals(session.getRefreshTokenHash())) {
            revokeSession(session, now, "REUSE_DETECTED");
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));

        if (!user.isActive()
                || user.getDeletedAt() != null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                || !user.isEmailVerified()) {
            revokeSession(session, now, "USER_NOT_ALLOWED");
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        List<String> roles = roleRepository.findActiveRoleCodesByUserId(user.getId());

        UUID newTokenId = UUID.randomUUID();
        String newAccessToken = jwtService.generateAccessToken(user.getId(), roles, newTokenId);
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), newTokenId);

        session.setTokenId(newTokenId);
        session.setRefreshTokenHash(hashRefreshToken(newRefreshToken));
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

    private void revokeSession(UserSession session, OffsetDateTime now, String reason) {
        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(reason);
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

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((refreshToken + refreshTokenPepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }
}