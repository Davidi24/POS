package pos.pos.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pos.pos.auth.dto.AuthTokensResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.dto.RefreshRequest;
import pos.pos.auth.service.AuthLoginService;
import pos.pos.auth.service.AuthLogoutService;
import pos.pos.auth.service.AuthRefreshService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.user.dto.UserResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceAuthControllerTest {

    @Mock
    private AuthLoginService authLoginService;

    @Mock
    private AuthRefreshService authRefreshService;

    @Mock
    private AuthLogoutService authLogoutService;

    @Mock
    private ClientInfoExtractor clientInfoExtractor;

    @InjectMocks
    private DeviceAuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UserResponse USER = UserResponse.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .email("cashier@pos.local")
            .firstName("John")
            .lastName("Doe")
            .isActive(true)
            .roles(List.of("CASHIER"))
            .build();

    private static final AuthTokensResponse AUTH_TOKENS = AuthTokensResponse.builder()
            .accessToken("access.token.here")
            .refreshToken("refresh.token.here")
            .tokenType("Bearer")
            .expiresIn(900L)
            .user(USER)
            .build();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        given(clientInfoExtractor.extract(any())).willReturn(new ClientInfo("127.0.0.1", "JUnit/5"));
    }

    /*
     * =========================================
     * POST /auth/device/login
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/device/login")
    class Login {

        @Test
        @DisplayName("Should return 200 with all token fields on valid credentials")
        void shouldReturn200_onValidCredentials() throws Exception {
            given(authLoginService.login(any(), any())).willReturn(AUTH_TOKENS);

            mockMvc.perform(post("/auth/device/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access.token.here"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh.token.here"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.user.email").value("cashier@pos.local"))
                    .andExpect(jsonPath("$.user.firstName").value("John"));

            verify(clientInfoExtractor).extract(any());
            verify(authLoginService).login(any(), any());
        }

        @Test
        @DisplayName("Should return 400 when email is invalid format")
        void shouldReturn400_onInvalidEmailFormat() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("not-an-email")
                    .password("SecurePass1!")
                    .build();

            mockMvc.perform(post("/auth/device/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(authLoginService);
            verifyNoInteractions(clientInfoExtractor);
        }

        @Test
        @DisplayName("Should return 400 when email is blank")
        void shouldReturn400_onBlankEmail() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("")
                    .password("SecurePass1!")
                    .build();

            mockMvc.perform(post("/auth/device/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is blank")
        void shouldReturn400_onBlankPassword() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("cashier@pos.local")
                    .password("")
                    .build();

            mockMvc.perform(post("/auth/device/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is too short")
        void shouldReturn400_onShortPassword() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("cashier@pos.local")
                    .password("short")
                    .build();

            mockMvc.perform(post("/auth/device/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 401 on invalid credentials")
        void shouldReturn401_onInvalidCredentials() throws Exception {
            given(authLoginService.login(any(), any()))
                    .willThrow(new InvalidCredentialsException("Bad credentials"));

            mockMvc.perform(post("/auth/device/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginRequest())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Bad credentials"));
        }

        @Test
        @DisplayName("Should return 400 on malformed JSON body")
        void shouldReturn400_onMalformedJson() throws Exception {
            String malformedJson = "{\"email\": \"test@test.com\", \"password\": }";

            mockMvc.perform(post("/auth/device/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authLoginService);
            verifyNoInteractions(clientInfoExtractor);
        }
    }

    /*
     * =========================================
     * POST /auth/device/refresh
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/device/refresh")
    class Refresh {

        @Test
        @DisplayName("Should return 200 with new tokens on valid refresh token")
        void shouldReturn200_onValidToken() throws Exception {
            given(authRefreshService.refresh(any(), any())).willReturn(AUTH_TOKENS);

            mockMvc.perform(post("/auth/device/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("refresh.token.here"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access.token.here"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh.token.here"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900));

            verify(clientInfoExtractor).extract(any());
            verify(authRefreshService).refresh(any(), any());
        }

        @Test
        @DisplayName("Should trim refresh token before calling refresh service")
        void shouldTrimRefreshToken_beforeRefresh() throws Exception {
            given(authRefreshService.refresh(any(), any())).willReturn(AUTH_TOKENS);

            mockMvc.perform(post("/auth/device/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("  refresh.token.here  "))))
                    .andExpect(status().isOk());

            verify(authRefreshService).refresh(eq("refresh.token.here"), any());
        }

        @Test
        @DisplayName("Should return 401 when body is absent")
        void shouldReturn401_onAbsentBody() throws Exception {
            mockMvc.perform(post("/auth/device/refresh")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when refresh token is blank")
        void shouldReturn401_onBlankToken() throws Exception {
            mockMvc.perform(post("/auth/device/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(""))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when service rejects the token")
        void shouldReturn401_onRejectedToken() throws Exception {
            given(authRefreshService.refresh(any(), any()))
                    .willThrow(new InvalidCredentialsException("Token invalid or expired"));

            mockMvc.perform(post("/auth/device/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("bad-token"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Token invalid or expired"));
        }
    }

    /*
     * =========================================
     * POST /auth/device/logout
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/device/logout")
    class Logout {

        @Test
        @DisplayName("Should return 204 and call logout service on valid token")
        void shouldReturn204_onValidToken() throws Exception {
            mockMvc.perform(post("/auth/device/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("refresh.token.here"))))
                    .andExpect(status().isNoContent());

            verify(authLogoutService).logout("refresh.token.here");
        }

        @Test
        @DisplayName("Should trim refresh token before calling logout service")
        void shouldTrimRefreshToken_beforeLogout() throws Exception {
            mockMvc.perform(post("/auth/device/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("  refresh.token.here  "))))
                    .andExpect(status().isNoContent());

            verify(authLogoutService).logout("refresh.token.here");
        }

        @Test
        @DisplayName("Should return 204 (no-op) when body is absent")
        void shouldReturn204_onAbsentBody() throws Exception {
            mockMvc.perform(post("/auth/device/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verifyNoInteractions(authLogoutService);
        }

        @Test
        @DisplayName("Should return 204 (no-op) when refresh token is blank")
        void shouldReturn204_onBlankToken() throws Exception {
            mockMvc.perform(post("/auth/device/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(""))))
                    .andExpect(status().isNoContent());

            verifyNoInteractions(authLogoutService);
        }
    }

    /*
     * =========================================
     * POST /auth/device/logout-all
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/device/logout-all")
    class LogoutAll {

        @Test
        @DisplayName("Should return 204 and call logoutAll service on valid token")
        void shouldReturn204_onValidToken() throws Exception {
            mockMvc.perform(post("/auth/device/logout-all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("refresh.token.here"))))
                    .andExpect(status().isNoContent());

            verify(authLogoutService).logoutAll("refresh.token.here");
        }

        @Test
        @DisplayName("Should return 401 when body is absent")
        void shouldReturn401_onAbsentBody() throws Exception {
            mockMvc.perform(post("/auth/device/logout-all")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 when refresh token is blank")
        void shouldReturn401_onBlankToken() throws Exception {
            mockMvc.perform(post("/auth/device/logout-all")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(""))))
                    .andExpect(status().isUnauthorized());
        }
    }

    private LoginRequest validLoginRequest() {
        return LoginRequest.builder()
                .email("cashier@pos.local")
                .password("SecurePass1!")
                .build();
    }
}
