package pos.pos.integration.role;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.role.entity.Role;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
@DisplayName("Role admin integration test")
class RoleAdminIntegrationTest extends AbstractRoleIntegrationTest {

    private static final String SCHEMA = "role_admin_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registerProdProperties(registry, SCHEMA);
    }

    @Test
    @DisplayName("ROLE-008 POST /roles creates custom role with derived code and actor-safe rank")
    void role008CreatesCustomRoleWithDerivedCodeAndActorSafeRank() throws Exception {
        User actor = createUser("role008-admin", role("ADMIN"));

        MvcResult result = mockMvc.perform(post("/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-008-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " Floor Supervisor ",
                                "description", " Manages floor operations "
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("code").asText()).isEqualTo("FLOOR_SUPERVISOR");
        assertThat(body.get("name").asText()).isEqualTo("Floor Supervisor");
        assertThat(body.get("description").asText()).isEqualTo("Manages floor operations");
        assertThat(body.get("rank").asLong()).isEqualTo(29_999L);
        assertThat(body.get("isSystem").asBoolean()).isFalse();
        assertThat(body.get("isActive").asBoolean()).isTrue();
        assertThat(body.get("isAssignable").asBoolean()).isTrue();
        assertThat(role("FLOOR_SUPERVISOR").getRank()).isEqualTo(29_999L);
    }

    @Test
    @DisplayName("ROLE-009 POST /roles rejects duplicate role name or derived role code")
    void role009RejectsDuplicateRoleNameOrDerivedRoleCode() throws Exception {
        User actor = createUser("role009-admin", role("ADMIN"));
        createRole("FLOOR_SUPERVISOR", "Floor Supervisor", "existing", 12_000L, false, true, true, false);
        String accessToken = accessTokenFor(actor, "ROLE-009-login");

        MvcResult duplicateNameResult = mockMvc.perform(post("/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Floor Supervisor",
                                "description", "duplicate name"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult duplicateCodeResult = mockMvc.perform(post("/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Floor-Supervisor",
                                "description", "duplicate code"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(messageOf(duplicateNameResult)).isEqualTo("Role name already in use");
        assertThat(messageOf(duplicateCodeResult)).isEqualTo("Role code already in use");
    }

    @Test
    @DisplayName("ROLE-010 PUT /roles/{roleId} updates custom role name and description")
    void role010UpdatesCustomRoleNameAndDescription() throws Exception {
        User actor = createUser("role010-admin", role("ADMIN"));
        Role target = createRole("ROLE010_TARGET", "Role010 Target", "before", 14_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(put("/roles/{roleId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-010-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Senior Floor Lead",
                                "description", "Updated role description"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("code").asText()).isEqualTo("ROLE010_TARGET");
        assertThat(body.get("name").asText()).isEqualTo("Senior Floor Lead");
        assertThat(body.get("description").asText()).isEqualTo("Updated role description");

        Role stored = roleRepository.findById(target.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Senior Floor Lead");
        assertThat(stored.getDescription()).isEqualTo("Updated role description");
    }

    @Test
    @DisplayName("ROLE-011 PUT /roles/{roleId} rejects updates to system roles")
    void role011RejectsUpdatesToSystemRoles() throws Exception {
        User actor = createUser("role011-admin", role("ADMIN"));

        MvcResult result = mockMvc.perform(put("/roles/{roleId}", role("MANAGER").getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-011-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated Manager",
                                "description", "should fail"
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(messageOf(result)).isEqualTo("System roles cannot be modified");
    }

    @Test
    @DisplayName("ROLE-012 PUT /roles/{roleId} rejects role outside actor management scope")
    void role012RejectsRoleOutsideActorManagementScope() throws Exception {
        User actor = createUser("role012-admin", role("ADMIN"));
        Role aboveActor = createRole("ROLE012_ABOVE", "Role012 Above", "above actor", 35_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(put("/roles/{roleId}", aboveActor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-012-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Role012 Attempt",
                                "description", "should fail"
                        ))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(messageOf(result)).isEqualTo("You are not allowed to manage this role");
    }

    @Test
    @DisplayName("ROLE-013 PUT /roles/{roleId}/permissions replaces role permissions")
    void role013ReplacesRolePermissions() throws Exception {
        User actor = createUser("role013-admin", role("ADMIN"));
        Role target = createRole("ROLE013_TARGET", "Role013 Target", "permissions", 13_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(put("/roles/{roleId}/permissions", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-013-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionIds", List.of(
                                        permission("ROLES_READ").getId().toString(),
                                        permission("USERS_UPDATE").getId().toString()
                                )
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(codesOf(bodyOf(result))).containsExactly("USERS_UPDATE", "ROLES_READ");
        assertThat(rolePermissionRepository.findByRoleId(target.getId()))
                .extracting(assignment -> assignment.getPermissionId().toString())
                .containsExactlyInAnyOrder(
                        permission("ROLES_READ").getId().toString(),
                        permission("USERS_UPDATE").getId().toString()
                );
    }

    @Test
    @DisplayName("ROLE-014 PUT /roles/{roleId}/permissions rejects unknown permission ids")
    void role014RejectsUnknownPermissionIds() throws Exception {
        User actor = createUser("role014-admin", role("ADMIN"));
        Role target = createRole("ROLE014_TARGET", "Role014 Target", "permissions", 13_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(put("/roles/{roleId}/permissions", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-014-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionIds", List.of(UUID.randomUUID().toString())
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(messageOf(result)).isEqualTo("Permission not found");
    }

    @Test
    @DisplayName("ROLE-015 PUT /roles/{roleId}/permissions rejects assigning permissions the actor does not hold")
    void role015RejectsAssigningPermissionsTheActorDoesNotHold() throws Exception {
        Role limitedActorRole = createRoleWithPermissions(
                "ROLE015_LIMITED_ACTOR",
                "Role015 Limited Actor",
                "limited actor",
                25_000L,
                false,
                true,
                true,
                false,
                "ROLES_ASSIGN_PERMISSIONS"
        );
        User actor = createUser("role015-actor", limitedActorRole);
        Role target = createRole("ROLE015_TARGET", "Role015 Target", "permissions", 10_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(put("/roles/{roleId}/permissions", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-015-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "permissionIds", List.of(permission("USERS_DELETE").getId().toString())
                        ))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(messageOf(result)).isEqualTo("You are not allowed to assign one or more permissions");
    }

    @Test
    @DisplayName("ROLE-016 PATCH /roles/{roleId}/status activates or deactivates a custom role")
    void role016ActivatesOrDeactivatesACustomRole() throws Exception {
        User actor = createUser("role016-admin", role("ADMIN"));
        Role target = createRole("ROLE016_TARGET", "Role016 Target", "status", 12_000L, false, true, true, false);
        String accessToken = accessTokenFor(actor, "ROLE-016-login");

        MvcResult deactivateResult = mockMvc.perform(patch("/roles/{roleId}/status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("isActive", false))))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult activateResult = mockMvc.perform(patch("/roles/{roleId}/status", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("isActive", true))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(bodyOf(deactivateResult).get("isActive").asBoolean()).isFalse();
        assertThat(bodyOf(activateResult).get("isActive").asBoolean()).isTrue();
        assertThat(roleRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("ROLE-017 DELETE /roles/{roleId} soft deletes a custom role")
    void role017SoftDeletesACustomRole() throws Exception {
        User actor = createUser("role017-admin", role("ADMIN"));
        Role target = createRole("ROLE017_TARGET", "Role017 Target", "delete", 12_000L, false, true, true, false);

        mockMvc.perform(delete("/roles/{roleId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-017-login"))))
                .andExpect(status().isNoContent());

        Role stored = roleRepository.findById(target.getId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.isAssignable()).isFalse();
        assertThat(stored.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("ROLE-018 DELETE /roles/{roleId} keeps deleted role out of GET /roles results")
    void role018KeepsDeletedRoleOutOfGetRolesResults() throws Exception {
        User actor = createUser("role018-admin", role("ADMIN"));
        Role target = createRole("ROLE018_TARGET", "Role018 Target", "delete", 12_000L, false, true, true, false);
        String accessToken = accessTokenFor(actor, "ROLE-018-login");

        mockMvc.perform(delete("/roles/{roleId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(get("/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(codesOf(bodyOf(result))).doesNotContain(target.getCode());
    }
}
