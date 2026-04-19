package pos.pos.unit.user.controller;

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
import pos.pos.common.dto.PageResponse;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.user.controller.UserAdminController;
import pos.pos.user.dto.AdminPasswordResetRequest;
import pos.pos.user.dto.ReplaceUserRolesRequest;
import pos.pos.user.dto.UpdateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.service.UserAdminActionService;
import pos.pos.user.service.UserAdminService;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserAdminController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(UserAdminControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("UserAdminController security")
class UserAdminControllerSecurityTest {

    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private UserAdminActionService userAdminActionService;

    @Test
    @DisplayName("GET /users should return 200 when authenticated with USERS_READ")
    void shouldAllowGetUsersWhenAuthorized() throws Exception {
        given(userAdminService.getUsers(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(PageResponse.<UserResponse>builder().items(List.of()).page(0).size(20).totalElements(0).totalPages(0).build());

        mockMvc.perform(get("/users")
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(userAdminService);
    }

    @Test
    @DisplayName("GET /users should return 403 when missing USERS_READ")
    void shouldReturn403WhenMissingUsersRead() throws Exception {
        mockMvc.perform(get("/users")
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_UPDATE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(userAdminService);
    }

    @Test
    @DisplayName("PUT /users/{userId} should return 200 when authenticated with USERS_UPDATE")
    void shouldAllowUpdateWhenAuthorized() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("+15550200")
                .isActive(true)
                .build();

        given(userAdminService.updateUser(any(), any(), any())).willReturn(
                UserResponse.builder().id(TARGET_USER_ID).username("cashier.one").roles(List.of("WAITER")).build()
        );

        mockMvc.perform(put("/users/{userId}", TARGET_USER_ID)
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_UPDATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /users/{userId}/roles should return 403 when missing USERS_UPDATE")
    void shouldRejectReplaceRolesWhenMissingUsersUpdate() throws Exception {
        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest();
        request.setRoleIds(Set.of(ROLE_ID));

        mockMvc.perform(put("/users/{userId}/roles", TARGET_USER_ID)
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(userAdminService);
    }

    @Test
    @DisplayName("DELETE /users/{userId} should return 204 when authenticated with USERS_DELETE")
    void shouldAllowDeleteWhenAuthorized() throws Exception {
        mockMvc.perform(delete("/users/{userId}", TARGET_USER_ID)
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_DELETE"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /users/{userId}/reset-password should return 204 when authenticated with USERS_UPDATE")
    void shouldAllowResetPasswordWhenAuthorized() throws Exception {
        AdminPasswordResetRequest request = new AdminPasswordResetRequest();

        mockMvc.perform(post("/users/{userId}/reset-password", TARGET_USER_ID)
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_UPDATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /users/{userId}/send-phone-verification should return 403 when missing USERS_UPDATE")
    void shouldRejectSendPhoneVerificationWhenMissingUsersUpdate() throws Exception {
        mockMvc.perform(post("/users/{userId}/send-phone-verification", TARGET_USER_ID)
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "USERS_READ"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(userAdminActionService);
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
