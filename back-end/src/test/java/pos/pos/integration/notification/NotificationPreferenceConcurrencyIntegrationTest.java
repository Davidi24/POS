package pos.pos.integration.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
@DisplayName("Notification preference concurrency integration tests")
class NotificationPreferenceConcurrencyIntegrationTest extends AbstractPosApiIntegrationTest {

    @Test
    @DisplayName("Read actions skip queued messages and reject a single undelivered message")
    void readActionsRequireDelivery() throws Exception {
        World world = newWorld("notification-read-delivery");
        UUID queuedEmailId = insertNotification(world, "EMAIL", "QUEUED", null, null);
        OffsetDateTime deliveredAt = OffsetDateTime.now(ZoneOffset.UTC);
        UUID deliveredInAppId = insertNotification(world, "IN_APP", "DELIVERED", deliveredAt, deliveredAt);
        UUID restaurantWideId = insertNotification(world, "IN_APP", "DELIVERED", deliveredAt, deliveredAt, null);

        JsonNode feed = get("/restaurants/{restaurantId}/notifications", world.restaurantId())
                .as(world.ownerToken())
                .param("branchId", world.branchId())
                .param("personalOnly", true)
                .param("size", 10)
                .expect(200);
        JsonNode items = feed.get("items");
        JsonNode queuedItem = findNotification(items, queuedEmailId);
        JsonNode deliveredItem = findNotification(items, deliveredInAppId);
        JsonNode restaurantWideItem = findNotification(items, restaurantWideId);
        assertThat(queuedItem.get("markReadAllowed").asBoolean()).isFalse();
        assertThat(deliveredItem.get("markReadAllowed").asBoolean()).isTrue();
        assertThat(restaurantWideItem.get("markReadAllowed").asBoolean()).isTrue();

        post("/restaurants/{restaurantId}/notifications/read-all", world.restaurantId())
                .as(world.ownerToken())
                .param("branchId", world.branchId())
                .expect(204);

        assertThat(isRead(deliveredInAppId)).isTrue();
        assertThat(isRead(restaurantWideId)).isTrue();
        assertThatUpdatedAfterCreation(deliveredInAppId);
        assertThatUpdatedAfterCreation(restaurantWideId);
        assertThat(isRead(queuedEmailId)).isFalse();
        assertThat(statusOf(queuedEmailId)).isEqualTo("QUEUED");

        post("/restaurants/{restaurantId}/notifications/{notificationId}/read", world.restaurantId(), queuedEmailId)
                .as(world.ownerToken())
                .expect(409);
        assertThat(isRead(queuedEmailId)).isFalse();
        assertThat(statusOf(queuedEmailId)).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("Manual broadcasts reject channels that have no configured delivery worker")
    void manualBroadcastFailsClosedForUnconfiguredChannels() throws Exception {
        World world = newWorld("notification-unconfigured-channel");
        Integer before = jdbcTemplate.queryForObject("select count(*) from notifications where restaurant_id = ?", Integer.class, world.restaurantId());

        for (String channel : List.of("EMAIL", "SMS", "PUSH", "WEBHOOK")) {
            int status = post("/restaurants/{restaurantId}/notifications/broadcast", world.restaurantId())
                    .as(world.ownerToken())
                    .body(Map.of("topic", "ORDER", "channel", channel, "body", "Operational test"))
                    .send()
                    .status();
            assertThat(status).as("channel %s must not be silently left queued", channel).isEqualTo(409);
        }

        Integer after = jdbcTemplate.queryForObject("select count(*) from notifications where restaurant_id = ?", Integer.class, world.restaurantId());
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("Manual in-app broadcasts remain immediately available")
    void manualInAppBroadcastIsPersistedAsDelivered() throws Exception {
        World world = newWorld("notification-in-app-broadcast");

        JsonNode response = post("/restaurants/{restaurantId}/notifications/broadcast", world.restaurantId())
                .as(world.ownerToken())
                .body(Map.of("topic", "ORDER", "body", "Kitchen needs help"))
                .expect(201);

        assertThat(response.get("channel").asText()).isEqualTo("IN_APP");
        assertThat(response.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(response.get("sentAt").asText()).isNotBlank();
        assertThat(response.get("deliveredAt").asText()).isNotBlank();
        assertThat(response.get("eventCode").asText()).isEqualTo("ORDER_ORDER_BROADCAST");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from notifications where restaurant_id = ? and id = ? and event_code = ?",
                Integer.class,
                world.restaurantId(), UUID.fromString(response.get("id").asText()), response.get("eventCode").asText())).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent first-time upserts for one user create one preference row")
    void concurrentFirstWritesKeepOnePreference() throws Exception {
        World world = newWorld("notification-preference-race");
        Callable<Integer> enable = () -> updatePreference(world.ownerToken(), true);
        Callable<Integer> disable = () -> updatePreference(world.ownerToken(), false);

        List<Integer> results = runTogether(enable, disable);
        assertThat(results).containsExactlyInAnyOrder(200, 200);

        JsonNode preferences = get("/notifications/preferences").as(world.ownerToken()).expect(200);
        assertThat(preferences).hasSize(1);
        assertThat(preferences.get(0).get("channel").asText()).isEqualTo("EMAIL");
        assertThat(preferences.get(0).get("eventCode").asText()).isEqualTo("ORDER_READY");
    }

    private int updatePreference(String token, boolean enabled) throws Exception {
        return put("/notifications/preferences").as(token).body(List.of(Map.of(
                "channel", "EMAIL",
                "eventCode", "ORDER_READY",
                "enabled", enabled
        ))).send().status();
    }

    private UUID insertNotification(World world, String channel, String status, OffsetDateTime sentAt, OffsetDateTime deliveredAt) {
        return insertNotification(world, channel, status, sentAt, deliveredAt, world.branchId());
    }

    private UUID insertNotification(
            World world,
            String channel,
            String status,
            OffsetDateTime sentAt,
            OffsetDateTime deliveredAt,
            UUID branchId
    ) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                insert into notifications (
                    id, restaurant_id, branch_id, recipient_user_id, channel, status, priority, event_code,
                    attempt_count, sent_at, delivered_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 'NORMAL', 'ORDER_READY', 0, ?, ?, ?, ?)
                """,
                id, world.restaurantId(), branchId, world.owner().getId(), channel, status,
                sentAt, deliveredAt, now, now
        );
        return id;
    }

    private JsonNode findNotification(JsonNode items, UUID id) {
        for (JsonNode item : items) {
            if (id.toString().equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Notification not found: " + id);
    }

    private boolean isRead(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select read_at is not null from notifications where id = ?", Boolean.class, id));
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject("select status from notifications where id = ?", String.class, id);
    }

    private void assertThatUpdatedAfterCreation(UUID id) {
        assertThat(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select updated_at > created_at from notifications where id = ?", Boolean.class, id))).isTrue();
    }

    private List<Integer> runTogether(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> pending = new ArrayList<>();
            for (Callable<Integer> request : List.of(first, second)) {
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent preference write start timed out");
                    }
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> completed = new ArrayList<>();
            for (Future<Integer> result : pending) {
                completed.add(result.get(30, TimeUnit.SECONDS));
            }
            return completed;
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
