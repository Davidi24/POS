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
import pos.pos.restaurant.controller.RestaurantAdminController;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.UpdateRestaurantStatusRequest;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantAdminService;
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
        controllers = RestaurantAdminController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(RestaurantAdminControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("RestaurantAdminController security")
class RestaurantAdminControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestaurantAdminService restaurantAdminService;

    @Test
    @DisplayName("GET /restaurants should return 200 when authenticated with RESTAURANTS_READ")
    void shouldAllowGetRestaurantsWhenAuthorized() throws Exception {
        given(restaurantAdminService.getRestaurants(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(PageResponse.<RestaurantResponse>builder().items(List.of()).page(0).size(20).totalElements(0).totalPages(0).build());

        mockMvc.perform(get("/restaurants")
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /restaurants should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/restaurants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(restaurantAdminService);
    }

    @Test
    @DisplayName("GET /restaurants should return 403 when missing RESTAURANTS_READ")
    void shouldReturn403WhenMissingRestaurantsRead() throws Exception {
        mockMvc.perform(get("/restaurants")
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_UPDATE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(restaurantAdminService);
    }

    @Test
    @DisplayName("POST /restaurants should return 201 when authenticated with RESTAURANTS_CREATE")
    void shouldAllowCreateWhenAuthorized() throws Exception {
        CreateRestaurantRequest request = CreateRestaurantRequest.builder()
                .name("POS Main")
                .legalName("POS Main LLC")
                .currency("USD")
                .timezone("Europe/Berlin")
                .build();

        given(restaurantAdminService.createRestaurant(any(), any()))
                .willReturn(RestaurantResponse.builder().id(RESTAURANT_ID).name("POS Main").build());

        mockMvc.perform(post("/restaurants")
                        .header("X-Test-User", "super.admin@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PATCH /restaurants/{restaurantId}/status should return 200 when authenticated with RESTAURANTS_UPDATE")
    void shouldAllowPatchStatusWhenAuthorized() throws Exception {
        UpdateRestaurantStatusRequest request = new UpdateRestaurantStatusRequest();
        request.setIsActive(false);
        request.setStatus(RestaurantStatus.SUSPENDED);

        given(restaurantAdminService.updateRestaurantStatus(any(), any(), any()))
                .willReturn(RestaurantResponse.builder().id(RESTAURANT_ID).isActive(false).status(RestaurantStatus.SUSPENDED).build());

        mockMvc.perform(patch("/restaurants/{restaurantId}/status", RESTAURANT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_UPDATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /restaurants/{restaurantId} should return 403 when missing RESTAURANTS_DELETE")
    void shouldRejectDeleteWhenMissingRestaurantsDelete() throws Exception {
        mockMvc.perform(delete("/restaurants/{restaurantId}", RESTAURANT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_UPDATE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(restaurantAdminService);
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
