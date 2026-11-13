package pos.pos.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// The whole list, in order: occasions left out are removed (bookings keep what they picked).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveReservationOccasionsRequest {
    @NotNull(message = "occasions is required")
    @Size(max = 40, message = "at most 40 occasions")
    private List<@Valid ReservationOccasionDto> occasions;
}
