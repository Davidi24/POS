package pos.pos.integration.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.user.entity.User;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("User recovery integration test")
class UserRecoveryIntegrationTest extends AbstractUserIntegrationTest {

    @Test
    @DisplayName("USER-012 POST /users/{userId}/reset-password email path sends admin-triggered reset")
    void user012SendsAdminTriggeredEmailReset() throws Exception {
        User target = createUser("user012-target", "WAITER", "Recover", "Email", true, true, null, false);
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(post("/users/{userId}/reset-password", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "EMAIL",
                                "clientTarget", "UNIVERSAL"
                        ))))
                .andExpect(status().isNoContent());

        assertThat(latestPasswordResetUrl.get()).isNotBlank();
        assertThat(tokenFromUrl(latestPasswordResetUrl.get())).isNotBlank();
        assertThat(latestPasswordResetTokenFor(target).getUsedAt()).isNull();
    }

    @Test
    @DisplayName("USER-013 POST /users/{userId}/reset-password SMS path sends admin-triggered reset")
    void user013SendsAdminTriggeredSmsReset() throws Exception {
        User target = createUser("user013-target", "WAITER", "Recover", "Sms", true, true, "+1555010820", true);
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(post("/users/{userId}/reset-password", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "SMS"
                        ))))
                .andExpect(status().isNoContent());

        assertThat(latestPasswordResetCode.get()).isNotBlank();
        assertThat(latestSmsCodeFor(target, SmsOtpPurpose.PASSWORD_RESET).getUsedAt()).isNull();
    }

    @Test
    @DisplayName("USER-014 POST /users/{userId}/reset-password rejects inactive user or unverified contact channel")
    void user014RejectsInactiveUserOrUnverifiedContactChannel() throws Exception {
        User inactiveUser = createUser("user014-inactive", "WAITER", "Inactive", "User", false, true, null, false);
        User unverifiedEmailUser = createUser("user014-email", "WAITER", "Email", "User", true, false, null, false);
        User unverifiedPhoneUser = createUser("user014-phone", "WAITER", "Phone", "User", true, true, "+1555010821", false);
        String adminAccessToken = adminAccessToken();

        MvcResult inactiveResult = mockMvc.perform(post("/users/{userId}/reset-password", inactiveUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("channel", "EMAIL"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult unverifiedEmailResult = mockMvc.perform(post("/users/{userId}/reset-password", unverifiedEmailUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("channel", "EMAIL"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult unverifiedPhoneResult = mockMvc.perform(post("/users/{userId}/reset-password", unverifiedPhoneUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("channel", "SMS"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(inactiveResult).get("message").asText()).isEqualTo("User account must be active before sending a password reset");
        assertThat(bodyOf(unverifiedEmailResult).get("message").asText()).isEqualTo("User must have a verified email before sending a password reset email");
        assertThat(bodyOf(unverifiedPhoneResult).get("message").asText()).isEqualTo("User must have a verified phone before sending an SMS password reset");
    }

    @Test
    @DisplayName("USER-015 POST /users/{userId}/send-verification-email sends verification email for active unverified user")
    void user015SendsVerificationEmailForActiveUnverifiedUser() throws Exception {
        User target = createUser("user015-target", "WAITER", "Verify", "Email", true, false, null, false);
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(post("/users/{userId}/send-verification-email", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("clientTarget", "UNIVERSAL"))))
                .andExpect(status().isNoContent());

        assertThat(latestVerificationUrl.get()).isNotBlank();
        assertThat(tokenFromUrl(latestVerificationUrl.get())).isNotBlank();
        assertThat(latestEmailVerificationTokenFor(target).getUsedAt()).isNull();
    }

    @Test
    @DisplayName("USER-016 POST /users/{userId}/send-verification-email rejects already verified email")
    void user016RejectsAlreadyVerifiedEmail() throws Exception {
        User target = createUser("user016-target", "WAITER", "Verify", "Email", true, true, null, false);
        String adminAccessToken = adminAccessToken();

        MvcResult result = mockMvc.perform(post("/users/{userId}/send-verification-email", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("clientTarget", "UNIVERSAL"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("User email is already verified");
    }

    @Test
    @DisplayName("USER-017 POST /users/{userId}/send-phone-verification sends phone verification code for active unverified user")
    void user017SendsPhoneVerificationCodeForActiveUnverifiedUser() throws Exception {
        User target = createUser("user017-target", "WAITER", "Verify", "Phone", true, true, "+1555010822", false);
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(post("/users/{userId}/send-phone-verification", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        assertThat(latestPhoneVerificationCode.get()).isNotBlank();
        assertThat(latestSmsCodeFor(target, SmsOtpPurpose.PHONE_VERIFICATION).getUsedAt()).isNull();
    }

    @Test
    @DisplayName("USER-018 POST /users/{userId}/send-phone-verification rejects already verified phone")
    void user018RejectsAlreadyVerifiedPhone() throws Exception {
        User target = createUser("user018-target", "WAITER", "Verify", "Phone", true, true, "+1555010823", true);
        String adminAccessToken = adminAccessToken();

        MvcResult result = mockMvc.perform(post("/users/{userId}/send-phone-verification", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("User phone is already verified");
    }
}
