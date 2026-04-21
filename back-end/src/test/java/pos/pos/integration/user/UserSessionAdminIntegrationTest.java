package pos.pos.integration.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.auth.entity.UserSession;
import pos.pos.user.entity.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("User session admin integration test")
class UserSessionAdminIntegrationTest extends AbstractUserIntegrationTest {

    @Test
    @DisplayName("USER-019 GET /users/{userId}/sessions returns active sessions for a manageable target user")
    void user019ReturnsActiveSessionsForManageableTargetUser() throws Exception {
        User target = createUser("user019-target", "WAITER", "Session", "Target", true, true, null, false);
        webLogin(target.getUsername(), DEFAULT_PASSWORD, nextIp(), "USER-019-1", status().isOk());
        webLogin(target.getEmail(), DEFAULT_PASSWORD, nextIp(), "Windows USER-019-2", status().isOk());
        String adminAccessToken = adminAccessToken();

        MvcResult result = mockMvc.perform(get("/users/{userId}/sessions", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body).hasSize(2);
        assertThat(body.findValuesAsText("sessionType")).containsOnly("PASSWORD");
        assertThat(body.findValuesAsText("deviceName")).contains("Device", "Windows");
        assertThat(body.findValuesAsText("current")).containsOnly("false");
        assertThat(body.findValuesAsText("userId")).containsOnly(target.getId().toString());
    }

    @Test
    @DisplayName("USER-020 DELETE /users/{userId}/sessions revokes all active sessions for a manageable target user")
    void user020RevokesAllActiveSessionsForManageableTargetUser() throws Exception {
        User target = createUser("user020-target", "WAITER", "Session", "Target", true, true, null, false);
        webLogin(target.getUsername(), DEFAULT_PASSWORD, nextIp(), "USER-020-1", status().isOk());
        webLogin(target.getEmail(), DEFAULT_PASSWORD, nextIp(), "USER-020-2", status().isOk());
        String adminAccessToken = adminAccessToken();

        mockMvc.perform(delete("/users/{userId}/sessions", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        assertThat(activeSessionsFor(target)).isEmpty();
        assertThat(sessionsFor(target))
                .allSatisfy(session -> {
                    assertThat(session.isRevoked()).isTrue();
                    assertThat(session.getRevokedReason()).isEqualTo("SESSION_REVOKED");
                });
    }

    @Test
    @DisplayName("USER-021 DELETE /users/{userId}/sessions/{sessionId} revokes one active session for a manageable target user")
    void user021RevokesOneActiveSessionForManageableTargetUser() throws Exception {
        User target = createUser("user021-target", "WAITER", "Session", "Target", true, true, null, false);
        webLogin(target.getUsername(), DEFAULT_PASSWORD, nextIp(), "USER-021-1", status().isOk());
        webLogin(target.getEmail(), DEFAULT_PASSWORD, nextIp(), "USER-021-2", status().isOk());
        String adminAccessToken = adminAccessToken();

        List<UserSession> sessions = activeSessionsFor(target);
        UserSession sessionToRevoke = sessions.getFirst();
        UserSession sessionToKeep = sessions.getLast();

        mockMvc.perform(delete("/users/{userId}/sessions/{sessionId}", target.getId(), sessionToRevoke.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isNoContent());

        UserSession revokedSession = userSessionRepository.findById(sessionToRevoke.getId()).orElseThrow();
        UserSession keptSession = userSessionRepository.findById(sessionToKeep.getId()).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();
        assertThat(revokedSession.getRevokedReason()).isEqualTo("SESSION_REVOKED");
        assertThat(keptSession.isRevoked()).isFalse();
        assertThat(activeSessionsFor(target)).hasSize(1);
    }
}
