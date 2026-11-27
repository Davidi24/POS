package pos.pos.integration.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.security.rbac.PinSignIn;
import pos.pos.user.entity.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A PIN does the job, a password changes the system (user, 2026-11-25). A real PIN sign-in on a connected till: its
 * permissions leave out `PinSignIn.PASSWORD_ONLY` (sign-in answer, /auth/me, refresh), the server refuses what changes the
 * system or the account, and service work (orders, voids) still works. A password sign-in of the same person is untouched.
 */
class PinSignInLimitsIntegrationTest extends AbstractPosApiIntegrationTest {

    /**
     * Every test request comes from this computer, and connecting a device allows 30 tries per address in 10 minutes: the
     * suite's tries must not add up across tests (they did on CI, in another order), so each test starts and ends at zero.
     */
    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void freshPairingTries() {
        jdbcTemplate.update("delete from " + SCHEMA + ".public_rate_limits where scope = 'DEVICE_PAIRING_IP'");
    }

    /** Service work a PIN sign-in keeps (an owner has all of these). */
    private static final List<String> KEPT = List.of("ORDER_READ", "ORDER_UPDATE", "ORDER_VOID", "ORDER_CLOSE", "ORDER_CLOSE_UNPAID",
            "ORDER_DISCOUNT_APPLY", "PAYMENT_REFUND", "TABLES_UPDATE", "RESERVATION_MANAGE", "MENUS_SOLD_OUT", "KDS_UPDATE",
            "INVENTORY_RECEIVE", "INVENTORY_WASTE", "INVENTORY_COUNT", "SHIFT_SELF", "REPORTS_READ", "POS_ACCESS", "KDS_ACCESS",
            "USERS_READ", "SETTINGS_READ");

    private record PinSession(String access, String refresh, JsonNode user) {
    }

    @Test
    void aPinSignInLeavesOutWhatChangesTheSystem() throws Exception {
        World world = newWorld("pin limits");
        PinSession pin = pinSignIn(world, world.owner(), world.ownerToken());

        assertPinPermissions(pin.user().get("permissions"));
        assertPinPermissions(get("/auth/me").as(pin.access()).expect(200).get("permissions"));

        JsonNode refreshed = post("/auth/device/refresh").body(Map.of("refreshToken", pin.refresh())).expect(200);
        assertPinPermissions(refreshed.get("user").get("permissions"));
        String access = refreshed.get("accessToken").asText();
        assertPinPermissions(get("/auth/me").as(access).expect(200).get("permissions"));

        // The same person signed in with the password keeps everything and has no mark.
        List<String> password = codes(get("/auth/me").as(world.ownerToken()).expect(200).get("permissions"));
        assertThat(password).doesNotContain(PinSignIn.MARK);
        assertThat(password).contains("DEVICES_MANAGE", "ADMIN_ACCESS", "USERS_CREATE", "SETTINGS_UPDATE", "MENUS_PRICES");
    }

