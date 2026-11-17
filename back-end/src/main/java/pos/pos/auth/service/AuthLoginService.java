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
import pos.pos.auth.enums.SessionType;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.enums.LoginFailureReason;
import pos.pos.auth.mapper.CurrentUserMapper;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.LoginRefusedException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoNormalizer;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// checked
// tested
@Service
@RequiredArgsConstructor
public class AuthLoginService {

    private static final Logger logger = LoggerFactory.getLogger(AuthLoginService.class);
    private static final String INVALID_CREDENTIALS_MESSAGE = "Wrong username or password.";
    static final String RESTAURANT_CLOSED_MESSAGE = "This restaurant can't sign in right now. Ask the owner.";
    private static final String TOO_MANY_ATTEMPTS_MESSAGE = "Too many tries. Wait a few minutes and try again.";
    private static final String LOCKED_MESSAGE = "Too many wrong passwords.";
    private static final String DEVICE_PAUSED_MESSAGE = "Too many wrong tries on this device.";
    private static final String NETWORK_PAUSED_MESSAGE = "Many wrong tries on this network.";
    private static final String ACCOUNT_OFF_MESSAGE = "Your account is switched off. Ask your manager.";
    private static final String EMAIL_NOT_CONFIRMED_MESSAGE = "Your email isn't confirmed yet. Use the link in the email we sent you, or ask your manager.";
    private static final String TOKEN_TYPE = "Bearer";

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOePaWxn96p36aH8uY7f9ZC2w5Q5f5e7a";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserSessionRepository userSessionRepository;
    private final AuthLoginAttemptRepository authLoginAttemptRepository;
    private final UserSessionMapper userSessionMapper;
    private final CurrentUserMapper currentUserMapper;

    @Value("${app.security.login.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.security.login.lock-duration-minutes}")
    private long lockDurationMinutes;

    @Value("${app.security.login.max-attempts-per-ip}")
    private int maxAttemptsPerIp;

    // Wrong passwords on one device, and from one network address, that pause sign-in there (user, 2026-11-17).
    @Value("${app.security.login.max-wrong-per-device:50}")
    private int maxWrongPerDevice;

    @Value("${app.security.login.max-wrong-per-address:200}")
    private int maxWrongPerAddress;

    @Value("${app.security.login.max-attempts-per-account:${app.security.login.max-failed-attempts}}")
    private int maxAttemptsPerAccount;

    @Value("${app.security.login.window-minutes}")
    private long windowMinutes;

    @Value("${app.security.session.max-active-sessions}")
    private int maxActiveSessions;

