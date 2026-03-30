package pos.pos.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.dto.AuthTokensResponse;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.RefreshRateLimiter;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthRefreshServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @Mock
    private RefreshRateLimiter refreshRateLimiter;

    @InjectMocks
    private AuthRefreshService authRefreshService;

    @BeforeEach
    void setUp() {
        when(jwtProperties.getAccessExpiration()).thenReturn(Duration.ofMinutes(15));
    }

    @Test
    void refresh_shouldUseLockedSessionLookupAndActiveUserLookup() {
        UUID oldTokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                new RefreshTokenSecurityService.ValidatedRefreshToken("token", "old-hash", oldTokenId, userId);
        UserSession session = UserSession.builder()
                .userId(userId)
                .tokenId(oldTokenId)
                .refreshTokenHash("stored-hash")
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .revoked(false)
                .build();
        User user = User.builder()
                .id(userId)
                .email("owner@pos.local")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .build();
        UserResponse userResponse = UserResponse.builder()
                .id(userId)
                .email("owner@pos.local")
                .roles(List.of("ADMIN"))
                .build();

        when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
        when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(oldTokenId)).thenReturn(Optional.of(session));
        when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findActiveRoleCodesByUserId(userId)).thenReturn(List.of("ADMIN"));
        when(jwtService.generateAccessToken(eq(userId), eq(List.of("ADMIN")), any(UUID.class))).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(eq(userId), any(UUID.class))).thenReturn("new-refresh-token");
        when(refreshTokenSecurityService.hash("new-refresh-token")).thenReturn("new-refresh-hash");
        when(userMapper.toUserResponse(user, List.of("ADMIN"))).thenReturn(userResponse);

        AuthTokensResponse response = authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit"));

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(userSessionRepository).findByTokenIdAndRevokedFalseForUpdate(oldTokenId);
        verify(userRepository).findActiveById(userId);
    }
}
