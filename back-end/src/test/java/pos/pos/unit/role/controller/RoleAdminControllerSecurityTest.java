package pos.pos.unit.role.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.OncePerRequestFilter;
import pos.pos.role.controller.RoleAdminController;
import pos.pos.role.dto.CloneRoleRequest;
import pos.pos.role.dto.CreateRoleRequest;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.role.dto.ReplaceRolePermissionsRequest;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.service.RoleAdminService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RoleAdminController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(RoleAdminControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("RoleAdminController security")
class RoleAdminControllerSecurityTest {

    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000233");
    private static final UUID PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000234");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoleAdminService roleAdminService;

    @Test
    @DisplayName("POST /roles should return 201 when authenticated with ROLES_CREATE")
    void shouldAllowCreateWhenAuthorized() throws Exception {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("Floor Supervisor")
                .description("Manages floor operations")
                .build();

        given(roleAdminService.createRole(any(), any())).willReturn(
                RoleResponse.builder().id(ROLE_ID).code("FLOOR_SUPERVISOR").build()
        );

        mockMvc.perform(post("/roles")
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "ROLES_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /roles should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("Floor Supervisor")
                .description("Manages floor operations")
                .build();

        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(roleAdminService);
    }

    @Test
    @DisplayName("PUT /roles/{roleId}/permissions should return 403 when missing ROLES_ASSIGN_PERMISSIONS")
    void shouldRejectReplacePermissionsWhenMissingAuthority() throws Exception {
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest();
        request.setPermissionIds(Set.of(PERMISSION_ID));

        mockMvc.perform(put("/roles/{roleId}/permissions", ROLE_ID)
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "ROLES_UPDATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(roleAdminService);
    }

    @Test
    @DisplayName("DELETE /roles/{roleId} should return 204 when authenticated with ROLES_DELETE")
    void shouldAllowDeleteWhenAuthorized() throws Exception {
        mockMvc.perform(delete("/roles/{roleId}", ROLE_ID)
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "ROLES_DELETE"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /roles/{roleId}/clone should require ROLES_CREATE")
    void shouldRequireRolesCreateForClone() throws Exception {
        CloneRoleRequest request = new CloneRoleRequest();
        request.setName("Assistant Manager");

        mockMvc.perform(post("/roles/{roleId}/clone", ROLE_ID)
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "ROLES_UPDATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(roleAdminService);
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
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            SecurityContextHolder.clearContext();

            String user = request.getHeader("X-Test-User");
            if (user != null && !user.isBlank()) {
                var authorities = Arrays.stream(Optional.ofNullable(request.getHeader("X-Test-Authorities")).orElse("").split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
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
