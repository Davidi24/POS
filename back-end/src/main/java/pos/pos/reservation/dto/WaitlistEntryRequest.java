package pos.pos.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntryRequest {

    @NotBlank(message = "guestName is required")
    @Size(max = 150, message = "guestName must be at most 150 characters")
    private String guestName;

    @Size(max = 50, message = "contactPhone must be at most 50 characters")
    @Pattern(regexp = "^$|^(?=.{5,50}$)\\+?[0-9 ()./-]+$", message = "contactPhone may contain only digits, spaces, + ( ) . / -")
    private String contactPhone;

    @NotNull(message = "partySize is required")
    @Min(value = 1, message = "partySize must be greater than 0")
    @Max(value = 200, message = "partySize must be at most 200")
    private Integer partySize;

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;
}
