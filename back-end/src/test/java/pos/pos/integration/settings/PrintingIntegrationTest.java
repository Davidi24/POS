package pos.pos.integration.settings;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Printing (user, 2026-11-27): each printer says which tills' receipts and which kitchen stations' tickets it prints, one printer
 * per till and per station; a till reads where it prints; a printer plugged into one device prints for the others through the
 * queue; when things print by themselves is a setting.
 */
class PrintingIntegrationTest extends AbstractPosApiIntegrationTest {

    @BeforeEach
    @AfterEach
    void freshPairingTries() {
        jdbcTemplate.update("delete from " + SCHEMA + ".public_rate_limits where scope = 'DEVICE_PAIRING_IP'");
    }

    @Test
    void eachPrinterSaysWhatItPrints() throws Exception {
        World world = newWorld("printing");
        UUID r = world.restaurantId();
        String owner = world.ownerToken();
        UUID front = printer(world, "Front printer", "RECEIPT", "NETWORK", "192.168.1.20", null);
        UUID back = printer(world, "Back printer", "SMALL", "NETWORK", "192.168.1.21", null);
        UUID office = printer(world, "Office printer", "DOCUMENT", "NETWORK", "192.168.1.23", null);
        UUID barTill = till(world, "Bar till");
        UUID grill = id(post("/restaurants/{r}/branches/{b}/kds/stations", r, world.branchId()).as(owner)
                .body(Map.of("name", "Grill", "stationType", "GRILL")).expect(201));

        JsonNode none = get("/restaurants/{r}/printers/{p}/jobs", r, front).as(owner).expect(200);
        assertThat(none.get("receiptTillIds")).isEmpty();
        assertThat(none.get("tills")).hasSize(1);
        assertThat(none.get("stations")).hasSize(1);

        JsonNode saved = put("/restaurants/{r}/printers/{p}/jobs", r, front).as(owner)
                .body(Map.of("receiptTillIds", List.of(barTill), "kitchenStationIds", List.of(grill))).expect(200);
        assertThat(saved.get("receiptTillIds").get(0).asText()).isEqualTo(barTill.toString());
        assertThat(saved.get("kitchenStationIds").get(0).asText()).isEqualTo(grill.toString());
        assertThat(saved.get("tills").get(0).get("printerName").asText()).isEqualTo("Front printer");

        // One printer per till: choosing the bar till on the back printer takes it off the front one; the grill stays.
        put("/restaurants/{r}/printers/{p}/jobs", r, back).as(owner).body(Map.of("receiptTillIds", List.of(barTill), "kitchenStationIds", List.of())).expect(200);
        JsonNode frontNow = get("/restaurants/{r}/printers/{p}/jobs", r, front).as(owner).expect(200);
        assertThat(frontNow.get("receiptTillIds")).isEmpty();
        assertThat(frontNow.get("kitchenStationIds")).hasSize(1);
        assertThat(frontNow.get("tills").get(0).get("printerName").asText()).isEqualTo("Back printer");

        // The bar till reads its one printer, the grill its own.
        String waiter = token(staff(world, "waiter", "WAITER"));
        JsonNode setup = get("/restaurants/{r}/print-setup", r).as(waiter).param("deviceId", barTill).expect(200);
        assertThat(setup.get("receiptPrinters")).hasSize(1);
        assertThat(setup.get("receiptPrinters").get(0).get("name").asText()).isEqualTo("Back printer");
        assertThat(setup.get("receiptPrinters").get(0).get("role").asText()).isEqualTo("SMALL");
        assertThat(setup.get("kitchen").get(0).get("printers")).hasSize(1);
        assertThat(setup.get("kitchen").get(0).get("textSize").asText()).isEqualTo("NORMAL");
        // Each station picks its tickets' look (decided 2026-11-28): big letters, notes in red.
        put("/restaurants/{r}/branches/{b}/kds/stations/{s}", r, world.branchId(), grill).as(owner)
                .body(Map.of("name", "Grill", "stationType", "GRILL", "ticketTextSize", "BIG", "ticketNotesRed", true)).expect(200);
        JsonNode looked = get("/restaurants/{r}/print-setup", r).as(waiter).param("deviceId", barTill).expect(200);
        assertThat(looked.get("kitchen").get(0).get("textSize").asText()).isEqualTo("BIG");
        assertThat(looked.get("kitchen").get(0).get("notesRed").asBoolean()).isTrue();
        put("/restaurants/{r}/branches/{b}/kds/stations/{s}", r, world.branchId(), grill).as(owner)
                .body(Map.of("name", "Grill", "stationType", "GRILL", "ticketTextSize", "SMALL")).expect(400);
        assertThat(setup.get("kitchen").get(0).get("printers").get(0).get("name").asText()).isEqualTo("Front printer");
        assertThat(setup.get("printKitchenOnSend").asBoolean()).isTrue();

        // A document printer prints documents only; another restaurant's till and a waiter changing it are refused.
        put("/restaurants/{r}/printers/{p}/jobs", r, office).as(owner).body(Map.of("receiptTillIds", List.of(barTill), "kitchenStationIds", List.of())).expect(400);
        put("/restaurants/{r}/printers/{p}/jobs", r, office).as(owner).body(Map.of("receiptTillIds", List.of(), "kitchenStationIds", List.of())).expect(200);
        World other = newWorld("printing other");
        UUID theirTill = till(other, "Their till");
        put("/restaurants/{r}/printers/{p}/jobs", r, front).as(owner).body(Map.of("receiptTillIds", List.of(theirTill), "kitchenStationIds", List.of())).expect(400);
        put("/restaurants/{r}/printers/{p}/jobs", r, front).as(waiter).body(Map.of("receiptTillIds", List.of(), "kitchenStationIds", List.of())).expect(403);
        get("/restaurants/{r}/printers/{p}/jobs", other.restaurantId(), front).as(other.ownerToken()).expect(404);

        // Removing a printer lets go of what it printed.
        delete("/restaurants/{r}/settings/printers/{p}", r, back).as(owner).expect(204);
        JsonNode after = get("/restaurants/{r}/print-setup", r).as(waiter).param("deviceId", barTill).expect(200);
        assertThat(after.get("receiptPrinters")).isEmpty();
    }

