package pos.pos.unit.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pos.pos.auth.controller.AdminSessionController;
import pos.pos.auth.service.SessionService;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.user.dto.UserSessionResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSessionController")
class AdminSessionControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AdminSessionController controller;

    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        AuthenticatedUser actor = AuthenticatedUser.builder()
                .id(ACTOR_ID)
                .email("admin@pos.local")
                .active(true)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of());
    }

    @Nested
    @DisplayName("GET /users/{userId}/sessions")
    class GetUserActiveSessionsTests {

        @Test
        @DisplayName("Should return 200 with the target user's sessions")
        void shouldReturn200WithSessions() throws Exception {
            UserSessionResponse session = UserSessionResponse.builder()
                    .id(SESSION_ID)
                    .userId(TARGET_USER_ID)
                    .deviceName("Chrome on Windows")
                    .current(false)
                    .build();

            given(sessionService.getUserActiveSessions(eq(authentication), eq(TARGET_USER_ID))).willReturn(List.of(session));

            mockMvc.perform(get("/users/{userId}/sessions", TARGET_USER_ID)
                            .principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(SESSION_ID.toString()))
                    .andExpect(jsonPath("$[0].userId").value(TARGET_USER_ID.toString()))
                    .andExpect(jsonPath("$[0].deviceName").value("Chrome on Windows"))
                    .andExpect(jsonPath("$[0].current").value(false));

            verify(sessionService).getUserActiveSessions(eq(authentication), eq(TARGET_USER_ID));
        }

        @Test
        @DisplayName("Should return 403 when actor is not allowed to manage the target user")
        void shouldReturn403WhenActorCannotManageTarget() throws Exception {
            given(sessionService.getUserActiveSessions(eq(authentication), eq(TARGET_USER_ID)))
                    .willThrow(new UserManagementNotAllowedException());

            mockMvc.perform(get("/users/{userId}/sessions", TARGET_USER_ID)
                            .principal(authentication))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("You are not allowed to manage this user"));
        }
    }

    @Nested
    @DisplayName("DELETE /users/{userId}/sessions")
    class RevokeAllUserSessionsTests {

        @Test
        @DisplayName("Should return 204 when all target sessions are revoked")
        void shouldReturn204WhenSessionsRevoked() throws Exception {
            mockMvc.perform(delete("/users/{userId}/sessions", TARGET_USER_ID)
                            .principal(authentication))
                    .andExpect(status().isNoContent());

            verify(sessionService).revokeAllUserSessions(eq(authentication), eq(TARGET_USER_ID));
        }

        @Test
        @DisplayName("Should return 403 when actor is not allowed to revoke target sessions")
        void shouldReturn403WhenActorCannotRevokeTargetSessions() throws Exception {
            willThrow(new UserManagementNotAllowedException())
                    .given(sessionService).revokeAllUserSessions(eq(authentication), eq(TARGET_USER_ID));

            mockMvc.perform(delete("/users/{userId}/sessions", TARGET_USER_ID)
                            .principal(authentication))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("You are not allowed to manage this user"));
        }
    }
}
