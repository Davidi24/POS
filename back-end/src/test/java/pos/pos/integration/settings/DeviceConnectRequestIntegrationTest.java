package pos.pos.integration.settings;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.user.entity.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A till's code typed on another device waits for the person who made it to agree (user, 2026-11-25): asking, agreeing,
 * claiming the connection, refusing (ends the code), time running out, one asker per code, only the maker answers, only the
 * asking device claims, and "Use this device" taking only your own code.
 */
class DeviceConnectRequestIntegrationTest extends AbstractPosApiIntegrationTest {

    /**
     * Every test request comes from this computer, and connecting a device allows 30 tries per address in 10 minutes: the
     * suite's tries must not add up across tests (they did on CI, in another order), so each test starts and ends at zero.
     */
    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void freshPairingTries() {
        jdbcTemplate.update("delete from " + SCHEMA + ".public_rate_limits where scope = 'DEVICE_PAIRING_IP'");
    }

    @Test
    void theMakerAgreesThenTheAskingDeviceTakesTheConnection() throws Exception {
        World world = newWorld("connect agree");
        UUID r = world.restaurantId();
        User admin = staff(world, "admin", "ADMIN");
        String asker = token(admin);
        UUID device = till(world, "Bar till");
        String code = code(world, device);

        // Someone else's code can't be used at once: it waits for its maker.
        assertThat(post("/restaurants/{r}/device-pairing/redeem", r).as(asker).body(Map.of("pairingToken", code)).expect(400)
                .get("message").asText()).contains("waits for the person who made it");

        JsonNode asked = post("/restaurants/{r}/device-connect-requests", r).as(asker)
                .header("X-Device-Name", "Linux computer (bar)").header("X-Device-System", "Linux 6.17")
                .body(Map.of("pairingToken", code.toLowerCase().replace("-", ""))).expect(200);
        JsonNode request = asked.get("request");
        UUID requestId = id(request);
        String claim = asked.get("claimToken").asText();
        assertThat(request.get("status").asText()).isEqualTo("WAITING");
        assertThat(request.get("deviceName").asText()).isEqualTo("Bar till");
        assertThat(request.get("askingDevice").asText()).isEqualTo("Linux computer (bar)");
        assertThat(request.get("madeById").asText()).isEqualTo(world.owner().getId().toString());
        assertThat(request.get("deviceInUse").asBoolean()).isFalse();
        assertThat(request.get("secondsLeft").asLong()).isBetween(170L, 180L);

        // Not agreed yet; only the maker answers; the maker sees it waiting.
        post("/restaurants/{r}/device-connect-requests/{id}/claim", r, requestId).as(asker).body(Map.of("claimToken", claim)).expect(409);
        post("/restaurants/{r}/device-connect-requests/{id}/agree", r, requestId).as(asker).expect(403);
        JsonNode waiting = get("/restaurants/{r}/device-connect-requests/waiting", r).as(world.ownerToken()).expect(200);
        assertThat(waiting).hasSize(1);
        assertThat(waiting.get(0).get("id").asText()).isEqualTo(requestId.toString());
        assertThat(get("/restaurants/{r}/device-connect-requests/waiting", r).as(asker).expect(200)).isEmpty();
        // The maker got a notification.
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + SCHEMA + ".notifications where event_code = 'DEVICE_CONNECT_REQUEST'"
                + " and recipient_user_id = ? and reference_id = ?", Integer.class, world.owner().getId(), requestId)).isEqualTo(1);

        JsonNode agreed = post("/restaurants/{r}/device-connect-requests/{id}/agree", r, requestId).as(world.ownerToken()).expect(200);
        assertThat(agreed.get("status").asText()).isEqualTo("AGREED");
        post("/restaurants/{r}/device-connect-requests/{id}/agree", r, requestId).as(world.ownerToken()).expect(409);

        // Only the asking device (its claim), not just the same person.
        post("/restaurants/{r}/device-connect-requests/{id}/claim", r, requestId).as(asker).body(Map.of("claimToken", "not-the-claim")).expect(404);
        post("/restaurants/{r}/device-connect-requests/{id}/claim", r, requestId).as(world.ownerToken()).body(Map.of("claimToken", claim)).expect(404);
        JsonNode connected = post("/restaurants/{r}/device-connect-requests/{id}/claim", r, requestId).as(asker)
                .body(Map.of("claimToken", claim)).expect(200);
        assertThat(connected.get("deviceId").asText()).isEqualTo(device.toString());
        String secret = connected.get("deviceSecret").asText();
        assertThat(get("/restaurants/{r}/device-connect-requests/{id}", r, requestId).as(asker).expect(200).get("status").asText()).isEqualTo("DONE");
        post("/restaurants/{r}/device-connect-requests/{id}/claim", r, requestId).as(asker).body(Map.of("claimToken", claim)).expect(409);

        // The new device works; the code is used up; Devices says which device has it (user, 2026-11-27).
        post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", secret).body(Map.of("platform", "Linux")).expect(200);
        JsonNode listed = null;
        for (JsonNode one : get("/restaurants/{r}/branches/{b}/devices", r, world.branchId()).as(world.ownerToken()).expect(200)) {
            if (one.get("id").asText().equals(device.toString())) listed = one;
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("connectedDeviceName").asText()).isEqualTo("Linux computer (bar)");
        assertThat(listed.get("connectedDeviceSystem").asText()).isEqualTo("Linux 6.17");
        post("/restaurants/{r}/devices/{d}/disconnect", r, device).as(world.ownerToken()).expect(204);
        for (JsonNode one : get("/restaurants/{r}/branches/{b}/devices", r, world.branchId()).as(world.ownerToken()).expect(200)) {
            if (one.get("id").asText().equals(device.toString())) assertThat(one.get("connectedDeviceName").isNull()).isTrue();
        }
        post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code)).expect(400);
    }

    @Test
    void aTillInUseIsDisconnectedFirstThenItsDeviceStopsWorking() throws Exception {
        World world = newWorld("connect move");
        UUID r = world.restaurantId();
        String asker = token(staff(world, "admin", "ADMIN"));
        UUID device = till(world, "Front till");
        String old = code(world, device);
        String first = post("/restaurants/{r}/device-pairing/redeem", r).as(world.ownerToken())
                .body(Map.of("pairingToken", old)).expect(200).get("deviceSecret").asText();
        post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", first).body(Map.of("platform", "Linux")).expect(200);

        // In use: no new code until it's disconnected (user, 2026-11-25).
        assertThat(post("/restaurants/{r}/devices/{d}/pairing-tokens", r, device).as(world.ownerToken()).body(Map.of("ttlMinutes", 15))
                .expect(409).get("message").asText()).isEqualTo("Front till is in use. Disconnect it first to make a code.");
        // A waiter can't disconnect it.
        post("/restaurants/{r}/devices/{d}/disconnect", r, device).as(token(staff(world, "waiter", "WAITER"))).expect(403);

        post("/restaurants/{r}/devices/{d}/disconnect", r, device).as(asker).expect(204);
        assertThat(post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", first).body(Map.of("platform", "Linux"))
                .send().status()).isEqualTo(401);
        JsonNode listed = get("/restaurants/{r}/branches/{b}/devices", r, world.branchId()).as(world.ownerToken()).expect(200);
        for (JsonNode one : listed) {
            if (one.get("id").asText().equals(device.toString())) assertThat(one.get("connected").asBoolean()).isFalse();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + SCHEMA + ".\"settings-audit-logs\" where action = 'DEVICE_DISCONNECTED'"
                + " and entity_id = ?", Integer.class, device)).isEqualTo(1);

        // Then it's connected again on the other device, through the agreement.
        JsonNode asked = post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code(world, device))).expect(200);
        assertThat(asked.get("request").get("deviceInUse").asBoolean()).isFalse();
        UUID requestId = id(asked.get("request"));
        post("/restaurants/{r}/device-connect-requests/{id}/agree", r, requestId).as(world.ownerToken()).expect(200);
        String second = post("/restaurants/{r}/device-connect-requests/{id}/claim", r, requestId).as(asker)
                .body(Map.of("claimToken", asked.get("claimToken").asText())).expect(200).get("deviceSecret").asText();
        post("/public/devices/{d}/heartbeat", device).header("X-Device-Secret", second).body(Map.of("platform", "Linux")).expect(200);
    }

    @Test
    void refusingEndsTheCodeAndOnlyOnePersonWaitsWithACode() throws Exception {
        World world = newWorld("connect refuse");
        UUID r = world.restaurantId();
        String asker = token(staff(world, "admin", "ADMIN"));
        String other = token(staff(world, "coowner", "CO_OWNER"));
        UUID device = till(world, "Terrace tablet");
        String code = code(world, device);

        JsonNode asked = post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code)).expect(200);
        // Someone else with the same code waits for the first one.
        assertThat(post("/restaurants/{r}/device-connect-requests", r).as(other).body(Map.of("pairingToken", code)).expect(409)
                .get("message").asText()).contains("Someone else is waiting");
        // The same person asking again (the app was closed) replaces their own request.
        JsonNode again = post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code)).expect(200);
        assertThat(get("/restaurants/{r}/device-connect-requests/{id}", r, id(asked.get("request"))).as(asker).expect(200)
                .get("status").asText()).isEqualTo("CANCELLED");

        UUID requestId = id(again.get("request"));
        assertThat(post("/restaurants/{r}/device-connect-requests/{id}/refuse", r, requestId).as(world.ownerToken()).expect(200)
                .get("status").asText()).isEqualTo("REFUSED");
        post("/restaurants/{r}/device-connect-requests/{id}/claim", r, requestId).as(asker)
                .body(Map.of("claimToken", again.get("claimToken").asText())).expect(409);
        post("/restaurants/{r}/device-connect-requests", r).as(other).body(Map.of("pairingToken", code)).expect(400);
    }

    @Test
    void noAnswerInTimeChangesNothingAndCancelStopsIt() throws Exception {
        World world = newWorld("connect late");
        UUID r = world.restaurantId();
        String asker = token(staff(world, "admin", "ADMIN"));
        UUID device = till(world, "Kitchen tablet");
        String code = code(world, device);

        JsonNode asked = post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code)).expect(200);
        UUID requestId = id(asked.get("request"));
        jdbcTemplate.update("update " + SCHEMA + ".device_connect_requests set expires_at = now() - interval '1 second' where id = ?", requestId);
        JsonNode late = get("/restaurants/{r}/device-connect-requests/{id}", r, requestId).as(asker).expect(200);
        assertThat(late.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(late.get("secondsLeft").asLong()).isZero();
        post("/restaurants/{r}/device-connect-requests/{id}/agree", r, requestId).as(world.ownerToken()).expect(409);
        assertThat(get("/restaurants/{r}/device-connect-requests/waiting", r).as(world.ownerToken()).expect(200)).isEmpty();

        // The code still works for a new try, which the asking device can cancel.
        JsonNode retry = post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code)).expect(200);
        assertThat(delete("/restaurants/{r}/device-connect-requests/{id}", r, id(retry.get("request"))).as(asker).expect(200)
                .get("status").asText()).isEqualTo("CANCELLED");
        post("/restaurants/{r}/device-connect-requests/{id}/agree", r, id(retry.get("request"))).as(world.ownerToken()).expect(409);
    }

    @Test
    void wrongCodesCardTerminalsAndOtherRestaurants() throws Exception {
        World world = newWorld("connect wrong");
        World elsewhere = newWorld("connect elsewhere");
        UUID r = world.restaurantId();
        String asker = token(staff(world, "admin", "ADMIN"));

        post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", "ZZZZZZZZ")).expect(400);
        UUID card = device(world, "Card reader", "PAYMENT_TERMINAL");
        assertThat(post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code(world, card))).expect(400)
                .get("message").asText()).contains("doesn't work here");
        UUID theirs = till(elsewhere, "Their till");
        post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code(elsewhere, theirs))).expect(400);
        // A request of another restaurant isn't there for this one.
        JsonNode asked = post("/restaurants/{r}/device-connect-requests", elsewhere.restaurantId()).as(elsewhere.ownerToken())
                .body(Map.of("pairingToken", code(elsewhere, theirs))).expect(200);
        get("/restaurants/{r}/device-connect-requests/{id}", r, id(asked.get("request"))).as(world.ownerToken()).expect(404);
        // A waiter can't ask at all.
        post("/restaurants/{r}/device-connect-requests", r).as(token(staff(world, "waiter", "WAITER")))
                .body(Map.of("pairingToken", "ZZZZZZZZ")).expect(403);
    }

    @Test
    void removingADeviceTakesItsRequestsAlong() throws Exception {
        World world = newWorld("connect remove");
        UUID r = world.restaurantId();
        String asker = token(staff(world, "admin", "ADMIN"));
        UUID device = till(world, "Old till");
        post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code(world, device))).expect(200);
        delete("/restaurants/{r}/branches/{b}/devices/{d}", r, world.branchId(), device).as(world.ownerToken()).expect(204);
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + SCHEMA + ".device_connect_requests where device_id = ?",
                Integer.class, device)).isZero();
    }

    // ---- helpers ----

    private UUID till(World world, String name) throws Exception {
        return device(world, name, "POS_TERMINAL");
    }

    private UUID device(World world, String name, String type) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", world.branchId());
        body.put("code", "DEV" + next());
        body.put("name", name);
        body.put("deviceType", type);
        body.put("active", true);
        body.put("online", false);
        body.put("status", "PROVISIONING");
        return id(post("/restaurants/{r}/branches/{b}/devices", world.restaurantId(), world.branchId())
                .as(world.ownerToken()).body(body).expect(201));
    }

    /** A code the owner makes on the device's page. */
    private String code(World world, UUID device) throws Exception {
        return post("/restaurants/{r}/devices/{d}/pairing-tokens", world.restaurantId(), device)
                .as(world.ownerToken()).body(Map.of("ttlMinutes", 15)).expect(201).get("pairingToken").asText();
    }
}
