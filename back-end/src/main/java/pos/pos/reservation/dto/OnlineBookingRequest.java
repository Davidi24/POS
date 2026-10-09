package pos.pos.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// A booking made by the guest online. The length comes from the restaurant's settings.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineBookingRequest {

    @NotNull(message = "Say how many guests")
    @Min(value = 1, message = "At least 1 guest")
    @Max(value = 500, message = "At most 500 guests")
    private Integer partySize;

    @NotNull(message = "Pick a day and time")
    private OffsetDateTime reservationStart;

    @NotBlank(message = "Write your name")
    @Size(max = 150, message = "The name is too long")
    private String contactName;

    @Size(max = 50, message = "The phone number is too long")
    @Pattern(regexp = "^$|^(?=.{5,50}$)\\+?[0-9 ()./-]+$", message = "The phone number may contain only digits, spaces, + ( ) . / -")
    private String contactPhone;

    @NotBlank(message = "Write your email, so we can confirm the booking")
    @Size(max = 150, message = "The email is too long")
    @Email(message = "Write a valid email")
    private String contactEmail;

    @Size(max = 2000, message = "The note is too long")
    private String specialRequests;

    @Size(max = 40)
    private String occasionCode;

    @Size(max = 20)
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") String> occasionOptions;

    @Size(max = 1000, message = "The occasion note is too long")
    private String occasionNote;

    // Paid extras from the restaurant's special menus (e.g. a cake), paid online.
    @Size(max = 20)
    private List<@Valid Extra> extras;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Extra {
        @NotNull
        private UUID menuItemId;
        @NotNull
        @Min(1)
        @Max(50)
        private Integer quantity;
    }
}
