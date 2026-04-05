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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pos.pos.auth.controller.PasswordController;
import pos.pos.auth.dto.ChangePasswordRequest;
import pos.pos.auth.dto.ForgotPasswordRequest;
import pos.pos.auth.dto.ResetPasswordRequest;
import pos.pos.auth.service.ChangePasswordService;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.security.service.JwtService;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordController")
class PasswordControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TOKEN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String BEARER_TOKEN = "Bearer jwt.token.here";

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private ChangePasswordService changePasswordService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private PasswordController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Authentication authentication;
    private User currentUser;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        currentUser = User.builder()
                .id(USER_ID)
                .email("cashier@pos.local")
                .passwordHash("hash")
                .build();

        authentication = new UsernamePasswordAuthenticationToken(currentUser, null, List.of());
    }

    @Nested
    @DisplayName("POST /auth/forgot-password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Should return 204 when email is valid")
        void shouldReturn204WhenEmailIsValid() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("cashier@pos.local");

            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(passwordResetService).requestReset(any(ForgotPasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when email format is invalid")
        void shouldReturn400WhenEmailFormatIsInvalid() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("not-an-email");

            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(passwordResetService);
        }
    }

    @Nested
    @DisplayName("POST /auth/reset-password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should return 204 when token and new password are valid")
        void shouldReturn204WhenTokenAndPasswordAreValid() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("reset-token");
            request.setNewPassword("SecurePass1!");

            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(passwordResetService).resetPassword(any(ResetPasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when token is blank")
        void shouldReturn400WhenTokenIsBlank() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("");
            request.setNewPassword("SecurePass1!");

            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(passwordResetService);
        }

        @Test
        @DisplayName("Should return 400 when new password is too short")
        void shouldReturn400WhenNewPasswordIsTooShort() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("reset-token");
            request.setNewPassword("short");

            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(passwordResetService);
        }

        @Test
        @DisplayName("Should return 401 when reset service rejects the token")
        void shouldReturn401WhenResetServiceRejectsTheToken() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("expired-token");
            request.setNewPassword("SecurePass1!");

            willThrow(new InvalidTokenException())
                    .given(passwordResetService).resetPassword(any(ResetPasswordRequest.class));

            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));
        }
    }

    @Nested
    @DisplayName("PUT /auth/change-password")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should return 204 and pass the current token id to the service")
        void shouldReturn204AndPassCurrentTokenIdToService() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("OldSecurePass1!");
            request.setNewPassword("NewSecurePass1!");

            given(jwtService.extractTokenId("jwt.token.here")).willReturn(TOKEN_ID);

            mockMvc.perform(put("/auth/change-password")
                            .principal(authentication)
                            .header("Authorization", BEARER_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(jwtService).extractTokenId("jwt.token.here");
            verify(changePasswordService).changePassword(eq(currentUser), eq(TOKEN_ID), any(ChangePasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when current password is blank")
        void shouldReturn400WhenCurrentPasswordIsBlank() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("");
            request.setNewPassword("NewSecurePass1!");

            mockMvc.perform(put("/auth/change-password")
                            .principal(authentication)
                            .header("Authorization", BEARER_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(jwtService, changePasswordService);
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is missing")
        void shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("OldSecurePass1!");
            request.setNewPassword("NewSecurePass1!");

            mockMvc.perform(put("/auth/change-password")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(jwtService, changePasswordService);
        }

        @Test
        @DisplayName("Should return 401 when change password service rejects the current password")
        void shouldReturn401WhenChangePasswordServiceRejectsCurrentPassword() throws Exception {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("WrongSecurePass1!");
            request.setNewPassword("NewSecurePass1!");

            given(jwtService.extractTokenId("jwt.token.here")).willReturn(TOKEN_ID);
            willThrow(new InvalidCredentialsException("Current password is incorrect"))
                    .given(changePasswordService).changePassword(eq(currentUser), eq(TOKEN_ID), any(ChangePasswordRequest.class));

            mockMvc.perform(put("/auth/change-password")
                            .principal(authentication)
                            .header("Authorization", BEARER_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        }
    }
}
