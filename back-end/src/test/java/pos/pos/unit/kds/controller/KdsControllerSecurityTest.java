package pos.pos.unit.kds.controller;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.OncePerRequestFilter;
import pos.pos.kds.controller.KdsBoardController;
import pos.pos.kds.controller.KdsStationController;
import pos.pos.kds.controller.OrderKdsController;
import pos.pos.kds.dto.KdsStationResponse;
import pos.pos.kds.enums.KdsStationType;
import pos.pos.kds.service.KdsStationCommandService;
import pos.pos.kds.service.KdsStationQueryService;
import pos.pos.kds.service.KdsTicketQueryService;
import pos.pos.kds.service.KdsTicketWorkflowService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                KdsStationController.class,
                KdsBoardController.class,
                OrderKdsController.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(KdsControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("KDS controller security")
class KdsControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000001011");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000001012");
    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000001013");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000001014");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KdsStationQueryService kdsStationQueryService;
    @MockBean
    private KdsStationCommandService kdsStationCommandService;
    @MockBean
    private KdsTicketQueryService kdsTicketQueryService;
    @MockBean
    private KdsTicketWorkflowService kdsTicketWorkflowService;

    @Test
    @DisplayName("GET KDS stations should allow SETTINGS_READ")
    void shouldAllowSettingsReadForStations() throws Exception {
        given(kdsStationQueryService.getStations(any(), any(), any(), anyBoolean()))
                .willReturn(List.of(KdsStationResponse.builder()
                        .id(UUID.randomUUID())
                        .code("HOT_LINE")
                        .name("Hot Line")
                        .stationType(KdsStationType.GRILL)
                        .build()));

        mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/kds/stations", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "settings.reader@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST KDS ticket ready should reject missing ORDER_UPDATE")
    void shouldRejectMissingOrderUpdateForTicketAction() throws Exception {
        String request = objectMapper.writeValueAsString(java.util.Map.of("note", "Ready for pass"));

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/kds/tickets/{ticketId}/ready", RESTAURANT_ID, BRANCH_ID, TICKET_ID)
                        .header("X-Test-User", "order.reader@pos.local")
                        .header("X-Test-Authorities", "ORDER_READ")
                        .contentType(APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(kdsStationQueryService, kdsStationCommandService, kdsTicketQueryService, kdsTicketWorkflowService);
    }

    @Test
    @DisplayName("POST order KDS sync should reject missing ORDER_UPDATE")
    void shouldRejectMissingOrderUpdateForOrderSync() throws Exception {
        mockMvc.perform(post("/restaurants/{restaurantId}/orders/{orderId}/kds/tickets/sync", RESTAURANT_ID, ORDER_ID)
                        .header("X-Test-User", "order.reader@pos.local")
                        .header("X-Test-Authorities", "ORDER_READ"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(kdsStationQueryService, kdsStationCommandService, kdsTicketQueryService, kdsTicketWorkflowService);
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
