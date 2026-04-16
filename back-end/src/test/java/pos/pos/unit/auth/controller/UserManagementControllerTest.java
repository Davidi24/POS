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
import pos.pos.auth.controller.UserManagementController;
import pos.pos.auth.service.AuthRegisterService;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.exception.auth.PhoneAlreadyExistsException;
import pos.pos.exception.auth.UsernameAlreadyExistsException;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.exception.role.RoleAssignmentNotAllowedException;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.user.dto.CreateUserRequest;
import pos.pos.user.dto.UserResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//checked
@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementController")
class UserManagementControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CREATED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private AuthRegisterService authRegisterService;

    @InjectMocks
    private UserManagementController controller;

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

        AuthenticatedUser actor = AuthenticatedUser.builder()
                .id(ACTOR_ID)
                .email("manager@pos.local")
                .username("manager.main")
                .active(true)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of());
    }

    @Nested
    @DisplayName("POST /auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Should return 201 with the created user")
        void shouldReturn201WithCreatedUser() throws Exception {
            CreateUserRequest request = validRequest();
            UserResponse response = UserResponse.builder()
                    .id(CREATED_USER_ID)
                    .email("cashier@pos.local")
                    .username("cashier.one")
                    .firstName("John")
                    .lastName("Doe")
                    .phone("+49-555-0100")
                    .isActive(true)
                    .roles(List.of("CASHIER"))
                    .build();

            given(authRegisterService.register(any(CreateUserRequest.class), eq(authentication))).willReturn(response);

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CREATED_USER_ID.toString()))
                    .andExpect(jsonPath("$.email").value("cashier@pos.local"))
                    .andExpect(jsonPath("$.username").value("cashier.one"))
                    .andExpect(jsonPath("$.firstName").value("John"))
                    .andExpect(jsonPath("$.lastName").value("Doe"))
                    .andExpect(jsonPath("$.phone").value("+49-555-0100"))
                    .andExpect(jsonPath("$.isActive").value(true))
                    .andExpect(jsonPath("$.roles[0]").value("CASHIER"));

            verify(authRegisterService).register(any(CreateUserRequest.class), eq(authentication));
        }

        @Test
        @DisplayName("Should return 400 when username is missing")
        void shouldReturn400WhenUsernameIsMissing() throws Exception {
            CreateUserRequest request = validRequest();
            request.setUsername(null);

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("username: Username is required"));

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when email format is invalid")
        void shouldReturn400WhenEmailFormatIsInvalid() throws Exception {
            CreateUserRequest request = validRequest();
            request.setEmail("not-an-email");

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when role id is missing")
        void shouldReturn400WhenRoleIdIsMissing() throws Exception {
            CreateUserRequest request = validRequest();
            request.setRoleId(null);

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when temporary password is missing")
        void shouldReturn400WhenTemporaryPasswordIsMissing() throws Exception {
            CreateUserRequest request = validRequest();
            request.setTemporaryPassword(null);

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("temporaryPassword: Temporary password is required"));

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when first name is missing")
        void shouldReturn400WhenFirstNameIsMissing() throws Exception {
            CreateUserRequest request = validRequest();
            request.setFirstName(null);

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("firstName: First name is required"));

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when last name is missing")
        void shouldReturn400WhenLastNameIsMissing() throws Exception {
            CreateUserRequest request = validRequest();
            request.setLastName(null);

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("lastName: Last name is required"));

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when phone exceeds the maximum length")
        void shouldReturn400WhenPhoneExceedsTheMaximumLength() throws Exception {
            CreateUserRequest request = validRequest();
            request.setPhone("1".repeat(31));

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("phone: Phone must be at most 30 characters"));

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturn400WhenRequestBodyIsMissing() throws Exception {
            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 on malformed JSON body")
        void shouldReturn400OnMalformedJsonBody() throws Exception {
            String malformedJson = "{\"email\":\"cashier@pos.local\",\"temporaryPassword\":}";

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authRegisterService);
        }

        @Test
        @DisplayName("Should return 400 when email already exists")
        void shouldReturn400WhenEmailAlreadyExists() throws Exception {
            given(authRegisterService.register(any(CreateUserRequest.class), eq(authentication)))
                    .willThrow(new EmailAlreadyExistsException());

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Email already in use"));

            verify(authRegisterService).register(any(CreateUserRequest.class), eq(authentication));
        }

        @Test
        @DisplayName("Should return 400 when username already exists")
        void shouldReturn400WhenUsernameAlreadyExists() throws Exception {
            given(authRegisterService.register(any(CreateUserRequest.class), eq(authentication)))
                    .willThrow(new UsernameAlreadyExistsException());

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Username already in use"));

            verify(authRegisterService).register(any(CreateUserRequest.class), eq(authentication));
        }

        @Test
        @DisplayName("Should return 400 when phone already exists")
        void shouldReturn400WhenPhoneAlreadyExists() throws Exception {
            given(authRegisterService.register(any(CreateUserRequest.class), eq(authentication)))
                    .willThrow(new PhoneAlreadyExistsException());

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Phone already in use"));

            verify(authRegisterService).register(any(CreateUserRequest.class), eq(authentication));
        }

        @Test
        @DisplayName("Should return 403 when actor cannot assign the requested role")
        void shouldReturn403WhenActorCannotAssignRole() throws Exception {
            given(authRegisterService.register(any(CreateUserRequest.class), eq(authentication)))
                    .willThrow(new RoleAssignmentNotAllowedException());

            mockMvc.perform(post("/auth/register")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("You are not allowed to assign this role"));

            verify(authRegisterService).register(any(CreateUserRequest.class), eq(authentication));
        }
    }

    private CreateUserRequest validRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("cashier@pos.local");
        request.setUsername("cashier.one");
        request.setTemporaryPassword("SecurePass1!");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setPhone("+49-555-0100");
        request.setRoleId(ROLE_ID);
        return request;
    }
}
