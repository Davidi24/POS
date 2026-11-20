package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Restaurant-scoped invalidations; clients reload authoritative inventory data after each event. */
@Service
@RequiredArgsConstructor
public class InventoryChangeNotifier {
    private final RestaurantScopeService restaurantScopeService;
    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
        subscribers.compute(restaurantId, (ignored, current) -> {
            var set = current == null ? new CopyOnWriteArraySet<SseEmitter>() : current;
            set.add(emitter);
            return set;
        });
        emitter.onCompletion(() -> remove(restaurantId, emitter));
        emitter.onTimeout(() -> { remove(restaurantId, emitter); emitter.complete(); });
        emitter.onError(error -> remove(restaurantId, emitter));
        send(restaurantId, emitter, "connected");
        return emitter;
    }

    public void changedAfterCommit(UUID restaurantId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            @SuppressWarnings("unchecked")
            Set<UUID> pending = (Set<UUID>) TransactionSynchronizationManager.getResource(this);
            if (pending != null) {
                pending.add(restaurantId);
                return;
            }
            Set<UUID> restaurants = new HashSet<>();
            restaurants.add(restaurantId);
            TransactionSynchronizationManager.bindResource(this, restaurants);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    restaurants.forEach(id -> broadcast(id, "inventory-changed"));
                }
                @Override public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(InventoryChangeNotifier.this);
                }
            });
        } else {
            broadcast(restaurantId, "inventory-changed");
        }
    }

    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        subscribers.keySet().forEach(id -> broadcast(id, "heartbeat"));
    }

    private void broadcast(UUID restaurantId, String event) {
        var set = subscribers.get(restaurantId);
        if (set != null) set.forEach(emitter -> send(restaurantId, emitter, event));
    }

    private void send(UUID restaurantId, SseEmitter emitter, String event) {
        try {
            emitter.send(SseEmitter.event().name(event).data(restaurantId.toString()));
        } catch (IOException | IllegalStateException error) {
            remove(restaurantId, emitter);
            emitter.completeWithError(error);
        }
    }

    private void remove(UUID restaurantId, SseEmitter emitter) {
        subscribers.computeIfPresent(restaurantId, (ignored, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
