package pos.pos.order.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Branch-scoped invalidations. Clients fetch authoritative data on connect and change. */
@Service
@RequiredArgsConstructor
public class OrderChangeNotifier {
    private final RestaurantScopeService restaurantScopeService;
    private final ConcurrentHashMap<BranchKey, CopyOnWriteArraySet<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        BranchKey key = new BranchKey(restaurantId, branchId);
        // Periodically reconnect to revalidate authentication and branch permissions.
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
        subscribers.compute(key, (ignored, current) -> {
            var set = current == null ? new CopyOnWriteArraySet<SseEmitter>() : current;
            set.add(emitter);
            return set;
        });
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> { remove(key, emitter); emitter.complete(); });
        emitter.onError(error -> remove(key, emitter));
        send(key, emitter, "connected");
        return emitter;
    }

    public void changedAfterCommit(UUID restaurantId, UUID branchId) {
        BranchKey key = new BranchKey(restaurantId, branchId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { broadcast(key, "orders-changed"); }
            });
        } else {
            broadcast(key, "orders-changed");
        }
    }

    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        subscribers.keySet().forEach(key -> broadcast(key, "heartbeat"));
    }

    private void broadcast(BranchKey key, String event) {
        var set = subscribers.get(key);
        if (set != null) set.forEach(emitter -> send(key, emitter, event));
    }

    private void send(BranchKey key, SseEmitter emitter, String event) {
        try {
            emitter.send(SseEmitter.event().name(event).data(key.branchId().toString()));
        } catch (IOException | IllegalStateException error) {
            remove(key, emitter);
            emitter.completeWithError(error);
        }
    }

    private void remove(BranchKey key, SseEmitter emitter) {
        subscribers.computeIfPresent(key, (ignored, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    private record BranchKey(UUID restaurantId, UUID branchId) {}
}
