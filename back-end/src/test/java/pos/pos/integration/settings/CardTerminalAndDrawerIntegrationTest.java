package pos.pos.integration.settings;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Card terminals and cash drawers (user, 2026-11-27): a card terminal works only through the till or tablet it's connected to,
 * by Bluetooth or cable; a cash drawer is plugged into a printer, which then opens it; a code is typed inside its own kind.
 */
class CardTerminalAndDrawerIntegrationTest extends AbstractPosApiIntegrationTest {

    @BeforeEach
    @AfterEach
    void freshPairingTries() {
        jdbcTemplate.update("delete from " + SCHEMA + ".public_rate_limits where scope = 'DEVICE_PAIRING_IP'");
    }

    @Test
    void aCardTerminalWorksOnlyThroughItsTillOrTablet() throws Exception {
        World world = newWorld("card terminals");
        UUID r = world.restaurantId();
        String owner = world.ownerToken();
        UUID barTill = device(world, "Bar till", "POS_TERMINAL", null, null, null);
        UUID tablet = device(world, "Terrace tablet", "TABLET", null, null, null);
        UUID printer = printer(world, "Receipt printer");

        UUID bluetooth = device(world, "Card terminal 1", "PAYMENT_TERMINAL", barTill, "BLUETOOTH", "AA:BB:CC:DD:EE:01");
        JsonNode listed = listed(world, bluetooth);
        assertThat(listed.get("linkedDeviceName").asText()).isEqualTo("Bar till");
        assertThat(listed.get("linkType").asText()).isEqualTo("BLUETOOTH");
        assertThat(listed.get("linkAddress").asText()).isEqualTo("AA:BB:CC:DD:EE:01");

        // The bar till sends card payments to it; the tablet has none until one is connected to it by cable.
        String waiter = token(staff(world, "waiter", "WAITER"));
        JsonNode mine = get("/restaurants/{r}/card-terminal", r).as(waiter).param("deviceId", barTill).expect(200);
        assertThat(mine.get("name").asText()).isEqualTo("Card terminal 1");
        assertThat(get("/restaurants/{r}/card-terminal", r).as(waiter).param("deviceId", tablet).send().status()).isEqualTo(204);
        device(world, "Card terminal 2", "PAYMENT_TERMINAL", tablet, "USB", null);
        assertThat(get("/restaurants/{r}/card-terminal", r).as(waiter).param("deviceId", tablet).expect(200).get("linkType").asText()).isEqualTo("USB");

        // Not connected: saved, but no till uses it.
        UUID loose = device(world, "Spare terminal", "PAYMENT_TERMINAL", null, null, null);
        assertThat(listed(world, loose).get("linkedDeviceId").isNull()).isTrue();
        // In use means it's ready: not connected, it waits to be confirmed, and it can't be put in use.
        assertThat(listed(world, loose).get("status").asText()).isEqualTo("PROVISIONING");
        patch("/restaurants/{r}/branches/{b}/devices/{d}/status", r, world.branchId(), loose).as(owner)
                .body(Map.of("status", "ACTIVE", "active", true, "online", false)).expect(400);
        assertThat(listed(world, bluetooth).get("status").asText()).isEqualTo("ACTIVE");

        // Only a till or tablet, by Bluetooth (with the paired terminal) or cable, of this restaurant.
        expectRefused(world, "To a printer", "PAYMENT_TERMINAL", printer, "USB", null);
        expectRefused(world, "No way", "PAYMENT_TERMINAL", barTill, null, null);
        expectRefused(world, "No address", "PAYMENT_TERMINAL", barTill, "BLUETOOTH", null);
        expectRefused(world, "A till on a till", "POS_TERMINAL", barTill, "USB", null);
        World other = newWorld("card terminals other");
        UUID theirTill = device(other, "Their till", "POS_TERMINAL", null, null, null);
        expectRefused(world, "Their till's", "PAYMENT_TERMINAL", theirTill, "USB", null);

        // Removing the till lets go of the terminal.
        delete("/restaurants/{r}/branches/{b}/devices/{d}", r, world.branchId(), barTill).as(owner).expect(204);
        assertThat(listed(world, bluetooth).get("linkedDeviceId").isNull()).isTrue();
        assertThat(listed(world, bluetooth).get("status").asText()).isEqualTo("PROVISIONING");
    }

