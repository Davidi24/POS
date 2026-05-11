package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationValidationResponse {

    private Boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private List<ReservationAvailabilityOptionResponse> suggestions;
}
