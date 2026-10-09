package pos.pos.preorder.repository;

import java.util.UUID;

// What the dispatch job needs to lock a scheduled pre-order in the right order (reservation first, then pre-order).
public record ScheduledPreOrderRef(UUID preOrderId, UUID reservationId) {
}
