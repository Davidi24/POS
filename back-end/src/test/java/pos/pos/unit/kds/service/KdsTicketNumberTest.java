package pos.pos.unit.kds.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.kds.repository.KdsTicketRepository;
import pos.pos.kds.service.KdsSupport;
import pos.pos.restaurant.entity.Restaurant;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KdsTicketNumberTest {

    @Mock KdsTicketRepository kdsTicketRepository;
    @InjectMocks KdsSupport kdsSupport;

    // Regression: numbers came from a time-ordered UUID's first 8 characters, which repeat for ~27 seconds,
    // so a second ticket within that time failed to get a number and sending to the kitchen broke.
    @Test void ticketsCreatedBackToBackGetDifferentNumbers() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setCode("MAIN");

        String first = kdsSupport.nextTicketNumber(restaurant);
        String second = kdsSupport.nextTicketNumber(restaurant);

        assertThat(first).startsWith("MAIN_KDS-");
        assertThat(second).isNotEqualTo(first);
    }
}
