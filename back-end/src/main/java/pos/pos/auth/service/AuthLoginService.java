package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.AuthenticationResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.enums.LoginFailureReason;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

//checked
@Service
@RequiredArgsConstructor
public class AuthLoginService {

    private static final Logger logger = LoggerFactory.getLogger(AuthLoginService.class);
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String TOO_MANY_ATTEMPTS_MESSAGE = "Too many login attempts. Try again later.";
    private static final String TOKEN_TYPE = "Bearer";

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOePaWxn96p36aH8uY7f9ZC2w5Q5f5e7a";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserSessionRepository userSessionRepository;
    private final AuthLoginAttemptRepository authLoginAttemptRepository;
    private final UserSessionMapper userSessionMapper;
    private final UserMapper userMapper;

    @Value("${app.security.login.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.security.login.lock-duration-minutes}")
    private long lockDurationMinutes;

    @Value("${app.security.login.max-attempts-per-ip}")
    private int maxAttemptsPerIp;

    @Value("${app.security.login.max-attempts-per-account:${app.security.login.max-failed-attempts}}")
    private int maxAttemptsPerAccount;

    @Value("${app.security.login.window-minutes}")
    private long windowMinutes;

    @Value("${app.security.session.max-active-sessions}")
    private int maxActiveSessions;

    private final RefreshTokenSecurityService refreshTokenSecurityService;

    @Transactional
    public AuthenticationResponse login(LoginRequest request, ClientInfo clientInfo) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String normalizedEmail = normalizeEmail(request.getEmail());

        ClientInfo normalizedClientInfo = normalizeClientInfo(clientInfo);
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElse(null);
        UUID userId = user != null ? user.getId() : null;

        checkIpRateLimit(userId, normalizedEmail, normalizedClientInfo, now);
        checkAccountRateLimit(userId, normalizedEmail, normalizedClientInfo, now);

        if (user == null) {
            // Always fails on purpose to prevent timing attacks by making response time the same even if the user does not exist
            passwordService.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            saveLoginAttempt(null, normalizedEmail, normalizedClientInfo, false, LoginFailureReason.INVALID_CREDENTIALS, now);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        validateUserCanLogin(user, now, normalizedEmail, normalizedClientInfo);

        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user, normalizedEmail, normalizedClientInfo, now);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        enforceSessionLimit(user.getId(), now);

