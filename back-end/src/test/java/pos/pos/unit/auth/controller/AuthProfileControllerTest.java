package pos.pos.unit.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pos.pos.auth.controller.AuthProfileController;
import pos.pos.auth.dto.MeResponse;
import pos.pos.auth.service.AuthProfileService;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthProfileController")
class AuthProfileControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private AuthProfileService authProfileService;

    @InjectMocks
    private AuthProfileController controller;

    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        User user = User.builder()
                .id(USER_ID)
                .email("manager@pos.local")
                .build();

        authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    @Test
    @DisplayName("GET /auth/me should return the authenticated user's profile")
    void shouldReturnAuthenticatedUserProfile() throws Exception {
        MeResponse response = MeResponse.builder()
                .id(USER_ID)
                .email("manager@pos.local")
                .firstName("Maria")
                .lastName("Manager")
                .phone("+49-555-0101")
                .isActive(true)
                .roles(List.of("MANAGER"))
                .permissions(List.of("SESSIONS_MANAGE", "USERS_CREATE"))
                .build();

        given(authProfileService.getMe(authentication)).willReturn(response);

        mockMvc.perform(get("/auth/me").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("manager@pos.local"))
                .andExpect(jsonPath("$.firstName").value("Maria"))
                .andExpect(jsonPath("$.lastName").value("Manager"))
                .andExpect(jsonPath("$.phone").value("+49-555-0101"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"))
                .andExpect(jsonPath("$.permissions[0]").value("SESSIONS_MANAGE"));

        verify(authProfileService).getMe(authentication);
    }
}
