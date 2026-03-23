package pos.pos.security.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.JwtService;
import pos.pos.support.AuthTestDataFactory;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldSkipPublicPaths() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository, userRoleRepository, roleRepository);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_shouldAuthenticateValidBearerToken() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository, userRoleRepository, roleRepository);
        FilterChain chain = mock(FilterChain.class);

        User user = AuthTestDataFactory.user();
        UUID roleId = UUID.randomUUID();
        List<UserRole> userRoles = List.of(AuthTestDataFactory.userRole(user.getId(), roleId));

        when(jwtService.isValid("token")).thenReturn(true);
        when(jwtService.isAccessToken("token")).thenReturn(true);
        when(jwtService.extractUserId("token")).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(userRoles);
        when(roleRepository.findByIdIn(anyList())).thenReturn(List.of(AuthTestDataFactory.role("ADMIN")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
    }
}