        //here it means all the check have passed so it creates a successful login.
        List<String> roles = loadRoleCodes(user.getId());

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setLastLoginIp(normalizedClientInfo != null ? normalizedClientInfo.ipAddress() : null);
        userRepository.save(user);

        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(user.getId(), roles, tokenId);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), tokenId);

        userSessionRepository.save(
                userSessionMapper.toSession(
                        user.getId(),
                        tokenId,
                        "PASSWORD",
                        refreshTokenSecurityService.hash(refreshToken),
                        normalizedClientInfo
                )
        );

        saveLoginAttempt(user.getId(), normalizedEmail, normalizedClientInfo, true, null, now);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtProperties.getAccessExpiration().getSeconds())
                .user(userMapper.toUserResponse(user, roles))
                .build();
    }


    private List<String> loadRoleCodes(UUID userId) {
        return roleRepository.findActiveRoleCodesByUserId(userId);
    }


    /**
     * Ensures the user does not exceed the maximum number of active sessions.
     * - Counts current active (not revoked and not expired) sessions for the user.
     * - If the limit is reached, revokes the oldest active session.
     * - Logs the action if a session was successfully revoked.
     */
    private void enforceSessionLimit(UUID userId, OffsetDateTime now) {
        long activeSessions = userSessionRepository
                .countByUserIdAndRevokedFalseAndExpiresAtAfter(userId, now);

        if (activeSessions >= maxActiveSessions) {
            int revokedSessions = userSessionRepository.revokeOldestSession(
                    userId,
                    now,
                    SessionRevocationReason.SESSION_LIMIT.name()
            );
            if (revokedSessions > 0) {
                logger.info(
                        "Revoked oldest active session for user {} because the session limit of {} was reached",
                        userId,
                        maxActiveSessions
                );
            }
        }
    }


    /**
     * Handles a failed login attempt when email or password does nto match.
     * - Increments the user's failed login attempts count.
     * - If the limit is reached, locks the account for a defined time.
     * - Saves the updated user state.
     */
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
        saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, false, LoginFailureReason.INVALID_CREDENTIALS, now);
    }


    /**
     * Validates if the user is allowed to log in.
     * - Blocks login if the account is inactive, soft-deleted, or not in ACTIVE status.
     * - Blocks login if the email is not verified.
     * - Blocks login if the account is currently locked (lockedUntil in the future).
     * - In each case, the attempt is saved with the corresponding failure reason.
     */
    private void validateUserCanLogin(
            User user,
            OffsetDateTime now,
            String normalizedEmail,
            ClientInfo clientInfo
    ) {
        if (!Boolean.TRUE.equals(user.isActive())
                || user.getDeletedAt() != null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, false, LoginFailureReason.ACCOUNT_INACTIVE, now);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (!Boolean.TRUE.equals(user.isEmailVerified())) {
            saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, false, LoginFailureReason.EMAIL_NOT_VERIFIED, now);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            saveLoginAttempt(user.getId(), normalizedEmail, clientInfo, false, LoginFailureReason.ACCOUNT_LOCKED, now);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
    }


    /**
     * Saves a login attempt to the database.
     * - Creates a new login attempt record with user ID, email, IP, and user agent.
     * - Marks whether the attempt was successful or failed.
     * - Stores the reason if the login failed.
     * - Records the exact time of the attempt.
     * - Persists the record using the repository.
     */
    private void saveLoginAttempt(
            UUID userId,
            String email,
            ClientInfo clientInfo,
            boolean success,
            LoginFailureReason failureReason,
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

    /**
     * Checks rate limiting based on the account (email).
     * - If email is missing, skip the check.
     * - Defines a time window (last X minutes from now).
     * - Counts failed login attempts for this email within that time window.
     * - If the number exceeds the allowed limit, saves the attempt and blocks the login.
     */
    private void checkAccountRateLimit(
            UUID userId,
            String normalizedEmail,
            ClientInfo clientInfo,
            OffsetDateTime now
    ) {
        if (normalizedEmail == null) {
            return;
        }

        OffsetDateTime windowStart = now.minusMinutes(windowMinutes);
        long attempts = authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(
                normalizedEmail,
                windowStart
        );

        if (attempts >= maxAttemptsPerAccount) {
            saveLoginAttempt(userId, normalizedEmail, clientInfo, false, LoginFailureReason.ACCOUNT_RATE_LIMITED, now);
            throw new InvalidCredentialsException(TOO_MANY_ATTEMPTS_MESSAGE);
        }
    }

    /**
     * Checks if an IP address is making too many login attempts.
     * - Gets the IP address; if none, skip the check.
     * - Defines a "time window" (a recent period, e.g., last X minutes from now).
     * - Counts how many login attempts were made from this IP within this time window.
     * - If the number is too high, saves the attempt and blocks the login.
     */
    private void checkIpRateLimit(UUID userId, String normalizedEmail, ClientInfo clientInfo, OffsetDateTime now) {
        String ip = clientInfo != null ? clientInfo.ipAddress() : null;
        if (ip == null) {
            return;
        }

        OffsetDateTime windowStart = now.minusMinutes(windowMinutes);
        long attempts = authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(ip, windowStart);

        if (attempts >= maxAttemptsPerIp) {
            saveLoginAttempt(userId, normalizedEmail, clientInfo, false, LoginFailureReason.IP_RATE_LIMITED, now);
            throw new InvalidCredentialsException(TOO_MANY_ATTEMPTS_MESSAGE);
        }
    }




    private String normalizeEmail(String email) {
        return NormalizationUtils.normalizeLower(email);
    }

    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return null;
        return ipAddress.trim();
    }

    private String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        return userAgent.trim();
    }

    private ClientInfo normalizeClientInfo(ClientInfo clientInfo) {
        if (clientInfo == null) return null;

        return new ClientInfo(
                normalizeIpAddress(clientInfo.ipAddress()),
                normalizeUserAgent(clientInfo.userAgent())
        );
    }
}
