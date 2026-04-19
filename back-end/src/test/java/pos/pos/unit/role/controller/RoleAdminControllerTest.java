package pos.pos.unit.role.controller;

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
import pos.pos.exception.handler.GlobalExceptionHandler;
import pos.pos.role.controller.RoleAdminController;
import pos.pos.role.dto.CloneRoleRequest;
import pos.pos.role.dto.CreateRoleRequest;
import pos.pos.role.dto.ReplaceRolePermissionsRequest;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.dto.UpdateRoleRequest;
import pos.pos.role.dto.UpdateRoleStatusRequest;
import pos.pos.role.service.RoleAdminService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleAdminController")
class RoleAdminControllerTest {

    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000231");
    private static final UUID PERMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000232");

    @Mock
    private RoleAdminService roleAdminService;

    @InjectMocks
    private RoleAdminController controller;

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

        authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("admin@pos.local")
                        .username("admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("POST /roles should return 201 with the created role")
    void shouldCreateRole() throws Exception {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("Floor Supervisor")
                .description("Manages floor operations")
                .build();

        given(roleAdminService.createRole(eq(authentication), any(CreateRoleRequest.class)))
                .willReturn(RoleResponse.builder().id(ROLE_ID).code("FLOOR_SUPERVISOR").name("Floor Supervisor").build());

        mockMvc.perform(post("/roles")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROLE_ID.toString()))
                .andExpect(jsonPath("$.code").value("FLOOR_SUPERVISOR"));
    }

    @Test
    @DisplayName("PUT /roles/{roleId} should return the updated role")
    void shouldUpdateRole() throws Exception {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("Senior Manager")
                .description("Updated description")
                .build();

        given(roleAdminService.updateRole(eq(authentication), eq(ROLE_ID), any(UpdateRoleRequest.class)))
                .willReturn(RoleResponse.builder().id(ROLE_ID).code("MANAGER").name("Senior Manager").build());

        mockMvc.perform(put("/roles/{roleId}", ROLE_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Senior Manager"));
    }

    @Test
    @DisplayName("PUT /roles/{roleId}/permissions should validate the request body")
    void shouldValidateReplacePermissionsBody() throws Exception {
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest();

        mockMvc.perform(put("/roles/{roleId}/permissions", ROLE_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("permissionIds: permissionIds is required"));

        verifyNoInteractions(roleAdminService);
    }

    @Test
    @DisplayName("PATCH /roles/{roleId}/status should return the updated role")
    void shouldUpdateRoleStatus() throws Exception {
        UpdateRoleStatusRequest request = new UpdateRoleStatusRequest();
        request.setIsActive(false);

        given(roleAdminService.updateRoleStatus(eq(authentication), eq(ROLE_ID), any(UpdateRoleStatusRequest.class)))
                .willReturn(RoleResponse.builder().id(ROLE_ID).code("MANAGER").isActive(false).build());

        mockMvc.perform(patch("/roles/{roleId}/status", ROLE_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("POST /roles/{roleId}/clone should return 201 with the cloned role")
    void shouldCloneRole() throws Exception {
        CloneRoleRequest request = new CloneRoleRequest();
        request.setName("Assistant Manager");

        given(roleAdminService.cloneRole(eq(authentication), eq(ROLE_ID), any(CloneRoleRequest.class)))
                .willReturn(RoleResponse.builder().id(ROLE_ID).code("ASSISTANT_MANAGER").name("Assistant Manager").build());

        mockMvc.perform(post("/roles/{roleId}/clone", ROLE_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ASSISTANT_MANAGER"));
    }

    @Test
    @DisplayName("PUT /roles/{roleId}/permissions should return updated permissions")
    void shouldReplaceRolePermissions() throws Exception {
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest();
        request.setPermissionIds(Set.of(PERMISSION_ID));

        given(roleAdminService.replaceRolePermissions(eq(authentication), eq(ROLE_ID), any(ReplaceRolePermissionsRequest.class)))
                .willReturn(List.of(pos.pos.role.dto.PermissionResponse.builder()
                        .id(PERMISSION_ID)
                        .code("USERS_READ")
                        .name("View Users")
                        .build()));

        mockMvc.perform(put("/roles/{roleId}/permissions", ROLE_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USERS_READ"));

        verify(roleAdminService).replaceRolePermissions(eq(authentication), eq(ROLE_ID), any(ReplaceRolePermissionsRequest.class));
    }
}
