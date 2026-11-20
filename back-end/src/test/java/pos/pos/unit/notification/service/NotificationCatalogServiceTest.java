package pos.pos.unit.notification.service;

import org.junit.jupiter.api.Test;
import pos.pos.notification.dto.NotificationCapabilityResponse;
import pos.pos.notification.enums.NotificationCapabilityStatus;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationCatalogService;

import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationCatalogServiceTest {

    private final NotificationCatalogService service = new NotificationCatalogService();

    @Test
    void advertisesLiveOperationalEventsForImplementedBusinessModules() {
        var byTopic = service.getCatalog().getItems().stream().collect(Collectors.toMap(
                item -> item.getTopic(),
                Function.identity()
        ));

        assertThat(byTopic).containsKeys(
                NotificationTopic.INVENTORY,
                NotificationTopic.PAYMENT,
                NotificationTopic.SHIFT,
                NotificationTopic.RECIPE
        );
        assertLive(byTopic.get(NotificationTopic.INVENTORY),
                "INVENTORY_INVENTORY_ITEM_UPSERT",
                "INVENTORY_INVENTORY_COUNT_LINE_UPSERT",
                "INVENTORY_INVENTORY_MOVEMENT_UPSERT",
                "INVENTORY_INVENTORY_SALES_SOURCE_UPSERT");
        assertLive(byTopic.get(NotificationTopic.PAYMENT),
                "PAYMENT_PAYMENT_UPSERT",
                "PAYMENT_PAYMENT_TRANSACTION_UPSERT");
        assertLive(byTopic.get(NotificationTopic.SHIFT),
                "SHIFT_SHIFT_UPSERT",
                "SHIFT_SHIFT_BREAK_UPSERT");
        assertLive(byTopic.get(NotificationTopic.RECIPE),
                "RECIPE_RECIPE_UPSERT",
                "RECIPE_RECIPE_COMPONENT_UPSERT");
    }

    @Test
    void keepsReportAndAuthenticationCapabilitiesExplicitlyPending() {
        var byTopic = service.getCatalog().getItems().stream().collect(Collectors.toMap(
                item -> item.getTopic(),
                Function.identity()
        ));

        assertThat(byTopic.get(NotificationTopic.REPORT).getStatus()).isEqualTo(NotificationCapabilityStatus.TODO);
        assertThat(byTopic.get(NotificationTopic.REPORT).getNotes()).contains("not implemented");
        assertThat(byTopic.get(NotificationTopic.AUTH).getStatus()).isEqualTo(NotificationCapabilityStatus.TODO);
        assertThat(byTopic.get(NotificationTopic.AUTH).getNotes()).contains("later");
    }

    private void assertLive(NotificationCapabilityResponse capability, String... eventCodes) {
        assertThat(capability.getStatus()).isEqualTo(NotificationCapabilityStatus.LIVE);
        assertThat(capability.isLiveStreamSupported()).isTrue();
        assertThat(capability.isPersistentFeedSupported()).isTrue();
        assertThat(capability.isTemplateSupported()).isTrue();
        assertThat(capability.isPreferenceSupported()).isTrue();
        assertThat(capability.getEventCodes()).contains(eventCodes);
    }
}
