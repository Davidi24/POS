package pos.pos.integration.settings;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Device management concurrency integration tests")
class DeviceManagementConcurrencyIntegrationTest extends AbstractSettingsIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Inactive and retired devices cannot receive new pairing tokens")
    void inactiveDeviceCannotBePaired() throws Exception {
        Restaurant restaurant = createRestaurant("inactive-device-pairing");
        Branch branch = createBranch(restaurant, "inactive-device-pairing");
        User admin = createRestaurantAdmin(restaurant, "inactive-device-pairing");
        String accessToken = accessTokenFor(admin, "inactive-device-pairing");
        JsonNode device = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/devices",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "RETIRED_TERMINAL",
                                "name", "Retired Terminal",
                                "deviceType", "TERMINAL",
                                "status", "PROVISIONING",
                                "active", true,
                                "online", false
                        ))))
                .andReturn());

        MvcResult retired = mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}/status",
                        restaurant.getId(), branch.getId(), device.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "RETIRED", "active", false, "online", false))))
                .andReturn();
        assertThat(retired.getResponse().getStatus()).isEqualTo(200);

        MvcResult pairing = mockMvc.perform(post("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), device.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn();
        assertThat(pairing.getResponse().getStatus()).isEqualTo(409);

        JsonNode tokens = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), device.get("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))).andReturn());
        assertThat(tokens).isEmpty();
    }

    @Test
    @DisplayName("a pairing token is redeemed once, stores only a secret hash, and returns the secret once")
    void pairingTokenRedemptionIsSingleUse() throws Exception {
        Restaurant restaurant = createRestaurant("redeem-device-pairing");
        Branch branch = createBranch(restaurant, "redeem-device-pairing");
        User admin = createRestaurantAdmin(restaurant, "redeem-device-pairing");
        String accessToken = accessTokenFor(admin, "redeem-device-pairing");
        JsonNode device = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/devices",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "PAIR_REDEEM",
                                "name", "Pairing Test Device",
                                "deviceType", "TERMINAL",
                                "status", "PROVISIONING",
                                "active", true,
                                "online", false
                        ))))
                .andReturn());
        String deviceId = device.get("id").asText();
        JsonNode issued = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn());
        String rawPairingToken = issued.get("pairingToken").asText();
        String pairingTokenId = issued.get("id").asText();
        Callable<MvcResult> redeem = () -> mockMvc.perform(post("/public/devices/pairing/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pairingToken", rawPairingToken))))
                .andReturn();

        List<MvcResult> results = runTogether(redeem);

        assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList())
                .containsExactlyInAnyOrder(200, 401);
        MvcResult success = results.stream().filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
        JsonNode redeemed = bodyOf(success);
        assertThat(redeemed.get("deviceId").asText()).isEqualTo(deviceId);
        assertThat(redeemed.get("restaurantId").asText()).isEqualTo(restaurant.getId().toString());
        String deviceSecret = redeemed.get("deviceSecret").asText();
        assertThat(deviceSecret).hasSize(43).isNotEqualTo(rawPairingToken);

        String secretHash = jdbcTemplate.queryForObject(
                "select auth_secret_hash from devices where id = ?", String.class, java.util.UUID.fromString(deviceId));
        assertThat(secretHash).hasSize(64).isNotEqualTo(deviceSecret);
        Integer usedTokens = jdbcTemplate.queryForObject(
                "select count(*) from \"device-pairing-tokens\" where id = ? and used_at is not null",
                Integer.class,
                java.util.UUID.fromString(pairingTokenId)
        );
        assertThat(usedTokens).isEqualTo(1);

        JsonNode history = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), deviceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))).andReturn());
        assertThat(history.get(0).get("state").asText()).isEqualTo("USED");
        assertThat(history.get(0).get("pairingToken").isNull()).isTrue();
    }

    @Test
    @DisplayName("unknown, revoked, and expired pairing tokens all fail with the same response")
    void invalidPairingTokensHaveGenericUnauthorizedResponse() throws Exception {
        Restaurant restaurant = createRestaurant("invalid-device-pairing");
        Branch branch = createBranch(restaurant, "invalid-device-pairing");
        User admin = createRestaurantAdmin(restaurant, "invalid-device-pairing");
        String accessToken = accessTokenFor(admin, "invalid-device-pairing");
        JsonNode device = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/devices",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "PAIR_INVALID",
                                "name", "Invalid Pairing Test Device",
                                "deviceType", "TERMINAL",
                                "status", "PROVISIONING",
                                "active", true,
                                "online", false
                        ))))
                .andReturn());
        String deviceId = device.get("id").asText();

        JsonNode revokedToken = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn());
        mockMvc.perform(post("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens/{pairingTokenId}/revoke",
                        restaurant.getId(), deviceId, revokedToken.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        JsonNode expiredToken = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn());
        jdbcTemplate.update(
                "update \"device-pairing-tokens\" set created_at = now() - interval '2 seconds', "
                        + "expires_at = now() - interval '1 second' where id = ?",
                java.util.UUID.fromString(expiredToken.get("id").asText())
        );

        List<String> attemptedTokens = List.of(
                revokedToken.get("pairingToken").asText(),
                expiredToken.get("pairingToken").asText(),
                "unknown-device-pairing-token"
        );
        List<String> responses = new ArrayList<>();
        for (String attemptedToken : attemptedTokens) {
            MvcResult result = mockMvc.perform(post("/public/devices/pairing/redeem")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("pairingToken", attemptedToken))))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            responses.add(bodyOf(result).get("message").asText());
        }
        assertThat(responses).containsOnly("Pairing token is invalid or expired");
    }

    @Test
    @DisplayName("a paired device authenticates heartbeats and inactive devices are rejected")
    void deviceSecretAuthenticatesHeartbeatAndDeactivationBlocksIt() throws Exception {
        Restaurant restaurant = createRestaurant("device-heartbeat-auth");
        Branch branch = createBranch(restaurant, "device-heartbeat-auth");
        User admin = createRestaurantAdmin(restaurant, "device-heartbeat-auth");
        String accessToken = accessTokenFor(admin, "device-heartbeat-auth");
        JsonNode device = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/devices",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "HEARTBEAT_DEVICE",
                                "name", "Heartbeat Test Device",
                                "deviceType", "TERMINAL",
                                "status", "PROVISIONING",
                                "active", true,
                                "online", false
                        ))))
                .andReturn());
        String deviceId = device.get("id").asText();
        JsonNode issued = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn());
        JsonNode paired = bodyOf(mockMvc.perform(post("/public/devices/pairing/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pairingToken", issued.get("pairingToken").asText()
                        ))))
                .andReturn());
        String deviceSecret = paired.get("deviceSecret").asText();

        MvcResult heartbeat = mockMvc.perform(post("/public/devices/{deviceId}/heartbeat", deviceId)
                        .header("X-Device-Secret", deviceSecret))
                .andReturn();
        assertThat(heartbeat.getResponse().getStatus()).isEqualTo(200);
        JsonNode heartbeatBody = bodyOf(heartbeat);
        assertThat(heartbeatBody.get("deviceId").asText()).isEqualTo(deviceId);
        assertThat(heartbeatBody.get("online").asBoolean()).isTrue();
        assertThat(heartbeatBody.get("lastSeenAt").asText()).isNotBlank();
        Integer onlineWithLastSeen = jdbcTemplate.queryForObject(
                "select count(*) from devices where id = ? and is_online = true and last_seen_at is not null",
                Integer.class,
                java.util.UUID.fromString(deviceId)
        );
        assertThat(onlineWithLastSeen).isEqualTo(1);

        MvcResult wrongSecret = mockMvc.perform(post("/public/devices/{deviceId}/heartbeat", deviceId)
                        .header("X-Device-Secret", "incorrect-secret"))
                .andReturn();
        assertThat(wrongSecret.getResponse().getStatus()).isEqualTo(401);
        assertThat(bodyOf(wrongSecret).get("message").asText()).isEqualTo("Device authentication failed");

        mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}/status",
                        restaurant.getId(), branch.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RETIRED", "active", false, "online", false
                        ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        Integer deactivatedAndCredentialCleared = jdbcTemplate.queryForObject(
                "select count(*) from devices where id = ? and is_online = false "
                        + "and auth_secret_hash is null and auth_secret_rotated_at is null",
                Integer.class,
                java.util.UUID.fromString(deviceId)
        );
        assertThat(deactivatedAndCredentialCleared).isEqualTo(1);
        MvcResult retiredDevice = mockMvc.perform(post("/public/devices/{deviceId}/heartbeat", deviceId)
                        .header("X-Device-Secret", deviceSecret))
                .andReturn();
        assertThat(retiredDevice.getResponse().getStatus()).isEqualTo(401);
        assertThat(bodyOf(retiredDevice).get("message").asText()).isEqualTo("Device authentication failed");

        mockMvc.perform(patch("/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}/status",
                        restaurant.getId(), branch.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACTIVE", "active", true, "online", false
                        ))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        MvcResult oldSecretAfterReactivation = mockMvc.perform(post(
                        "/public/devices/{deviceId}/heartbeat", deviceId)
                        .header("X-Device-Secret", deviceSecret))
                .andReturn();
        assertThat(oldSecretAfterReactivation.getResponse().getStatus()).isEqualTo(401);

        JsonNode rotatedPairing = bodyOf(mockMvc.perform(post(
                        "/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens", restaurant.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn());
        JsonNode reconnected = bodyOf(mockMvc.perform(post("/public/devices/pairing/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pairingToken", rotatedPairing.get("pairingToken").asText()
                        ))))
                .andReturn());
        String rotatedSecret = reconnected.get("deviceSecret").asText();
        assertThat(rotatedSecret).isNotEqualTo(deviceSecret);
        assertThat(mockMvc.perform(post("/public/devices/{deviceId}/heartbeat", deviceId)
                        .header("X-Device-Secret", rotatedSecret))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        MvcResult unknownDevice = mockMvc.perform(post(
                        "/public/devices/{deviceId}/heartbeat",
                        java.util.UUID.randomUUID()
                ).header("X-Device-Secret", deviceSecret))
                .andReturn();
        assertThat(unknownDevice.getResponse().getStatus()).isEqualTo(401);
        assertThat(bodyOf(unknownDevice).get("message").asText()).isEqualTo("Device authentication failed");
    }

    @Test
    @DisplayName("Disabling a device revokes its active pairing tokens even when token issuance races")
    void disablingDeviceRevokesExistingAndRacingPairingTokens() throws Exception {
        Restaurant restaurant = createRestaurant("disable-device-pairing-race");
        Branch branch = createBranch(restaurant, "disable-device-pairing-race");
        User admin = createRestaurantAdmin(restaurant, "disable-device-pairing-race");
        String accessToken = accessTokenFor(admin, "disable-device-pairing-race");
        JsonNode device = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/devices",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "DISABLE_RACE_TERMINAL",
                                "name", "Disable Race Terminal",
                                "deviceType", "TERMINAL",
                                "status", "PROVISIONING",
                                "active", true,
                                "online", false
                        ))))
                .andReturn());
        String deviceId = device.get("id").asText();

        MvcResult initialToken = mockMvc.perform(post("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), deviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn();
        assertThat(initialToken.getResponse().getStatus()).isEqualTo(201);

        Callable<MvcResult> disableDevice = () -> mockMvc.perform(patch(
                        "/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}/status",
                        restaurant.getId(), branch.getId(), deviceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "status", "RETIRED", "active", false, "online", false
                )))).andReturn();
        Callable<MvcResult> issueToken = () -> mockMvc.perform(post(
                        "/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens", restaurant.getId(), deviceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10))))
                .andReturn();

        List<MvcResult> racingResults = runTogether(disableDevice, issueToken);
        assertThat(racingResults.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(racingResults.get(1).getResponse().getStatus()).isIn(201, 409);

        JsonNode tokens = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens",
                        restaurant.getId(), deviceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))).andReturn());
        assertThat(countState(tokens, "ACTIVE")).isZero();
        assertThat(countState(tokens, "REVOKED")).isEqualTo(tokens.size());
    }

    @Test
    @DisplayName("Concurrent device assignment and pairing-token writes keep one active record")
    void concurrentDeviceWritesKeepOneActiveAssignmentAndPairingToken() throws Exception {
        Restaurant restaurant = createRestaurant("device-write-race");
        Branch branch = createBranch(restaurant, "device-write-race");
        User admin = createRestaurantAdmin(restaurant, "device-write-race");
        String accessToken = accessTokenFor(admin, "device-write-race");
        JsonNode device = bodyOf(mockMvc.perform(post("/restaurants/{restaurantId}/branches/{branchId}/devices",
                        restaurant.getId(), branch.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "RACE_TERMINAL",
                                "name", "Race Terminal",
                                "deviceType", "TERMINAL",
                                "status", "PROVISIONING",
                                "active", true,
                                "online", false
                        ))))
                .andReturn());

        Callable<MvcResult> assignmentRequest = () -> mockMvc.perform(post(
                        "/restaurants/{restaurantId}/devices/{deviceId}/assignments", restaurant.getId(), device.get("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "assignmentType", "BRANCH", "branchId", branch.getId().toString()
                )))).andReturn();
        List<MvcResult> assignmentResults = runTogether(assignmentRequest);
        assertThat(assignmentResults).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));

        JsonNode assignments = bodyOf(mockMvc.perform(get("/restaurants/{restaurantId}/devices/{deviceId}/assignments",
                        restaurant.getId(), device.get("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))).andReturn());
        assertThat(assignments).hasSize(2);
        assertThat(countActive(assignments)).isEqualTo(1);

        Callable<MvcResult> pairingRequest = () -> mockMvc.perform(post(
                        "/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens", restaurant.getId(), device.get("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ttlMinutes", 10)))
                ).andReturn();
        List<MvcResult> pairingResults = runTogether(pairingRequest);
        assertThat(pairingResults).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));

        JsonNode pairingTokens = bodyOf(mockMvc.perform(get(
                        "/restaurants/{restaurantId}/devices/{deviceId}/pairing-tokens", restaurant.getId(), device.get("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))).andReturn());
        assertThat(pairingTokens).hasSize(2);
        assertThat(countState(pairingTokens, "ACTIVE")).isEqualTo(1);
        assertThat(countState(pairingTokens, "REVOKED")).isEqualTo(1);
    }

    private List<MvcResult> runTogether(Callable<MvcResult> request) throws Exception {
        return runTogether(request, request);
    }

    private List<MvcResult> runTogether(Callable<MvcResult> firstRequest, Callable<MvcResult> secondRequest) throws Exception {
        int callers = 2;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<MvcResult>> pending = new ArrayList<>();
            for (Callable<MvcResult> request : List.of(firstRequest, secondRequest)) {
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent device write start timed out");
                    }
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<MvcResult> completed = new ArrayList<>();
            for (Future<MvcResult> result : pending) completed.add(result.get(30, TimeUnit.SECONDS));
            return completed;
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private long countActive(JsonNode values) {
        long active = 0;
        for (JsonNode value : values) if (value.get("active").asBoolean()) active++;
        return active;
    }

    private long countState(JsonNode values, String state) {
        long count = 0;
        for (JsonNode value : values) if (state.equals(value.get("state").asText())) count++;
        return count;
    }
}
