package pos.pos.tables.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
public class TableLayoutChangeNotifier {

    private final RestaurantScopeService restaurantScopeService;
    private final ConcurrentHashMap<BranchKey, CopyOnWriteArraySet<SseEmitter>>
            subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        restaurantScopeService.requireAccessibleBranch(
                authentication,
                restaurantId,
                branchId
        );

        BranchKey key = new BranchKey(restaurantId, branchId);
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(
                key,
                ignored -> new CopyOnWriteArraySet<>()
        ).add(emitter);

        Runnable removeSubscriber = () -> remove(key, emitter);
        emitter.onCompletion(removeSubscriber);
        emitter.onTimeout(removeSubscriber);
        emitter.onError(ignored -> removeSubscriber.run());

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connected")
                            .data(branchId.toString())
            );
        } catch (IOException | IllegalStateException exception) {
            removeSubscriber.run();
            emitter.completeWithError(exception);
        }

        return emitter;
    }

    public void notifyBranchChanged(
            UUID restaurantId,
            UUID branchId
    ) {
        BranchKey key = new BranchKey(restaurantId, branchId);
        Set<SseEmitter> branchSubscribers = subscribers.get(key);
        if (branchSubscribers == null) {
            return;
        }

        TableLayoutChangeEvent event =
                new TableLayoutChangeEvent(restaurantId, branchId);

        branchSubscribers.forEach(emitter -> {
            try {
                emitter.send(
                        SseEmitter.event()
                                .id(UUID.randomUUID().toString())
                                .name("layout-changed")
                                .data(event)
                );
            } catch (IOException | IllegalStateException exception) {
                remove(key, emitter);
                emitter.completeWithError(exception);
            }
        });
    }

    int subscriberCount(UUID restaurantId, UUID branchId) {
        Set<SseEmitter> branchSubscribers = subscribers.get(
                new BranchKey(restaurantId, branchId)
        );
        return branchSubscribers == null ? 0 : branchSubscribers.size();
    }

    private void remove(BranchKey key, SseEmitter emitter) {
        subscribers.computeIfPresent(key, (ignored, branchSubscribers) -> {
            branchSubscribers.remove(emitter);
            return branchSubscribers.isEmpty() ? null : branchSubscribers;
        });
    }

    private record BranchKey(
            UUID restaurantId,
            UUID branchId
    ) {
    }
}
