package pos.pos.unit.order.controller;

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
import pos.pos.order.controller.BranchOrderController;
import pos.pos.order.controller.PublicOrderController;
import pos.pos.order.controller.RestaurantOrderController;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.service.OrderPublicService;
import pos.pos.order.service.OrderService;
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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                RestaurantOrderController.class,
                BranchOrderController.class,
                PublicOrderController.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(OrderControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("Order controller security")
class OrderControllerSecurityTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000921");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000922");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000923");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;
    @MockBean
    private OrderPublicService orderPublicService;

    @Test
    @DisplayName("GET restaurant orders should allow ORDER_READ")
    void shouldAllowOrderRead() throws Exception {
        given(orderService.getOrders(any(), any(), any(), any()))
                .willReturn(List.of(OrderResponse.builder().id(ORDER_ID).orderNumber("ORD-SEC001").status(OrderStatus.OPEN).build()));

        mockMvc.perform(get("/restaurants/{restaurantId}/orders", RESTAURANT_ID)
                        .header("X-Test-User", "order.reader@pos.local")
                        .header("X-Test-Authorities", "ORDER_READ"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST branch order should reject missing ORDER_CREATE")
    void shouldRejectMissingOrderCreate() throws Exception {
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "guestCount", 2,
                "items", List.of(java.util.Map.of(
                        "menuItemId", UUID.fromString("00000000-0000-0000-0000-000000000924"),
                        "quantity", 1
                ))
        ));

        mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/orders", RESTAURANT_ID, BRANCH_ID)
                        .header("X-Test-User", "order.reader@pos.local")
                        .header("X-Test-Authorities", "ORDER_READ")
                        .contentType(APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(orderService, orderPublicService);
    }

    @Test
    @DisplayName("GET public order should allow anonymous access")
    void shouldAllowAnonymousPublicOrder() throws Exception {
        given(orderPublicService.getPublicOrder("ORD-PUBLIC"))
                .willReturn(OrderResponse.builder().id(ORDER_ID).orderNumber("ORD-PUBLIC").status(OrderStatus.OPEN).build());

        mockMvc.perform(get("/public/orders/{orderNumber}", "ORD-PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-PUBLIC"));
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
