package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationEvent;
import pos.pos.reservation.entity.ReservationPayment;
import pos.pos.reservation.enums.PaymentKind;
import pos.pos.reservation.enums.PaymentStatus;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationPaymentRepository;
import pos.pos.reservation.service.BookingMoneyService;
import pos.pos.reservation.service.ReservationPolicy;
import pos.pos.reservation.service.ReservationRuleResolver;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.entity.SettingsReservationRule;
import pos.pos.settings.repository.SettingsReservationRuleRepository;
import pos.pos.settings.repository.SettingsRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Money rules agreed with the owner: each paid part refunds minus the card fee before its own deadline, is kept after
// it (or on a no-show), comes back in full when the restaurant declines, and only part of kept money can be given
// back as goodwill.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Booking money")
class BookingMoneyServiceTest {

    // 20:00 in Berlin.
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 27, 18, 0, 0, 0, ZoneOffset.UTC);

    @Mock ReservationPaymentRepository paymentRepository;
    @Mock PreOrderRepository preOrderRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock ReservationEventRepository eventRepository;
    @Mock SettingsRepository settingsRepository;
    @Mock SettingsReservationRuleRepository ruleRepository;

    private final List<ReservationPayment> payments = new ArrayList<>();
    private BookingMoneyService money;
    private Reservation booking;

    @BeforeEach
    void setUp() {
        ReservationRuleResolver rules = new ReservationRuleResolver(ruleRepository);
        ReservationPolicy policy = new ReservationPolicy(settingsRepository, rules);
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, policy);
        money = new BookingMoneyService(paymentRepository, preOrderRepository, menuItemRepository, eventRepository, policy, rules, support);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setTimezone("Europe/Berlin");
        restaurant.setCurrency("EUR");
        Branch branch = new Branch();
        branch.setId(UUID.randomUUID());
        branch.setRestaurant(restaurant);
        booking = new Reservation();
        booking.setId(UUID.randomUUID());
        booking.setRestaurant(restaurant);
        booking.setBranch(branch);
        booking.setStatus(ReservationStatus.CONFIRMED);
        booking.setPartySize(8);
        booking.setReservationStart(NOW.plusDays(2));
        booking.setReservationEnd(NOW.plusDays(2).plusHours(2));
        when(paymentRepository.findAllByReservation_IdOrderByCreatedAtAsc(booking.getId())).thenAnswer(call -> List.copyOf(payments));
        when(paymentRepository.findAllByReservationIdForUpdate(booking.getId())).thenAnswer(call -> List.copyOf(payments));
        when(paymentRepository.save(any(ReservationPayment.class))).thenAnswer(call -> {
            ReservationPayment payment = call.getArgument(0);
            if (payment.getId() == null) payment.setId(UUID.randomUUID());
            if (!payments.contains(payment)) payments.add(payment);
            return payment;
        });
        when(paymentRepository.findByIdAndReservation_Id(any(), any())).thenAnswer(call ->
                payments.stream().filter(p -> p.getId().equals(call.getArgument(0))).findFirst());
    }

    private ReservationPayment paid(String description, String amount, OffsetDateTime deadline) {
        ReservationPayment payment = new ReservationPayment();
        payment.setId(UUID.randomUUID());
        payment.setReservation(booking);
        payment.setKind(PaymentKind.EXTRA);
        payment.setDescription(description);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrency("EUR");
        payment.setStatus(PaymentStatus.PAID);
        payment.setRefundDeadline(deadline);
        payments.add(payment);
        return payment;
    }

    @Test
    @DisplayName("the card fee is 1.5 % + €0.25 by default, never more than the amount")
    void cardFee() {
        assertThat(money.cardFee(new BigDecimal("50.00"), booking.getRestaurant())).isEqualByComparingTo("1.00");
        assertThat(money.cardFee(new BigDecimal("0.20"), booking.getRestaurant())).isEqualByComparingTo("0.20");
    }

    @Test
    @DisplayName("a guest's cancel refunds what's before its deadline minus the fee, and keeps the rest")
    void guestCancel() {
        ReservationPayment cake = paid("Birthday cake", "25.00", NOW.plusDays(1));
        ReservationPayment flowers = paid("Flowers", "15.00", NOW.minusHours(1));
        ReservationPayment unpaid = paid("Decoration", "20.00", NOW.plusDays(1));
        unpaid.setStatus(PaymentStatus.PENDING);

        money.settle(booking, BookingMoneyService.Outcome.GUEST_CANCELLED, NOW);

        assertThat(cake.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(cake.getRefundedAmount()).isEqualByComparingTo("24.37");
        assertThat(cake.getCardFee()).isEqualByComparingTo("0.63");
        assertThat(flowers.getStatus()).isEqualTo(PaymentStatus.KEPT);
        assertThat(unpaid.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat((ReservationEvent e) ->
                e.getType() == ReservationEventType.REFUNDED && e.getDetail().startsWith("€24.37 back")));
    }

    @Test
    @DisplayName("a declined request gives everything back; a no-show keeps everything")
    void declinedAndNoShow() {
        ReservationPayment late = paid("Deposit", "50.00", NOW.minusHours(3));
        money.settle(booking, BookingMoneyService.Outcome.RESTAURANT_DECLINED, NOW);
        assertThat(late.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(late.getRefundedAmount()).isEqualByComparingTo("50.00");

        payments.clear();
        ReservationPayment kept = paid("Deposit", "50.00", NOW.plusDays(1));
        money.settle(booking, BookingMoneyService.Outcome.NO_SHOW, NOW);
        assertThat(kept.getStatus()).isEqualTo(PaymentStatus.KEPT);
    }

    @Test
    @DisplayName("each line says what a cancel now would do")
    void explanations() {
        paid("Birthday cake", "50.00", NOW.plusDays(1));
        paid("Flowers", "15.00", NOW.minusHours(1));

        List<MoneyLineResponse> lines = money.lines(booking, NOW);

        assertThat(lines.get(0).getRefundIfCancelledNow()).isEqualByComparingTo("49.00");
        assertThat(lines.get(0).getExplanation()).isEqualTo("€49.00 back if cancelled now: birthday cake €50.00 minus the card fee (€1.00)");
        assertThat(lines.get(1).getRefundIfCancelledNow()).isEqualByComparingTo("0");
        assertThat(lines.get(1).getExplanation()).startsWith("No refund if cancelled now: flowers");
    }

    @Test
    @DisplayName("goodwill gives back part of kept money only, never all of it")
    void goodwill() {
        ReservationPayment kept = paid("Deposit", "50.00", NOW.minusHours(1));
        kept.setStatus(PaymentStatus.KEPT);
        ReservationPayment stillPaid = paid("Cake", "25.00", NOW.plusDays(1));

        assertThatThrownBy(() -> money.goodwill(booking, kept.getId().toString(), new BigDecimal("50.00"), "Family emergency", null))
                .hasMessageContaining("never all of it");
        assertThatThrownBy(() -> money.goodwill(booking, stillPaid.getId().toString(), new BigDecimal("5"), "Nice guest", null))
                .hasMessageContaining("Only money the restaurant kept");
        assertThatThrownBy(() -> money.goodwill(booking, kept.getId().toString(), new BigDecimal("5"), " ", null))
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> money.goodwill(booking, "not-a-uuid", new BigDecimal("5"), "Bad line id", null))
                .hasMessageContaining("isn't on the booking");
        assertThatThrownBy(() -> money.goodwill(booking, null, new BigDecimal("5"), "Missing line id", null))
                .hasMessageContaining("isn't on the booking");

        money.goodwill(booking, kept.getId().toString(), new BigDecimal("20.00"), "Family emergency", UUID.randomUUID());
        assertThat(kept.getRefundedAmount()).isEqualByComparingTo("20.00");
        assertThat(kept.getStatus()).isEqualTo(PaymentStatus.KEPT);
    }

    @Test
    @DisplayName("goodwill locks and partially refunds a retained pre-order")
    void preOrderGoodwill() {
        UUID preOrderId = UUID.randomUUID();
        PreOrder preOrder = new PreOrder();
        preOrder.setId(preOrderId);
        preOrder.setReservation(booking);
        preOrder.setRestaurant(booking.getRestaurant());
        preOrder.setStatus(PreOrderStatus.SCHEDULED);
        preOrder.setPaymentStatus(PreOrderPaymentStatus.RETAINED);
        preOrder.setPaidAmount(new BigDecimal("50.00"));
        preOrder.setRefundedAmount(BigDecimal.ZERO);
        when(preOrderRepository.lockById(preOrderId)).thenReturn(Optional.of(preOrder));

        money.goodwill(booking, "pre-order-" + preOrderId, new BigDecimal("20.00"), "Goodwill after cancellation", UUID.randomUUID());

        assertThat(preOrder.getRefundedAmount()).isEqualByComparingTo("20.00");
        verify(preOrderRepository).save(preOrder);
    }

    @Test
    @DisplayName("big groups pay a deposit only when the restaurant turned deposits on")
    void deposit() {
        money.createDepositIfRequired(booking);
        assertThat(payments).isEmpty();

        SettingsReservationRule rule = new SettingsReservationRule();
        rule.setRuleName("Default");
        rule.setActive(true);
        rule.setRequireDeposit(true);
        rule.setDepositValue(new BigDecimal("40"));
        rule.setCancellationWindowHours(24);
        when(ruleRepository.findAllBySettings_Restaurant_IdOrderByPriorityAscCreatedAtAsc(booking.getRestaurant().getId())).thenReturn(List.of(rule));

        money.createDepositIfRequired(booking);
        assertThat(payments).singleElement().satisfies(deposit -> {
            assertThat(deposit.getKind()).isEqualTo(PaymentKind.DEPOSIT);
            assertThat(deposit.getAmount()).isEqualByComparingTo("40.00");
            assertThat(deposit.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(deposit.getRefundDeadline()).isEqualTo(booking.getReservationStart().minusHours(24));
        });

        payments.clear();
        booking.setPartySize(4);
        money.createDepositIfRequired(booking);
        assertThat(payments).isEmpty();
    }

    @Test
    @DisplayName("an extra must come from a special menu, fit the occasion and be ordered in time")
    void extras() {
        Menu special = new Menu();
        special.setRestaurant(booking.getRestaurant());
        special.setSpecial(true);
        MenuSection section = new MenuSection();
        section.setMenu(special);
        MenuItem cake = new MenuItem();
        cake.setId(UUID.randomUUID());
        cake.setSection(section);
        cake.setName("Birthday cake");
        cake.setBasePrice(new BigDecimal("25.00"));
        cake.setAvailable(true);
        cake.setOrderBeforeHours(48);
        cake.setOccasionCodes("BIRTHDAY");
        when(menuItemRepository.findById(cake.getId())).thenReturn(Optional.of(cake));

        assertThatThrownBy(() -> money.addExtra(booking, cake.getId(), 1, NOW)).hasMessageContaining("only offered for some occasions");
        booking.setOccasionCode("BIRTHDAY");
        // 47 h before the booking: too late for a cake that needs 48 h.
        booking.setReservationStart(NOW.plusHours(47));
        assertThatThrownBy(() -> money.addExtra(booking, cake.getId(), 1, NOW)).hasMessageContaining("at least 48 h ahead");

        booking.setReservationStart(NOW.plusDays(3));
        ReservationPayment extra = money.addExtra(booking, cake.getId(), 2, NOW);
        assertThat(extra.getDescription()).isEqualTo("2 × Birthday cake");
        assertThat(extra.getAmount()).isEqualByComparingTo("50.00");
        assertThat(extra.getRefundDeadline()).isEqualTo(NOW.plusDays(3).minusHours(48));

        special.setSpecial(false);
        assertThatThrownBy(() -> money.addExtra(booking, cake.getId(), 1, NOW)).hasMessageContaining("special menu");
    }
}
