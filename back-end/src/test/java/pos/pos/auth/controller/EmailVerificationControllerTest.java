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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import pos.pos.auth.controller.EmailVerificationController;
import pos.pos.auth.dto.ResendVerificationRequest;
import pos.pos.auth.dto.VerifyEmailRequest;
import pos.pos.auth.service.EmailVerificationService;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.handler.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationController")
class EmailVerificationControllerTest {

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private EmailVerificationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Nested
    @DisplayName("POST /auth/verify-email")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should return 204 when token is valid")
        void shouldReturn204WhenTokenIsValid() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken("verification-token");

            mockMvc.perform(post("/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(emailVerificationService).verifyEmail(any(VerifyEmailRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when token is blank")
        void shouldReturn400WhenTokenIsBlank() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken("");

            mockMvc.perform(post("/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(emailVerificationService);
        }

        @Test
        @DisplayName("Should return 401 when verification token is invalid")
        void shouldReturn401WhenVerificationTokenIsInvalid() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken("expired-token");

            willThrow(new InvalidTokenException())
                    .given(emailVerificationService).verifyEmail(any(VerifyEmailRequest.class));

            mockMvc.perform(post("/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));
        }
    }

    @Nested
    @DisplayName("POST /auth/resend-verification")
    class ResendVerificationTests {

        @Test
        @DisplayName("Should return 204 when email is valid")
        void shouldReturn204WhenEmailIsValid() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("cashier@pos.local");

            mockMvc.perform(post("/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(emailVerificationService).resendVerification(any(ResendVerificationRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when email format is invalid")
        void shouldReturn400WhenEmailFormatIsInvalid() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("invalid-email");

            mockMvc.perform(post("/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(emailVerificationService);
        }

        @Test
        @DisplayName("Should return 400 when email is blank")
        void shouldReturn400WhenEmailIsBlank() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("");

            mockMvc.perform(post("/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(emailVerificationService);
        }
    }
}
