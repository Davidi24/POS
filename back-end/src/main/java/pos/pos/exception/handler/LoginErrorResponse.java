package pos.pos.exception.handler;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * A refused sign-in: the usual status and message, plus why ([code]); for a locked account until when; for an ended sign-in
 * why it ended ([reason]); at the device limit the person's signed-in devices ([devices]).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginErrorResponse(int status, String message, String code, OffsetDateTime lockedUntil, String reason,
                                 java.util.List<pos.pos.exception.auth.LoginRefusedException.SignedInDevice> devices) {
    public LoginErrorResponse(int status, String message, String code, OffsetDateTime lockedUntil) {
        this(status, message, code, lockedUntil, null, null);
    }
}
