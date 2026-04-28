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
import pos.pos.restaurant.controller.RestaurantAddressController;
import pos.pos.restaurant.controller.RestaurantBrandingController;
import pos.pos.restaurant.controller.RestaurantContactController;
import pos.pos.restaurant.controller.RestaurantTaxProfileController;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.dto.RestaurantTaxProfileResponse;
import pos.pos.restaurant.service.RestaurantAddressService;
import pos.pos.restaurant.service.RestaurantBrandingService;
import pos.pos.restaurant.service.RestaurantContactService;
import pos.pos.restaurant.service.RestaurantTaxProfileService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                RestaurantBrandingController.class,
                RestaurantAddressController.class,
                RestaurantContactController.class,
                RestaurantTaxProfileController.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(RestaurantResourceControllersSecurityTest.TestSecurityConfig.class)
@DisplayName("Restaurant resource controllers security")
class RestaurantResourceControllersSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TAX_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestaurantBrandingService restaurantBrandingService;

    @MockBean
    private RestaurantAddressService restaurantAddressService;

    @MockBean
    private RestaurantContactService restaurantContactService;

    @MockBean
    private RestaurantTaxProfileService restaurantTaxProfileService;

    @Test
    @DisplayName("GET branding should allow RESTAURANTS_READ")
    void shouldAllowReadBranding() throws Exception {
        given(restaurantBrandingService.getBranding(any(), any()))
                .willReturn(RestaurantBrandingResponse.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(get("/restaurants/{restaurantId}/branding", RESTAURANT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT branding should return 401 when unauthenticated")
    void shouldRequireAuthenticationForBrandingUpdate() throws Exception {
        mockMvc.perform(put("/restaurants/{restaurantId}/branding", RESTAURANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST address should return 403 without RESTAURANTS_UPDATE")
    void shouldRejectAddressCreateWithoutUpdatePermission() throws Exception {
        String request = """
                {
                  "addressType": "PHYSICAL",
                  "country": "Albania",
                  "city": "Tirana",
                  "streetLine1": "Main Street"
                }
                """;

        mockMvc.perform(post("/restaurants/{restaurantId}/addresses", RESTAURANT_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "RESTAURANTS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(restaurantAddressService);
    }

    @Test
    @DisplayName("PATCH tax profile default should allow RESTAURANTS_UPDATE")
    void shouldAllowDefaultTaxProfileWithUpdatePermission() throws Exception {
        given(restaurantTaxProfileService.makeDefault(any(), any(), any()))
                .willReturn(RestaurantTaxProfileResponse.builder().id(TAX_PROFILE_ID).isDefault(true).build());

        mockMvc.perform(patch("/restaurants/{restaurantId}/tax-profiles/{taxProfileId}/default", RESTAURANT_ID, TAX_PROFILE_ID)
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
