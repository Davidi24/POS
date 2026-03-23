package pos.pos.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pos.pos.auth.dto.AuthTokensResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.dto.LoginResponse;
import pos.pos.auth.service.AuthService;
import pos.pos.security.filter.JwtAuthenticationFilter;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.CookieService;
import pos.pos.user.dto.UserResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerLoginTest {

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
    @DisplayName("POST /auth/login should return 200 and set refresh cookie")
    void login_shouldReturnOkAndSetRefreshCookie() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .build();

        ClientInfo clientInfo = new ClientInfo("127.0.0.1", "JUnit");
        UUID userId = UUID.randomUUID();

        UserResponse user = UserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .firstName("David")
                .lastName("Keci")
                .build();

        AuthTokensResponse authResult = AuthTokensResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(user)
                .build();

        when(clientInfoExtractor.extract(any(HttpServletRequest.class))).thenReturn(clientInfo);
        when(authService.login(any(LoginRequest.class), any(ClientInfo.class))).thenReturn(authResult);
        doNothing().when(cookieService).addRefreshTokenCookie(any(HttpServletResponse.class), any(String.class));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.firstName").value("David"))
                .andExpect(jsonPath("$.user.lastName").value("Keci"));

        ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        ArgumentCaptor<ClientInfo> clientInfoCaptor = ArgumentCaptor.forClass(ClientInfo.class);

        verify(clientInfoExtractor, times(1)).extract(any(HttpServletRequest.class));
        verify(authService, times(1)).login(requestCaptor.capture(), clientInfoCaptor.capture());
        verify(cookieService, times(1)).addRefreshTokenCookie(any(HttpServletResponse.class), any(String.class));

        assertEquals("test@example.com", requestCaptor.getValue().getEmail());
        assertEquals("Password123!", requestCaptor.getValue().getPassword());
        assertEquals(clientInfo, clientInfoCaptor.getValue());
    }

    @Test
    @DisplayName("POST /auth/login should pass refresh token to cookie service")
    void login_shouldPassRefreshTokenToCookieService() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .build();

        ClientInfo clientInfo = new ClientInfo("127.0.0.1", "JUnit");

        AuthTokensResponse authResult = AuthTokensResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token-123")
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(UserResponse.builder()
                        .id(UUID.randomUUID())
                        .email("test@example.com")
                        .firstName("David")
                        .lastName("Keci")
                        .build())
                .build();

        when(clientInfoExtractor.extract(any(HttpServletRequest.class))).thenReturn(clientInfo);
        when(authService.login(any(LoginRequest.class), any(ClientInfo.class))).thenReturn(authResult);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(cookieService).addRefreshTokenCookie(any(HttpServletResponse.class), refreshTokenCaptor.capture());
        assertEquals("refresh-token-123", refreshTokenCaptor.getValue());
    }

    @Test
    @DisplayName("POST /auth/login should return 400 when request body is invalid")
    void login_shouldReturnBadRequest_whenBodyIsInvalid() throws Exception {
        String invalidRequest = """
                {
                  "email": "",
                  "password": ""
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(clientInfoExtractor, never()).extract(any(HttpServletRequest.class));
        verify(authService, never()).login(any(LoginRequest.class), any(ClientInfo.class));
        verify(cookieService, never()).addRefreshTokenCookie(any(HttpServletResponse.class), any(String.class));
    }

    @Test
    @DisplayName("POST /auth/login should return 400 when required fields are missing")
    void login_shouldReturnBadRequest_whenRequiredFieldsAreMissing() throws Exception {
        String invalidRequest = """
                {
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(clientInfoExtractor, never()).extract(any(HttpServletRequest.class));
        verify(authService, never()).login(any(LoginRequest.class), any(ClientInfo.class));
        verify(cookieService, never()).addRefreshTokenCookie(any(HttpServletResponse.class), any(String.class));
    }

    @Test
    @DisplayName("POST /auth/login should return 400 when JSON is malformed")
    void login_shouldReturnBadRequest_whenJsonIsMalformed() throws Exception {
        String malformedJson = """
                {
                  "email": "test@example.com",
                  "password": "Password123!"
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());

        verify(clientInfoExtractor, never()).extract(any(HttpServletRequest.class));
        verify(authService, never()).login(any(LoginRequest.class), any(ClientInfo.class));
        verify(cookieService, never()).addRefreshTokenCookie(any(HttpServletResponse.class), any(String.class));
    }

}
