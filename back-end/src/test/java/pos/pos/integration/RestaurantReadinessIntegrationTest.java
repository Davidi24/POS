package pos.pos.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.user.entity.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Restaurant checklist end to end on PostgreSQL: required contact details, ownership transfer, archiving that
 * retires devices, public applications with their limits and status, reserved codes, the branch chooser's paging and
 * search, and list positions under simultaneous creation.
 */
@DisplayName("Restaurant readiness")
class RestaurantReadinessIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final String S = SCHEMA + ".";

    // ---- Contact details ----

    @Test
    @DisplayName("an update, an application or a creation without the restaurant's email or phone is refused and changes nothing")
    void contactDetailsAreRequiredEverywhere() throws Exception {
        World w = newWorld("contact");
        String admin = superAdminToken();
        String route = "/restaurants/" + w.restaurantId();
        Map<String, Object> edit = editable(get(route).as(admin).expect(200));

        for (var change : List.<Map.Entry<String, Object>>of(Map.entry("email", ""), Map.entry("email", "   "), Map.entry("phone", ""))) {
            Map<String, Object> body = new HashMap<>(edit);
            body.put(change.getKey(), change.getValue());
            put(route).as(admin).body(body).expect(400);
            put(route).as(w.ownerToken()).body(body).expect(400);
        }
        Map<String, Object> missing = new HashMap<>(edit);
        missing.remove("phone");
        put(route).as(admin).body(missing).expect(400);
        assertThat(get(route).as(admin).expect(200).get("phone").asText()).isEqualTo(w.restaurant().getPhone());

        Map<String, Object> create = new HashMap<>(newRestaurant("No phone " + next()));
        create.remove("phone");
        post("/restaurants").as(admin).body(create).expect(400);

        Map<String, Object> application = new HashMap<>(application("No email " + next(), "noemail" + next()));
        application.remove("email");
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp()).body(application).expect(400);
        application.put("email", "x@restaurant.example");
        application.put("phone", " ");
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp()).body(application).expect(400);
    }

    // ---- Ownership transfer ----

    @Test
    @DisplayName("giving the restaurant to a staff member signs the former owner out for good and keeps every record")
    void transferToExistingStaffRemovesFormerOwnerAccess() throws Exception {
        World w = newWorld("transfer-staff");
        String admin = superAdminToken();
        User manager = staff(w, "manager", "MANAGER");
        String managerToken = token(manager);
        String formerToken = w.ownerToken();
        get("/auth/me").as(formerToken).expect(200);
        UUID order = paidOrder(w);
        JsonNode receipt = get("/restaurants/{r}/orders/{o}/receipt", w.restaurantId(), order).as(admin).expect(200);

        JsonNode candidates = get("/restaurants/{r}/owner/candidates", w.restaurantId()).as(admin).expect(200);
        assertThat(ids(candidates)).contains(manager.getId().toString()).doesNotContain(w.owner().getId().toString(), superAdmin().getId().toString());
        assertThat(ids(get("/restaurants/{r}/owner/candidates", w.restaurantId()).as(admin).param("search", "nobody-matches").expect(200))).isEmpty();

        long version = restaurantRepository.findById(w.restaurantId()).orElseThrow().getVersion();
        JsonNode transfer = post("/restaurants/{r}/owner/transfer", w.restaurantId()).as(admin)
                .body(Map.of("newOwnerUserId", manager.getId(), "expectedVersion", version, "reason", "Sold the business")).expect(200);

        assertThat(transfer.get("newOwnerId").asText()).isEqualTo(manager.getId().toString());
        assertThat(transfer.get("previousOwnerId").asText()).isEqualTo(w.owner().getId().toString());
        assertThat(transfer.get("previousOwnerDeactivated").asBoolean()).isTrue();
        assertThat(transfer.get("newOwnerInvited").asBoolean()).isFalse();
        assertThat(transfer.get("sessionsRevoked").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(transfer.get("reason").asText()).isEqualTo("Sold the business");

        // The former owner is out at once and can't sign in again.
        get("/auth/me").as(formerToken).expect(401);
        assertThat(loginStatus(w.owner().getUsername())).isNotEqualTo(200);
        User former = userRepository.findById(w.owner().getId()).orElseThrow();
        assertThat(former.isActive()).isFalse();
        assertThat(former.getDeletedAt()).isNull();
        assertThat(roles(w.owner().getId())).doesNotContain("OWNER");
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "user_sessions where user_id = ? and revoked = false",
                Long.class, w.owner().getId())).isZero();

        // The new owner works straight away, with the owner's powers, in the same restaurant.
        assertThat(roles(manager.getId())).contains("OWNER", "MANAGER");
        assertThat(get("/auth/me").as(managerToken).expect(200).get("roles").toString()).contains("OWNER");
        assertThat(restaurantRepository.findById(w.restaurantId()).orElseThrow().getOwnerId()).isEqualTo(manager.getId());
        Map<String, Object> info = editable(get("/restaurants/{r}", w.restaurantId()).as(managerToken).expect(200));
        info.put("description", "Run by the new owner");
        put("/restaurants/{r}", w.restaurantId()).as(managerToken).body(info).expect(200);

        // Every record stays: the sale, its receipt, the history of owners and the audit line.
        JsonNode receiptAfter = get("/restaurants/{r}/orders/{o}/receipt", w.restaurantId(), order).as(admin).expect(200);
        ((com.fasterxml.jackson.databind.node.ObjectNode) receipt).remove("printedAt");
        ((com.fasterxml.jackson.databind.node.ObjectNode) receiptAfter).remove("printedAt");
        assertThat(receiptAfter).isEqualTo(receipt);
        JsonNode history = get("/restaurants/{r}/owner/transfers", w.restaurantId()).as(admin).expect(200);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("newOwnerName").asText()).isEqualTo(manager.getFirstName() + " " + manager.getLastName());
        assertThat(history.get(0).get("transferredByName").asText()).isEqualTo("Super Admin");
        assertThat(jdbcTemplate.queryForObject("select message from " + S + "\"settings-audit-logs\" where entity_id = ? and action = 'OWNER_TRANSFER'",
                String.class, w.restaurantId())).contains("Sold the business");
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "email_outbox where user_id = ? and kind = 'OWNERSHIP_TRANSFERRED'",
                Long.class, w.owner().getId())).isEqualTo(1);

        // The same version can't be used twice: the first transfer moved it on.
        post("/restaurants/{r}/owner/transfer", w.restaurantId()).as(admin)
                .body(Map.of("newOwnerUserId", w.owner().getId(), "expectedVersion", version)).expect(409);
    }

    @Test
    @DisplayName("giving the restaurant to a new person invites them, and the former owner loses access")
    void transferToInvitedPerson() throws Exception {
        World w = newWorld("transfer-invite");
        String admin = superAdminToken();
        int n = next();
        Map<String, Object> invitee = Map.of("email", "new.owner." + n + "@pos.example", "username", "new.owner." + n,
                "firstName", "New", "lastName", "Owner");
        long version = restaurantRepository.findById(w.restaurantId()).orElseThrow().getVersion();

        JsonNode transfer = post("/restaurants/{r}/owner/transfer", w.restaurantId()).as(admin)
                .body(Map.of("newOwner", invitee, "expectedVersion", version)).expect(200);

        assertThat(transfer.get("newOwnerInvited").asBoolean()).isTrue();
        User newOwner = userRepository.findByEmailAndDeletedAtIsNull("new.owner." + n + "@pos.example").orElseThrow();
        assertThat(newOwner.getRestaurantId()).isEqualTo(w.restaurantId());
        assertThat(roles(newOwner.getId())).contains("OWNER");
        assertThat(restaurantRepository.findById(w.restaurantId()).orElseThrow().getOwnerId()).isEqualTo(newOwner.getId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "email_outbox where user_id = ? and kind = 'OWNER_INVITE'",
                Long.class, newOwner.getId())).isEqualTo(1);
        get("/auth/me").as(w.ownerToken()).expect(401);
        // The invitation status now follows the new owner.
        assertThat(get("/restaurants/{r}/owner/invite", w.restaurantId()).as(admin).expect(200).get("email").asText())
                .isEqualTo("new.owner." + n + "@pos.example");

        // The invited email is taken now (the app's usual "already in use" answer), and nothing changes.
        long next = restaurantRepository.findById(w.restaurantId()).orElseThrow().getVersion();
        assertThat(post("/restaurants/{r}/owner/transfer", w.restaurantId()).as(admin)
                .body(Map.of("newOwner", invitee, "expectedVersion", next)).expect(400).get("message").asText()).isEqualTo("Email already in use");
        assertThat(restaurantRepository.findById(w.restaurantId()).orElseThrow().getOwnerId()).isEqualTo(newOwner.getId());
    }

    @Test
    @DisplayName("only the Super Admin transfers, to exactly one valid person, and nothing changes when refused")
    void transferGuards() throws Exception {
        World w = newWorld("transfer-guards");
        World other = newWorld("transfer-other");
        String admin = superAdminToken();
        User co = staff(w.restaurant(), w.branch(), "co-transfer", "CO_SUPER_ADMIN");
        User manager = staff(w, "guard-manager", "MANAGER");
        long version = restaurantRepository.findById(w.restaurantId()).orElseThrow().getVersion();
        Map<String, Object> valid = Map.of("newOwnerUserId", manager.getId(), "expectedVersion", version);
        String path = "/restaurants/" + w.restaurantId() + "/owner/transfer";

        post(path).body(valid).expect(401);
        post(path).as(w.ownerToken()).body(valid).expect(403);
        post(path).as(token(manager)).body(valid).expect(403);
        post(path).as(token(co)).body(valid).expect(403);
        assertThat(post(path).as(other.ownerToken()).body(valid).send().status()).isIn(403, 404);
        get("/restaurants/{r}/owner/candidates", w.restaurantId()).as(w.ownerToken()).expect(403);
        assertThat(get("/restaurants/{r}/owner/transfers", w.restaurantId()).as(other.ownerToken()).send().status()).isIn(403, 404);

        post(path).as(admin).body(Map.of("expectedVersion", version)).expect(400);
        post(path).as(admin).body(Map.of("newOwnerUserId", manager.getId(), "expectedVersion", version,
                "newOwner", Map.of("email", "x" + next() + "@pos.example", "username", "x" + next(), "firstName", "X", "lastName", "Y"))).expect(400);
        post(path).as(admin).body(Map.of("newOwnerUserId", manager.getId())).expect(400);
        post(path).as(admin).body(Map.of("newOwnerUserId", w.owner().getId(), "expectedVersion", version)).expect(400);
        post(path).as(admin).body(Map.of("newOwnerUserId", superAdmin().getId(), "expectedVersion", version)).expect(400);
        post(path).as(admin).body(Map.of("newOwnerUserId", co.getId(), "expectedVersion", version)).expect(400);
        post(path).as(admin).body(Map.of("newOwnerUserId", other.owner().getId(), "expectedVersion", version)).expect(409);
        post(path).as(admin).body(Map.of("newOwnerUserId", UUID.randomUUID(), "expectedVersion", version)).expect(400);
        post(path).as(admin).body(Map.of("newOwnerUserId", manager.getId(), "expectedVersion", version + 1)).expect(409);
        post("/restaurants/{r}/owner/transfer", UUID.randomUUID()).as(admin).body(valid).expect(404);

        assertThat(restaurantRepository.findById(w.restaurantId()).orElseThrow().getOwnerId()).isEqualTo(w.owner().getId());
        get("/auth/me").as(w.ownerToken()).expect(200);
        assertThat(get("/restaurants/{r}/owner/transfers", w.restaurantId()).as(admin).expect(200)).isEmpty();
    }

    // ---- Archiving ----

    @Test
    @DisplayName("archiving a restaurant retires its devices: secrets and unused pairing tokens stop working, history stays")
    void archivingRetiresDevices() throws Exception {
        World w = newWorld("archive-devices");
        String admin = superAdminToken();
        UUID order = paidOrder(w);
        String device = post("/restaurants/{r}/branches/{b}/devices", w.restaurantId(), w.branchId()).as(admin)
                .body(Map.of("code", "TILL_" + next(), "name", "Front till", "deviceType", "TERMINAL", "status", "ACTIVE",
                        "active", true, "online", false)).expect(201).get("id").asText();
        String assignment = post("/restaurants/{r}/devices/{d}/assignments", w.restaurantId(), device).as(admin)
                .body(Map.of("assignmentType", "USER", "userId", w.owner().getId().toString())).expect(201).get("id").asText();
        String usedToken = post("/restaurants/{r}/devices/{d}/pairing-tokens", w.restaurantId(), device).as(admin)
                .body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();
        String secret = post("/public/devices/pairing/redeem").body(Map.of("pairingToken", usedToken)).expect(200).get("deviceSecret").asText();
        post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", secret).expect(200);
        String unusedToken = post("/restaurants/{r}/devices/{d}/pairing-tokens", w.restaurantId(), device).as(admin)
                .body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();

        delete("/restaurants/{r}", w.restaurantId()).as(admin).expect(204);

        post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", secret).expect(401);
        post("/public/devices/pairing/redeem").body(Map.of("pairingToken", unusedToken)).expect(401);
        Map<String, Object> row = jdbcTemplate.queryForMap("select status, is_active, is_online, auth_secret_hash from " + S + "devices where id = ?",
                UUID.fromString(device));
        assertThat(row.get("status")).isEqualTo("RETIRED");
        assertThat(row.get("is_active")).isEqualTo(false);
        assertThat(row.get("is_online")).isEqualTo(false);
        assertThat(row.get("auth_secret_hash")).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "\"device-pairing-tokens\" where device_id = ?"
                + " and used_at is null and revoked_at is null", Long.class, UUID.fromString(device))).isZero();
        assertThat(jdbcTemplate.queryForObject("select is_active from " + S + "\"device-assignments\" where id = ?",
                Boolean.class, UUID.fromString(assignment))).isFalse();
        // History stays: the device row, its assignment, the sale and its receipt.
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "\"device-assignments\" where device_id = ?",
                Long.class, UUID.fromString(device))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "payments where order_id = ?", Long.class, order)).isEqualTo(1);
        get("/restaurants/{r}/orders/{o}/receipt", w.restaurantId(), order).as(admin).expect(200);
        assertThat(jdbcTemplate.queryForObject("select message from " + S + "\"settings-audit-logs\" where entity_id = ? and action = 'ARCHIVE'",
                String.class, w.restaurantId())).contains("devices retired=1");
        // Staff of the archived restaurant are out too.
        get("/auth/me").as(w.ownerToken()).expect(401);
    }

    @Test
    @DisplayName("closing a restaurant for good through its status also retires its devices")
    void archivedStatusRetiresDevices() throws Exception {
        World w = newWorld("archive-status");
        String admin = superAdminToken();
        String device = post("/restaurants/{r}/branches/{b}/devices", w.restaurantId(), w.branchId()).as(admin)
                .body(Map.of("code", "KDS_" + next(), "name", "Kitchen screen", "deviceType", "TERMINAL", "status", "ACTIVE",
                        "active", true, "online", false)).expect(201).get("id").asText();
        String pairing = post("/restaurants/{r}/devices/{d}/pairing-tokens", w.restaurantId(), device).as(admin)
                .body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();
        String secret = post("/public/devices/pairing/redeem").body(Map.of("pairingToken", pairing)).expect(200).get("deviceSecret").asText();

        patch("/restaurants/{r}/status", w.restaurantId()).as(admin).body(Map.of("isActive", false, "status", "SUSPENDED")).expect(200);
        post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", secret).expect(200);

        patch("/restaurants/{r}/status", w.restaurantId()).as(admin).body(Map.of("isActive", false, "status", "ARCHIVED")).expect(200);
        post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", secret).expect(401);
        assertThat(jdbcTemplate.queryForObject("select status from " + S + "devices where id = ?", String.class, UUID.fromString(device)))
                .isEqualTo("RETIRED");
    }

    // ---- Public applications ----

    @Test
    @DisplayName("an applicant applies, is limited per address and per email, follows the status by code and sees the rejection reason")
    void publicApplicationsFlow() throws Exception {
        String admin = superAdminToken();
        String ip = randomIp();
        int n = next();
        JsonNode receipt = post("/restaurants/registrations").header("X-Forwarded-For", ip)
                .body(application("Applied Bistro " + n, "applied" + n)).expect(201);
        String code = receipt.get("statusCode").asText();
        UUID applicationId = UUID.fromString(receipt.get("id").asText());
        assertThat(receipt.get("status").asText()).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "email_outbox where restaurant_id = ? and kind = 'RESTAURANT_APPLICATION'",
                Long.class, applicationId)).isEqualTo(1);

        // A second waiting application for the same person is a conflict, wherever it comes from.
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp())
                .body(application("Applied Bistro Again " + n, "applied" + n)).expect(409);

        // Five applications an hour from one address, then 429.
        for (int i = 0; i < 4; i++) {
            post("/restaurants/registrations").header("X-Forwarded-For", ip).body(application("Same Place " + n + "-" + i, "same" + n + "x" + i)).expect(201);
        }
        JsonNode limited = post("/restaurants/registrations").header("X-Forwarded-For", ip)
                .body(application("One Too Many " + n, "toomany" + n)).expect(429);
        assertThat(limited.get("message").asText()).contains("Too many applications");
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp())
                .body(application("Other Place " + n, "otherplace" + n)).expect(201);

        // Three attempts a day per owner email, then 429 even from new addresses.
        Map<String, Object> repeated = application("Repeat " + n, "repeat" + n);
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp()).body(repeated).expect(201);
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp()).body(repeated).expect(409);
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp()).body(repeated).expect(409);
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp()).body(repeated).expect(429);

        // Status by code: only the code answers, typed loosely or not; wrong codes are 404.
        assertThat(status(code, randomIp()).get("status").asText()).isEqualTo("PENDING");
        assertThat(status(code.toLowerCase().replace("-", " "), randomIp()).get("id").asText()).isEqualTo(applicationId.toString());
        post("/restaurants/registrations/status").header("X-Forwarded-For", randomIp()).body(Map.of("code", "ZZZZZ-ZZZZZ-ZZZZZ-ZZZZZ")).expect(404);
        post("/restaurants/registrations/status").header("X-Forwarded-For", randomIp()).body(Map.of("code", "")).expect(400);

        // Reviewers see it in the queue; a rejection needs a reason, which the applicant then sees.
        JsonNode queue = get("/platform/restaurant-applications").as(admin).param("search", "Applied Bistro " + n).expect(200);
        assertThat(ids(queue.get("items"))).containsExactly(applicationId.toString());
        assertThat(get("/platform/restaurant-applications/{id}", applicationId).as(admin).expect(200).get("phone").asText()).isNotBlank();
        patch("/restaurants/registrations/{id}/review", applicationId).as(admin).body(Map.of("decision", "REJECT")).expect(400);
        patch("/restaurants/registrations/{id}/review", applicationId).as(admin)
                .body(Map.of("decision", "REJECT", "rejectionReason", "Please add your VAT number")).expect(200);
        JsonNode rejected = status(code, randomIp());
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("rejectionReason").asText()).isEqualTo("Please add your VAT number");
        assertThat(ids(get("/platform/restaurant-applications").as(admin).param("status", "REJECTED").param("search", "Applied Bistro " + n)
                .expect(200).get("items"))).containsExactly(applicationId.toString());
        patch("/restaurants/registrations/{id}/review", applicationId).as(admin).body(Map.of("decision", "APPROVE")).expect(409);

        // Rejected, the same person may apply again.
        post("/restaurants/registrations").header("X-Forwarded-For", randomIp())
                .body(application("Applied Bistro Second " + n, "applied" + n)).expect(201);
    }

    @Test
    @DisplayName("approving an application opens the restaurant last on the list with a first branch and an invited owner")
    void approvedApplicationBecomesARestaurant() throws Exception {
        String admin = superAdminToken();
        int n = next();
        JsonNode receipt = post("/restaurants/registrations").header("X-Forwarded-For", randomIp())
                .body(application("Approved Grill " + n, "approved" + n)).expect(201);
        UUID id = UUID.fromString(receipt.get("id").asText());
        assertThat(get("/restaurants/cards").as(admin).param("search", "Approved Grill " + n).expect(200).get("items")).isEmpty();

        JsonNode approved = patch("/restaurants/registrations/{id}/review", id).as(admin).body(Map.of("decision", "APPROVE")).expect(200);

        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("owner").get("username").asText()).isEqualTo("approved" + n);
        JsonNode cards = get("/restaurants/cards").as(admin).param("search", "Approved Grill " + n).expect(200).get("items");
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).get("branches").asLong()).isEqualTo(1);
        assertThat(cards.get(0).get("ownerInvitePending").asBoolean()).isTrue();
        Integer position = jdbcTemplate.queryForObject("select display_order from " + S + "restaurants where id = ?", Integer.class, id);
        Integer last = jdbcTemplate.queryForObject("select max(display_order) from " + S + "restaurants where deleted_at is null", Integer.class);
        assertThat(position).isEqualTo(last);
        assertThat(status(receipt.get("statusCode").asText(), randomIp()).get("status").asText()).isEqualTo("APPROVED");
        // Gone from the queue.
        assertThat(get("/platform/restaurant-applications").as(admin).param("search", "Approved Grill " + n).expect(200).get("items")).isEmpty();
        get("/platform/restaurant-applications/{id}", id).as(admin).expect(404);
    }

    @Test
    @DisplayName("status checks are limited per address")
    void statusChecksAreRateLimited() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 30; i++) {
            post("/restaurants/registrations/status").header("X-Forwarded-For", ip).body(Map.of("code", "ZZZZZ-ZZZZZ-ZZZZZ-ZZZZ" + (i % 10))).expect(404);
        }
        post("/restaurants/registrations/status").header("X-Forwarded-For", ip).body(Map.of("code", "ZZZZZ-ZZZZZ-ZZZZZ-ZZZZZ")).expect(429);
        post("/restaurants/registrations/status").header("X-Forwarded-For", randomIp()).body(Map.of("code", "ZZZZZ-ZZZZZ-ZZZZZ-ZZZZZ")).expect(404);
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + S + "public_rate_limits where key_hash like ?", Long.class, "%" + ip + "%"))
                .as("addresses are stored only as hashes").isZero();
    }

    // ---- Reserved codes ----

    @Test
    @DisplayName("an archived restaurant's code and web address and a removed branch's code stay reserved with a clear 409")
    void archivedIdentifiersStayReserved() throws Exception {
        String admin = superAdminToken();
        int n = next();
        Map<String, Object> first = new HashMap<>(newRestaurant("Reserved " + n));
        first.put("code", "RESERVED_" + n);
        first.put("slug", "reserved-" + n);
        UUID archived = UUID.fromString(post("/restaurants").as(admin).body(first).expect(201).get("id").asText());
        delete("/restaurants/{r}", archived).as(admin).expect(204);

        Map<String, Object> sameCode = new HashMap<>(newRestaurant("Reserved again " + n));
        sameCode.put("code", "reserved " + n);
        assertThat(post("/restaurants").as(admin).body(sameCode).expect(409).get("message").asText()).contains("stays reserved");
        Map<String, Object> sameSlug = new HashMap<>(newRestaurant("Reserved slug " + n));
        sameSlug.put("code", "FRESH_" + n);
        sameSlug.put("slug", "reserved-" + n);
        assertThat(post("/restaurants").as(admin).body(sameSlug).expect(409).get("message").asText()).contains("stays reserved");

        World w = newWorld("reserved-update");
        Map<String, Object> edit = editable(get("/restaurants/{r}", w.restaurantId()).as(admin).expect(200));
        edit.put("code", "RESERVED_" + n);
        assertThat(put("/restaurants/{r}", w.restaurantId()).as(admin).body(edit).expect(409).get("message").asText()).contains("stays reserved");

        // An application whose name matches the archived restaurant gets the next free code instead of failing.
        JsonNode receipt = post("/restaurants/registrations").header("X-Forwarded-For", randomIp())
                .body(application("Reserved " + n, "reservedapp" + n)).expect(201);
        assertThat(jdbcTemplate.queryForObject("select code from " + S + "restaurants where id = ?", String.class,
                UUID.fromString(receipt.get("id").asText()))).isEqualTo("RESERVED_" + n + "_2");

        String branchCode = "OLD_" + n;
        UUID removed = UUID.fromString(post("/restaurants/{r}/branches", w.restaurantId()).as(admin)
                .body(Map.of("name", "Old branch", "code", branchCode)).expect(201).get("id").asText());
        jdbcTemplate.update("update " + S + "branches set deleted_at = now(), status = 'ARCHIVED', is_active = false where id = ?", removed);
        assertThat(post("/restaurants/{r}/branches", w.restaurantId()).as(admin).body(Map.of("name", "New branch", "code", branchCode))
                .expect(409).get("message").asText()).contains("stays reserved");
        Map<String, Object> branchEdit = editable(get("/restaurants/{r}/branches/{b}", w.restaurantId(), w.branchId()).as(admin).expect(200));
        branchEdit.put("code", branchCode);
        assertThat(put("/restaurants/{r}/branches/{b}", w.restaurantId(), w.branchId()).as(admin).body(branchEdit)
                .expect(409).get("message").asText()).contains("stays reserved");
        // A live branch's code is a plain conflict.
        post("/restaurants/{r}/branches", w.restaurantId()).as(admin).body(Map.of("name", "Twin", "code", w.branch().getCode())).expect(409);
    }

    // ---- Branch chooser ----

    @Test
    @DisplayName("the branch chooser pages and searches on the server, open branches first, with totals for the whole restaurant")
    void branchChooserPagesAndSearches() throws Exception {
        World w = newWorld("chooser");
        String admin = superAdminToken();
        for (int i = 0; i < 30; i++) {
            Branch branch = new Branch();
            branch.setRestaurant(w.restaurant());
            branch.setName((i % 3 == 0 ? "North " : "South ") + String.format("%02d", i));
            branch.setCode("CH" + i);
            if (i % 5 == 0) {
                branch.setActive(false);
                branch.setStatus(BranchStatus.INACTIVE);
            }
            branchRepository.saveAndFlush(branch);
        }
        UUID durres = branchRepository.findAllByRestaurantIdAndDeletedAtIsNull(w.restaurantId()).stream()
                .filter(b -> b.getCode().equals("CH7")).findFirst().orElseThrow().getId();
        post("/restaurants/{r}/branches/{b}/addresses", w.restaurantId(), durres).as(admin).body(Map.of("addressType", "PHYSICAL",
                "country", "Albania", "city", "Durres", "streetLine1", "Rruga Taulantia 5", "isPrimary", true)).expect(201);
        String route = "/restaurants/" + w.restaurantId() + "/branches/cards";

        JsonNode first = get(route).as(admin).param("size", 10).expect(200);
        assertThat(first.get("items")).hasSize(10);
        assertThat(first.get("totalElements").asLong()).isEqualTo(31);
        assertThat(first.get("totalPages").asInt()).isEqualTo(4);
        assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(first.get("allBranches").asLong()).isEqualTo(31);
        assertThat(first.get("openBranches").asLong()).isEqualTo(25);

        Set<String> seen = new HashSet<>();
        List<Boolean> openInOrder = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            JsonNode slice = get(route).as(admin).param("page", page).param("size", 10).expect(200);
            for (JsonNode card : slice.get("items")) {
                assertThat(seen.add(card.get("id").asText())).as("no branch twice").isTrue();
                openInOrder.add(card.get("active").asBoolean());
            }
            assertThat(slice.get("hasNext").asBoolean()).isEqualTo(page < 3);
        }
        assertThat(seen).hasSize(31);
        assertThat(openInOrder.indexOf(false)).as("open branches come first").isEqualTo(25);
        assertThat(get(route).as(admin).param("page", 9).param("size", 10).expect(200).get("items")).isEmpty();

        JsonNode north = get(route).as(admin).param("search", "north").param("size", 100).expect(200);
        assertThat(north.get("totalElements").asLong()).isEqualTo(10);
        assertThat(north.get("allBranches").asLong()).isEqualTo(31);
        north.get("items").forEach(card -> assertThat(card.get("name").asText()).startsWith("North"));
        JsonNode byCity = get(route).as(admin).param("search", "durr").expect(200);
        assertThat(ids(byCity.get("items"))).containsExactly(durres.toString());
        assertThat(byCity.get("items").get(0).get("city").asText()).isEqualTo("Durres");
        assertThat(ids(get(route).as(admin).param("search", "ch12").expect(200).get("items"))).hasSize(1);
        assertThat(get(route).as(admin).param("search", "%").expect(200).get("totalElements").asLong()).isZero();

        get(route).as(admin).param("size", 101).expect(400);
        get(route).as(admin).param("size", 0).expect(400);
        get(route).as(admin).param("page", -1).expect(400);
        get(route).as(admin).param("search", "x".repeat(101)).expect(400);
        get(route).as(admin).param("page", Integer.MAX_VALUE).param("size", 100).expect(400);
    }

    // ---- List positions ----

    @Test
    @DisplayName("restaurants created at the same moment each get their own place at the end of the list")
    void simultaneousCreationsGetDistinctPositions() throws Exception {
        String admin = superAdminToken();
        Integer before = jdbcTemplate.queryForObject("select coalesce(max(display_order), -1) from " + S + "restaurants where deleted_at is null",
                Integer.class);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> created = new ArrayList<>();
        int n = next();
        try {
            for (int i = 0; i < threads; i++) {
                Map<String, Object> body = newRestaurant("Rush " + n + " " + i);
                Callable<String> create = () -> {
                    start.await();
                    return post("/restaurants").as(admin).body(body).expect(201).get("id").asText();
                };
                created.add(pool.submit(create));
            }
            start.countDown();
            List<UUID> ids = new ArrayList<>();
            for (Future<String> future : created) {
                ids.add(UUID.fromString(future.get(60, TimeUnit.SECONDS)));
            }
            List<Integer> positions = new ArrayList<>();
            for (UUID id : ids) {
                positions.add(jdbcTemplate.queryForObject("select display_order from " + S + "restaurants where id = ?", Integer.class, id));
            }
            assertThat(new HashSet<>(positions)).as("distinct positions").hasSize(threads);
            assertThat(positions).allSatisfy(position -> assertThat(position).isGreaterThan(before));
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- Helpers ----

    private UUID paidOrder(World w) throws Exception {
        Map<String, UUID> menu = menu(w, "Readiness menu " + next());
        UUID dish = item(w, menu, "Soup", "10");
        UUID order = id(order(w, w.ownerToken(), Map.of(dish, 1)));
        post("/restaurants/{r}/orders/{o}/payments", w.restaurantId(), order).as(w.ownerToken())
                .body(Map.of("method", "CASH", "amount", "10", "tenderedAmount", "10")).expect(201);
        return order;
    }

    private Map<String, Object> editable(JsonNode current) {
        Map<String, Object> edit = objectMapper.convertValue(current, new TypeReference<Map<String, Object>>() { });
        edit.put("expectedVersion", current.get("version").asLong());
        return edit;
    }

    private Map<String, Object> newRestaurant(String name) {
        return Map.of("name", name, "legalName", name + " LLC", "currency", "EUR", "timezone", "Europe/Tirane",
                "email", "hello" + next() + "@restaurant.example", "phone", "+35569" + (1_000_000 + next() % 8_000_000));
    }

    private Map<String, Object> application(String name, String ownerHandle) {
        return Map.of(
                "name", name,
                "legalName", name + " LLC",
                "email", "contact." + ownerHandle + "@restaurant.example",
                "phone", "+35568" + (1_000_000 + next() % 8_000_000),
                "currency", "EUR",
                "timezone", "Europe/Tirane",
                "owner", Map.of("email", ownerHandle + "@owner.example", "username", ownerHandle, "firstName", "Applicant", "lastName", "Person")
        );
    }

    private JsonNode status(String code, String ip) throws Exception {
        return post("/restaurants/registrations/status").header("X-Forwarded-For", ip).body(Map.of("code", code)).expect(200);
    }

    private int loginStatus(String identifier) throws Exception {
        return post("/auth/web/login").header("X-Forwarded-For", randomIp())
                .body(Map.of("identifier", identifier, "password", PASSWORD)).send().status();
    }

    private List<String> roles(UUID userId) {
        return jdbcTemplate.queryForList("select r.code from " + S + "user_roles ur join " + S + "roles r on r.id = ur.role_id where ur.user_id = ?",
                String.class, userId);
    }

    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    private static String randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "172." + random.nextInt(16, 32) + "." + random.nextInt(0, 255) + "." + random.nextInt(1, 254);
    }
}
