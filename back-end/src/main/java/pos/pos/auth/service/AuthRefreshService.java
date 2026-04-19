package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.AuthenticationResponse;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.auth.mapper.CurrentUserMapper;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.RefreshRateLimiter;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoNormalizer;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// checked
// tested
@Service
@RequiredArgsConstructor
public class AuthRefreshService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final String TOKEN_TYPE = "Bearer";

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final CurrentUserMapper currentUserMapper;
    private final RefreshTokenSecurityService refreshTokenSecurityService;
    private final RefreshRateLimiter refreshRateLimiter;

    @Transactional
    public AuthenticationResponse refresh(String refreshToken, ClientInfo clientInfo) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClientInfo normalizedClientInfo = ClientInfoNormalizer.normalize(clientInfo);
        refreshRateLimiter.check(normalizedClientInfo != null ? normalizedClientInfo.ipAddress() : null);
        RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                refreshTokenSecurityService.validate(refreshToken);
        refreshRateLimiter.checkByTokenId(validatedRefreshToken.tokenId());

        // Fetches the active session by tokenId and locks the database row, orUpdate => (SELECT FOR UPDATE)
        // to prevent concurrent access or reuse of the same refresh token
        UserSession session = userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(validatedRefreshToken.tokenId())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));

        // if session is expired it revokes it
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            revokeSession(session, now, SessionRevocationReason.EXPIRED);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        // Ensures that the session userID is same as the user who send the request, so userID that came from refresh token
        // So the session belongs to this user not another user
        if (!session.getUserId().equals(validatedRefreshToken.userId())) {
            revokeSession(session, now, SessionRevocationReason.TOKEN_USER_MISMATCH);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        // Verifies the provided refresh token matches the stored hash to detect reuse or tampering (unauthorized modification or falsification of a token)
        if (!refreshTokenSecurityService.matchesHash(validatedRefreshToken, session.getRefreshTokenHash())) {
            revokeSession(session, now, SessionRevocationReason.REUSE_DETECTED);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        // after all checks now it checks if user exist
        User user = userRepository.findActiveById(session.getUserId()).orElse(null);

        if (user == null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                || !user.isEmailVerified()) {
            revokeSession(session, now, SessionRevocationReason.USER_NOT_ALLOWED);
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        List<Role> activeRoles = roleRepository.findActiveRolesByUserId(user.getId());
        List<String> roleCodes = activeRoles.stream().map(Role::getCode).toList();
        List<String> permissionCodes = loadPermissionCodes(activeRoles);

        UUID newTokenId = UUID.randomUUID();
        String newAccessToken = jwtService.generateAccessToken(user.getId(), roleCodes, newTokenId);
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), newTokenId);

        session.setTokenId(newTokenId);
        session.setRefreshTokenHash(refreshTokenSecurityService.hash(newRefreshToken));
        session.setLastUsedAt(now);
        session.setIpAddress(normalizedClientInfo != null ? normalizedClientInfo.ipAddress() : null);
        session.setUserAgent(normalizedClientInfo != null ? normalizedClientInfo.userAgent() : null);

        userSessionRepository.save(session);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtProperties.getAccessExpiration().getSeconds())
                .user(currentUserMapper.toCurrentUserResponse(user, roleCodes, permissionCodes))
                .build();
    }

    private List<String> loadPermissionCodes(List<Role> activeRoles) {
        List<UUID> roleIds = activeRoles.stream()
                .map(Role::getId)
                .toList();

        if (roleIds.isEmpty()) {
            return List.of();
        }

        return permissionRepository.findCodesByRoleIds(roleIds).stream()
                .distinct()
                .toList();
    }

    //revoke the session so it can be used anymore
    private void revokeSession(UserSession session, OffsetDateTime now, SessionRevocationReason reason) {
        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(reason.name());
        userSessionRepository.save(session);
    }
}
