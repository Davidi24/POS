package pos.pos.integration.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("User query integration test")
class UserQueryIntegrationTest extends AbstractUserIntegrationTest {

    @Test
    @DisplayName("USER-001 GET /users returns paginated users with search, filters, sorting, and visibility restrictions")
    void user001ReturnsPaginatedUsersWithVisibilityRestrictions() throws Exception {
        String label = "user001";
        User manager = createUser(label + "-manager", "MANAGER", "Manager", "Actor", true, true, null, false);
        User alphaWaiter = createUser(label + "-alpha", "WAITER", "Alpha", "Visible", true, true, null, false);
        User betaWaiter = createUser(label + "-beta", "WAITER", "Beta", "Visible", true, true, null, false);
        User inactiveWaiter = createUser(label + "-gamma", "WAITER", "Gamma", "Inactive", false, true, null, false);
        User hiddenAdmin = createUser(label + "-admin", "ADMIN", "Aaron", "Hidden", true, true, null, false);
        assignRole(hiddenAdmin, role("WAITER"));
        User hiddenOwner = createUser(label + "-owner", "OWNER", "Abel", "Hidden", true, true, null, false);
        assignRole(hiddenOwner, role("WAITER"));

        String managerAccessToken = accessTokenFor(manager.getUsername(), DEFAULT_PASSWORD, "USER-001-manager-login");

        MvcResult firstPageResult = mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .param("search", label)
                        .param("active", "true")
                        .param("roleCode", "WAITER")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "firstName")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstPage = bodyOf(firstPageResult);
        assertThat(firstPage.get("items")).hasSize(1);
        assertThat(firstPage.get("items").get(0).get("id").asText()).isEqualTo(alphaWaiter.getId().toString());
        assertThat(firstPage.get("totalElements").asInt()).isEqualTo(2);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.get("hasPrevious").asBoolean()).isFalse();

        MvcResult secondPageResult = mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .param("search", label)
                        .param("active", "true")
                        .param("roleCode", "WAITER")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sortBy", "firstName")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode secondPage = bodyOf(secondPageResult);
        assertThat(secondPage.get("items")).hasSize(1);
        assertThat(secondPage.get("items").get(0).get("id").asText()).isEqualTo(betaWaiter.getId().toString());
        assertThat(secondPage.get("hasNext").asBoolean()).isFalse();
        assertThat(secondPage.get("hasPrevious").asBoolean()).isTrue();

        MvcResult inactiveResult = mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken))
                        .param("search", label)
                        .param("active", "false")
                        .param("roleCode", "WAITER")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "firstName")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode inactivePage = bodyOf(inactiveResult);
        assertThat(inactivePage.get("items")).hasSize(1);
        assertThat(inactivePage.get("items").get(0).get("id").asText()).isEqualTo(inactiveWaiter.getId().toString());

        assertThat(firstPage.get("items").findValuesAsText("id"))
                .doesNotContain(hiddenAdmin.getId().toString(), hiddenOwner.getId().toString());
    }

    @Test
    @DisplayName("USER-002 GET /users/{userId} returns a manageable target user")
    void user002ReturnsManageableTargetUser() throws Exception {
        User manager = createUser("user002-manager", "MANAGER", "Manage", "Actor", true, true, null, false);
        User target = createUser("user002-target", "WAITER", "Waiter", "Target", true, true, "+1555010801", true);

        String managerAccessToken = accessTokenFor(manager.getUsername(), DEFAULT_PASSWORD, "USER-002-manager-login");

        MvcResult result = mockMvc.perform(get("/users/{userId}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("id").asText()).isEqualTo(target.getId().toString());
        assertThat(body.get("username").asText()).isEqualTo(target.getUsername());
        assertThat(body.get("phone").asText()).isEqualTo("+1555010801");
        assertThat(body.get("roles").get(0).asText()).isEqualTo("WAITER");
    }

    @Test
    @DisplayName("USER-003 GET /users/{userId} rejects target user above actor rank or protected target")
    void user003RejectsTargetUserAboveActorRankOrProtectedTarget() throws Exception {
        User manager = createUser("user003-manager", "MANAGER", "Manage", "Actor", true, true, null, false);
        User adminTarget = createUser("user003-admin", "ADMIN", "Admin", "Target", true, true, null, false);
        User ownerTarget = createUser("user003-owner", "OWNER", "Owner", "Target", true, true, null, false);

        String managerAccessToken = accessTokenFor(manager.getUsername(), DEFAULT_PASSWORD, "USER-003-manager-login");

        MvcResult adminResult = mockMvc.perform(get("/users/{userId}", adminTarget.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken)))
                .andExpect(status().isForbidden())
                .andReturn();

        MvcResult ownerResult = mockMvc.perform(get("/users/{userId}", ownerTarget.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerAccessToken)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(bodyOf(adminResult).get("message").asText()).isEqualTo("You are not allowed to manage this user");
        assertThat(bodyOf(ownerResult).get("message").asText()).isEqualTo("You are not allowed to manage this user");
    }
}
