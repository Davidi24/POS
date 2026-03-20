package pos.pos.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pos.pos.auth.dto.*;
import pos.pos.auth.mapper.AuthMapper;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserSession;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserSessionRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AuthMapper authMapper;
    private final UserMapper userMapper;
    private final UserSessionMapper userSessionMapper;
    private final JwtProperties jwtProperties;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:no-reply@pos.local}}")
    private String mailFrom;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public UserResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException();
        }

        String passwordHash = passwordService.hash(request.getPassword());

        User user = authMapper.toUser(request, passwordHash);
        userRepository.save(user);
        sendVerificationEmail(user);

        return userMapper.toUserResponse(user);
    }

    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        UUID tokenId = UUID.randomUUID();

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), tokenId);

        String refreshTokenHash = passwordService.hash(refreshToken);

        UserSession session = userSessionMapper.toSession(
                user.getId(),
                tokenId,
                refreshTokenHash,
                ipAddress,
                userAgent
        );

        userSessionRepository.save(session);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration().toMillis())
                .build();
    }

    public UserResponse me(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {

        validateToken(refreshToken);

        UUID userId = jwtService.extractUserId(refreshToken);
        UUID tokenId = jwtService.extractTokenId(refreshToken);

        UserSession session = userSessionRepository
                .findByTokenIdAndRevokedFalse(tokenId)
                .orElseThrow(InvalidTokenException::new);

        validateSession(session, userId);

        session.setRevoked(true);

        UUID newTokenId = UUID.randomUUID();

        String newRefreshToken = jwtService.generateRefreshToken(userId, newTokenId);
        String newRefreshTokenHash = passwordService.hash(newRefreshToken);

        UserSession newSession = createRotatedSession(session, newTokenId, newRefreshTokenHash);

        userSessionRepository.save(newSession);

        String newAccessToken = jwtService.generateAccessToken(userId);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration().toMillis())
                .build();
    }

    public void logout(String refreshToken) {

        if (refreshToken == null || !jwtService.isValid(refreshToken)) {
            return;
        }

        UUID tokenId = jwtService.extractTokenId(refreshToken);

        userSessionRepository
                .findByTokenIdAndRevokedFalse(tokenId)
                .ifPresent(session -> {
                    session.setRevoked(true);
                    session.setLastUsedAt(OffsetDateTime.now());
                    userSessionRepository.save(session);
                });
    }

    @Transactional
    public void logoutAll(UUID userId) {

        OffsetDateTime now = OffsetDateTime.now();

        userSessionRepository.findByUserId(userId)
                .forEach(session -> {
                    session.setRevoked(true);
                    session.setLastUsedAt(now);
                });
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setPasswordHash(passwordService.hash(request.getNewPassword()));
        userRepository.save(user);

        logoutAll(userId);
    }

    public void forgotPassword(ForgotPasswordRequest request) {

        userRepository.findByEmail(request.getEmail())
                .ifPresent(user -> {
                    String resetToken = jwtService.generateAccessToken(user.getId());
                    sendPasswordResetEmail(user, resetToken);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        validateToken(request.getToken());

        UUID userId = jwtService.extractUserId(request.getToken());

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        user.setPasswordHash(passwordService.hash(request.getNewPassword()));
        userRepository.save(user);

        logoutAll(userId);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {

        validateToken(request.getToken());

        UUID userId = jwtService.extractUserId(request.getToken());

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        user.setEmailVerified(true);
        userRepository.save(user);
    }

    public void resendVerification(ResendVerificationRequest request) {

        userRepository.findByEmail(request.getEmail())
                .ifPresent(user -> {
                    if (Boolean.TRUE.equals(user.getEmailVerified())) {
                        return;
                    }

                    String token = jwtService.generateAccessToken(user.getId());
                    sendVerificationEmail(user, token);
                });
    }

    public long getRefreshTokenMaxAgeSeconds() {
        return jwtProperties.getRefreshExpiration().getSeconds();
    }

    // ================= HELPERS =================

    private void validateToken(String token) {
        if (token == null || token.isBlank() || !jwtService.isValid(token)) {
            throw new InvalidTokenException();
        }
    }

    private void validateSession(UserSession session, UUID userId) {
        if (!session.getUserId().equals(userId)) {
            throw new InvalidTokenException();
        }

        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException();
        }
    }

    private UserSession createRotatedSession(UserSession oldSession, UUID newTokenId, String newHash) {
        OffsetDateTime now = OffsetDateTime.now();

        return UserSession.builder()
                .userId(oldSession.getUserId())
                .tokenId(newTokenId)
                .refreshTokenHash(newHash)
                .ipAddress(oldSession.getIpAddress())
                .userAgent(oldSession.getUserAgent())
                .lastUsedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshExpiration().getSeconds()))
                .createdAt(now)
                .revoked(false)
                .build();
    }

    private void sendVerificationEmail(User user) {
        String token = jwtService.generateAccessToken(user.getId());
        sendVerificationEmail(user, token);
    }

    private void sendVerificationEmail(User user, String token) {
        String verificationUrl = buildFrontendUrl("/verify-email", token);
        sendEmail(
                user.getEmail(),
                "Verify your email",
                "Welcome to POS.\n\nVerify your email using this link:\n" + verificationUrl
        );
    }

    private void sendPasswordResetEmail(User user, String token) {
        String resetUrl = buildFrontendUrl("/reset-password", token);
        sendEmail(
                user.getEmail(),
                "Reset your password",
                "We received a password reset request.\n\nReset your password using this link:\n" + resetUrl
        );
    }

    private String buildFrontendUrl(String path, String token) {
        return frontendBaseUrl + path + "?token=" + token;
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
