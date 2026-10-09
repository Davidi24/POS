package pos.pos.preorder.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.preorder.repository.ScheduledPreOrderRef;
import pos.pos.reservation.enums.ReservationStatus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

// Sends scheduled pre-orders to the kitchen when their lead time is reached, and refunds any whose booking
// ended without the reservation events reaching them. Each pre-order runs in its own transaction.
@Component
@RequiredArgsConstructor
public class PreOrderDispatchJob {

    // The longest lead time a restaurant can set; pre-orders for later bookings can't be due yet.
    static final Duration HORIZON = Duration.ofMinutes(240);

    private static final Logger logger = LoggerFactory.getLogger(PreOrderDispatchJob.class);

    private final PreOrderRepository preOrderRepository;
    private final PreOrderLifecycleService preOrderLifecycleService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 25_000)
    public void dispatchDuePreOrders() {
        dispatchDuePreOrders(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public int dispatchDuePreOrders(OffsetDateTime now) {
        Set<ScheduledPreOrderRef> candidates = new LinkedHashSet<>(preOrderRepository.findScheduledStartingBefore(now.plus(HORIZON)));
        candidates.addAll(preOrderRepository.findScheduledWithReservationStatusIn(
                EnumSet.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW, ReservationStatus.COMPLETED)));

        int handled = 0;
        for (ScheduledPreOrderRef candidate : candidates) {
            try {
                if (preOrderLifecycleService.processScheduled(candidate, now)) {
                    handled++;
                }
            } catch (RuntimeException error) {
                logger.error("Could not process pre-order {}", candidate.preOrderId(), error);
            }
        }
        if (handled > 0) {
            logger.info("Processed {} due pre-order(s)", handled);
        }
        return handled;
    }
}
