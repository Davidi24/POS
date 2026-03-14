package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.auth.dto.*;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserSession;
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

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        String refreshTokenHash = passwordService.hash(refreshToken);

        UserSession session = UserSession.builder()
                .userId(user.getId())
                .refreshTokenHash(refreshTokenHash)
                .createdAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .revoked(false)
                .build();

        userSessionRepository.save(session);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    public LoginResponse refresh(String refreshToken) {

        if (!jwtService.isValid(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        UUID userId = jwtService.extractUserId(refreshToken);

        List<UserSession> sessions = userSessionRepository.findByUserId(userId);

        UserSession session = sessions.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getRevoked()))
                .filter(s -> passwordService.matches(refreshToken, s.getRefreshTokenHash()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        String newAccessToken = jwtService.generateAccessToken(userId);
        String newRefreshToken = jwtService.generateRefreshToken(userId);

        session.setRefreshTokenHash(passwordService.hash(newRefreshToken));
        session.setLastUsedAt(OffsetDateTime.now());

        userSessionRepository.save(session);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    public void logout(String refreshToken) {

        if (!jwtService.isValid(refreshToken)) {
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

    public UserResponse me(String token) {

        if (token == null || !token.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid token");
        }

        String jwt = token.substring(7);

        if (!jwtService.isValid(jwt)) {
            throw new RuntimeException("Invalid token");
        }

        UUID userId = jwtService.extractUserId(jwt);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    public UserResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        String passwordHash = passwordService.hash(request.getPassword());

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .createdAt(OffsetDateTime.now())
                .build();

        userRepository.save(user);

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    public void changePassword(String token, ChangePasswordRequest request) {

        if (token == null || !token.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid token");
        }

        String jwt = token.substring(7);

        if (!jwtService.isValid(jwt)) {
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

//        String token = jwtService.generatePasswordResetToken(user.getId());

        // here you would normally send the token via email
    }


}