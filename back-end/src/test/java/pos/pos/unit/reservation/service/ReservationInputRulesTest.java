package pos.pos.unit.reservation.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.PublicReservationRequest;
import pos.pos.reservation.dto.ReservationNoteRequest;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.UpdateReservationRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.service.ReservationSupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Reservation input rules")
class ReservationInputRulesTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 9, 25, 19, 0, 0, 0, ZoneOffset.UTC);

    private static ValidatorFactory factory;
    private static Validator validator;

    private final ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, null);

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static ReservationRequest.ReservationRequestBuilder validCreate() {
        return ReservationRequest.builder()
                .partySize(2)
                .reservationStart(START)
                .reservationEnd(START.plusHours(2))
                .contactName("Emma Wilson")
                .contactPhone("+1 (234) 567-8900")
                .contactEmail("emma@example.com");
    }

    private static Set<String> invalidFields(Object request) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("accepts a normal reservation and one at every length limit")
    void acceptsValidAndBoundaryInput() {
        assertThat(invalidFields(validCreate().build())).isEmpty();
        assertThat(invalidFields(validCreate()
                .partySize(500)
                .contactName("a".repeat(150))
                .contactPhone("1".repeat(50))
                .contactEmail("a".repeat(64) + "@" + "b".repeat(60) + ".example.com")
                .specialRequests("x".repeat(2000))
                .internalNotes("y".repeat(2000))
                .build())).isEmpty();
    }

    @Test
    @DisplayName("rejects text that is too long")
    void rejectsLongText() {
        assertThat(invalidFields(validCreate()
                .contactName("a".repeat(151))
                .contactPhone("1".repeat(51))
                .contactEmail("a".repeat(139) + "@example.com")
                .specialRequests("x".repeat(2001))
                .internalNotes("y".repeat(2001))
                .build()))
                .containsExactlyInAnyOrder("contactName", "contactPhone", "contactEmail", "specialRequests", "internalNotes");
        assertThat(invalidFields(UpdateReservationRequest.builder().specialRequests("x".repeat(2001)).internalNotes("y".repeat(2001)).build()))
                .containsExactlyInAnyOrder("specialRequests", "internalNotes");
        assertThat(invalidFields(PublicReservationRequest.builder()
                .partySize(2).reservationStart(START).reservationEnd(START.plusHours(2))
                .specialRequests("x".repeat(2001)).build()))
                .containsExactly("specialRequests");
        assertThat(invalidFields(ReservationNoteRequest.builder().note("n".repeat(1001)).build())).containsExactly("note");
    }

    @Test
    @DisplayName("rejects a bad email, phone, blank note and impossible party size")
    void rejectsMalformedInput() {
        assertThat(invalidFields(validCreate().contactEmail("not-an-email").build())).containsExactly("contactEmail");
        assertThat(invalidFields(validCreate().contactPhone("call me maybe").build())).containsExactly("contactPhone");
        assertThat(invalidFields(validCreate().contactPhone("12").build())).containsExactly("contactPhone");
        assertThat(invalidFields(validCreate().partySize(0).build())).containsExactly("partySize");
        assertThat(invalidFields(validCreate().partySize(501).build())).containsExactly("partySize");
        assertThat(invalidFields(UpdateReservationRequest.builder().contactPhone("").contactEmail("").build())).isEmpty();
        assertThat(invalidFields(ReservationNoteRequest.builder().note("   ").build())).containsExactly("note");
    }

    @Test
    @DisplayName("limits how long a reservation can last")
    void limitsDuration() {
        support.validateReservationWindow(START, START.plusHours(ReservationSupport.MAX_RESERVATION_HOURS));
        assertThatThrownBy(() -> support.validateReservationWindow(START, START.plusHours(ReservationSupport.MAX_RESERVATION_HOURS).plusMinutes(1)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("at most");
        assertThatThrownBy(() -> support.validateReservationWindow(START, START))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("edits trim text, clear empty fields and refuse a blank name")
    void patchNormalizesText() {
        Reservation reservation = new Reservation();
        reservation.setReservationStart(START);
        reservation.setReservationEnd(START.plusHours(2));
        reservation.setContactName("Emma Wilson");
        reservation.setContactPhone("+1 234");
        reservation.setInternalNotes("old");

        support.applyReservationPatch(reservation, UpdateReservationRequest.builder()
                .contactName("  Emma Rose  ")
                .contactPhone("")
                .internalNotes("   ")
                .specialRequests("  Window seat  ")
                .build(), UUID.randomUUID());

        assertThat(reservation.getContactName()).isEqualTo("Emma Rose");
        assertThat(reservation.getContactPhone()).isNull();
        assertThat(reservation.getInternalNotes()).isNull();
        assertThat(reservation.getSpecialRequests()).isEqualTo("Window seat");

        assertThatThrownBy(() -> support.applyReservationPatch(
                reservation, UpdateReservationRequest.builder().contactName("   ").build(), UUID.randomUUID()))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("contactName");
    }
}
