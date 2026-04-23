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
import org.springframework.http.HttpMethod;
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
import pos.pos.restaurant.controller.RestaurantRegistrationController;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantRegistrationService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RestaurantRegistrationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(RestaurantRegistrationControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("RestaurantRegistrationController security")
class RestaurantRegistrationControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestaurantRegistrationService restaurantRegistrationService;

    @Test
    @DisplayName("POST /restaurants/registrations should be public")
    void shouldAllowPublicRegistration() throws Exception {
        given(restaurantRegistrationService.registerRestaurant(any()))
                .willReturn(RestaurantResponse.builder().id(RESTAURANT_ID).status(RestaurantStatus.PENDING).isActive(false).build());

        mockMvc.perform(post("/restaurants/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RestaurantRegistrationRequest.builder()
                                .name("Burger House")
                                .legalName("Burger House LLC")
                                .currency("USD")
                                .timezone("America/New_York")
                                .owner(CreateRestaurantOwnerRequest.builder()
                                        .email("owner@burger.house")
                                        .username("burger.owner")
                                        .firstName("Burger")
                                        .lastName("Owner")
                                        .build())
                                .build())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PATCH /restaurants/registrations/{restaurantId}/review should return 401 when unauthenticated")
    void shouldRequireAuthenticationForReview() throws Exception {
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", RESTAURANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(restaurantRegistrationService);
    }

    @Test
    @DisplayName("PATCH /restaurants/registrations/{restaurantId}/review should return 403 when missing RESTAURANTS_UPDATE")
    void shouldRejectReviewWhenMissingRestaurantsUpdate() throws Exception {
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", RESTAURANT_ID)
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(restaurantRegistrationService);
    }

    @Test
    @DisplayName("PATCH /restaurants/registrations/{restaurantId}/review should return 200 when authorized")
    void shouldAllowReviewWhenAuthorized() throws Exception {
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        given(restaurantRegistrationService.reviewRegistration(any(), any(), any()))
                .willReturn(RestaurantResponse.builder().id(RESTAURANT_ID).status(RestaurantStatus.ACTIVE).isActive(true).build());

        mockMvc.perform(patch("/restaurants/registrations/{restaurantId}/review", RESTAURANT_ID)
                        .header("X-Test-User", "admin@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_UPDATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
                            .requestMatchers(HttpMethod.POST, "/restaurants/registrations").permitAll()
                            .anyRequest().authenticated()
                    )
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
