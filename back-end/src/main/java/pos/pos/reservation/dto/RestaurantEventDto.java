package pos.pos.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// An event night as the Admin Hub edits it (and as the app shows it on that day's bookings and menus).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantEventDto {
    private UUID id;

    @NotBlank(message = "name is required")
    @Size(max = 120, message = "name must be at most 120 characters")
    private String name;

    @NotBlank(message = "icon is required")
    @Size(max = 16, message = "icon must be at most 16 characters")
    private String icon;

    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    private LocalDate endDate;

    private UUID menuId;
    private String menuName;
    private Boolean specialMenuOnly;
    private Boolean active;
}
