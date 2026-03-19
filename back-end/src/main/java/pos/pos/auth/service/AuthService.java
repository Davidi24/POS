package pos.pos.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.auth.dto.*;
import pos.pos.auth.mapper.AuthMapper;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserSession;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserSessionRepository;

import java.time.OffsetDateTime;
import java.util.List;
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

    public UserResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException();
        }

        String passwordHash = passwordService.hash(request.getPassword());

        User user = authMapper.toUser(request, passwordHash);

        userRepository.save(user);

        return userMapper.toUserResponse(user);
    }

    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null || !passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        String refreshTokenHash = passwordService.hash(refreshToken);

        UserSession session = userSessionMapper.toSession(
                user.getId(),
                refreshTokenHash,
                ipAddress,
                userAgent
        );

        userSessionRepository.save(session);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    public UserResponse me(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        return userMapper.toUserResponse(user);
    }


    @Transactional
    public LoginResponse refresh(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank() || !jwtService.isValid(refreshToken)) {
            throw new InvalidTokenException();
        }

        UUID userId = jwtService.extractUserId(refreshToken);

        List<UserSession> sessions = userSessionRepository.findByUserId(userId);

        UserSession session = sessions.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getRevoked()))
                .filter(s -> passwordService.matches(refreshToken, s.getRefreshTokenHash()))
                .findFirst()
                .orElseThrow(InvalidTokenException::new);

        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException();
        }

        session.setRevoked(true);

        String newRefreshToken = jwtService.generateRefreshToken(userId);
        String newRefreshTokenHash = passwordService.hash(newRefreshToken);

        UserSession newSession = createRotatedSession(session, newRefreshTokenHash);

        userSessionRepository.save(newSession);

        String newAccessToken = jwtService.generateAccessToken(userId);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    public void logout(String refreshToken) {

        if (jwtService.isValid(refreshToken)) {
            return;
        }

        UUID userId = jwtService.extractUserId(refreshToken);

        List<UserSession> sessions = userSessionRepository.findByUserId(userId);

        UserSession session = sessions.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getRevoked()))
                .filter(s -> passwordService.matches(refreshToken, s.getRefreshTokenHash()))
                .findFirst()
                .orElse(null);

        if (session == null) {
            return;
        }

        session.setRevoked(true);
        session.setLastUsedAt(OffsetDateTime.now());

        userSessionRepository.save(session);
    }

    public void logoutAll(UUID userId) {

        List<UserSession> sessions = userSessionRepository.findByUserId(userId);

        if (sessions.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        sessions.forEach(session -> {
            session.setRevoked(true);
            session.setLastUsedAt(now);
        });

        userSessionRepository.saveAll(sessions);
    }

    public void changePassword(String token, ChangePasswordRequest request) {

        if (token == null || !token.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid token");
        }

        String jwt = token.substring(7);

        if (jwtService.isValid(jwt)) {
            throw new RuntimeException("Invalid token");
        }

        UUID userId = jwtService.extractUserId(jwt);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        String newPasswordHash = passwordService.hash(request.getNewPassword());

        user.setPasswordHash(newPasswordHash);

        userRepository.save(user);
    }

    public void forgotPassword(ForgotPasswordRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null) {
            return;
        }

        // here you would normally send the token via email
    }

    public void resetPassword(ResetPasswordRequest request) {

        if (jwtService.isValid(request.getToken())) {
            throw new RuntimeException("Invalid reset token");
        }

        UUID userId = jwtService.extractUserId(request.getToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newPasswordHash = passwordService.hash(request.getNewPassword());

        user.setPasswordHash(newPasswordHash);

        userRepository.save(user);
    }

    private UserSession createRotatedSession(UserSession oldSession, String newHash) {
        return UserSession.builder()
                .userId(oldSession.getUserId())
                .refreshTokenHash(newHash)
                .ipAddress(oldSession.getIpAddress())
                .userAgent(oldSession.getUserAgent())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
    }
}