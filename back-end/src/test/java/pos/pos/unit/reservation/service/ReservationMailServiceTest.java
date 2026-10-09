package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pos.pos.config.properties.AppMailProperties;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.service.ReservationMailService;
import pos.pos.reservation.service.ReservationReminderDeliveryService;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Restaurant;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationMailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final ReservationReminderDeliveryService deliveryService = mock(ReservationReminderDeliveryService.class);
    private final ReservationSupport support = mock(ReservationSupport.class);
    private final AppMailProperties properties = new AppMailProperties();
    private final ReservationMailService service;

    ReservationMailServiceTest() {
        properties.setFrom("pos@example.com");
        when(support.restaurantZone(any())).thenReturn(ZoneId.of("UTC"));
        service = new ReservationMailService(mailSender, properties, support, deliveryService);
        ReflectionTestUtils.setField(service, "guestBaseUrl", "https://pos.example");
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void smtpFailureDoesNotMarkTheGuestAsReminded() {
        Reservation booking = booking();
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        service.reminder(booking);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(deliveryService, never()).recordGuestReminderSent(booking.getId());

        reset(mailSender);
        service.reminder(booking);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(deliveryService).recordGuestReminderSent(booking.getId());
    }

    @Test
    void recordsReminderOnlyAfterSmtpAcceptsTheMessage() {
        Reservation booking = booking();

        service.reminder(booking);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(deliveryService).recordGuestReminderSent(booking.getId());
    }

    @Test
    void defersBothSendingAndReminderEventUntilTheBookingTransactionCommits() {
        Reservation booking = booking();
        TransactionSynchronizationManager.initSynchronization();

        service.reminder(booking);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(deliveryService, never()).recordGuestReminderSent(booking.getId());
        TransactionSynchronization callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
        callback.afterCommit();

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(deliveryService).recordGuestReminderSent(booking.getId());
    }

    private Reservation booking() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Test Bistro");
        Reservation booking = new Reservation();
        booking.setId(UUID.randomUUID());
        booking.setRestaurant(restaurant);
        booking.setContactName("Ada Example");
        booking.setContactEmail("guest@example.com");
        booking.setReservationCode("RES_TEST");
        booking.setPartySize(2);
        booking.setReservationStart(OffsetDateTime.parse("2026-10-08T18:00:00Z"));
        booking.setGuestToken("guest-token");
        return booking;
    }
}
