package pos.pos.unit.reservation.controller;

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
import pos.pos.customer.controller.CustomerController;
import pos.pos.customer.dto.CustomerResponse;
import pos.pos.customer.service.CustomerService;
import pos.pos.reservation.controller.BranchReservationController;
import pos.pos.reservation.controller.PublicReservationController;
import pos.pos.reservation.controller.RestaurantReservationController;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.service.ReservationCrudService;
import pos.pos.reservation.service.ReservationDepositService;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationNoteService;
import pos.pos.reservation.service.ReservationPublicService;
import pos.pos.reservation.service.ReservationQueryService;
import pos.pos.reservation.service.ReservationTableAssignmentService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.time.OffsetDateTime;
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
                RestaurantReservationController.class,
                BranchReservationController.class,
                PublicReservationController.class,
                CustomerController.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(ReservationControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("Reservation and customer controller security")
class ReservationControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000821");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000822");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000823");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000824");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationQueryService reservationQueryService;
    @MockBean
    private ReservationCrudService reservationCrudService;
    @MockBean
    private ReservationLifecycleService reservationLifecycleService;
    @MockBean
    private ReservationTableAssignmentService reservationTableAssignmentService;
    @MockBean
    private ReservationNoteService reservationNoteService;
    @MockBean
    private ReservationDepositService reservationDepositService;
    @MockBean
    private ReservationPublicService reservationPublicService;

    @MockBean
    private CustomerService customerService;

    @Test
    @DisplayName("GET reservations should allow SETTINGS_READ")
    void shouldAllowReservationReadWithSettingsReadPermission() throws Exception {
        given(reservationQueryService.getReservations(any(), any()))
                .willReturn(List.of(ReservationResponse.builder().id(RESERVATION_ID).reservationCode("RES_TEST").build()));

        mockMvc.perform(get("/restaurants/{restaurantId}/reservations", RESTAURANT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST reservation should reject missing SETTINGS_UPDATE")
    void shouldRejectReservationCreateWithoutSettingsUpdatePermission() throws Exception {
        String request = """
                {
                  "branchId":"%s",
                  "partySize":2,
                  "reservationStart":"2026-05-10T18:00:00Z",
                  "reservationEnd":"2026-05-10T19:00:00Z",
                  "contactName":"Alex"
                }
                """.formatted(BRANCH_ID);

        mockMvc.perform(post("/restaurants/{restaurantId}/reservations", RESTAURANT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(
                reservationQueryService,
                reservationCrudService,
                reservationLifecycleService,
                reservationTableAssignmentService,
                reservationNoteService,
                reservationDepositService,
                reservationPublicService
        );
    }

    @Test
    @DisplayName("GET public availability should allow anonymous access")
    void shouldAllowAnonymousPublicAvailability() throws Exception {
        given(reservationPublicService.getPublicAvailability(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).willReturn(List.of(ReservationAvailabilityOptionResponse.builder()
                .tableIds(List.of(UUID.fromString("00000000-0000-0000-0000-000000000825")))
                .tableNumbers(List.of("A1"))
                .primaryTableId(UUID.fromString("00000000-0000-0000-0000-000000000825"))
                .tableCount(1)
                .totalCapacity(4)
                .exactFit(false)
                .build()));

        mockMvc.perform(get("/public/restaurants/{restaurantSlug}/branches/{branchCode}/reservations/availability",
                        "reservation-restaurant",
                        "MAIN")
                        .queryParam("reservationStart", "2026-05-10T18:00:00Z")
                        .queryParam("reservationEnd", "2026-05-10T20:00:00Z")
                        .queryParam("partySize", "4"))
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
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/public/**").permitAll()
                            .anyRequest().authenticated())
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
