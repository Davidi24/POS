package pos.pos.shift.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.user.event.UserRemovedEvent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** A removed person's planned shifts that haven't started are removed with them; worked and missed shifts stay. */
@Component
@RequiredArgsConstructor
public class ShiftUserRemoval {

    private final ShiftRepository shiftRepository;

    @EventListener
    public void onRemoved(UserRemovedEvent event) {
        shiftRepository.deletePlannedOfUser(event.userId(), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
