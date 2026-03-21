package pos.pos.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pos.pos.auth.dto.RegisterRequest;
import pos.pos.auth.service.AuthService;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.CookieService;
import pos.pos.user.dto.UserResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerRegisterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private ClientInfoExtractor clientInfoExtractor;

    @MockBean
    private CookieService cookieService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("POST /auth/register returns 200 and forwards the request to the service")
    void shouldReturn200AndCallServiceWhenRequestIsValid() throws Exception {
        UserResponse response = UserResponse.builder()
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .isActive(true)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.isActive").value(true));

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture());
        assertEquals("john@example.com", captor.getValue().getEmail());
        assertEquals("Password123", captor.getValue().getPassword());
        assertEquals("John", captor.getValue().getFirstName());
        assertEquals("Doe", captor.getValue().getLastName());
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when email already exists")
    void shouldReturn400WhenEmailAlreadyExists() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Email already in use"));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when body is missing")
    void shouldReturn400WhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when JSON is invalid")
    void shouldReturn400WhenJsonIsInvalid() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJsonBody()))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when all required fields are missing")
    void shouldReturn400WhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest())))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when email format is invalid")
    void shouldReturn400WhenEmailFormatIsInvalid() throws Exception {
        RegisterRequest request = validRequest();
        request.setEmail("invalid-email");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when email is blank")
    void shouldReturn400WhenEmailIsBlank() throws Exception {
        RegisterRequest request = validRequest();
        request.setEmail("");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when password is blank")
    void shouldReturn400WhenPasswordIsBlank() throws Exception {
        RegisterRequest request = validRequest();
        request.setPassword("");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when password is too short")
    void shouldReturn400WhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = validRequest();
        request.setPassword("1234567");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when password is too long")
    void shouldReturn400WhenPasswordIsTooLong() throws Exception {
        RegisterRequest request = validRequest();
        request.setPassword("a".repeat(101));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when first name is blank")
    void shouldReturn400WhenFirstNameIsBlank() throws Exception {
        RegisterRequest request = validRequest();
        request.setFirstName("");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when first name is too long")
    void shouldReturn400WhenFirstNameIsTooLong() throws Exception {
        RegisterRequest request = validRequest();
        request.setFirstName("a".repeat(51));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when last name is blank")
    void shouldReturn400WhenLastNameIsBlank() throws Exception {
        RegisterRequest request = validRequest();
        request.setLastName("");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 400 when last name is too long")
    void shouldReturn400WhenLastNameIsTooLong() throws Exception {
        RegisterRequest request = validRequest();
        request.setLastName("a".repeat(51));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register returns 415 when content type is unsupported")
    void shouldReturn415WhenContentTypeIsUnsupported() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("invalid"))
                .andExpect(status().isUnsupportedMediaType());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/register propagates unhandled runtime exceptions")
    void shouldPropagateUnhandledRuntimeException()  {
        when(authService.register(any(RegisterRequest.class))).thenThrow(new RuntimeException("boom"));

        assertThrows(ServletException.class, () -> mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest()))));
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@example.com");
        request.setPassword("Password123");
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }

    private byte[] invalidJsonBody() {
        return new byte[]{'{'};
    }
}
