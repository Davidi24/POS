package pos.pos.unit.preorder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.preorder.repository.ScheduledPreOrderRef;
import pos.pos.preorder.service.PreOrderDispatchJob;
import pos.pos.preorder.service.PreOrderLifecycleService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreOrderDispatchJobTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 26, 18, 0, 0, 0, ZoneOffset.UTC);

    @Mock PreOrderRepository preOrderRepository;
    @Mock PreOrderLifecycleService lifecycle;
    @InjectMocks PreOrderDispatchJob job;

    @Test void processesEachCandidateOnceAndKeepsGoingAfterAFailure() {
        ScheduledPreOrderRef due = new ScheduledPreOrderRef(UUID.randomUUID(), UUID.randomUUID());
        ScheduledPreOrderRef broken = new ScheduledPreOrderRef(UUID.randomUUID(), UUID.randomUUID());
        ScheduledPreOrderRef cancelled = new ScheduledPreOrderRef(UUID.randomUUID(), UUID.randomUUID());
        // Looks only as far ahead as the longest possible lead time (4 hours).
        when(preOrderRepository.findScheduledStartingBefore(NOW.plusHours(4))).thenReturn(List.of(due, broken));
        when(preOrderRepository.findScheduledWithReservationStatusIn(any())).thenReturn(List.of(cancelled, due));
        when(lifecycle.processScheduled(due, NOW)).thenReturn(true);
        when(lifecycle.processScheduled(broken, NOW)).thenThrow(new IllegalStateException("boom"));
        when(lifecycle.processScheduled(cancelled, NOW)).thenReturn(true);

        assertThat(job.dispatchDuePreOrders(NOW)).isEqualTo(2);

        verify(lifecycle, times(1)).processScheduled(due, NOW);
    }
}
