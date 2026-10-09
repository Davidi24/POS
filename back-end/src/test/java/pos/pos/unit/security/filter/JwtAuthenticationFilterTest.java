package pos.pos.unit.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
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
import pos.pos.auth.enums.SessionType;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.AuthCookieProperties;
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

    @Mock
    private AuthCookieProperties authCookieProperties;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtService,
                userRepository,
                userRoleRepository,
                roleRepository,
                permissionRepository,
                userSessionRepository,
                authCookieProperties
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
                .username("manager.main")
                .firstName("Maria")
                .lastName("Manager")
                .phone("+49-555-0101")
                .isActive(true)
                .build();

        UserSession session = UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(SessionType.PASSWORD)
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
        assertThat(principal.getUsername()).isEqualTo("manager.main");
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
    @DisplayName("Should fall back to the access token cookie when the authorization header is malformed")
    void shouldAuthenticateWithAccessTokenCookieFallback() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.setServletPath("/auth/me");
        request.addHeader("Authorization", "Token malformed-header");
        request.setCookies(new Cookie("access-token", "  cookie-access-token  "));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(authCookieProperties.getAccessTokenName()).thenReturn("access-token");

        User user = User.builder()
                .id(userId)
                .email("cashier@pos.local")
                .username("cashier.one")
                .firstName("Casey")
                .lastName("Cashier")
                .isActive(true)
                .build();

        UserSession session = UserSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(SessionType.PASSWORD)
                .refreshTokenHash("hash")
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                .build();

        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .build();

        Role role = Role.builder()
                .id(roleId)
                .code("CASHIER")
                .name("Cashier")
                .isActive(true)
                .build();

        when(jwtService.isValid("cookie-access-token")).thenReturn(true);
        when(jwtService.isAccessToken("cookie-access-token")).thenReturn(true);
        when(jwtService.extractUserId("cookie-access-token")).thenReturn(userId);
        when(jwtService.extractTokenId("cookie-access-token")).thenReturn(tokenId);
        when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)).thenReturn(Optional.of(session));
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserId(userId)).thenReturn(List.of(userRole));
        when(roleRepository.findByIdIn(List.of(roleId))).thenReturn(List.of(role));
        when(permissionRepository.findCodesByRoleIds(List.of(roleId))).thenReturn(List.of("ORDERS_READ"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CASHIER", "ORDERS_READ");

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
                .sessionType(SessionType.PASSWORD)
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

    @Test
    @DisplayName("Should continue unauthenticated when neither header nor cookie provides an access token")
    void shouldContinueUnauthenticatedWhenNoTokenIsPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.setServletPath("/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
