package pos.pos.unit.settings.controller;

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
import pos.pos.settings.controller.SettingsController;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.UpdateRestaurantSettingsRequest;
import pos.pos.settings.enums.WeekStartDay;
import pos.pos.settings.service.SettingsService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SettingsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(SettingsControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("SettingsController security")
class SettingsControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettingsService settingsService;

    @Test
    @DisplayName("GET /restaurants/{restaurantId}/settings should return 200 with SETTINGS_READ")
    void shouldAllowGetSettingsWhenAuthorized() throws Exception {
        given(settingsService.getSettings(any(), any()))
                .willReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        mockMvc.perform(get("/restaurants/{restaurantId}/settings", RESTAURANT_ID)
                        .header("X-Test-User", "manager@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(RESTAURANT_ID.toString()));
    }

    @Test
    @DisplayName("GET /restaurants/{restaurantId}/settings should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/restaurants/{restaurantId}/settings", RESTAURANT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(settingsService);
    }

    @Test
    @DisplayName("PUT /restaurants/{restaurantId}/settings should return 403 without SETTINGS_UPDATE")
    void shouldRejectPutWhenMissingSettingsUpdate() throws Exception {
        UpdateRestaurantSettingsRequest request = UpdateRestaurantSettingsRequest.builder()
                .defaultLanguage("en")
                .dateFormat("yyyy-MM-dd")
                .timeFormat("HH:mm")
                .weekStartDay(WeekStartDay.MONDAY)
                .reservationSlotMinutes(15)
                .defaultTableTurnTimeMinutes(90)
                .serviceChargeEnabled(false)
                .cashRoundingEnabled(false)
                .allowSplitBills(true)
                .allowOpenTickets(true)
                .requireCustomerForInvoice(false)
                .enableQrOrdering(false)
                .enableTakeaway(true)
                .enableDelivery(false)
                .build();

        mockMvc.perform(put("/restaurants/{restaurantId}/settings", RESTAURANT_ID)
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(settingsService);
    }

    @Test
    @DisplayName("POST /restaurants/{restaurantId}/settings/reset should return 200 with SETTINGS_UPDATE")
    void shouldAllowResetWhenAuthorized() throws Exception {
        given(settingsService.resetSettings(any(), any()))
                .willReturn(SettingsResponse.builder().restaurantId(RESTAURANT_ID).build());

        mockMvc.perform(post("/restaurants/{restaurantId}/settings/reset", RESTAURANT_ID)
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(RESTAURANT_ID.toString()));
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
