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
import pos.pos.tables.controller.TableCategoryController;
import pos.pos.tables.dto.TableCategoryResponse;
import pos.pos.tables.service.TableCategoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TableCategoryController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(TableCategoryControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("TableCategoryController security")
class TableCategoryControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000621");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000622");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000623");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TableCategoryService tableCategoryService;

    @Test
    @DisplayName("GET table categories should allow SETTINGS_READ")
    void shouldAllowReadWithSettingsReadPermission() throws Exception {
        given(tableCategoryService.getTableCategories(any(), any(), any()))
                .willReturn(List.of(TableCategoryResponse.builder().id(CATEGORY_ID).name("Patio").build()));

        mockMvc.perform(get("/restaurants/{restaurantId}/branches/{branchId}/table-categories", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST table category should reject missing SETTINGS_UPDATE")
    void shouldRejectCreateWithoutSettingsUpdatePermission() throws Exception {
        String request = objectMapper.writeValueAsString(new CreateRequest("patio", "Patio", 4, "PATIO", "#22AA66", 0, true));

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/table-categories", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "owner@pos.local")
                        .header("X-Test-Authorities", "SETTINGS_READ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(tableCategoryService);
    }

    private record CreateRequest(
            String code,
            String name,
            Integer defaultCapacity,
            String locationType,
            String color,
            Integer displayOrder,
            Boolean active
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
