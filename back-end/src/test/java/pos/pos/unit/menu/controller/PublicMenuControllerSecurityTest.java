package pos.pos.unit.menu.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import pos.pos.menu.controller.PublicMenuController;
import pos.pos.menu.dto.PublicMenuResponse;
import pos.pos.menu.service.PublicMenuService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PublicMenuController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(PublicMenuControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("PublicMenuController security")
class PublicMenuControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000371");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000372");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET public menu endpoints should allow unauthenticated access")
    void shouldAllowUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/public/restaurants/{restaurantId}/menus", RESTAURANT_ID))
                .andExpect(status().isOk());

        mockMvc.perform(get("/public/restaurants/{restaurantId}/menus/{menuId}", RESTAURANT_ID, MENU_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Non-public endpoints should still require authentication")
    void shouldRequireAuthenticationOutsidePublicMenuRoutes() throws Exception {
        mockMvc.perform(get("/menus").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
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
                            .requestMatchers("/public/restaurants/*/menus", "/public/restaurants/*/menus/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                    .build();
        }

        @Bean
        StubPublicMenuService publicMenuService() {
            return new StubPublicMenuService();
        }
    }

    static class StubPublicMenuService extends PublicMenuService {

        StubPublicMenuService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<PublicMenuResponse> getMenus(UUID restaurantId) {
            return List.of();
        }

        @Override
        public PublicMenuResponse getMenu(UUID restaurantId, UUID menuId, boolean includeSections, boolean includeItems) {
            return PublicMenuResponse.builder().id(menuId).build();
        }
    }

    static class HeaderAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            SecurityContextHolder.clearContext();

            String user = request.getHeader("X-Test-User");
            if (user != null && !user.isBlank()) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                AuthenticatedUser.builder()
                                        .id(UUID.nameUUIDFromBytes(user.getBytes()))
                                        .email(user)
                                        .username(user.substring(0, user.indexOf('@')))
                                        .active(true)
                                        .build(),
                                null,
                                List.of()
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
