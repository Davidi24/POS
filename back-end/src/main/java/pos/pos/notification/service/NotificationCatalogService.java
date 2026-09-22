package pos.pos.notification.service;

import org.springframework.stereotype.Service;
import pos.pos.notification.dto.NotificationCapabilityResponse;
import pos.pos.notification.dto.NotificationCatalogResponse;
import pos.pos.notification.enums.NotificationCapabilityStatus;
import pos.pos.notification.enums.NotificationTopic;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class NotificationCatalogService {

    public NotificationCatalogResponse getCatalog() {
        return NotificationCatalogResponse.builder()
                .generatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .items(List.of(
                        capability(NotificationTopic.ORDER, "Orders, order items, and operational order state", NotificationCapabilityStatus.LIVE,
                                List.of("ORDER_ORDER_UPSERT", "ORDER_ORDER_DELETE", "ORDER_ORDER_LINE_ITEM_UPSERT")),
                        capability(NotificationTopic.KDS, "Kitchen display stations, tickets, and workflow", NotificationCapabilityStatus.LIVE,
                                List.of("KDS_KDS_STATION_UPSERT", "KDS_KDS_TICKET_UPSERT", "KDS_KDS_TICKET_ITEM_UPSERT")),
                        capability(NotificationTopic.DEVICE, "Branch devices, assignments, and pairing tokens", NotificationCapabilityStatus.LIVE,
                                List.of("DEVICE_DEVICE_UPSERT", "DEVICE_DEVICE_ASSIGNMENT_UPSERT", "DEVICE_DEVICE_PAIRING_TOKEN_UPSERT")),
                        capability(NotificationTopic.SETTINGS, "Restaurant settings, templates, and operational config", NotificationCapabilityStatus.LIVE,
                                List.of("SETTINGS_SETTINGS_UPSERT", "SETTINGS_SETTINGS_TEMPLATE_UPSERT", "SETTINGS_SETTINGS_SPECIAL_HOUR_UPSERT")),
                        capability(NotificationTopic.TABLE, "Table map, layout, merge, and status changes", NotificationCapabilityStatus.LIVE,
                                List.of("TABLE_RESTAURANT_TABLE_UPSERT", "TABLE_TABLE_CATEGORY_UPSERT")),
                        capability(NotificationTopic.RESERVATION, "Reservation lifecycle, notes, deposits, and table assignments", NotificationCapabilityStatus.LIVE,
                                List.of("RESERVATION_RESERVATION_UPSERT", "RESERVATION_RESERVATION_NOTE_UPSERT", "RESERVATION_RESERVATION_TABLE_ASSIGNMENT_UPSERT")),
                        capability(NotificationTopic.MENU, "Menus, sections, items, variants, options, and routing changes", NotificationCapabilityStatus.LIVE,
                                List.of("MENU_MENU_UPSERT", "MENU_MENU_ITEM_UPSERT", "MENU_OPTION_GROUP_UPSERT")),
                        capability(NotificationTopic.CUSTOMER, "Customer records attached to restaurant operations", NotificationCapabilityStatus.LIVE,
                                List.of("CUSTOMER_CUSTOMER_UPSERT")),
                        capability(NotificationTopic.USER, "Restaurant users and account status changes", NotificationCapabilityStatus.LIVE,
                                List.of("USER_USER_UPSERT", "USER_USER_ROLE_STATE_CHANGE")),
                        capability(NotificationTopic.ROLE, "Role definitions and permission assignment changes", NotificationCapabilityStatus.LIVE,
                                List.of("ROLE_ROLE_UPSERT", "ROLE_ROLE_PERMISSION_STATE_CHANGE")),
                        capability(NotificationTopic.RESTAURANT, "Restaurant profile and lifecycle changes", NotificationCapabilityStatus.LIVE,
                                List.of("RESTAURANT_RESTAURANT_UPSERT")),
                        capability(NotificationTopic.BRANCH, "Branch profile and lifecycle changes", NotificationCapabilityStatus.LIVE,
                                List.of("BRANCH_BRANCH_UPSERT")),
                        todo(NotificationTopic.INVENTORY, "Inventory schema exists. Service/controller wiring is not in place yet; event pipeline is prepared for later repository usage."),
                        todo(NotificationTopic.PAYMENT, "Payment entities exist, but operational services are not connected yet."),
                        todo(NotificationTopic.SHIFT, "Shift entities exist, but shift workflow services are not connected yet."),
                        todo(NotificationTopic.RECIPE, "Recipe schema exists; notification wiring should start when recipe command services arrive."),
                        todo(NotificationTopic.REPORT, "Report definitions exist; report execution and delivery hooks are still pending."),
                        todo(NotificationTopic.AUTH, "Account security, session, and verification events can be targeted later when a user-facing inbox is required.")
                ))
                .build();
    }

    private NotificationCapabilityResponse capability(
            NotificationTopic topic,
            String description,
            NotificationCapabilityStatus status,
            List<String> eventCodes
    ) {
        return NotificationCapabilityResponse.builder()
                .topic(topic)
                .description(description)
                .status(status)
                .liveStreamSupported(true)
                .persistentFeedSupported(true)
                .templateSupported(true)
                .preferenceSupported(true)
                .eventCodes(eventCodes)
                .notes(null)
                .build();
    }

    private NotificationCapabilityResponse todo(NotificationTopic topic, String notes) {
        return NotificationCapabilityResponse.builder()
                .topic(topic)
                .description(topic.name() + " notifications")
                .status(NotificationCapabilityStatus.TODO)
                .liveStreamSupported(false)
                .persistentFeedSupported(false)
                .templateSupported(true)
                .preferenceSupported(true)
                .eventCodes(List.of(topic.name() + "_*_UPSERT"))
                .notes(notes)
                .build();
    }
}