    @Test
    void aPinSignInIsRefusedWhatChangesTheSystemOrTheAccount() throws Exception {
        World world = newWorld("pin refused");
        PinSession pin = pinSignIn(world, world.owner(), world.ownerToken());
        String t = pin.access();
        UUID r = world.restaurantId();
        UUID b = world.branchId();
        UUID device = till(world, "Second till");

        // Devices.
        post("/restaurants/{r}/devices/{d}/pairing-tokens", r, device).as(t).body(Map.of("ttlMinutes", 10)).expect(403);
        post("/restaurants/{r}/device-pairing/redeem", r).as(t).body(Map.of("pairingToken", "ABCDEFGH")).expect(403);
        post("/restaurants/{r}/device-connect-requests", r).as(t).body(Map.of("pairingToken", "ABCDEFGH")).expect(403);
        get("/restaurants/{r}/device-connect-requests/waiting", r).as(t).expect(403);
        delete("/restaurants/{r}/branches/{b}/devices/{d}", r, b, device).as(t).expect(403);

        // Settings, menu, staff.
        patch("/restaurants/{r}/settings/inventory", r).as(t).body(Map.of("valuableStockFrom", 150)).expect(403);
        patch("/restaurants/{r}/settings/inventory", r).as(world.ownerToken()).body(Map.of("valuableStockFrom", 150)).expect(200);
        Map<String, UUID> menu = menu(world, "PIN menu");
        UUID dish = item(world, menu, "Soup", "6.00");
        put("/menus/{m}/sections/{s}/items/{i}", menu.get("menuId"), menu.get("sectionId"), dish).as(t)
                .body(Map.of("name", "Soup", "basePrice", "7.00", "available", true, "sendToKitchen", true, "displayOrder", 0)).expect(403);

        // Your own account.
        put("/auth/change-password").as(t).body(Map.of("currentPassword", PASSWORD, "newPassword", "Another1!Pass")).expect(403);
        put("/auth/me/details").as(t).body(Map.of("firstName", "Pin", "lastName", "Person", "phone", "+355691234567")).expect(403);
        post("/auth/me/email").as(t).body(Map.of("newEmail", "pin.person@pos.example", "currentPassword", PASSWORD)).expect(403);
        put("/auth/me/username").as(t).body(Map.of("username", "pin.person." + next(), "currentPassword", PASSWORD)).expect(403);
        post("/auth/me/pin/suggestion").as(t).expect(403);
        put("/auth/me/pin").as(t).body(Map.of("pin", "12345")).expect(403);
        delete("/auth/me/email").as(t).expect(403);
        get("/auth/sessions").as(t).expect(403);
        delete("/auth/sessions/others").as(t).expect(403);
        get("/auth/sign-ins").as(t).expect(403);
        post("/auth/device/logout-all").body(Map.of("refreshToken", pin.refresh())).expect(403);
        get("/auth/me").as(world.ownerToken()).expect(200); // nothing was signed out

        // Asking the Super Admin to remove an owner or for a support admin needs the password; reporting a problem doesn't.
        post("/restaurants/{r}/requests", r).as(t).body(Map.of("kind", "NEED_ADMIN", "note", "Please")).expect(403);
        post("/restaurants/{r}/requests", r).as(t).body(Map.of("kind", "PROBLEM", "note", "The printer jams")).expect(200);

        // Downloads.
        get("/restaurants/{r}/statistics/reports/daily-sales.csv", r).as(t)
                .param("from", LocalDate.now().minusDays(7)).param("to", LocalDate.now()).expect(403);

        // Reading stays.
        get("/restaurants/{r}/statistics/reports", r).as(t).expect(200);
        get("/auth/me/profile").as(t).expect(200);
        get("/auth/me/pin").as(t).expect(200);

        // The password sign-in may do the same things (refused for other reasons, never for being a PIN).
        assertThat(put("/auth/change-password").as(world.ownerToken())
                .body(Map.of("currentPassword", "WrongPass1!", "newPassword", "Another1!Pass")).send().status()).isNotEqualTo(403);
        assertThat(get("/restaurants/{r}/statistics/reports/daily-sales.csv", r).as(world.ownerToken())
                .param("from", LocalDate.now().minusDays(7)).param("to", LocalDate.now()).send().status()).isEqualTo(200);
        post("/restaurants/{r}/devices/{d}/pairing-tokens", r, device).as(world.ownerToken()).body(Map.of("ttlMinutes", 10)).expect(201);
    }

    @Test
    void serviceWorkStillWorksWithAPin() throws Exception {
        World world = newWorld("pin service");
        Map<String, UUID> menu = menu(world, "Service menu");
        UUID dish = item(world, menu, "Pasta", "9.00");
        PinSession pin = pinSignIn(world, world.owner(), world.ownerToken());

        JsonNode order = order(world, pin.access(), Map.of(dish, 2));
        post("/restaurants/{r}/orders/{o}/void", world.restaurantId(), id(order)).as(pin.access())
                .body(Map.of("reason", "Typed on the wrong table")).expect(200);

        // A waiter's PIN sign-in keeps the waiter's work too.
        User waiter = staff(world, "waiter", "WAITER");
        PinSession waiterPin = pinSignIn(world, waiter, token(waiter));
        assertThat(codes(waiterPin.user().get("permissions"))).contains(PinSignIn.MARK, "ORDER_READ", "POS_ACCESS");
        order(world, waiterPin.access(), Map.of(dish, 1));
    }

    @Test
    void withChosenPeopleOnlyOwnersAlwaysSignInOnATill() throws Exception {
        World world = newWorld("pin chosen");
        UUID r = world.restaurantId();
        UUID device = till(world, "Bar till");
        String code = post("/restaurants/{r}/devices/{d}/pairing-tokens", r, device).as(world.ownerToken())
                .body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();
        String secret = post("/restaurants/{r}/device-pairing/redeem", r).as(world.ownerToken())
                .body(Map.of("pairingToken", code)).expect(200).get("deviceSecret").asText();
        UUID waiterRole = roleRepository.findByCode("WAITER").orElseThrow().getId();
        put("/restaurants/{r}/devices/{d}/sign-in", r, device).as(world.ownerToken())
                .body(Map.of("roleIds", List.of(waiterRole), "userIds", List.of())).expect(200);

        // Admins and managers are chosen like anyone else now (user, 2026-11-27); owners still always sign in.
        for (String role : List.of("ADMIN", "MANAGER")) {
            User person = staff(world, role.toLowerCase(), role);
            JsonNode refused = post("/auth/device/pin-login").header("X-Device-Id", "pin-it-" + person.getId())
                    .body(Map.of("deviceId", device, "deviceSecret", secret, "pin", givePin(token(person)))).expect(403);
            assertThat(refused.get("code").asText()).as(role).isEqualTo("NOT_ON_THIS_DEVICE");
        }
        User waiter = staff(world, "waiter", "WAITER");
        post("/auth/device/pin-login").header("X-Device-Id", "pin-it-" + waiter.getId())
                .body(Map.of("deviceId", device, "deviceSecret", secret, "pin", givePin(token(waiter)))).expect(200);
        post("/auth/device/pin-login").header("X-Device-Id", "pin-it-" + world.owner().getId())
                .body(Map.of("deviceId", device, "deviceSecret", secret, "pin", givePin(world.ownerToken()))).expect(200);
        JsonNode view = get("/restaurants/{r}/devices/{d}/sign-in", r, device).as(world.ownerToken()).expect(200);
        assertThat(codes(view.get("alwaysAllowed"))).containsExactly("Owner", "Co-Owner");
    }

