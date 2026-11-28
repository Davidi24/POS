package pos.pos.tables.specialcase;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

// Does what nobody did (Settings → Tables → Special cases). Runs on the server, so it works with every device closed.
@Component
@RequiredArgsConstructor
public class SpecialCaseJob {

    private static final Logger logger = LoggerFactory.getLogger(SpecialCaseJob.class);

    private final SpecialCaseService specialCases;

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void run() {
        run(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public void run(OffsetDateTime now) {
        specialCases.tablesWithGuests().forEach(each(id -> specialCases.handleTable(id, now)));
        specialCases.bookingsDueOnTakenTables(now).forEach(each(id -> specialCases.handleBookingDue(id, now)));
        specialCases.bookingsToCheck(now).forEach(each(id -> specialCases.handleBookingNeverSeated(id, now)));
        specialCases.reopenedOrders().forEach(each(id -> specialCases.handleReopenedOrder(id, now)));
    }

    // One that fails never stops the others.
    private Consumer<UUID> each(Consumer<UUID> action) {
        return id -> {
            try {
                action.accept(id);
            } catch (RuntimeException failure) {
                logger.warn("Special case skipped for {}: {}", id, failure.getMessage());
            }
        };
    }
}
