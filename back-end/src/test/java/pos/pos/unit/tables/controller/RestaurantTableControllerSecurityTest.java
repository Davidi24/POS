package pos.pos.unit.tables.controller;

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
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.tables.controller.RestaurantTableController;
import pos.pos.tables.controller.TableLayoutController;
import pos.pos.tables.dto.TableLayoutResponse;
import pos.pos.tables.dto.TableResponse;
import pos.pos.tables.service.RestaurantTableService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                RestaurantTableController.class,
                TableLayoutController.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(RestaurantTableControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("RestaurantTableController security")
class RestaurantTableControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000721");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000722");
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000723");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestaurantTableService restaurantTableService;

    @Test
    @DisplayName("GET tables should allow SETTINGS_READ")
    void shouldAllowTableReadWithSettingsReadPermission() throws Exception {
        given(restaurantTableService.getTables(any(), any(), any()))
                .willReturn(List.of(TableResponse.builder().id(TABLE_ID).tableNumber("A1").build()));

        mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/tables", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST table should reject missing SETTINGS_UPDATE")
    void shouldRejectTableCreateWithoutSettingsUpdatePermission() throws Exception {
        String request = objectMapper.writeValueAsString(new CreateRequest("A1", "Window", 4, "Main"));

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/tables", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(restaurantTableService);
    }

    @Test
    @DisplayName("GET table layout should allow SETTINGS_READ")
    void shouldAllowTableLayoutReadWithSettingsReadPermission() throws Exception {
        given(restaurantTableService.getTableLayout(any(), any(), any()))
                .willReturn(TableLayoutResponse.builder()
                        .restaurantId(RESTAURANT_ID)
                        .branchId(BRANCH_ID)
                        .floors(List.of())
                        .tables(List.of())
                        .build());

        mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/table-layout", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ"))
                .andExpect(status().isOk());
    }

    private record CreateRequest(
            String tableNumber,
            String name,
            Integer capacity,
            String floor
    ) {
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
