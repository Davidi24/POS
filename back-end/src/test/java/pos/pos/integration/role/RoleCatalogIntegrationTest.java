package pos.pos.integration.role;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.role.entity.Role;
import pos.pos.security.rbac.AppPermission;
import pos.pos.user.entity.User;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
@DisplayName("Role catalog integration test")
class RoleCatalogIntegrationTest extends AbstractRoleIntegrationTest {

    private static final String SCHEMA = "role_catalog_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registerProdProperties(registry, SCHEMA);
    }

    @Test
    @DisplayName("ROLE-001 GET /roles returns active non-deleted roles in expected order")
    void role001ReturnsActiveNonDeletedRolesInExpectedOrder() throws Exception {
        Role alphaRankRole = createRole("ROLE001_ALPHA", "Role001 Alpha", "alpha", 45_000L, false, true, true, false);
        Role betaRankRole = createRole("ROLE001_BETA", "Role001 Beta", "beta", 45_000L, false, true, true, false);
        Role floorLead = createRole("ROLE001_FLOOR_LEAD", "Role001 Floor Lead", "floor", 15_000L, false, true, true, false);
        Role inactiveRole = createRole("ROLE001_INACTIVE", "Role001 Inactive", "inactive", 17_000L, false, false, true, false);
        Role deletedRole = createDeletedRole("ROLE001_DELETED", "Role001 Deleted", "deleted", 16_000L, false, true, false);

        MvcResult result = mockMvc.perform(get("/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken())))
                .andExpect(status().isOk())
                .andReturn();

        List<String> codes = codesOf(bodyOf(result));
        assertThat(codes).contains(alphaRankRole.getCode(), betaRankRole.getCode(), floorLead.getCode());
        assertThat(codes).doesNotContain(inactiveRole.getCode(), deletedRole.getCode());
        assertThat(indexOf(codes, "SUPER_ADMIN")).isLessThan(indexOf(codes, alphaRankRole.getCode()));
        assertThat(indexOf(codes, alphaRankRole.getCode())).isLessThan(indexOf(codes, betaRankRole.getCode()));
        assertThat(indexOf(codes, betaRankRole.getCode())).isLessThan(indexOf(codes, "CO_OWNER"));
        assertThat(indexOf(codes, "MANAGER")).isLessThan(indexOf(codes, floorLead.getCode()));
        assertThat(indexOf(codes, floorLead.getCode())).isLessThan(indexOf(codes, "WAITER"));
    }

    @Test
    @DisplayName("ROLE-002 GET /roles/system returns only active system roles")
    void role002ReturnsOnlyActiveSystemRoles() throws Exception {
        Role customSystemRole = createRole("ROLE002_SYSTEM_ACTIVE", "Role002 System Active", "system active", 12_000L, true, true, true, false);
        Role inactiveSystemRole = createRole("ROLE002_SYSTEM_INACTIVE", "Role002 System Inactive", "system inactive", 11_000L, true, false, true, false);
        Role activeCustomRole = createRole("ROLE002_CUSTOM_ACTIVE", "Role002 Custom Active", "custom active", 10_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(get("/roles/system")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        List<String> codes = codesOf(body);
        assertThat(codes).contains("SUPER_ADMIN", "OWNER", "CO_OWNER", "ADMIN", "MANAGER", "WAITER", customSystemRole.getCode());
        assertThat(codes).doesNotContain(inactiveSystemRole.getCode(), activeCustomRole.getCode());
        body.forEach(node -> assertThat(node.get("isSystem").asBoolean()).isTrue());
    }

    @Test
    @DisplayName("ROLE-003 GET /roles/{roleId} returns one existing role")
    void role003ReturnsOneExistingRole() throws Exception {
        Role target = createRole("ROLE003_TARGET", "Role003 Target", "One role", 18_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(get("/roles/{roleId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("id").asText()).isEqualTo(target.getId().toString());
        assertThat(body.get("code").asText()).isEqualTo("ROLE003_TARGET");
        assertThat(body.get("name").asText()).isEqualTo("Role003 Target");
        assertThat(body.get("rank").asLong()).isEqualTo(18_000L);
        assertThat(body.get("isActive").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("ROLE-004 GET /roles/{roleId} rejects missing or deleted role")
    void role004RejectsMissingOrDeletedRole() throws Exception {
        Role deletedRole = createDeletedRole("ROLE004_DELETED", "Role004 Deleted", "deleted", 9_000L, false, true, false);
        String accessToken = adminAccessToken();

        MvcResult missingResult = mockMvc.perform(get("/roles/{roleId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult deletedResult = mockMvc.perform(get("/roles/{roleId}", deletedRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(messageOf(missingResult)).isEqualTo("Role not found");
        assertThat(messageOf(deletedResult)).isEqualTo("Role not found");
    }

    @Test
    @DisplayName("ROLE-005 GET /permissions returns all seeded permissions including role-management permissions")
    void role005ReturnsAllSeededPermissionsIncludingRoleManagementPermissions() throws Exception {
        MvcResult result = mockMvc.perform(get("/permissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken())))
                .andExpect(status().isOk())
                .andReturn();

        List<String> codes = codesOf(bodyOf(result));
        List<String> expectedCodes = Arrays.stream(AppPermission.values())
                .map(Enum::name)
                .toList();

        assertThat(codes).hasSize(expectedCodes.size());
        assertThat(codes).containsExactlyInAnyOrderElementsOf(expectedCodes);
        assertThat(codes).contains(
                "ROLES_READ",
                "ROLES_CREATE",
                "ROLES_UPDATE",
                "ROLES_DELETE",
                "ROLES_ASSIGN_PERMISSIONS"
        );
    }

    @Test
    @DisplayName("ROLE-006 GET /roles/{roleId}/permissions returns assigned permissions in stable order")
    void role006ReturnsAssignedPermissionsInStableOrder() throws Exception {
        Role target = createRole("ROLE006_TARGET", "Role006 Target", "permissions target", 14_000L, false, true, true, false);
        assignPermissions(target, "ROLES_UPDATE", "USERS_CREATE", "ROLES_ASSIGN_PERMISSIONS");

        MvcResult result = mockMvc.perform(get("/roles/{roleId}/permissions", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(codesOf(bodyOf(result)))
                .containsExactly("ROLES_ASSIGN_PERMISSIONS", "USERS_CREATE", "ROLES_UPDATE");
    }

    @Test
    @DisplayName("ROLE-007 GET /roles/assignable returns only roles the actor may assign")
    void role007ReturnsOnlyRolesTheActorMayAssign() throws Exception {
        User actor = createUser("role007-admin", role("ADMIN"));
        Role shiftLead = createRole("ROLE007_SHIFT_LEAD", "Role007 Shift Lead", "assignable", 25_000L, false, true, true, false);
        Role cashier = createRole("ROLE007_CASHIER", "Role007 Cashier", "assignable", 15_000L, false, true, true, false);
        Role aboveActor = createRole("ROLE007_ABOVE_ACTOR", "Role007 Above Actor", "above actor", 35_000L, false, true, true, false);
        Role inactiveAssignable = createRole("ROLE007_INACTIVE", "Role007 Inactive", "inactive", 12_000L, false, false, true, false);
        Role unassignable = createRole("ROLE007_UNASSIGNABLE", "Role007 Unassignable", "unassignable", 11_000L, false, true, false, false);
        Role protectedRole = createRole("ROLE007_PROTECTED", "Role007 Protected", "protected", 10_500L, false, true, true, true);

        MvcResult result = mockMvc.perform(get("/roles/assignable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-007-login"))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> codes = codesOf(bodyOf(result));
        assertThat(codes).containsExactly("ROLE007_SHIFT_LEAD", "MANAGER", "ROLE007_CASHIER", "WAITER");
        assertThat(codes).doesNotContain(
                aboveActor.getCode(),
                inactiveAssignable.getCode(),
                unassignable.getCode(),
                protectedRole.getCode(),
                "ADMIN",
                "OWNER",
                "CO_OWNER",
                "SUPER_ADMIN"
        );
    }
}
