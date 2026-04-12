package pos.pos.unit.security.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.service.JwtService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtService,
                userRepository,
                userRoleRepository,
                roleRepository,
                permissionRepository,
                userSessionRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should store AuthenticatedUser as the principal for a valid access token")
    void shouldStoreAuthenticatedUserPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.setServletPath("/auth/me");
        request.addHeader("Authorization", "Bearer valid-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        User user = User.builder()
                .id(userId)
                .email("manager@pos.local")
                .firstName("Maria")
                .lastName("Manager")
                .phone("+49-555-0101")
                .isActive(true)
                .build();

        UserSession session = UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .sessionType("PASSWORD")
                .refreshTokenHash("hash")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                .build();

        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .build();

        Role role = Role.builder()
                .id(roleId)
                .code("MANAGER")
                .name("Manager")
                .isActive(true)
                .build();

        when(jwtService.isValid("valid-access-token")).thenReturn(true);
        when(jwtService.isAccessToken("valid-access-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-access-token")).thenReturn(userId);
        when(jwtService.extractTokenId("valid-access-token")).thenReturn(tokenId);
        when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)).thenReturn(Optional.of(session));
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserId(userId)).thenReturn(List.of(userRole));
        when(roleRepository.findByIdIn(List.of(roleId))).thenReturn(List.of(role));
        when(permissionRepository.findCodesByRoleIds(List.of(roleId))).thenReturn(List.of("USERS_CREATE"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isEqualTo("manager@pos.local");
        assertThat(principal.getFirstName()).isEqualTo("Maria");
        assertThat(principal.getLastName()).isEqualTo("Manager");
        assertThat(principal.getPhone()).isEqualTo("+49-555-0101");
        assertThat(principal.isActive()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_MANAGER", "USERS_CREATE");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should leave the security context empty when no active user is found")
    void shouldLeaveContextEmptyWhenUserIsMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.setServletPath("/auth/me");
        request.addHeader("Authorization", "Bearer valid-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        UserSession session = UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .sessionType("PASSWORD")
                .refreshTokenHash("hash")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                .build();

        when(jwtService.isValid("valid-access-token")).thenReturn(true);
        when(jwtService.isAccessToken("valid-access-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-access-token")).thenReturn(userId);
        when(jwtService.extractTokenId("valid-access-token")).thenReturn(tokenId);
        when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)).thenReturn(Optional.of(session));
        when(userRepository.findActiveById(userId)).thenReturn(Optional.empty());

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
