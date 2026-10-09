package pos.pos.reservation.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReservationRequest {

    private UUID branchId;
    private UUID customerId;

    private ReservationSource source;

    @Min(value = 1, message = "partySize must be greater than 0")
    @Max(value = 500, message = "partySize must be at most 500")
    private Integer partySize;

    private OffsetDateTime reservationStart;
    private OffsetDateTime reservationEnd;

    @Size(max = 150, message = "contactName must be at most 150 characters")
    private String contactName;

    @Size(max = 50, message = "contactPhone must be at most 50 characters")
    @Pattern(regexp = "^$|^(?=.{5,50}$)\\+?[0-9 ()./-]+$", message = "contactPhone may contain only digits, spaces, + ( ) . / -")
    private String contactPhone;

    @Size(max = 150, message = "contactEmail must be at most 150 characters")
    @Email(message = "contactEmail must be a valid email address")
    private String contactEmail;

    @Size(max = 50, message = "seatingPreference must be at most 50 characters")
    private String seatingPreference;

    @Size(max = 2000, message = "specialRequests must be at most 2000 characters")
    private String specialRequests;

    @Size(max = 2000, message = "internalNotes must be at most 2000 characters")
    private String internalNotes;

    private Boolean depositRequired;

    @DecimalMin(value = "0.0", inclusive = true, message = "depositAmount must not be negative")
    @Digits(integer = 12, fraction = 2, message = "depositAmount must have at most 12 digits and 2 decimals")
    private BigDecimal depositAmount;

    @Size(max = 50, message = "tableIds can have at most 50 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UUID> tableIds;
    private UUID primaryTableId;

    // An occasion (code from /reservation-occasions; empty clears it), its picked options and a note.
    @jakarta.validation.constraints.Size(max = 40, message = "occasionCode must be at most 40 characters")
    @Size(max = 40, message = "occasionCode must be at most 40 characters")
    private String occasionCode;

    @jakarta.validation.constraints.Size(max = 20, message = "at most 20 occasion options")
    private java.util.List<@Size(max = 100, message = "an occasion option must be at most 100 characters") String> occasionOptions;

    @jakarta.validation.constraints.Size(max = 1000, message = "occasionNote must be at most 1000 characters")
    @Size(max = 500, message = "occasionNote must be at most 500 characters")
    private String occasionNote;
}
