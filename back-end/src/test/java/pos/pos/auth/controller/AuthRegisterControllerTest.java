package pos.pos.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import pos.pos.auth.dto.RegisterRequest;
import pos.pos.auth.service.AuthService;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.user.dto.UserResponse;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthRegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@test.com");
        request.setPassword("Password123");
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }

    @Test
    void register_shouldReturnOk_whenRequestValid() throws Exception {

        RegisterRequest request = validRequest();

        UserResponse response = UserResponse.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        when(authService.register(request)).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /* EMAIL TESTS */

    @Test
    void register_shouldReturnBadRequest_whenEmailNull() throws Exception {
        RegisterRequest request = validRequest();
        request.setEmail(null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturnBadRequest_whenEmailBlank() throws Exception {
        RegisterRequest request = validRequest();
        request.setEmail(" ");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturnBadRequest_whenEmailInvalidFormat() throws Exception {
        RegisterRequest request = validRequest();
        request.setEmail("invalid-email");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /* PASSWORD TESTS */

    @Test
    void register_shouldReturnBadRequest_whenPasswordNull() throws Exception {
        RegisterRequest request = validRequest();
        request.setPassword(null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturnBadRequest_whenPasswordTooShort() throws Exception {
        RegisterRequest request = validRequest();
        request.setPassword("1234567");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturnBadRequest_whenPasswordTooLong() throws Exception {
        RegisterRequest request = validRequest();
        request.setPassword("a".repeat(101));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /* FIRST NAME TESTS */

    @Test
    void register_shouldReturnBadRequest_whenFirstNameNull() throws Exception {
        RegisterRequest request = validRequest();
        request.setFirstName(null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturnBadRequest_whenFirstNameTooLong() throws Exception {
        RegisterRequest request = validRequest();
        request.setFirstName("a".repeat(51));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /* LAST NAME TESTS */

    @Test
    void register_shouldReturnBadRequest_whenLastNameNull() throws Exception {
        RegisterRequest request = validRequest();
        request.setLastName(null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturnBadRequest_whenLastNameTooLong() throws Exception {
        RegisterRequest request = validRequest();
        request.setLastName("a".repeat(51));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}