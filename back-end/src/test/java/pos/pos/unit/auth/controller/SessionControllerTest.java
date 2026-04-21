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
import pos.pos.auth.controller.SessionController;
import pos.pos.auth.service.SessionService;
import pos.pos.exception.auth.SessionNotFoundException;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.service.JwtService;
import pos.pos.user.dto.UserSessionResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionController")
class SessionControllerTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private SessionController controller;

    private MockMvc mockMvc;
    private Authentication authentication;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TOKEN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String BEARER_TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        AuthenticatedUser user = AuthenticatedUser.builder()
                .id(USER_ID)
                .email("user@pos.local")
                .active(true)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    @Nested
    @DisplayName("GET /auth/sessions")
    class GetMySessionsTests {

        @Test
        @DisplayName("Should return 200 with session list")
        void shouldReturn200WithSessions() throws Exception {
            UserSessionResponse session = UserSessionResponse.builder()
                    .id(SESSION_ID)
                    .current(true)
                    .build();

            given(jwtService.extractTokenId(any())).willReturn(TOKEN_ID);
            given(sessionService.getMyActiveSessions(USER_ID, TOKEN_ID)).willReturn(List.of(session));

            mockMvc.perform(get("/auth/sessions")
                            .header("Authorization", BEARER_TOKEN)
                            .principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(SESSION_ID.toString()))
                    .andExpect(jsonPath("$[0].current").value(true));
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is missing")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            mockMvc.perform(get("/auth/sessions")
                            .principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(jwtService, sessionService);
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is malformed")
        void shouldReturn401WhenHeaderMalformed() throws Exception {
            mockMvc.perform(get("/auth/sessions")
                            .header("Authorization", "InvalidHeader")
                            .principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(jwtService, sessionService);
        }

        @Test
        @DisplayName("Should return 401 when JWT token id extraction fails")
        void shouldReturn401WhenTokenIdExtractionFails() throws Exception {
            given(jwtService.extractTokenId(any())).willThrow(new RuntimeException("bad token"));

            mockMvc.perform(get("/auth/sessions")
                            .header("Authorization", BEARER_TOKEN)
                            .principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(sessionService);
        }
    }

    @Nested
    @DisplayName("GET /auth/sessions/current")
    class GetCurrentSessionTests {

        @Test
        @DisplayName("Should return 200 with current session")
        void shouldReturn200WithCurrentSession() throws Exception {
            UserSessionResponse session = UserSessionResponse.builder()
                    .id(SESSION_ID)
                    .current(true)
                    .build();

            given(jwtService.extractTokenId(any())).willReturn(TOKEN_ID);
            given(sessionService.getCurrentSession(USER_ID, TOKEN_ID)).willReturn(session);

            mockMvc.perform(get("/auth/sessions/current")
                            .header("Authorization", BEARER_TOKEN)
                            .principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                    .andExpect(jsonPath("$.current").value(true));
        }

        @Test
        @DisplayName("Should return 404 when session not found")
        void shouldReturn404WhenSessionNotFound() throws Exception {
            given(jwtService.extractTokenId(any())).willReturn(TOKEN_ID);
            given(sessionService.getCurrentSession(USER_ID, TOKEN_ID))
                    .willThrow(new SessionNotFoundException());

            mockMvc.perform(get("/auth/sessions/current")
                            .header("Authorization", BEARER_TOKEN)
                            .principal(authentication))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is missing")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            mockMvc.perform(get("/auth/sessions/current")
                            .principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(jwtService, sessionService);
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is malformed")
        void shouldReturn401WhenHeaderMalformed() throws Exception {
            mockMvc.perform(get("/auth/sessions/current")
                            .header("Authorization", "InvalidHeader")
                            .principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(jwtService, sessionService);
        }
    }

    @Nested
    @DisplayName("DELETE /auth/sessions/{sessionId}")
    class RevokeSessionTests {

        @Test
        @DisplayName("Should return 204 when session is revoked")
        void shouldReturn204OnRevoke() throws Exception {
            mockMvc.perform(delete("/auth/sessions/{id}", SESSION_ID)
                            .principal(authentication))
                    .andExpect(status().isNoContent());

            verify(sessionService).revokeSession(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("Should return 404 when session not found or not owned")
        void shouldReturn404WhenNotFound() throws Exception {
            willThrow(new SessionNotFoundException())
                    .given(sessionService).revokeSession(SESSION_ID, USER_ID);

            mockMvc.perform(delete("/auth/sessions/{id}", SESSION_ID)
                            .principal(authentication))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /auth/sessions/others")
    class RevokeOtherSessionsTests {

        @Test
        @DisplayName("Should return 204 when other sessions are revoked")
        void shouldReturn204OnRevokeOthers() throws Exception {
            given(jwtService.extractTokenId(any())).willReturn(TOKEN_ID);

            mockMvc.perform(delete("/auth/sessions/others")
                            .header("Authorization", BEARER_TOKEN)
                            .principal(authentication))
                    .andExpect(status().isNoContent());

            verify(sessionService).revokeOtherSessions(USER_ID, TOKEN_ID);
        }

        @Test
        @DisplayName("Should return 404 when current session not found")
        void shouldReturn404WhenCurrentSessionNotFound() throws Exception {
            given(jwtService.extractTokenId(any())).willReturn(TOKEN_ID);
            willThrow(new SessionNotFoundException())
                    .given(sessionService).revokeOtherSessions(USER_ID, TOKEN_ID);

            mockMvc.perform(delete("/auth/sessions/others")
                            .header("Authorization", BEARER_TOKEN)
                            .principal(authentication))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is missing")
        void shouldReturn401WhenHeaderMissing() throws Exception {
            mockMvc.perform(delete("/auth/sessions/others")
                            .principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(jwtService, sessionService);
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is malformed")
        void shouldReturn401WhenHeaderMalformed() throws Exception {
            mockMvc.perform(delete("/auth/sessions/others")
                            .header("Authorization", "InvalidHeader")
                            .principal(authentication))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token"));

            verifyNoInteractions(jwtService, sessionService);
        }
    }
}
