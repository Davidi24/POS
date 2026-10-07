package pos.pos.integration.robustness;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Staff and roles stay inside their restaurant")
class StaffAndRolesIsolationIntegrationTest extends AbstractPosApiIntegrationTest {

    private UUID roleId(String code) {
        return roleRepository.findByCode(code).orElseThrow().getId();
    }

    private Map<String, Object> newStaff(String label, UUID roleId) {
        int n = next();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "staff." + label + "." + n + "@pos.example");
        body.put("username", "staff." + label + "." + n);
        body.put("temporaryPassword", "TempPass123!");
        body.put("firstName", "New");
        body.put("lastName", "Person" + n);
        body.put("roleId", roleId);
        return body;
    }

    @Test
    @DisplayName("people added by an owner join that owner's restaurant and branch, and can work there right away")
    void newStaffJoinTheRestaurant() throws Exception {
        World world = newWorld("hire");
        Map<String, Object> body = newStaff("hire", roleId("WAITER"));
        body.put("defaultBranchId", world.branchId());
        JsonNode created = post("/auth/register").as(world.ownerToken()).body(body).expect(201);
        UUID userId = id(created);
        User saved = userRepository.findById(userId).orElseThrow();
        assertThat(saved.getRestaurantId()).isEqualTo(world.restaurantId());
        assertThat(saved.getDefaultBranchId()).isEqualTo(world.branchId());

        // Another restaurant's branch, or another restaurant, is refused.
        World other = newWorld("hire-other");
        Map<String, Object> wrongBranch = newStaff("hire2", roleId("WAITER"));
        wrongBranch.put("defaultBranchId", other.branchId());
        post("/auth/register").as(world.ownerToken()).body(wrongBranch).expect(400);
        Map<String, Object> wrongRestaurant = newStaff("hire3", roleId("WAITER"));
        wrongRestaurant.put("restaurantId", other.restaurantId());
        post("/auth/register").as(world.ownerToken()).body(wrongRestaurant).expect(403);

        // The owner can move the person to another branch of the same restaurant only.
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("firstName", "Moved");
        update.put("lastName", "Person");
        update.put("isActive", true);
        update.put("defaultBranchId", other.branchId());
        put("/users/{id}", userId).as(world.ownerToken()).body(update).expect(400);
        update.put("defaultBranchId", world.branchId());
        put("/users/{id}", userId).as(world.ownerToken()).body(update).expect(200);
    }

    @Test
    @DisplayName("owners only list, read and change people of their own restaurant")
    void usersAreScoped() throws Exception {
        World a = newWorld("users-a");
        World b = newWorld("users-b");
        User waiterA = staff(a, "waitera", "WAITER");
        User waiterB = staff(b, "waiterb", "WAITER");

        JsonNode list = get("/users").as(a.ownerToken()).param("size", 100).expect(200);
        List<String> ids = new ArrayList<>();
        list.get("items").forEach(user -> ids.add(user.get("id").asText()));
        assertThat(ids).contains(waiterA.getId().toString()).doesNotContain(waiterB.getId().toString());

        get("/users/{id}", waiterB.getId()).as(a.ownerToken()).expect(403);
        get("/users/{id}/roles", waiterB.getId()).as(a.ownerToken()).expect(403);
        get("/users/{id}/sessions", waiterB.getId()).as(a.ownerToken()).expect(403);
        delete("/users/{id}", waiterB.getId()).as(a.ownerToken()).expect(403);
        get("/users/{id}", waiterA.getId()).as(a.ownerToken()).expect(200);
        assertThat(userRepository.findById(waiterB.getId()).orElseThrow().getDeletedAt()).isNull();

        // The super admin still sees everyone.
        JsonNode all = get("/users").as(superAdminToken()).param("size", 100).param("search", "waiterb").expect(200);
        assertThat(all.get("items")).isNotEmpty();
    }

    @Test
    @DisplayName("a custom role belongs to the restaurant that made it")
    void customRolesAreScoped() throws Exception {
        World a = newWorld("roles-a");
        World b = newWorld("roles-b");
        String name = "Shift Lead " + next();
        JsonNode role = post("/roles").as(a.ownerToken()).body(Map.of("name", name, "description", "Runs the floor")).expect(201);
        UUID roleIdA = id(role);

        JsonNode rolesForB = get("/roles").as(b.ownerToken()).expect(200);
        List<String> codes = new ArrayList<>();
        rolesForB.forEach(r -> codes.add(r.get("id").asText()));
        assertThat(codes).doesNotContain(roleIdA.toString()).isNotEmpty();
        // Reported exactly like a role that doesn't exist ("Role not found").
        assertThat(get("/roles/{id}", roleIdA).as(b.ownerToken()).send().status()).isIn(400, 404);
        assertThat(get("/roles/{id}/permissions", roleIdA).as(b.ownerToken()).send().status()).isIn(400, 404);
        assertThat(put("/roles/{id}", roleIdA).as(b.ownerToken()).body(Map.of("name", "Hijacked")).send().status()).isIn(400, 403, 404);
        assertThat(delete("/roles/{id}", roleIdA).as(b.ownerToken()).send().status()).isIn(400, 403, 404);
        assertThat(post("/roles/{id}/clone", roleIdA).as(b.ownerToken()).body(Map.of("name", "Copy " + next())).send().status()).isIn(400, 403, 404);

        // B can't hand A's role to its own people either.
        User waiterB = staff(b, "rolesb", "WAITER");
        assertThat(put("/users/{id}/roles", waiterB.getId()).as(b.ownerToken()).body(Map.of("roleIds", List.of(roleIdA))).send().status())
                .isIn(400, 403, 404);

        JsonNode untouched = get("/roles/{id}", roleIdA).as(a.ownerToken()).expect(200);
        assertThat(untouched.get("name").asText()).isEqualTo(name);
        assertThat(untouched.get("isActive").asBoolean()).isTrue();
        JsonNode assignable = get("/roles/assignable").as(a.ownerToken()).expect(200);
        List<String> assignableIds = new ArrayList<>();
        assignable.forEach(r -> assignableIds.add(r.get("id").asText()));
        assertThat(assignableIds).contains(roleIdA.toString());
    }
}
