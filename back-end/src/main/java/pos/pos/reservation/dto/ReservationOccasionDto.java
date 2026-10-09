package pos.pos.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// An occasion as the Admin Hub edits it and bookings pick it.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationOccasionDto {

    // Kept when renaming; a new occasion gets one from its name.
    @Size(max = 40, message = "code must be at most 40 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 80, message = "name must be at most 80 characters")
    private String name;

    @NotBlank(message = "icon is required")
    @Size(max = 16, message = "icon must be at most 16 characters")
    private String icon;

    @Size(max = 20, message = "at most 20 options")
    private List<@Size(max = 80, message = "an option must be at most 80 characters") String> options;

    private Boolean active;
}
