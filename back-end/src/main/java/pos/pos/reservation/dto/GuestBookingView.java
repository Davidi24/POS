package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// The guest's own view of their booking (from the link in their emails), with what they can do now.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestBookingView {
    private String token;
    private String reservationCode;
    private String restaurantName;
    // The restaurant's time zone, to show times as the restaurant does.
    private String timezone;
    private ReservationStatus status;
    // e.g. "Confirmed ✓", "Request sent: we'll let you know", "Cancelled".
    private String statusText;
    private Integer partySize;
    private OffsetDateTime reservationStart;
    private OffsetDateTime reservationEnd;
    private String contactName;
    private String occasion;
    private String eventName;
    private String eventIcon;
    private Boolean attendanceConfirmed;
    private Boolean canConfirm;
    private Boolean canCancel;
    // "I'm running late": which extra minutes can still be asked for (empty when not possible).
    private List<Integer> lateOptions;
    private OffsetDateTime holdUntil;
    private List<MoneyLineResponse> money;
    private BigDecimal amountDue;
    private String currency;
    // The payment page, when something is left to pay.
    private String payUrl;
    private Boolean testPayments;
}
