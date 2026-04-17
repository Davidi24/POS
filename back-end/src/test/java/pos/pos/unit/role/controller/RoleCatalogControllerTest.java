package pos.pos.unit.role.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.OncePerRequestFilter;
import pos.pos.role.controller.RoleCatalogController;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.service.RoleCatalogService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RoleCatalogController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(RoleCatalogControllerTest.TestSecurityConfig.class)
@DisplayName("RoleCatalogController")
class RoleCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleCatalogService roleCatalogService;

    @Test
    @DisplayName("GET /roles/assignable should return 200 when authenticated with ROLES_READ authority")
    void shouldReturnAssignableRolesWhenAuthorized() throws Exception {
        RoleResponse manager = RoleResponse.builder()
                .id(UUID.randomUUID())
                .code("MANAGER")
                .name("Manager")
                .description("Store manager")
                .rank(20_000L)
                .isAssignable(true)
                .build();
        RoleResponse waiter = RoleResponse.builder()
                .id(UUID.randomUUID())
                .code("WAITER")
                .name("Waiter")
                .description("Handles orders")
                .rank(10_000L)
                .isAssignable(true)
                .build();

        given(roleCatalogService.getAssignableRoles(any())).willReturn(List.of(manager, waiter));

        mockMvc.perform(get("/roles/assignable")
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "ROLES_READ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MANAGER"))
                .andExpect(jsonPath("$[1].code").value("WAITER"));

        verify(roleCatalogService).getAssignableRoles(any());
    }

    @Test
    @DisplayName("GET /roles/assignable should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/roles/assignable")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(roleCatalogService);
    }

    @Test
    @DisplayName("GET /roles/assignable should return 403 when missing ROLES_READ authority")
    void shouldReturn403WhenMissingAuthority() throws Exception {
        mockMvc.perform(get("/roles/assignable")
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_READ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(roleCatalogService);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    static class HeaderAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            SecurityContextHolder.clearContext();

            String user = request.getHeader("X-Test-User");
            if (user != null && !user.isBlank()) {
                List<SimpleGrantedAuthority> authorities = Arrays.stream(Optional.ofNullable(
                                request.getHeader("X-Test-Authorities")).orElse("").split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                AuthenticatedUser.builder()
                                        .id(UUID.nameUUIDFromBytes(user.getBytes()))
                                        .email(user)
                                        .username(user.substring(0, user.indexOf('@')))
                                        .active(true)
                                        .build(),
                                null,
                                authorities
                        )
                );
            }

            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
