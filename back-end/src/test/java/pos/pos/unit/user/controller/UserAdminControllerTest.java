package pos.pos.unit.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.role.dto.RoleResponse;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.user.controller.UserAdminController;
import pos.pos.user.dto.ReplaceUserRolesRequest;
import pos.pos.user.dto.UpdateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.service.UserAdminService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminController")
class UserAdminControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private UserAdminService userAdminService;

    @InjectMocks
    private UserAdminController controller;

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
                .email("admin@pos.local")
                .username("admin")
                .active(true)
                .build();

        authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of());
    }

    @Test
    @DisplayName("GET /users should return paged results")
    void shouldReturnPagedUsers() throws Exception {
        UserResponse user = userResponse();
        PageResponse<UserResponse> response = PageResponse.<UserResponse>builder()
                .items(List.of(user))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        given(userAdminService.getUsers(eq(authentication), eq("cashier"), eq(true), eq("WAITER"), eq(0), eq(20), eq("createdAt"), eq("desc")))
                .willReturn(response);

        mockMvc.perform(get("/users")
                        .principal(authentication)
                        .param("search", "cashier")
                        .param("active", "true")
                        .param("roleCode", "WAITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(TARGET_USER_ID.toString()))
                .andExpect(jsonPath("$.items[0].roles[0]").value("WAITER"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /users/{userId} should return 403 when target is not manageable")
    void shouldReturn403WhenTargetNotManageable() throws Exception {
        given(userAdminService.getUser(eq(authentication), eq(TARGET_USER_ID)))
                .willThrow(new UserManagementNotAllowedException());

        mockMvc.perform(get("/users/{userId}", TARGET_USER_ID)
                        .principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not allowed to manage this user"));
    }

    @Test
    @DisplayName("PUT /users/{userId} should return updated user")
    void shouldUpdateUser() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("+15550200")
                .isActive(true)
                .build();

        UserResponse response = userResponse();
        response.setFirstName("Jane");
        response.setLastName("Smith");

        given(userAdminService.updateUser(eq(authentication), eq(TARGET_USER_ID), any(UpdateUserRequest.class)))
                .willReturn(response);

        mockMvc.perform(put("/users/{userId}", TARGET_USER_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    @Test
    @DisplayName("PUT /users/{userId} should validate the request body")
    void shouldValidateUpdateUserBody() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("")
                .lastName("Smith")
                .isActive(true)
                .build();

        mockMvc.perform(put("/users/{userId}", TARGET_USER_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("firstName: First name is required"));

        verifyNoInteractions(userAdminService);
    }

    @Test
    @DisplayName("GET /users/{userId}/roles should return active roles")
    void shouldReturnUserRoles() throws Exception {
        given(userAdminService.getUserRoles(eq(authentication), eq(TARGET_USER_ID)))
                .willReturn(List.of(RoleResponse.builder().id(ROLE_ID).code("WAITER").name("Waiter").build()));

        mockMvc.perform(get("/users/{userId}/roles", TARGET_USER_ID)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$[0].code").value("WAITER"));
    }

    @Test
    @DisplayName("PUT /users/{userId}/roles should return the updated user")
    void shouldReplaceUserRoles() throws Exception {
        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest();
        request.setRoleIds(Set.of(ROLE_ID));

        given(userAdminService.replaceUserRoles(eq(authentication), eq(TARGET_USER_ID), any(ReplaceUserRolesRequest.class)))
                .willReturn(userResponse());

        mockMvc.perform(put("/users/{userId}/roles", TARGET_USER_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("WAITER"));
    }

    @Test
    @DisplayName("DELETE /users/{userId} should return 204")
    void shouldDeleteUser() throws Exception {
        mockMvc.perform(delete("/users/{userId}", TARGET_USER_ID)
                        .principal(authentication))
                .andExpect(status().isNoContent());

        verify(userAdminService).deleteUser(authentication, TARGET_USER_ID);
    }

    private UserResponse userResponse() {
        return UserResponse.builder()
                .id(TARGET_USER_ID)
                .email("cashier@pos.local")
                .username("cashier.one")
                .firstName("John")
                .lastName("Doe")
                .phone("+15550100")
                .isActive(true)
                .roles(List.of("WAITER"))
                .build();
    }
}