    private final RefreshTokenSecurityService refreshTokenSecurityService;

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, TooManyRequestsException.class, LoginRefusedException.class})
    public AuthenticationResponse login(LoginRequest request, ClientInfo clientInfo) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String normalizedIdentifier = normalizeIdentifier(request.getIdentifier());

        ClientInfo normalizedClientInfo = ClientInfoNormalizer.normalize(clientInfo);
        User user = findUserByIdentifier(normalizedIdentifier);
        UUID userId = user != null ? user.getId() : null;

        // Paused network, then paused device (user, 2026-11-17): only wrong passwords count, right sign-ins never do, and
        // a pause lasts 15 minutes from the wrong try that started it. People already signed in aren't touched.
        OffsetDateTime networkPausedUntil = networkPausedUntil(normalizedClientInfo, now);
        if (networkPausedUntil != null) {
            saveLoginAttempt(userId, normalizedIdentifier, normalizedClientInfo, false, LoginFailureReason.IP_RATE_LIMITED, now);
            throw networkPaused(networkPausedUntil);
        }
        OffsetDateTime devicePausedUntil = devicePausedUntil(normalizedClientInfo, now);
        if (devicePausedUntil != null) {
            saveLoginAttempt(userId, normalizedIdentifier, normalizedClientInfo, false, LoginFailureReason.DEVICE_RATE_LIMITED, now);
            throw devicePaused(devicePausedUntil);
        }
        // Locked: 5 wrong passwords within 15 minutes stop that username for 15 minutes. The same rule, from the same
        // records, for a username nobody has, so "locked" never tells a stranger that a username exists (user, 2026-11-16:
        // "so the hacker now knows the username"). It's checked before anything else about the account.
        OffsetDateTime lockedUntil = lockedUntil(user, normalizedIdentifier, now);
        if (lockedUntil != null) {
            saveLoginAttempt(userId, normalizedIdentifier, normalizedClientInfo, false, LoginFailureReason.ACCOUNT_LOCKED, now);
            throw locked(lockedUntil);
        }
        if (user == null) {
            // Always fails on purpose to prevent timing attacks by making response time the same even if the user does not exist
            passwordService.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            saveLoginAttempt(null, normalizedIdentifier, normalizedClientInfo, false, LoginFailureReason.INVALID_CREDENTIALS, now);
            OffsetDateTime nowLocked = lockedUntil(null, normalizedIdentifier, now);
            if (nowLocked != null) throw locked(nowLocked);
            throwIfPausedNow(normalizedClientInfo, now);
            throw wrongCredentials();
        }

        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user, normalizedIdentifier, normalizedClientInfo, now);
            // The wrong password that locks the account says so at once (the same as for a username nobody has).
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) throw locked(user.getLockedUntil());
            throwIfPausedNow(normalizedClientInfo, now);
            throw wrongCredentials();
        }

        // The right password: only now is the account's own state told (switched off, email not confirmed, restaurant
        // closed), so it tells nothing to someone guessing.
        validateUserCanLogin(user, now, normalizedIdentifier, normalizedClientInfo);
        if (userRepository.worksAtClosedRestaurant(user.getId())) {
            throw new LoginRefusedException(LoginRefusedException.Reason.RESTAURANT_CLOSED, RESTAURANT_CLOSED_MESSAGE, HttpStatus.FORBIDDEN, null);
        }

        enforceSessionLimit(user.getId(), now);

        //here it means all the check have passed so it creates a successful login.
        List<Role> activeRoles = loadActiveRoles(user.getId());
        List<String> roleCodes = activeRoles.stream().map(Role::getCode).toList();
        List<String> permissionCodes = loadPermissionCodes(user, activeRoles);

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setLastLoginIp(normalizedClientInfo != null ? normalizedClientInfo.ipAddress() : null);
        userRepository.save(user);

        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(user.getId(), roleCodes, tokenId);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), tokenId);

        userSessionRepository.save(
                userSessionMapper.toSession(
                        user.getId(),
                        tokenId,
                        SessionType.PASSWORD,
                        refreshTokenSecurityService.hash(refreshToken),
                        normalizedClientInfo
                )
        );

        saveLoginAttempt(user.getId(), normalizedIdentifier, normalizedClientInfo, true, null, now);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtProperties.getAccessExpiration().getSeconds())
                .user(currentUserMapper.toCurrentUserResponse(user, roleCodes, permissionCodes))
                .build();
    }


    private List<Role> loadActiveRoles(UUID userId) {
        return roleRepository.findActiveRolesByUserId(userId);
    }

    private List<String> loadPermissionCodes(User user, List<Role> activeRoles) {
        List<UUID> roleIds = activeRoles.stream()
                .map(Role::getId)
                .toList();

        // Their roles as their restaurant has them, less what's switched off for them, plus what's given to them alone.
        return pos.pos.role.service.EffectivePermissions.forUser(permissionRepository, user.getId(), user.getRestaurantId(), roleIds);
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
            String normalizedIdentifier,
            ClientInfo clientInfo,
            OffsetDateTime now
    ) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        saveLoginAttempt(user.getId(), normalizedIdentifier, clientInfo, false, LoginFailureReason.INVALID_CREDENTIALS, now);
        // Kept on the person too, so their own screens can show it; the rule itself is lockedUntil().
        user.setLockedUntil(lockedUntil(user, normalizedIdentifier, now));
        userRepository.save(user);
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
            String normalizedIdentifier,
            ClientInfo clientInfo
    ) {
        if (!Boolean.TRUE.equals(user.isActive())
                || user.getDeletedAt() != null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            saveLoginAttempt(user.getId(), normalizedIdentifier, clientInfo, false, LoginFailureReason.ACCOUNT_INACTIVE, now);
            throw new LoginRefusedException(LoginRefusedException.Reason.ACCOUNT_OFF, ACCOUNT_OFF_MESSAGE, HttpStatus.FORBIDDEN, null);
        }

        if (!Boolean.TRUE.equals(user.isEmailVerified())) {
            saveLoginAttempt(user.getId(), normalizedIdentifier, clientInfo, false, LoginFailureReason.EMAIL_NOT_VERIFIED, now);
            throw new LoginRefusedException(LoginRefusedException.Reason.EMAIL_NOT_CONFIRMED, EMAIL_NOT_CONFIRMED_MESSAGE, HttpStatus.FORBIDDEN, null);
        }
    }

    /**
     * Until when sign-in for this username is stopped, or null: 8 ([maxFailedAttempts]) wrong passwords within 15 minutes
     * ([lockDurationMinutes]) stop it for 15 minutes from the last of them. Counted from the stored sign-in attempts (wrong
     * passwords only; tries made while locked don't count), after the person's last sign-in or new password. A username
     * nobody has is counted by what was typed, the same way, so the two can't be told apart.
     */
    private OffsetDateTime lockedUntil(User user, String identifier, OffsetDateTime now) {
        java.time.Duration lock = java.time.Duration.ofMinutes(lockDurationMinutes);
        OffsetDateTime after = now.minus(lock.multipliedBy(2));
        java.util.List<OffsetDateTime> times;
        if (user != null) {
            if (user.getLastLoginAt() != null && user.getLastLoginAt().isAfter(after)) after = user.getLastLoginAt();
            if (user.getPasswordUpdatedAt() != null && user.getPasswordUpdatedAt().isAfter(after)) after = user.getPasswordUpdatedAt();
            times = authLoginAttemptRepository.wrongPasswordTimesForUser(user.getId(), after);
        } else {
            if (identifier == null) return null;
            times = authLoginAttemptRepository.wrongPasswordTimesForUnknown(identifier, after);
        }
        return pausedUntil(times, maxFailedAttempts, lock, now);
    }

    /** Until when sign-in from this device is paused: [maxWrongPerDevice] wrong passwords within 15 minutes, for 15 minutes. */
    private OffsetDateTime devicePausedUntil(ClientInfo clientInfo, OffsetDateTime now) {
        String deviceId = clientInfo != null ? clientInfo.deviceId() : null;
        if (deviceId == null) return null;
        java.time.Duration pause = java.time.Duration.ofMinutes(lockDurationMinutes);
        return pausedUntil(authLoginAttemptRepository.wrongPasswordTimesForDevice(deviceId, now.minus(pause.multipliedBy(2))), maxWrongPerDevice, pause, now);
    }

    /** The same for the network address, the safety net: [maxWrongPerAddress] wrong passwords within 15 minutes. */
    private OffsetDateTime networkPausedUntil(ClientInfo clientInfo, OffsetDateTime now) {
        String ip = clientInfo != null ? clientInfo.ipAddress() : null;
        if (ip == null) return null;
        java.time.Duration pause = java.time.Duration.ofMinutes(lockDurationMinutes);
        return pausedUntil(authLoginAttemptRepository.wrongPasswordTimesForAddress(ip, now.minus(pause.multipliedBy(2))), maxWrongPerAddress, pause, now);
    }

    /** After a wrong password: the one that pauses the device or the network says so at once. */
    private void throwIfPausedNow(ClientInfo clientInfo, OffsetDateTime now) {
        OffsetDateTime device = devicePausedUntil(clientInfo, now);
        if (device != null) throw devicePaused(device);
        OffsetDateTime network = networkPausedUntil(clientInfo, now);
        if (network != null) throw networkPaused(network);
    }

    /** [limit] tries within [pause] of each other pause it for [pause] from the last of them; null when not paused now. */
    private static OffsetDateTime pausedUntil(java.util.List<OffsetDateTime> times, int limit, java.time.Duration pause, OffsetDateTime now) {
        int n = Math.max(1, limit);
        OffsetDateTime until = null;
        for (int i = n - 1; i < times.size(); i++) {
            if (!times.get(i).isAfter(times.get(i - n + 1).plus(pause))) {
                OffsetDateTime candidate = times.get(i).plus(pause);
                if (until == null || candidate.isAfter(until)) until = candidate;
            }
        }
        return until != null && until.isAfter(now) ? until : null;
    }

    private static LoginRefusedException devicePaused(OffsetDateTime until) {
        return new LoginRefusedException(LoginRefusedException.Reason.DEVICE_PAUSED, DEVICE_PAUSED_MESSAGE, HttpStatus.TOO_MANY_REQUESTS, until);
    }

    private static LoginRefusedException networkPaused(OffsetDateTime until) {
        return new LoginRefusedException(LoginRefusedException.Reason.NETWORK_PAUSED, NETWORK_PAUSED_MESSAGE, HttpStatus.TOO_MANY_REQUESTS, until);
    }

    private static LoginRefusedException wrongCredentials() {
        return new LoginRefusedException(LoginRefusedException.Reason.WRONG_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE, HttpStatus.UNAUTHORIZED, null);
    }

    private static LoginRefusedException locked(OffsetDateTime until) {
        return new LoginRefusedException(LoginRefusedException.Reason.ACCOUNT_LOCKED, LOCKED_MESSAGE, HttpStatus.LOCKED, until);
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
            String identifier,
            ClientInfo clientInfo,
            boolean success,
            LoginFailureReason failureReason,
            OffsetDateTime attemptedAt
    ) {
        AuthLoginAttempt attempt = AuthLoginAttempt.builder()
                .userId(userId)
                .identifier(identifier)
                .ipAddress(clientInfo != null ? clientInfo.ipAddress() : null)
                .deviceId(clientInfo != null ? clientInfo.deviceId() : null)
                .userAgent(clientInfo != null ? clientInfo.userAgent() : null)
                .success(success)
                .failureReason(failureReason)
                .attemptedAt(attemptedAt)
                .build();

        authLoginAttemptRepository.save(attempt);
    }



    private User findUserByIdentifier(String normalizedIdentifier) {
        if (normalizedIdentifier == null) {
            return null;
        }

        if (normalizedIdentifier.contains("@")) {
            return userRepository.findByEmailAndDeletedAtIsNull(normalizedIdentifier).orElse(null);
        }

        return userRepository.findByUsernameAndDeletedAtIsNull(normalizedIdentifier).orElse(null);
    }

    private String normalizeIdentifier(String identifier) {
        return NormalizationUtils.normalizeLower(identifier);
    }

}
