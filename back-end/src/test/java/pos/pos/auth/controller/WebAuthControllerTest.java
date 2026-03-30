package pos.pos.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import pos.pos.auth.service.AuthLoginService;
import pos.pos.auth.service.AuthLogoutService;
import pos.pos.auth.service.AuthRefreshService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.CookieService;
import pos.pos.user.dto.UserResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebAuthControllerTest {

    @Mock
    private AuthLoginService authLoginService;

    @Mock
    private AuthRefreshService authRefreshService;

    @Mock
    private AuthLogoutService authLogoutService;

    @Mock
    private ClientInfoExtractor clientInfoExtractor;

    @Mock
    private CookieService cookieService;

    @InjectMocks
    private WebAuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN = "refresh.token.here";

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
            .refreshToken(REFRESH_TOKEN)
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
        given(cookieService.getRefreshTokenCookieName()).willReturn(COOKIE_NAME);
    }

    /*
     * =========================================
     * POST /auth/web/login
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/web/login")
    class Login {

        @Test
        @DisplayName("Should return 200, set cookie, and exclude refreshToken from body")
        void shouldReturn200_andSetCookie_onValidCredentials() throws Exception {
            given(authLoginService.login(any(), any())).willReturn(AUTH_TOKENS);

            mockMvc.perform(post("/auth/web/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access.token.here"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.user.email").value("cashier@pos.local"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist());

            verify(clientInfoExtractor).extract(any());
            verify(authLoginService).login(any(), any());
            verify(cookieService).addRefreshTokenCookie(any(), any());
        }

        @Test
        @DisplayName("Should return 400 when email format is invalid")
        void shouldReturn400_onInvalidEmailFormat() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("not-an-email")
                    .password("SecurePass1!")
                    .build();

            mockMvc.perform(post("/auth/web/login")
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

            mockMvc.perform(post("/auth/web/login")
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

            mockMvc.perform(post("/auth/web/login")
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

            mockMvc.perform(post("/auth/web/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authLoginService);
            verifyNoInteractions(clientInfoExtractor);
        }

        @Test
        @DisplayName("Should return 401 on invalid credentials")
        void shouldReturn401_onInvalidCredentials() throws Exception {
            given(authLoginService.login(any(), any()))
                    .willThrow(new InvalidCredentialsException("Bad credentials"));

            mockMvc.perform(post("/auth/web/login")
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

            mockMvc.perform(post("/auth/web/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authLoginService);
            verifyNoInteractions(clientInfoExtractor);
        }
    }

    /*
     * =========================================
     * POST /auth/web/refresh
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/web/refresh")
    class Refresh {

        @Test
        @DisplayName("Should return 200, new access token, and renew cookie on valid cookie")
        void shouldReturn200_andRenewCookie_onValidCookie() throws Exception {
            given(authRefreshService.refresh(any(), any())).willReturn(AUTH_TOKENS);

            mockMvc.perform(post("/auth/web/refresh")
                            .cookie(new Cookie(COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access.token.here"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist());

            verify(clientInfoExtractor).extract(any());
            verify(authRefreshService).refresh(any(), any());
            verify(cookieService).addRefreshTokenCookie(any(), any());
        }

        @Test
        @DisplayName("Should trim refresh token from cookie before calling refresh service")
        void shouldTrimRefreshTokenFromCookie_beforeRefresh() throws Exception {
            given(authRefreshService.refresh(any(), any())).willReturn(AUTH_TOKENS);

            mockMvc.perform(post("/auth/web/refresh")
                            .cookie(new Cookie(COOKIE_NAME, "  " + REFRESH_TOKEN + "  ")))
                    .andExpect(status().isOk());

            verify(authRefreshService).refresh(eq(REFRESH_TOKEN), any());
        }

        @Test
        @DisplayName("Should return 401 and clear cookie when no cookie present")
        void shouldReturn401_andClearCookie_onMissingCookie() throws Exception {
            mockMvc.perform(post("/auth/web/refresh"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authRefreshService);
            verify(cookieService).clearRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("Should return 401 and clear cookie when cookie value is blank")
        void shouldReturn401_andClearCookie_onBlankCookie() throws Exception {
            mockMvc.perform(post("/auth/web/refresh")
                            .cookie(new Cookie(COOKIE_NAME, "   ")))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authRefreshService);
            verify(cookieService).clearRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("Should return 401 and clear cookie when service rejects the token")
        void shouldReturn401_andClearCookie_onRejectedToken() throws Exception {
            given(authRefreshService.refresh(any(), any()))
                    .willThrow(new InvalidCredentialsException("Token invalid or expired"));

            mockMvc.perform(post("/auth/web/refresh")
                            .cookie(new Cookie(COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Token invalid or expired"));

            verify(cookieService).clearRefreshTokenCookie(any());
        }
    }

    /*
     * =========================================
     * POST /auth/web/logout
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/web/logout")
    class Logout {

        @Test
        @DisplayName("Should return 204 and clear cookie on valid cookie")
        void shouldReturn204_andClearCookie_onValidCookie() throws Exception {
            mockMvc.perform(post("/auth/web/logout")
                            .cookie(new Cookie(COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isNoContent());

            verify(authLogoutService).logout(REFRESH_TOKEN);
            verify(cookieService).clearRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("Should return 204 and clear cookie silently when no cookie present")
        void shouldReturn204_andClearCookie_onMissingCookie() throws Exception {
            mockMvc.perform(post("/auth/web/logout"))
                    .andExpect(status().isNoContent());

            verifyNoInteractions(authLogoutService);
            verify(cookieService).clearRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("Should return 204 and clear cookie when logout service rejects the token")
        void shouldReturn204_andClearCookie_onRejectedToken() throws Exception {
            willThrow(new InvalidCredentialsException("Invalid refresh token"))
                    .given(authLogoutService).logout(REFRESH_TOKEN);

            mockMvc.perform(post("/auth/web/logout")
                            .cookie(new Cookie(COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isNoContent());

            verify(authLogoutService).logout(REFRESH_TOKEN);
            verify(cookieService).clearRefreshTokenCookie(any());
        }
    }

    /*
     * =========================================
     * POST /auth/web/logout-all
     * =========================================
     */

    @Nested
    @DisplayName("POST /auth/web/logout-all")
    class LogoutAll {

        @Test
        @DisplayName("Should return 204 and clear cookie on valid cookie")
        void shouldReturn204_andClearCookie_onValidCookie() throws Exception {
            mockMvc.perform(post("/auth/web/logout-all")
                            .cookie(new Cookie(COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isNoContent());

            verify(authLogoutService).logoutAll(REFRESH_TOKEN);
            verify(cookieService).clearRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("Should return 401 and clear cookie when no cookie present")
        void shouldReturn401_andClearCookie_onMissingCookie() throws Exception {
            mockMvc.perform(post("/auth/web/logout-all"))
                    .andExpect(status().isUnauthorized());

            verify(cookieService).clearRefreshTokenCookie(any());
        }

        @Test
        @DisplayName("Should return 401 and clear cookie when logoutAll service rejects the token")
        void shouldReturn401_andClearCookie_onRejectedToken() throws Exception {
            willThrow(new InvalidCredentialsException("Invalid refresh token"))
                    .given(authLogoutService).logoutAll(REFRESH_TOKEN);

            mockMvc.perform(post("/auth/web/logout-all")
                            .cookie(new Cookie(COOKIE_NAME, REFRESH_TOKEN)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid refresh token"));

            verify(authLogoutService).logoutAll(REFRESH_TOKEN);
            verify(cookieService).clearRefreshTokenCookie(any());
        }
    }

    private LoginRequest validLoginRequest() {
        return LoginRequest.builder()
                .email("cashier@pos.local")
                .password("SecurePass1!")
                .build();
    }
}
