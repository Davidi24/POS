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
import pos.pos.auth.entity.UserSession;
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
    private final PinService pinService;
    private final pos.pos.device.service.ConnectedDeviceService connectedDevices;
    private final pos.pos.device.network.DeviceNetworkService deviceNetworks;
    private final pos.pos.device.signin.DeviceSignInService deviceSignIn;
    private final pos.pos.security.geo.GeoPlaces geoPlaces;
    private final AccountAudit accountAudit;
    private final UserSessionMapper userSessionMapper;
    private final CurrentUserMapper currentUserMapper;

    @Value("${app.security.login.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.security.login.lock-duration-minutes}")
    private long lockDurationMinutes;

    @Value("${app.security.login.max-attempts-per-ip}")
    private int maxAttemptsPerIp;

    // Wrong passwords on one device, and from one network address, that pause sign-in there (user, 2026-11-17).
    /** Wrong PINs on one till within the pause before it stops PIN sign-in (user, 2026-11-27: "not 5 times wrong, make it 10"). */
    @Value("${app.security.login.max-wrong-pins-per-till:10}")
    private int maxWrongPinsPerTill = 10;

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

        enforceSessionLimit(user, request.getSignOutSessionId(), normalizedClientInfo, now);

        return issue(user, normalizedIdentifier, normalizedClientInfo, SessionType.PASSWORD, now);
    }

    /**
     * Sign in with a PIN on one of the restaurant's connected tills (user, 2026-11-18): the device's id and secret say which
     * restaurant; the PIN finds the person there. 10 wrong PINs on a till within 15 minutes pause PIN sign-in on it for 15
     * minutes (people can still use their password). A PIN session lasts at most 12 hours and doesn't count toward the 5 devices.
     * While the till's branch keeps tills on its network, a PIN from anywhere else is OUTSIDE_NETWORK (403).
     */
    @Transactional(noRollbackFor = {LoginRefusedException.class})
    public AuthenticationResponse pinLogin(UUID deviceId, String deviceSecret, String pin, ClientInfo clientInfo) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        pos.pos.device.service.ConnectedDeviceService.Connected device = connectedDevices.check(deviceId, deviceSecret).orElseThrow(() ->
                new LoginRefusedException(LoginRefusedException.Reason.DEVICE_NOT_CONNECTED,
                        "This device isn't connected to a restaurant. Sign in with your password.", HttpStatus.UNAUTHORIZED, null));
        // A till works only on its restaurant's network while its branch keeps tills on it (user, 2026-11-24).
        deviceNetworks.beforePin(device, clientInfo == null ? null : clientInfo.ipAddress());
        String tillKey = "till:" + device.deviceId();
        ClientInfo base = ClientInfoNormalizer.normalize(clientInfo);
        ClientInfo info = new ClientInfo(base == null ? null : base.ipAddress(), base == null ? null : base.userAgent(), tillKey, device.name(),
                base == null ? null : base.deviceSystem());
        java.time.Duration pause = java.time.Duration.ofMinutes(lockDurationMinutes);
        OffsetDateTime pausedUntil = pausedUntil(authLoginAttemptRepository.wrongPinTimesForDevice(tillKey, now.minus(pause.multipliedBy(2))), maxWrongPinsPerTill, pause, now);
        if (pausedUntil != null) {
            saveLoginAttempt(null, "pin", info, false, LoginFailureReason.PIN_PAUSED, now);
            accountAudit.inRestaurant(device.restaurantId(), device.deviceId(), null, "WRONG_PIN",
                    "A PIN was tried on " + AccountAudit.deviceOf(device.name()) + " while it was paused");
            throw new LoginRefusedException(LoginRefusedException.Reason.PIN_PAUSED, "Too many wrong PINs on this till.", HttpStatus.TOO_MANY_REQUESTS, pausedUntil);
        }
        if (pin == null || !pin.matches("\\d{" + PinService.LENGTH + "}")) {
            throw new LoginRefusedException(LoginRefusedException.Reason.WRONG_PIN, "Wrong PIN.", HttpStatus.UNAUTHORIZED, null);
        }
        User user = userRepository.findByRestaurantIdAndPinHashAndDeletedAtIsNull(device.restaurantId(), pinService.hash(device.restaurantId(), pin))
                .filter(User::isPinEnabled).orElse(null);
        if (user == null) {
            saveLoginAttempt(null, "pin", info, false, LoginFailureReason.INVALID_PIN, now);
            accountAudit.inRestaurant(device.restaurantId(), device.deviceId(), null, "WRONG_PIN", "Wrong PIN on " + AccountAudit.deviceOf(device.name()));
            OffsetDateTime nowPaused = pausedUntil(authLoginAttemptRepository.wrongPinTimesForDevice(tillKey, now.minus(pause.multipliedBy(2))), maxWrongPinsPerTill, pause, now);
            if (nowPaused != null) {
                accountAudit.inRestaurant(device.restaurantId(), device.deviceId(), null, "TILL_PAUSED",
                        AccountAudit.deviceOf(device.name()) + " stopped PIN sign-in for " + lockDurationMinutes + " minutes after " + maxWrongPinsPerTill + " wrong PINs");
                throw new LoginRefusedException(LoginRefusedException.Reason.PIN_PAUSED, "Too many wrong PINs on this till.", HttpStatus.TOO_MANY_REQUESTS, nowPaused);
            }
            throw new LoginRefusedException(LoginRefusedException.Reason.WRONG_PIN, "Wrong PIN.", HttpStatus.UNAUTHORIZED, null);
        }
        validateUserCanLogin(user, now, user.getUsername(), info);
        if (userRepository.worksAtClosedRestaurant(user.getId())) {
            throw new LoginRefusedException(LoginRefusedException.Reason.RESTAURANT_CLOSED, RESTAURANT_CLOSED_MESSAGE, HttpStatus.FORBIDDEN, null);
        }
        // Only the people chosen for this till (Devices → Who can sign in here; user, 2026-11-25). The PIN was right, so it
        // doesn't count as a wrong one.
        if (!deviceSignIn.allows(device.deviceId(), user.getId(), loadActiveRoles(user.getId()))) {
            accountAudit.about(user, user.getId(), "NOT_ON_THIS_DEVICE",
                    AccountAudit.nameOf(user) + " tried to sign in on " + AccountAudit.deviceOf(device.name()) + ", which isn't for them");
            throw new LoginRefusedException(LoginRefusedException.Reason.NOT_ON_THIS_DEVICE, "You can't use this device. Ask a manager.",
                    HttpStatus.FORBIDDEN, null);
        }
        return issue(user, user.getUsername(), info, SessionType.PIN, now);
    }

    /** The checks have passed: a new session (and its tokens) for [user] on this device. */
    private AuthenticationResponse issue(User user, String normalizedIdentifier, ClientInfo normalizedClientInfo, SessionType sessionType, OffsetDateTime now) {
        List<Role> activeRoles = loadActiveRoles(user.getId());
        List<String> roleCodes = activeRoles.stream().map(Role::getCode).toList();
        List<String> permissionCodes = pos.pos.security.rbac.PinSignIn.mark(loadPermissionCodes(user, activeRoles), sessionType);

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setLastLoginIp(normalizedClientInfo != null ? normalizedClientInfo.ipAddress() : null);
        userRepository.save(user);

        UUID tokenId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(user.getId(), roleCodes, tokenId);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), tokenId);

        UserSession session = userSessionMapper.toSession(user.getId(), tokenId, sessionType,
                refreshTokenSecurityService.hash(refreshToken), normalizedClientInfo);
        // A PIN session on a shared till lasts one working day at most, whatever the refresh lifetime.
        if (sessionType == SessionType.PIN) session.setExpiresAt(now.plusHours(12));
        userSessionRepository.save(session);

        saveLoginAttempt(user.getId(), normalizedIdentifier, normalizedClientInfo, true, null, now);
        String device = AccountAudit.deviceOf(normalizedClientInfo == null ? null : normalizedClientInfo.deviceName());
        accountAudit.about(user, user.getId(), "SIGNED_IN", sessionType == SessionType.PIN
                ? AccountAudit.nameOf(user) + " signed in with their PIN on " + device
                : AccountAudit.nameOf(user) + " signed in on " + device);

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
     * At most [maxActiveSessions] (5) signed-in devices per person (user, 2026-11-18: "ask the person to sign out one").
     * At the limit the sign-in is refused with the list of devices, the one unused longest first; the app asks which to sign
     * out and signs in again with [signOutSessionId], which is signed out here (reason SESSION_LIMIT, so that device is told
     * "signed out from another device"). Only reached after the right password, so the list is never shown to a stranger.
     */
    private void enforceSessionLimit(User user, UUID signOutSessionId, ClientInfo clientInfo, OffsetDateTime now) {
        UUID userId = user.getId();
        if (signOutSessionId != null) {
            userSessionRepository.findByIdAndUserIdAndRevokedFalse(signOutSessionId, userId).ifPresent(chosen -> {
                chosen.setRevoked(true);
                chosen.setRevokedAt(now);
                chosen.setRevokedReason(SessionRevocationReason.SESSION_LIMIT.name());
                userSessionRepository.save(chosen);
                userSessionRepository.flush();
                accountAudit.about(user, userId, "SIGNED_OUT", AccountAudit.nameOf(user) + " signed out " + AccountAudit.deviceOf(chosen.getDeviceName())
                        + " to sign in on " + AccountAudit.deviceOf(clientInfo == null ? null : clientInfo.deviceName()) + " (5 devices at most)");
            });
        }
        if (userSessionRepository.countByUserIdAndRevokedFalseAndExpiresAtAfterAndSessionType(userId, now, SessionType.PASSWORD) < maxActiveSessions) {
            return;
        }
        List<LoginRefusedException.SignedInDevice> devices = userSessionRepository.findActiveSessionsByUserId(userId, now).stream()
                .filter(s -> s.getSessionType() != SessionType.PIN)
                .sorted(java.util.Comparator.comparing(UserSession::getLastUsedAt, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .map(s -> new LoginRefusedException.SignedInDevice(s.getId(), s.getDeviceName(), s.getLastUsedAt(), s.getCreatedAt(),
                        s.getDeviceSystem(), geoPlaces.placeOf(s.getIpAddress()), sameNetwork(s.getIpAddress(), clientInfo == null ? null : clientInfo.ipAddress())))
                .toList();
        throw new LoginRefusedException(LoginRefusedException.Reason.TOO_MANY_DEVICES,
                "You're signed in on " + maxActiveSessions + " devices. Sign out one to continue.", HttpStatus.CONFLICT, null, null, devices);
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
        if (user.getLockedUntil() != null) {
            accountAudit.about(user, null, "ACCOUNT_LOCKED", AccountAudit.nameOf(user) + "'s account was locked for " + lockDurationMinutes
                    + " minutes after " + maxFailedAttempts + " wrong passwords");
        }
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
    /** Whether two addresses are on one network (the same public address, or the same local network). */
    static boolean sameNetwork(String a, String b) {
        String one = pos.pos.device.network.NetworkAddress.of(a);
        return one != null && one.equals(pos.pos.device.network.NetworkAddress.of(b));
    }

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
                .deviceName(clientInfo != null ? clientInfo.deviceName() : null)
                .deviceSystem(clientInfo != null ? clientInfo.deviceSystem() : null)
                .userAgent(clientInfo != null ? clientInfo.userAgent() : null)
                .success(success)
                .failureReason(failureReason)
                .attemptedAt(attemptedAt)
                .build();

        authLoginAttemptRepository.save(attempt);
        if (!success && userId != null && failureReason != null) {
            String device = AccountAudit.deviceOf(clientInfo == null ? null : clientInfo.deviceName());
            switch (failureReason) {
                case INVALID_CREDENTIALS -> accountAudit.about(userId, userId, "SIGN_IN_FAILED", name -> "Wrong password for " + name + " on " + device);
                case ACCOUNT_LOCKED -> accountAudit.about(userId, userId, "SIGN_IN_FAILED", name -> name + " tried to sign in on " + device + " while the account was locked");
                case DEVICE_RATE_LIMITED -> accountAudit.about(userId, userId, "SIGN_IN_FAILED", name -> name + " tried to sign in on " + device + " while it was paused after many wrong passwords");
                case IP_RATE_LIMITED -> accountAudit.about(userId, userId, "SIGN_IN_FAILED", name -> name + " tried to sign in on " + device + " while its network was paused after many wrong passwords");
                case ACCOUNT_INACTIVE -> accountAudit.about(userId, userId, "SIGN_IN_REFUSED", name -> name + " couldn't sign in on " + device + ": the account is switched off");
                case EMAIL_NOT_VERIFIED -> accountAudit.about(userId, userId, "SIGN_IN_REFUSED", name -> name + " couldn't sign in on " + device + ": the email isn't confirmed");
                default -> { }
            }
        }
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
