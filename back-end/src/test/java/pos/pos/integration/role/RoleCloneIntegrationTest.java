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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
@DisplayName("Role clone integration test")
class RoleCloneIntegrationTest extends AbstractRoleIntegrationTest {

    private static final String SCHEMA = "role_clone_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void registerProdProperties(DynamicPropertyRegistry registry) {
        registerProdProperties(registry, SCHEMA);
    }

    @Test
    @DisplayName("ROLE-019 POST /roles/{roleId}/clone clones role with copied permissions and rank into a custom role")
    void role019ClonesRoleWithCopiedPermissionsAndRankIntoACustomRole() throws Exception {
        User actor = createUser("role019-admin", role("ADMIN"));
        Role source = createRoleWithPermissions(
                "ROLE019_SOURCE",
                "Role019 Source",
                "source description",
                17_000L,
                false,
                true,
                true,
                false,
                "ROLES_READ",
                "USERS_CREATE"
        );

        MvcResult cloneResult = mockMvc.perform(post("/roles/{roleId}/clone", source.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-019-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Role019 Clone"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode cloneBody = bodyOf(cloneResult);
        UUID clonedRoleId = UUID.fromString(cloneBody.get("id").asText());
        assertThat(cloneBody.get("code").asText()).isEqualTo("ROLE019_CLONE");
        assertThat(cloneBody.get("name").asText()).isEqualTo("Role019 Clone");
        assertThat(cloneBody.get("rank").asLong()).isEqualTo(17_000L);
        assertThat(cloneBody.get("isSystem").asBoolean()).isFalse();
        assertThat(cloneBody.get("description").asText()).isEqualTo("source description");

        MvcResult permissionsResult = mockMvc.perform(get("/roles/{roleId}/permissions", clonedRoleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-019-verify-login"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(codesOf(bodyOf(permissionsResult))).containsExactly("USERS_CREATE", "ROLES_READ");
    }

    @Test
    @DisplayName("ROLE-020 POST /roles/{roleId}/clone rejects duplicate target name or derived target code")
    void role020RejectsDuplicateTargetNameOrDerivedTargetCode() throws Exception {
        User actor = createUser("role020-admin", role("ADMIN"));
        createRole("ASSISTANT_MANAGER", "Assistant Manager", "existing", 12_000L, false, true, true, false);
        String accessToken = accessTokenFor(actor, "ROLE-020-login");

        MvcResult duplicateNameResult = mockMvc.perform(post("/roles/{roleId}/clone", role("MANAGER").getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Assistant Manager"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult duplicateCodeResult = mockMvc.perform(post("/roles/{roleId}/clone", role("MANAGER").getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Assistant-Manager"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(messageOf(duplicateNameResult)).isEqualTo("Role name already in use");
        assertThat(messageOf(duplicateCodeResult)).isEqualTo("Role code already in use");
    }

    @Test
    @DisplayName("ROLE-021 POST /roles/{roleId}/clone rejects cloning role outside actor assignment scope")
    void role021RejectsCloningRoleOutsideActorAssignmentScope() throws Exception {
        User actor = createUser("role021-admin", role("ADMIN"));
        Role source = createRole("ROLE021_SOURCE", "Role021 Source", "source", 35_000L, false, true, true, false);

        MvcResult result = mockMvc.perform(post("/roles/{roleId}/clone", source.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokenFor(actor, "ROLE-021-login")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Role021 Clone"))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(messageOf(result)).isEqualTo("You are not allowed to assign this role");
    }
}
