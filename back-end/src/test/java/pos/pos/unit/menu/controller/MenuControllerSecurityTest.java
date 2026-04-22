package pos.pos.unit.menu.controller;

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
import pos.pos.menu.controller.MenuController;
import pos.pos.menu.dto.CreateMenuRequest;
import pos.pos.menu.dto.MenuResponse;
import pos.pos.menu.service.MenuService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MenuController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(MenuControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("MenuController security")
class MenuControllerSecurityTest {

    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000341");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000342");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /menus should return 200 when authenticated with MENUS_READ")
    void shouldAllowGetMenusWhenAuthorized() throws Exception {
        mockMvc.perform(get("/menus")
                        .header("X-Test-User", "menu-admin@pos.local")
                        .header("X-Test-Authorities", "MENUS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /menus should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/menus"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /menus should return 403 when missing MENUS_CREATE")
    void shouldRejectCreateWhenMissingAuthority() throws Exception {
        CreateMenuRequest request = CreateMenuRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .name("Lunch Specials")
                .build();

        mockMvc.perform(post("/menus")
                        .header("X-Test-User", "menu-admin@pos.local")
                        .header("X-Test-Authorities", "MENUS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @DisplayName("DELETE /menus/{menuId} should return 204 when authenticated with MENUS_DELETE")
    void shouldAllowDeleteWhenAuthorized() throws Exception {
        mockMvc.perform(delete("/menus/{menuId}", MENU_ID)
                        .header("X-Test-User", "menu-admin@pos.local")
                        .header("X-Test-Authorities", "MENUS_DELETE"))
                .andExpect(status().isNoContent());
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

        @Bean
        StubMenuService menuService() {
            return new StubMenuService();
        }
    }

    static class StubMenuService extends MenuService {

        StubMenuService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public PageResponse<MenuResponse> getMenus(
                UUID restaurantId,
                Boolean active,
                String search,
                Integer page,
                Integer size,
                String sortBy,
                String direction
        ) {
            return PageResponse.<MenuResponse>builder()
                    .items(List.of())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build();
        }

        @Override
        public void deleteMenu(UUID menuId) {
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
