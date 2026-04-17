package pos.pos.unit.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import pos.pos.security.config.JwtAuthenticationEntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtAuthenticationEntryPoint")
class JwtAuthenticationEntryPointTest {

    @Test
    @DisplayName("Should return the unauthorized JSON contract")
    void shouldReturnUnauthorizedJson() throws Exception {
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\": \"Unauthorized\"");
        assertThat(response.getContentAsString()).contains("\"message\": \"Authentication required\"");
    }
}
