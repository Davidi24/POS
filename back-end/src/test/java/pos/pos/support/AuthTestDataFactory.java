package pos.pos.support;

import pos.pos.auth.dto.*;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.entity.UserSession;
import pos.pos.role.entity.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class AuthTestDataFactory {

    private AuthTestDataFactory() {
    }

    public static RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@example.com");
        request.setPassword("Password123");
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }

    public static LoginRequest validLoginRequest() {
        return LoginRequest.builder()
                .email("john@example.com")
                .password("Password123")
                .build();
    }

    public static ChangePasswordRequest validChangePasswordRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPassword123");
        request.setNewPassword("NewPassword123");
        return request;
    }

    public static ForgotPasswordRequest validForgotPasswordRequest() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("john@example.com");
        return request;
    }

    public static RefreshTokenRequest validRefreshTokenRequest() {
        return RefreshTokenRequest.builder()
                .refreshToken("refresh-token")
                .build();
    }

    public static ResetPasswordRequest validResetPasswordRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("NewPassword123");
        return request;
    }

    public static ResendVerificationRequest validResendVerificationRequest() {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("john@example.com");
        return request;
    }

    public static VerifyEmailRequest validVerifyEmailRequest() {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("verify-token");
        return request;
    }

    public static User user() {
        OffsetDateTime now = OffsetDateTime.now();

        return User.builder()
                .id(UUID.randomUUID())
                .email("john@example.com")
                .passwordHash("hashed-password")
                .firstName("John")
                .lastName("Doe")
                .phone("+49123456789")
                .isActive(true)
                .emailVerified(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static User verifiedUser() {
        User user = user();
        user.setEmailVerified(true);
        return user;
    }

    public static UserResponse userResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .build();
    }

    public static LoginResponse loginResponse() {
        return LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900_000L)
                .build();
    }

    public static UserSession session(UUID userId, UUID tokenId) {
        OffsetDateTime now = OffsetDateTime.now();

        return UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .refreshTokenHash("hashed-refresh-token")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .lastUsedAt(now)
                .expiresAt(now.plusDays(30))
                .createdAt(now)
                .revoked(false)
                .build();
    }

    public static UserRole userRole(UUID userId, UUID roleId) {
        return UserRole.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .roleId(roleId)
                .assignedAt(OffsetDateTime.now())
                .build();
    }

    public static Role role(String name) {
        OffsetDateTime now = OffsetDateTime.now();

        return Role.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(name + " role")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
