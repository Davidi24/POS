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
import pos.pos.role.entity.Role;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("User role management integration test")
class UserRoleManagementIntegrationTest extends AbstractUserIntegrationTest {

    @Test
    @DisplayName("USER-008 GET /users/{userId}/roles returns active roles for target user")
    void user008ReturnsActiveRolesForTargetUser() throws Exception {
        User target = createUser("user008-target", "WAITER", "Role", "Target", true, true, null, false);
        assignRole(target, role("MANAGER"));
        String adminAccessToken = adminAccessToken();

        MvcResult result = mockMvc.perform(get("/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body).hasSize(2);
        assertThat(body.findValuesAsText("code")).containsExactly("MANAGER", "WAITER");
    }

    @Test
    @DisplayName("USER-009 PUT /users/{userId}/roles replaces user roles")
    void user009ReplacesUserRoles() throws Exception {
        User target = createUser("user009-target", "WAITER", "Role", "Replace", true, true, null, false);
        String adminAccessToken = adminAccessToken();

        MvcResult result = mockMvc.perform(put("/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleIds", List.of(role("MANAGER").getId().toString(), role("WAITER").getId().toString())
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("roles").get(0).asText()).isEqualTo("MANAGER");
        assertThat(body.get("roles").get(1).asText()).isEqualTo("WAITER");
        assertThat(roleRepository.findActiveRoleCodesByUserId(target.getId())).containsExactly("MANAGER", "WAITER");
    }

    @Test
    @DisplayName("USER-010 PUT /users/{userId}/roles rejects missing, inactive, unassignable, protected, or above-rank roles")
    void user010RejectsMissingInactiveUnassignableProtectedOrAboveRankRoles() throws Exception {
        User manager = createUser("user010-manager", "MANAGER", "Role", "Actor", true, true, null, false);
        User target = createUser("user010-target", "WAITER", "Role", "Target", true, true, null, false);
        Role inactiveRole = createRole("inactive", 5_000L, false, true, false);
        Role unassignableRole = createRole("unassignable", 5_000L, true, false, false);
        Role protectedRole = createRole("protected", 5_000L, true, true, true);
        Role aboveRankRole = createRole("above_rank", 25_000L, true, true, false);

        String managerAccessToken = accessTokenFor(manager.getUsername(), DEFAULT_PASSWORD, "USER-010-manager-login");

        MvcResult missingRoleResult = replaceRoles(managerAccessToken, target.getId(), UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andReturn();
        MvcResult inactiveRoleResult = replaceRoles(managerAccessToken, target.getId(), inactiveRole.getId())
                .andExpect(status().isBadRequest())
                .andReturn();
        MvcResult unassignableRoleResult = replaceRoles(managerAccessToken, target.getId(), unassignableRole.getId())
                .andExpect(status().isForbidden())
                .andReturn();
        MvcResult protectedRoleResult = replaceRoles(managerAccessToken, target.getId(), protectedRole.getId())
                .andExpect(status().isForbidden())
                .andReturn();
        MvcResult aboveRankRoleResult = replaceRoles(managerAccessToken, target.getId(), aboveRankRole.getId())
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(bodyOf(missingRoleResult).get("message").asText()).isEqualTo("Role not found");
        assertThat(bodyOf(inactiveRoleResult).get("message").asText()).isEqualTo("Role not found");
        assertThat(bodyOf(unassignableRoleResult).get("message").asText()).isEqualTo("You are not allowed to assign this role");
        assertThat(bodyOf(protectedRoleResult).get("message").asText()).isEqualTo("You are not allowed to assign this role");
        assertThat(bodyOf(aboveRankRoleResult).get("message").asText()).isEqualTo("You are not allowed to assign this role");
    }

    private org.springframework.test.web.servlet.ResultActions replaceRoles(String accessToken, UUID userId, UUID roleId) throws Exception {
        return mockMvc.perform(put("/users/{userId}/roles", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "roleIds", List.of(roleId.toString())
                ))));
    }
}
