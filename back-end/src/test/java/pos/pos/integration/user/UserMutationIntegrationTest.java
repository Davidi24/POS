package pos.pos.integration.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.user.entity.User;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("User mutation integration test")
class UserMutationIntegrationTest extends AbstractUserIntegrationTest {

    @Test
    @DisplayName("USER-004 PUT /users/{userId} updates first name, last name, phone, and active state")
    void user004UpdatesFirstNameLastNamePhoneAndActiveState() throws Exception {
        User target = createUser("user004-target", "WAITER", "Before", "User", false, true, null, false);
        String adminAccessToken = adminAccessToken();

        MvcResult result = mockMvc.perform(put("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "After",
                                "lastName", "Updated",
                                "phone", "+1555010810",
                                "isActive", true
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("firstName").asText()).isEqualTo("After");
        assertThat(body.get("lastName").asText()).isEqualTo("Updated");
        assertThat(body.get("phone").asText()).isEqualTo("+1555010810");
        assertThat(body.get("isActive").asBoolean()).isTrue();

        User updatedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedUser.getFirstName()).isEqualTo("After");
        assertThat(updatedUser.getLastName()).isEqualTo("Updated");
        assertThat(updatedUser.getPhone()).isEqualTo("+1555010810");
        assertThat(updatedUser.isActive()).isTrue();
        assertThat(updatedUser.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("USER-005 PUT /users/{userId} deactivating user revokes active sessions")
    void user005DeactivatingUserRevokesActiveSessions() throws Exception {
        User target = createUser("user005-target", "WAITER", "Active", "User", true, true, "+1555010811", true);
        webLogin(target.getUsername(), DEFAULT_PASSWORD, nextIp(), "USER-005-1", status().isOk());
        webLogin(target.getEmail(), DEFAULT_PASSWORD, nextIp(), "USER-005-2", status().isOk());
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(put("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", target.getFirstName(),
                                "lastName", target.getLastName(),
                                "phone", target.getPhone(),
                                "isActive", false
                        ))))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedUser.isActive()).isFalse();
        assertThat(updatedUser.getStatus()).isEqualTo("INACTIVE");
        assertThat(activeSessionsFor(target)).isEmpty();
        assertThat(sessionsFor(target))
                .allSatisfy(session -> {
                    assertThat(session.isRevoked()).isTrue();
                    assertThat(session.getRevokedReason()).isEqualTo("SESSION_REVOKED");
                });
    }

    @Test
    @DisplayName("USER-006 PUT /users/{userId} changing phone resets phone verification")
    void user006ChangingPhoneResetsPhoneVerification() throws Exception {
        User target = createUser("user006-target", "WAITER", "Phone", "Verified", true, true, "+1555010812", true);
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(put("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", target.getFirstName(),
                                "lastName", target.getLastName(),
                                "phone", "+1555010813",
                                "isActive", true
                        ))))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedUser.getPhone()).isEqualTo("+1555010813");
        assertThat(updatedUser.isPhoneVerified()).isFalse();
        assertThat(updatedUser.getPhoneVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("USER-007 PUT /users/{userId} rejects duplicate phone")
    void user007RejectsDuplicatePhone() throws Exception {
        User target = createUser("user007-target", "WAITER", "Target", "User", true, true, "+1555010814", false);
        User otherUser = createUser("user007-other", "WAITER", "Other", "User", true, true, "+1555010815", true);
        String adminAccessToken = adminAccessToken();

        MvcResult result = mockMvc.perform(put("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", target.getFirstName(),
                                "lastName", target.getLastName(),
                                "phone", otherUser.getPhone(),
                                "isActive", true
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(bodyOf(result).get("message").asText()).isEqualTo("Phone already in use");
    }

    @Test
    @DisplayName("USER-011 DELETE /users/{userId} soft deletes user and revokes sessions")
    void user011SoftDeletesUserAndRevokesSessions() throws Exception {
        User target = createUser("user011-target", "WAITER", "Delete", "Target", true, true, "+1555010816", true);
        webLogin(target.getUsername(), DEFAULT_PASSWORD, nextIp(), "USER-011-1", status().isOk());
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(delete("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        User deletedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(deletedUser.getDeletedAt()).isNotNull();
        assertThat(deletedUser.isActive()).isFalse();
        assertThat(deletedUser.getStatus()).isEqualTo("DELETED");
        assertThat(activeSessionsFor(target)).isEmpty();
        assertThat(sessionsFor(target))
                .allSatisfy(session -> {
                    assertThat(session.isRevoked()).isTrue();
                    assertThat(session.getRevokedReason()).isEqualTo("SESSION_REVOKED");
                });
    }
}
