package pos.pos.unit.payment;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import pos.pos.fraud.controller.FraudController;
import pos.pos.fraud.service.FraudService;
import pos.pos.payment.controller.BranchPaymentController;
import pos.pos.payment.controller.OrderPaymentController;
import pos.pos.payment.service.PaymentQueryService;
import pos.pos.payment.service.PaymentService;
import pos.pos.report.controller.StatisticsController;
import pos.pos.report.service.StatisticsService;
import pos.pos.security.config.JwtAuthenticationEntryPoint;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.principal.AuthenticatedUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {OrderPaymentController.class, BranchPaymentController.class, StatisticsController.class, FraudController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(MoneyAndReportsControllerSecurityTest.TestSecurityConfig.class)
@DisplayName("Payments, statistics and fraud endpoints: who may call them")
class MoneyAndReportsControllerSecurityTest {

    private static final String R = "/restaurants/" + UUID.randomUUID();
    private static final String O = R + "/orders/" + UUID.randomUUID();
    private static final String P = UUID.randomUUID().toString();
    private static final String DATES = "?from=2026-10-01&to=2026-10-06";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private PaymentService paymentService;
    @MockBean private PaymentQueryService paymentQueryService;
    @MockBean private StatisticsService statisticsService;
    @MockBean private FraudService fraudService;

    static Stream<Arguments> endpoints() {
        String take = "{\"method\":\"CARD\",\"amount\":10.00}";
        String refund = "{\"amount\":1.00,\"reason\":\"Cold food\"}";
        String cancel = "{\"reason\":\"Wrong method\"}";
        String review = "{\"status\":\"REVIEWED\"}";
        return Stream.of(
                Arguments.of("GET", O + "/payments", null, "ORDER_READ"),
                Arguments.of("POST", O + "/payments", take, "ORDER_CLOSE"),
                Arguments.of("POST", O + "/payments/" + P + "/refund", refund, "PAYMENT_REFUND"),
                Arguments.of("POST", O + "/payments/" + P + "/void", cancel, "ORDER_VOID"),
                Arguments.of("GET", O + "/receipt", null, "ORDER_READ"),
                Arguments.of("GET", R + "/payments/" + P, null, "ORDER_READ"),
                Arguments.of("GET", R + "/branches/" + UUID.randomUUID() + "/payments", null, "ORDER_READ"),
                Arguments.of("GET", R + "/statistics/overview" + DATES, null, "REPORTS_READ"),
                Arguments.of("GET", R + "/statistics/sales" + DATES, null, "REPORTS_READ"),
                Arguments.of("GET", R + "/statistics/staff" + DATES, null, "REPORTS_READ"),
                Arguments.of("GET", R + "/statistics/reports", null, "REPORTS_READ"),
                Arguments.of("GET", R + "/fraud/overview" + DATES, null, "FRAUD_READ"),
                Arguments.of("GET", R + "/fraud/alerts" + DATES, null, "FRAUD_READ"),
                Arguments.of("GET", R + "/fraud/activity" + DATES, null, "FRAUD_READ"),
                Arguments.of("GET", R + "/fraud/rules", null, "FRAUD_READ"),
                Arguments.of("PUT", R + "/fraud/alerts/HIGH_TIP:" + P + "/review", review, "FRAUD_REVIEW")
        );
    }

    private MockHttpServletRequestBuilderAdapter request(String method, String path, String body) {
        var builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return new MockHttpServletRequestBuilderAdapter(builder);
    }

    private record MockHttpServletRequestBuilderAdapter(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        MockHttpServletRequestBuilderAdapter as(String authorities) {
            builder.header("X-Test-User", "someone@pos.local").header("X-Test-Authorities", authorities);
            return this;
        }
    }

    @ParameterizedTest(name = "{0} {1} needs {3}")
    @MethodSource("endpoints")
    @DisplayName("refuses callers without the permission, before any work is done")
    void refusesWithoutPermission(String method, String path, String body, String authority) throws Exception {
        // Every other permission of this group, but not the one needed.
        String others = Stream.of("ORDER_READ", "ORDER_CLOSE", "PAYMENT_REFUND", "ORDER_VOID", "REPORTS_READ", "FRAUD_READ", "FRAUD_REVIEW")
                .filter(code -> !code.equals(authority)).reduce((a, b) -> a + "," + b).orElse("");
        mockMvc.perform(request(method, path, body).as(others).builder())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
        mockMvc.perform(request(method, path, body).builder()).andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(mockingDetails(paymentService).getInvocations()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(mockingDetails(statisticsService).getInvocations()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(mockingDetails(fraudService).getInvocations()).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} with {3}")
    @MethodSource("endpoints")
    @DisplayName("lets callers with the permission through")
    void allowsWithPermission(String method, String path, String body, String authority) throws Exception {
        mockMvc.perform(request(method, path, body).as(authority).builder())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isBetween(200, 299));
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
                var authorities = Arrays.stream(String.valueOf(request.getHeader("X-Test-Authorities")).split(","))
                        .map(String::trim).filter(value -> !value.isEmpty() && !value.equals("null"))
                        .map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        AuthenticatedUser.builder().id(UUID.nameUUIDFromBytes(user.getBytes())).email(user).username("someone").active(true).build(),
                        null, authorities));
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