    @Test
    void aTillPausesPinSignInAfterTenWrongPins() throws Exception {
        World world = newWorld("pin pause");
        UUID r = world.restaurantId();
        UUID device = till(world, "Pause till");
        String code = post("/restaurants/{r}/devices/{d}/pairing-tokens", r, device).as(world.ownerToken())
                .body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();
        String secret = post("/restaurants/{r}/device-pairing/redeem", r).as(world.ownerToken())
                .body(Map.of("pairingToken", code)).expect(200).get("deviceSecret").asText();
        String pin = givePin(world.ownerToken());
        String wrong = String.format("%05d", (Integer.parseInt(pin) + 1) % 100_000);
        // 10 wrong PINs (user, 2026-11-27: "not 5 times, make it 10"): nine are wrong, the tenth pauses the till.
        for (int i = 1; i <= 9; i++) {
            assertThat(post("/auth/device/pin-login").header("X-Device-Id", "pin-pause-" + i)
                    .body(Map.of("deviceId", device, "deviceSecret", secret, "pin", wrong)).expect(401).get("code").asText()).isEqualTo("WRONG_PIN");
        }
        assertThat(post("/auth/device/pin-login").header("X-Device-Id", "pin-pause-10")
                .body(Map.of("deviceId", device, "deviceSecret", secret, "pin", wrong)).expect(429).get("code").asText()).isEqualTo("PIN_PAUSED");
        // Paused: even the right PIN waits.
        assertThat(post("/auth/device/pin-login").header("X-Device-Id", "pin-pause-right")
                .body(Map.of("deviceId", device, "deviceSecret", secret, "pin", pin)).expect(429).get("code").asText()).isEqualTo("PIN_PAUSED");
    }

    /** A PIN for the person signed in with [passwordToken]: suggested, then kept. */
    private String givePin(String passwordToken) throws Exception {
        String pin = post("/auth/me/pin/suggestion").as(passwordToken).expect(200).get("pin").asText();
        put("/auth/me/pin").as(passwordToken).body(Map.of("pin", pin)).expect(200);
        return pin;
    }

    // ---- helpers ----

    private void assertPinPermissions(JsonNode permissions) {
        List<String> codes = codes(permissions);
        assertThat(codes).contains(PinSignIn.MARK);
        assertThat(codes).doesNotContainAnyElementsOf(PinSignIn.PASSWORD_ONLY);
        assertThat(codes).containsAll(KEPT);
    }

    private static List<String> codes(JsonNode permissions) {
        List<String> out = new ArrayList<>();
        permissions.forEach(node -> out.add(node.asText()));
        return out;
    }

    /** A till at the world's branch, as the owner adds it. */
    private UUID till(World world, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", world.branchId());
        body.put("code", "TILL" + next());
        body.put("name", name);
        body.put("deviceType", "POS_TERMINAL");
        body.put("active", true);
        body.put("online", false);
        body.put("status", "PROVISIONING");
        return id(post("/restaurants/{r}/branches/{b}/devices", world.restaurantId(), world.branchId())
                .as(world.ownerToken()).body(body).expect(201));
    }

    /** Connects a till ("Use this device" by the owner), gives [person] a PIN and signs in with it there. */
    private PinSession pinSignIn(World world, User person, String passwordToken) throws Exception {
        UUID device = till(world, "Till " + next());
        String code = post("/restaurants/{r}/devices/{d}/pairing-tokens", world.restaurantId(), device)
                .as(world.ownerToken()).body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();
        String secret = post("/restaurants/{r}/device-pairing/redeem", world.restaurantId()).as(world.ownerToken())
                .body(Map.of("pairingToken", code)).expect(200).get("deviceSecret").asText();
        String pin = post("/auth/me/pin/suggestion").as(passwordToken).expect(200).get("pin").asText();
        put("/auth/me/pin").as(passwordToken).body(Map.of("pin", pin)).expect(200);
        JsonNode signedIn = post("/auth/device/pin-login")
                .header("X-Device-Id", "pin-it-" + person.getId())
                .body(Map.of("deviceId", device, "deviceSecret", secret, "pin", pin)).expect(200);
        return new PinSession(signedIn.get("accessToken").asText(), signedIn.get("refreshToken").asText(), signedIn.get("user"));
    }
}
