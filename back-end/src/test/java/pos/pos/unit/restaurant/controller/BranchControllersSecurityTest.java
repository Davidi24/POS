package pos.pos.unit.restaurant.controller;

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
import pos.pos.restaurant.controller.BranchAddressController;
import pos.pos.restaurant.controller.BranchAdminController;
import pos.pos.restaurant.controller.BranchContactController;
import pos.pos.restaurant.dto.BranchResponse;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.service.BranchAddressService;
import pos.pos.restaurant.service.BranchAdminService;
import pos.pos.restaurant.service.BranchContactService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                BranchAdminController.class,
                BranchAddressController.class,
                BranchContactController.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(BranchControllersSecurityTest.TestSecurityConfig.class)
@DisplayName("Branch controllers security")
class BranchControllersSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID CONTACT_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BranchAdminService branchAdminService;

    @MockBean
    private BranchAddressService branchAddressService;

    @MockBean
    private BranchContactService branchContactService;

    @Test
    @DisplayName("GET branches should allow RESTAURANTS_READ")
    void shouldAllowBranchRead() throws Exception {
        given(branchAdminService.getBranches(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(PageResponse.<BranchResponse>builder().items(List.of()).page(0).size(20).totalElements(0).totalPages(0).build());

        mockMvc.perform(get("/restaurants/{restaurantId}/branches", RESTAURANT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST branch should require authentication")
    void shouldRequireAuthenticationForBranchCreate() throws Exception {
        String request = """
                {
                  "name": "Downtown",
                  "code": "downtown"
                }
                """;

        mockMvc.perform(post("/restaurants/{restaurantId}/branches", RESTAURANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("DELETE branch should return 403 without RESTAURANTS_UPDATE")
    void shouldRejectBranchDeleteWithoutUpdatePermission() throws Exception {
        mockMvc.perform(delete("/restaurants/{restaurantId}/branches/{branchId}", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_READ"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(branchAdminService);
    }

    @Test
    @DisplayName("PATCH branch contact primary should allow RESTAURANTS_UPDATE")
    void shouldAllowBranchContactPrimaryWithUpdatePermission() throws Exception {
        given(branchContactService.makePrimary(any(), any(), any(), any()))
                .willReturn(ContactResponse.builder().id(CONTACT_ID).isPrimary(true).build());

        mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/contacts/{contactId}/primary", RESTAURANT_ID, BRANCH_ID, CONTACT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_UPDATE"))
                .andExpect(status().isOk());
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
