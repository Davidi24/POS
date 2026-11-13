package pos.pos.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationOperationalPublisher;
import pos.pos.order.event.OrderClosedUnpaidEvent;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.user.repository.UserRepository;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Tells the people chosen in Settings → Payments right away when an order is closed as unpaid (user, 2026-11-01).
@Component
@RequiredArgsConstructor
public class OrderUnpaidAlerts {

    public static final String EVENT_CODE = "ORDER_CLOSED_UNPAID";

    private final SettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final NotificationOperationalPublisher notifications;

    @EventListener
    public void onClosedUnpaid(OrderClosedUnpaidEvent event) {
        String codes = settingsRepository.findByRestaurant_Id(event.restaurantId()).map(Settings::getUnpaidAlertRoles).orElse("OWNER,MANAGER");
        Set<String> roles = Arrays.stream(codes.split(",")).map(String::trim).filter(code -> !code.isEmpty()).collect(Collectors.toSet());
        if (roles.isEmpty()) {
            return;
        }
        String by = event.actorId() == null ? null : userRepository.findActiveById(event.actorId())
                .map(user -> (user.getFirstName() + " " + user.getLastName()).trim())
                .orElse(null);
        String where = event.tableNumber() == null ? "" : " at " + event.tableNumber();
        String body = event.owed().toPlainString() + " " + event.currency() + " not paid" + where + " (" + event.branchName() + "): "
                + event.reason() + "." + (by == null ? "" : " Closed by " + by + ".");
        for (UUID recipient : userRepository.findActiveStaffIdsWithRoles(event.restaurantId(), event.branchId(), roles)) {
            if (recipient.equals(event.actorId())) {
                continue;
            }
            notifications.publishCustom(
                    NotificationTopic.ORDER,
                    NotificationMutationType.STATE_CHANGE,
                    NotificationChannel.IN_APP,
                    NotificationPriority.HIGH,
                    EVENT_CODE,
                    event.restaurantId(),
                    null,
                    recipient,
                    "ORDER",
                    event.orderId(),
                    event.actorId(),
                    "Order " + event.orderNumber() + " closed as unpaid",
                    body
            );
        }
    }
}
