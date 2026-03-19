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
    private final JwtProperties jwtProperties;

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

        if (refreshToken == null || refreshToken.isBlank() || !jwtService.isValid(refreshToken)) {
            throw new InvalidTokenException();
        }

        UUID userId = jwtService.extractUserId(refreshToken);
        UUID tokenId = jwtService.extractTokenId(refreshToken);

        UserSession session = userSessionRepository
                .findByTokenIdAndRevokedFalse(tokenId)
                .orElseThrow(InvalidTokenException::new);

        if (!session.getUserId().equals(userId)) {
            throw new InvalidTokenException();
        }

        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException();
        }

        session.setRevoked(true);

        UUID newTokenId = UUID.randomUUID();

        String newRefreshToken = jwtService.generateRefreshToken(userId, newTokenId);
        String newRefreshTokenHash = passwordService.hash(newRefreshToken);

        UserSession newSession = createRotatedSession(
                session,
                newTokenId,
                newRefreshTokenHash
        );

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

        UserSession session = userSessionRepository
                .findByTokenIdAndRevokedFalse(tokenId)
                .orElse(null);

        if (session == null) {
            return;
        }

        session.setRevoked(true);
        session.setLastUsedAt(OffsetDateTime.now());

        userSessionRepository.save(session);
    }


    @Transactional
    public void logoutAll(UUID userIdFromToken) {

        OffsetDateTime now = OffsetDateTime.now();

        userSessionRepository.findByUserId(userIdFromToken)
                .forEach(session -> {
                    session.setRevoked(true);
                    session.setLastUsedAt(now);
                });
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

    private UserSession createRotatedSession(UserSession oldSession, UUID newTokenId, String newHash) {
        return UserSession.builder()
                .userId(oldSession.getUserId())
                .tokenId(newTokenId)
                .refreshTokenHash(newHash)
                .ipAddress(oldSession.getIpAddress())
                .userAgent(oldSession.getUserAgent())
                .lastUsedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration().getSeconds()))
                .createdAt(OffsetDateTime.now())
                .revoked(false)
                .build();
    }
}
