package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.AuthTokensResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

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
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserSessionRepository userSessionRepository;
    private final AuthLoginAttemptRepository authLoginAttemptRepository;
    private final UserSessionMapper userSessionMapper;
    private final UserMapper userMapper;

    @Value("${app.security.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.login.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    @Transactional
    public AuthTokensResponse login(LoginRequest request, ClientInfo clientInfo) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> {
                    saveLoginAttempt(null, normalizedEmail, clientInfo, false, "INVALID_CREDENTIALS", now);
                    return new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
                });

        validateUserCanLogin(user, now, normalizedEmail, clientInfo);

        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user, normalizedEmail, clientInfo, now);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        List<String> roles = loadRoleCodes(user.getId());

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setLastLoginIp(clientInfo.ipAddress());
        userRepository.save(user);

        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(user.getId(), roles, tokenId);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), tokenId);

        userSessionRepository.save(
                userSessionMapper.toSession(
                        user.getId(),
                        tokenId,
                        "PASSWORD",
                        extractDeviceName(clientInfo != null ? clientInfo.userAgent() : null),
                        sha256(refreshToken),
                        clientInfo
                )
        );

        saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, true, null, now);

        return AuthTokensResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtProperties.getAccessExpiration().getSeconds())
                .user(userMapper.toUserResponse(user, roles))
                .build();
    }

    private void validateUserCanLogin(
            User user,
            OffsetDateTime now,
            String normalizedEmail,
            ClientInfo clientInfo
    ) {
        if (!user.isActive() || user.getDeletedAt() != null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, false, "ACCOUNT_INACTIVE", now);
            throw new InvalidCredentialsException("User account is inactive");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, false, "ACCOUNT_LOCKED", now);
            throw new InvalidCredentialsException("User account is temporarily locked");
        }
    }

    private void handleFailedLogin(
            User user,
            String normalizedEmail,
            ClientInfo clientInfo,
            OffsetDateTime now
    ) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(now.plusMinutes(lockDurationMinutes));
        }

        userRepository.save(user);
        saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, false, "INVALID_CREDENTIALS", now);
    }

    private List<String> loadRoleCodes(UUID userId) {
        List<UUID> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();

        if (roleIds.isEmpty()) {
            return List.of();
        }

        return roleRepository.findByIdIn(roleIds).stream()
                .filter(Role::isActive)
                .map(Role::getCode)
                .toList();
    }

    private void saveLoginAttempt(
            UUID userId,
            String email,
            ClientInfo clientInfo,
            boolean success,
            String failureReason,
            OffsetDateTime attemptedAt
    ) {
        AuthLoginAttempt attempt = AuthLoginAttempt.builder()
                .userId(userId)
                .email(email)
                .ipAddress(clientInfo != null ? clientInfo.ipAddress() : null)
                .userAgent(clientInfo != null ? clientInfo.userAgent() : null)
                .success(success)
                .failureReason(failureReason)
                .attemptedAt(attemptedAt)
                .build();

        authLoginAttemptRepository.save(attempt);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String extractDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        String trimmed = userAgent.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }
}