    @Test
    void aCashDrawerIsPluggedIntoAPrinterThatOpensIt() throws Exception {
        World world = newWorld("cash drawers");
        UUID r = world.restaurantId();
        UUID printer = printer(world, "Receipt printer");
        UUID till = device(world, "Front till", "POS_TERMINAL", null, null, null);
        assertThat(setupPrinter(world, printer).get("cashDrawerKick").asBoolean()).isFalse();

        UUID drawer = device(world, "Front cash drawer", "CASH_DRAWER", printer, "PRINTER", null);
        assertThat(listed(world, drawer).get("linkedDeviceName").asText()).isEqualTo("Receipt printer");
        assertThat(listed(world, drawer).get("deviceType").asText()).isEqualTo("CASH_DRAWER");
        assertThat(setupPrinter(world, printer).get("cashDrawerKick").asBoolean()).isTrue();

        // A drawer goes into a printer, not a till.
        expectRefused(world, "Into a till", "CASH_DRAWER", till, "PRINTER", null);
        delete("/restaurants/{r}/branches/{b}/devices/{d}", r, world.branchId(), drawer).as(world.ownerToken()).expect(204);
        assertThat(setupPrinter(world, printer).get("cashDrawerKick").asBoolean()).isFalse();
        // Removing its printer takes a drawer out of use.
        UUID another = device(world, "Bar cash drawer", "CASH_DRAWER", printer, "PRINTER", null);
        delete("/restaurants/{r}/settings/printers/{p}", r, printer).as(world.ownerToken()).expect(204);
        assertThat(listed(world, another).get("status").asText()).isEqualTo("PROVISIONING");
    }

    @Test
    void aCodeIsTypedInsideItsOwnKind() throws Exception {
        World world = newWorld("codes by kind");
        UUID r = world.restaurantId();
        UUID till = device(world, "Bar till", "POS_TERMINAL", null, null, null);
        String code = post("/restaurants/{r}/devices/{d}/pairing-tokens", r, till).as(world.ownerToken()).body(Map.of("ttlMinutes", 10))
                .expect(201).get("pairingToken").asText();
        String asker = token(staff(world, "admin", "ADMIN"));
        JsonNode wrong = post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code, "kind", "TABLET")).expect(400);
        assertThat(wrong.get("message").asText()).contains("Tills");
        post("/restaurants/{r}/device-connect-requests", r).as(asker).body(Map.of("pairingToken", code, "kind", "TILL")).expect(200);
    }

    // ---- helpers ----

    private Map<String, Object> body(World world, String name, String type, UUID linked, String linkType, String address) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", world.branchId());
        body.put("code", "DEV" + next());
        body.put("name", name);
        body.put("deviceType", type);
        body.put("active", true);
        body.put("online", false);
        body.put("status", "ACTIVE");
        if (linked != null) body.put("linkedDeviceId", linked);
        if (linkType != null) body.put("linkType", linkType);
        if (address != null) body.put("linkAddress", address);
        return body;
    }

    private UUID device(World world, String name, String type, UUID linked, String linkType, String address) throws Exception {
        return id(post("/restaurants/{r}/branches/{b}/devices", world.restaurantId(), world.branchId()).as(world.ownerToken())
                .body(body(world, name, type, linked, linkType, address)).expect(201));
    }

    private void expectRefused(World world, String name, String type, UUID linked, String linkType, String address) throws Exception {
        post("/restaurants/{r}/branches/{b}/devices", world.restaurantId(), world.branchId()).as(world.ownerToken())
                .body(body(world, name, type, linked, linkType, address)).expect(400);
    }

    private UUID printer(World world, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "PRN" + next());
        body.put("name", name);
        body.put("deviceType", "PRINTER");
        body.put("status", "ACTIVE");
        body.put("active", true);
        body.put("online", true);
        body.put("printerConnectionType", "NETWORK");
        body.put("printerRole", "RECEIPT");
        body.put("paperWidthMm", 80);
        body.put("printerIp", "192.168.1.60");
        body.put("printerPort", 9100);
        body.put("autoCut", true);
        body.put("cashDrawerKickEnabled", false);
        return id(post("/restaurants/{r}/settings/printers", world.restaurantId()).as(world.ownerToken()).body(body).expect(201));
    }

    private JsonNode listed(World world, UUID deviceId) throws Exception {
        for (JsonNode one : get("/restaurants/{r}/branches/{b}/devices", world.restaurantId(), world.branchId()).as(world.ownerToken()).expect(200)) {
            if (one.get("id").asText().equals(deviceId.toString())) return one;
        }
        throw new AssertionError("Not listed: " + deviceId);
    }

    private JsonNode setupPrinter(World world, UUID printerId) throws Exception {
        for (JsonNode printer : get("/restaurants/{r}/print-setup", world.restaurantId()).as(world.ownerToken()).expect(200).get("printers")) {
            if (printer.get("id").asText().equals(printerId.toString())) return printer;
        }
        throw new AssertionError("No printer " + printerId);
    }
}