    @Test
    void aPrinterPluggedIntoOneTillPrintsForTheOthers() throws Exception {
        World world = newWorld("printing relay");
        UUID r = world.restaurantId();
        String owner = world.ownerToken();
        UUID host = till(world, "Bar till");
        String code = post("/restaurants/{r}/devices/{d}/pairing-tokens", r, host).as(owner).body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();
        String secret = post("/restaurants/{r}/device-pairing/redeem", r).as(owner).body(Map.of("pairingToken", code)).expect(200).get("deviceSecret").asText();
        Map<String, Object> usb = new LinkedHashMap<>(printerBody("USB printer", "RECEIPT", "USB", null, "EPSON TM-T20"));
        usb.put("hostDeviceId", host);
        UUID printer = id(post("/restaurants/{r}/settings/printers", r).as(owner).body(usb).expect(201));
        UUID network = printer(world, "Network printer", "RECEIPT", "NETWORK", "192.168.1.30", null);

        String waiter = token(staff(world, "waiter", "WAITER"));
        String data = Base64.getEncoder().encodeToString("Hello printer".getBytes());
        JsonNode queued = post("/restaurants/{r}/printers/{p}/queue", r, printer).as(waiter).body(Map.of("data", data)).expect(200);
        UUID job = id(queued);
        assertThat(get("/restaurants/{r}/print-queue/{id}", r, job).as(waiter).expect(200).get("status").asText()).isEqualTo("WAITING");
        // Only a printer plugged into a device takes prints this way.
        post("/restaurants/{r}/printers/{p}/queue", r, network).as(waiter).body(Map.of("data", data)).expect(400);

        // The till it's plugged into picks it up with its own secret, prints it and says so.
        get("/public/devices/{d}/print-queue", host).header("X-Device-Secret", "wrong").expect(401);
        JsonNode jobs = get("/public/devices/{d}/print-queue", host).header("X-Device-Secret", secret).expect(200);
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).get("data").asText()).isEqualTo(data);
        assertThat(get("/public/devices/{d}/print-queue", host).header("X-Device-Secret", secret).expect(200)).isEmpty();
        post("/public/devices/{d}/print-queue/{id}", host, job).header("X-Device-Secret", secret).body(Map.of("ok", true)).expect(204);
        assertThat(get("/restaurants/{r}/print-queue/{id}", r, job).as(waiter).expect(200).get("status").asText()).isEqualTo("DONE");

        // Nobody printing it within 2 minutes reads as not printed.
        UUID late = id(post("/restaurants/{r}/printers/{p}/queue", r, printer).as(waiter).body(Map.of("data", data)).expect(200));
        jdbcTemplate.update("update " + SCHEMA + ".print_queue set created_at = now() - interval '3 minutes' where id = ?", late);
        JsonNode lateNow = get("/restaurants/{r}/print-queue/{id}", r, late).as(waiter).expect(200);
        assertThat(lateNow.get("status").asText()).isEqualTo("FAILED");
        assertThat(lateNow.get("problem").asText()).contains("app open");
        // Another restaurant doesn't see it.
        World other = newWorld("printing relay other");
        get("/restaurants/{r}/print-queue/{id}", other.restaurantId(), job).as(other.ownerToken()).expect(404);
    }

    @Test
    void tillsCheckWhichPrintersAnswer() throws Exception {
        World world = newWorld("printer checks");
        UUID r = world.restaurantId();
        String owner = world.ownerToken();
        UUID host = till(world, "Bar till");
        UUID otherTill = till(world, "Front till");
        String code = post("/restaurants/{r}/devices/{d}/pairing-tokens", r, host).as(owner).body(Map.of("ttlMinutes", 10)).expect(201).get("pairingToken").asText();
        String secret = post("/restaurants/{r}/device-pairing/redeem", r).as(owner).body(Map.of("pairingToken", code)).expect(200).get("deviceSecret").asText();
        UUID network = printer(world, "Kitchen printer", "KITCHEN", "NETWORK", "192.168.1.40", null);
        Map<String, Object> usb = new LinkedHashMap<>(printerBody("Bar printer", "BAR", "USB", null, "EPSON TM-T20"));
        usb.put("hostDeviceId", host);
        UUID mine = id(post("/restaurants/{r}/settings/printers", r).as(owner).body(usb).expect(201));
        Map<String, Object> theirs = new LinkedHashMap<>(printerBody("Front printer", "RECEIPT", "USB", null, "Star TSP100"));
        theirs.put("hostDeviceId", otherTill);
        UUID onOtherTill = id(post("/restaurants/{r}/settings/printers", r).as(owner).body(theirs).expect(201));

        // The bar till checks the network printers and the one plugged into it, not the one on the front till.
        get("/public/devices/{d}/printer-checks", host).header("X-Device-Secret", "wrong").expect(401);
        JsonNode list = get("/public/devices/{d}/printer-checks", host).header("X-Device-Secret", secret).expect(200);
        List<String> ids = new java.util.ArrayList<>();
        list.forEach(p -> ids.add(p.get("id").asText()));
        assertThat(ids).containsExactlyInAnyOrder(network.toString(), mine.toString());
        assertThat(printerNamed(world, "Kitchen printer").get("printerState").asText()).isEqualTo("UNCHECKED");

        World other = newWorld("printer checks other");
        UUID foreign = printer(other, "Their printer", "RECEIPT", "NETWORK", "10.0.0.9", null);
        post("/public/devices/{d}/printer-checks", host).header("X-Device-Secret", secret).body(Map.of("checks", List.of(
                Map.of("printerId", network, "reachable", true),
                Map.of("printerId", mine, "reachable", false, "problem", "EPSON TM-T20 isn't taking prints"),
                Map.of("printerId", onOtherTill, "reachable", true),
                Map.of("printerId", foreign, "reachable", true)))).expect(204);

        JsonNode kitchen = printerNamed(world, "Kitchen printer");
        assertThat(kitchen.get("printerState").asText()).isEqualTo("ONLINE");
        assertThat(kitchen.get("online").asBoolean()).isTrue();
        assertThat(kitchen.get("printerCheckedBy").asText()).isEqualTo("Bar till");
        JsonNode bar = printerNamed(world, "Bar printer");
        assertThat(bar.get("printerState").asText()).isEqualTo("OFFLINE");
        assertThat(bar.get("online").asBoolean()).isFalse();
        assertThat(bar.get("printerProblem").asText()).contains("isn't taking prints");
        // Only the till it's plugged into checks it, and another restaurant's printer isn't touched.
        assertThat(printerNamed(world, "Front printer").get("printerState").asText()).isEqualTo("UNCHECKED");
        assertThat(get("/restaurants/{r}/settings/printers", other.restaurantId()).as(other.ownerToken()).expect(200).get(0)
                .get("printerState").asText()).isEqualTo("UNCHECKED");

        // A failed check doesn't undo another till's answer from a moment ago; nobody checking for a while reads unchecked.
        post("/public/devices/{d}/printer-checks", host).header("X-Device-Secret", secret).body(Map.of("checks", List.of(
                Map.of("printerId", network, "reachable", false, "problem", "No answer at 192.168.1.40")))).expect(204);
        assertThat(printerNamed(world, "Kitchen printer").get("printerState").asText()).isEqualTo("ONLINE");
        jdbcTemplate.update("update " + SCHEMA + ".\"device-printer-profiles\" set reached_at = now() - interval '10 minutes', checked_at = now() - interval '10 minutes' where device_id = ?", network);
        assertThat(printerNamed(world, "Kitchen printer").get("printerState").asText()).isEqualTo("UNCHECKED");
        post("/public/devices/{d}/printer-checks", host).header("X-Device-Secret", secret).body(Map.of("checks", List.of(
                Map.of("printerId", network, "reachable", false, "problem", "No answer at 192.168.1.40")))).expect(204);
        JsonNode down = printerNamed(world, "Kitchen printer");
        assertThat(down.get("printerState").asText()).isEqualTo("OFFLINE");
        assertThat(down.get("printerProblem").asText()).isEqualTo("No answer at 192.168.1.40");
    }

    @Test
    void whenThingsPrintByThemselvesIsASetting() throws Exception {
        World world = newWorld("printing settings");
        UUID r = world.restaurantId();
        JsonNode saved = patch("/restaurants/{r}/settings/printing", r).as(world.ownerToken()).body(Map.of(
                "printKitchenOnSend", false, "printBillOnRequest", false, "printReceiptAfterPayment", "ALWAYS")).expect(200);
        assertThat(saved.get("printKitchenOnSend").asBoolean()).isFalse();
        assertThat(saved.get("printReceiptAfterPayment").asText()).isEqualTo("ALWAYS");
        JsonNode setup = get("/restaurants/{r}/print-setup", r).as(world.ownerToken()).expect(200);
        assertThat(setup.get("printBillOnRequest").asBoolean()).isFalse();
        patch("/restaurants/{r}/settings/printing", r).as(world.ownerToken()).body(Map.of(
                "printKitchenOnSend", true, "printBillOnRequest", true, "printReceiptAfterPayment", "SOMETIMES")).expect(400);
        patch("/restaurants/{r}/settings/printing", r).as(token(staff(world, "waiter", "WAITER"))).body(Map.of(
                "printKitchenOnSend", true, "printBillOnRequest", true, "printReceiptAfterPayment", "ASK")).expect(403);
    }

    // ---- helpers ----

    private JsonNode printerNamed(World world, String name) throws Exception {
        for (JsonNode printer : get("/restaurants/{r}/settings/printers", world.restaurantId()).as(world.ownerToken()).expect(200)) {
            if (name.equals(printer.get("name").asText())) return printer;
        }
        throw new AssertionError("No printer " + name);
    }

    private Map<String, Object> printerBody(String name, String role, String connection, String ip, String systemName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "PRN" + next());
        body.put("name", name);
        body.put("deviceType", "PRINTER");
        body.put("status", "ACTIVE");
        body.put("active", true);
        body.put("online", true);
        body.put("printerConnectionType", connection);
        body.put("printerRole", role);
        body.put("paperWidthMm", 80);
        if (ip != null) { body.put("printerIp", ip); body.put("printerPort", 9100); }
        if (systemName != null) body.put("systemPrinterName", systemName);
        body.put("autoCut", true);
        body.put("cashDrawerKickEnabled", true);
        return body;
    }

    private UUID printer(World world, String name, String role, String connection, String ip, String systemName) throws Exception {
        return id(post("/restaurants/{r}/settings/printers", world.restaurantId()).as(world.ownerToken())
                .body(printerBody(name, role, connection, ip, systemName)).expect(201));
    }

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
}
