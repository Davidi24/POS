package pos.pos.unit.auth.controller;

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
import pos.pos.auth.controller.PhoneVerificationController;
import pos.pos.auth.dto.VerifyPhoneRequest;
import pos.pos.auth.service.PhoneVerificationService;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhoneVerificationController")
class PhoneVerificationControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many phone verification requests. Try again later.";

    @Mock
    private PhoneVerificationService phoneVerificationService;

    @InjectMocks
    private PhoneVerificationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        AuthenticatedUser currentUser = AuthenticatedUser.builder()
                .id(USER_ID)
                .email("cashier@pos.local")
                .active(true)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(currentUser, null, List.of());
    }

    @Nested
    @DisplayName("POST /auth/request-phone-verification")
    class RequestPhoneVerificationTests {

        @Test
        @DisplayName("Should return 204 when request is allowed")
        void shouldReturn204WhenRequestIsAllowed() throws Exception {
            mockMvc.perform(post("/auth/request-phone-verification")
                            .principal(authentication))
                    .andExpect(status().isNoContent());

            verify(phoneVerificationService).requestPhoneVerification(USER_ID);
        }

        @Test
        @DisplayName("Should return 429 when request rate limit is hit")
        void shouldReturn429WhenRequestRateLimitIsHit() throws Exception {
            willThrow(new TooManyRequestsException(TOO_MANY_REQUESTS_MESSAGE))
                    .given(phoneVerificationService).requestPhoneVerification(USER_ID);

            mockMvc.perform(post("/auth/request-phone-verification")
                            .principal(authentication))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.message").value(TOO_MANY_REQUESTS_MESSAGE));
        }
    }

    @Nested
    @DisplayName("POST /auth/verify-phone")
    class VerifyPhoneTests {

        @Test
        @DisplayName("Should return 204 when code is valid")
        void shouldReturn204WhenCodeIsValid() throws Exception {
            VerifyPhoneRequest request = new VerifyPhoneRequest();
            request.setCode("123456");

            mockMvc.perform(post("/auth/verify-phone")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(phoneVerificationService).verifyPhone(eq(USER_ID), any(VerifyPhoneRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when code is blank")
        void shouldReturn400WhenCodeIsBlank() throws Exception {
            VerifyPhoneRequest request = new VerifyPhoneRequest();
            request.setCode("");

            mockMvc.perform(post("/auth/verify-phone")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(phoneVerificationService);
        }

        @Test
        @DisplayName("Should return 401 when code is invalid")
        void shouldReturn401WhenCodeIsInvalid() throws Exception {
            VerifyPhoneRequest request = new VerifyPhoneRequest();
            request.setCode("000000");

            willThrow(new InvalidTokenException())
                    .given(phoneVerificationService).verifyPhone(eq(USER_ID), any(VerifyPhoneRequest.class));

            mockMvc.perform(post("/auth/verify-phone")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));
        }
    }
}
