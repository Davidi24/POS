package pos.pos.exception.handler;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/** A refused sign-in: the usual status and message, plus why ([code]) and, for a locked account, until when. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginErrorResponse(int status, String message, String code, OffsetDateTime lockedUntil) {
}
