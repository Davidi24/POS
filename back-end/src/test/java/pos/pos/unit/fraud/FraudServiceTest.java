package pos.pos.unit.fraud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.fraud.FraudReviewStatus;
import pos.pos.fraud.FraudRule;
import pos.pos.fraud.FraudSeverity;
import pos.pos.fraud.dto.FraudDtos.ReviewRequest;
import pos.pos.fraud.entity.FraudAlertReview;
import pos.pos.fraud.repository.FraudAlertReviewRepository;
import pos.pos.fraud.repository.FraudQueryRepository;
import pos.pos.fraud.repository.FraudQueryRepository.Finding;
import pos.pos.fraud.service.FraudService;
import pos.pos.payment.mapper.PaymentMapper;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;
import pos.pos.settings.repository.SettingsRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FraudService")
class FraudServiceTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final OffsetDateTime REFERENCE_TIME = OffsetDateTime.parse("2026-10-06T12:00:00Z");

    @Mock private RestaurantScopeService scope;
    @Mock private FraudQueryRepository queries;
    @Mock private FraudAlertReviewRepository reviews;
    @Mock private SettingsRepository settingsRepository;
    @Mock private PaymentMapper paymentMapper;

    private final Authentication authentication = mock(Authentication.class);
    private FraudService service;
    private Settings settings;
    private final LocalDate today = LocalDate.of(2026, 10, 6);

    @BeforeEach
    void setUp() {
        service = new FraudService(scope, queries, reviews, settingsRepository, paymentMapper);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setTimezone("Europe/Rome");
        restaurant.setCurrency("EUR");
        settings = new Settings();
        when(scope.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(scope.requireManageableRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(scope.currentUserId(authentication)).thenReturn(REVIEWER);
        when(settingsRepository.findByRestaurant_Id(RESTAURANT_ID)).thenReturn(Optional.of(settings));
        when(paymentMapper.names(anyCollection())).thenReturn(Map.of(ANNA, "Anna", REVIEWER, "Owner"));
        when(reviews.saveAndFlush(any(FraudAlertReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Finding finding(FraudRule rule, String ref, int minutesAgo, String amount, String extra, String ratio, long count) {
        return new Finding(rule, ref, REFERENCE_TIME.minusMinutes(minutesAgo), ANNA, UUID.randomUUID(), "ORD-1",
                null, amount == null ? null : new BigDecimal(amount), "EUR", extra, ratio == null ? null : new BigDecimal(ratio), count);
    }

    @Test
    @DisplayName("every rule describes the action in plain words")
    void details() {
        assertThat(FraudService.detail(finding(FraudRule.LARGE_DISCOUNT, "d", 0, "18", "Staff meal · friend", "45.0", 0), "Anna", settings))
                .isEqualTo("Anna gave a 45% discount (18.00 EUR) on ORD-1 · Staff meal · friend");
        assertThat(FraudService.detail(finding(FraudRule.ITEM_VOID_AFTER_KITCHEN, "i", 0, "9.5", "2 × Pizza", null, 0), "Anna", settings))
                .isEqualTo("Anna removed 2 × Pizza (9.50 EUR) after it went to the kitchen on ORD-1");
        assertThat(FraudService.detail(finding(FraudRule.MANY_VOIDS, "m", 0, "40", "2026-10-06", null, 7), "Anna", settings))
                .isEqualTo("Anna removed 7 items or orders on 2026-10-06 (40.00 EUR)");
        assertThat(FraudService.detail(finding(FraudRule.LARGE_REFUND, "r", 0, "120", "CASH · Cold", null, 0), null, settings))
                .isEqualTo("Someone refunded 120.00 EUR on ORD-1 · CASH · Cold");
        assertThat(FraudService.detail(finding(FraudRule.HIGH_TIP, "t", 0, "15", "CARD", "50", 0), "Anna", settings))
                .isEqualTo("A 50% tip (15.00 EUR) was taken by Anna on ORD-1");
        assertThat(FraudService.detail(finding(FraudRule.OFF_SHIFT_ACTION, "o", 0, "3", "REFUND", null, 0), "Anna", settings))
                .isEqualTo("Anna made a refund (3.00 EUR) on ORD-1 while not clocked in");
        for (FraudRule rule : FraudRule.values()) {
            assertThat(FraudService.detail(finding(rule, "x", 0, "1", "x", "1", 1), "Anna", settings)).isNotBlank();
            assertThat(FraudService.detail(finding(rule, "x", 0, null, null, null, 0), null, settings)).isNotBlank();
        }
    }

    @Test
    @DisplayName("alerts carry the owner's verdict, newest first, and skip duplicates")
    void alertsWithVerdicts() {
        when(queries.largeDiscounts(any(), anyInt())).thenReturn(List.of(
                finding(FraudRule.LARGE_DISCOUNT, "d1", 60, "10", null, "40", 0),
                finding(FraudRule.LARGE_DISCOUNT, "d1", 60, "10", null, "40", 0)));
        when(queries.largeRefunds(any(), any())).thenReturn(List.of(finding(FraudRule.LARGE_REFUND, "r1", 5, "90", null, null, 0)));
        FraudAlertReview verdict = new FraudAlertReview();
        verdict.setRestaurantId(RESTAURANT_ID);
        verdict.setAlertKey("LARGE_REFUND:r1");
        verdict.setStatus(FraudReviewStatus.DISMISSED);
        verdict.setReviewedBy(REVIEWER);
        verdict.setNote("Guest complaint");
        when(reviews.findAllByRestaurantIdAndAlertKeyIn(eq(RESTAURANT_ID), anyCollection())).thenReturn(List.of(verdict));

        var page = service.alerts(authentication, RESTAURANT_ID, null, today, today, null, null, null, null, 0, 40);
        assertThat(page.items()).extracting(alert -> alert.key()).containsExactly("LARGE_REFUND:r1", "LARGE_DISCOUNT:d1");
        assertThat(page.items().get(0).status()).isEqualTo(FraudReviewStatus.DISMISSED);
        assertThat(page.items().get(0).reviewedByName()).isEqualTo("Owner");
        assertThat(page.items().get(1).status()).isEqualTo(FraudReviewStatus.OPEN);
        assertThat(page.items().get(1).staffName()).isEqualTo("Anna");

        var open = service.alerts(authentication, RESTAURANT_ID, null, today, today, null, null, FraudReviewStatus.OPEN, null, 0, 40);
        assertThat(open.items()).hasSize(1);
        var high = service.alerts(authentication, RESTAURANT_ID, null, today, today, null, FraudSeverity.HIGH, null, null, 0, 40);
        assertThat(high.items()).extracting(alert -> alert.rule()).containsExactly(FraudRule.LARGE_REFUND);
        var second = service.alerts(authentication, RESTAURANT_ID, null, today, today, null, null, null, null, 1, 1);
        assertThat(second.items()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
        var beyond = service.alerts(authentication, RESTAURANT_ID, null, today, today, null, null, null, null, 9, 40);
        assertThat(beyond.items()).isEmpty();
    }

    @Test
    @DisplayName("a switched-off rule is not even queried")
    void disabledRules() {
        settings.setFraudDisabledRules("LARGE_DISCOUNT,HIGH_TIP");
        service.alerts(authentication, RESTAURANT_ID, null, today, today, null, null, null, null, 0, 10);
        verify(queries, never()).largeDiscounts(any(), anyInt());
        verify(queries, never()).highTips(any(), anyInt());
        verify(queries).largeRefunds(any(), eq(settings.getFraudRefundAmount()));
        var rules = service.rules(authentication, RESTAURANT_ID);
        assertThat(rules.rules()).filteredOn(rule -> !rule.enabled()).extracting(rule -> rule.rule())
                .containsExactlyInAnyOrder(FraudRule.LARGE_DISCOUNT, FraudRule.HIGH_TIP);
        assertThat(rules.discountPercent()).isEqualTo(30);
    }

    @Test
    @DisplayName("the overview counts open and high alerts per rule, person and day")
    void overview() {
        when(queries.largeRefunds(any(), any())).thenReturn(List.of(
                finding(FraudRule.LARGE_REFUND, "r1", 5, "90", null, null, 0),
                finding(FraudRule.LARGE_REFUND, "r2", 6, "60", null, null, 0)));
        when(queries.highTips(any(), anyInt())).thenReturn(List.of(finding(FraudRule.HIGH_TIP, "t1", 7, "5", null, "40", 0)));
        LocalDate day = REFERENCE_TIME.atZoneSameInstant(java.time.ZoneId.of("Europe/Rome")).toLocalDate();
        var overview = service.overview(authentication, RESTAURANT_ID, null, day.minusDays(1), day);
        assertThat(overview.totalAlerts()).isEqualTo(3);
        assertThat(overview.openAlerts()).isEqualTo(3);
        assertThat(overview.highOpenAlerts()).isEqualTo(2);
        assertThat(overview.amountInvolved()).isEqualByComparingTo("155.00");
        assertThat(overview.rules()).hasSize(FraudRule.values().length);
        assertThat(overview.staff()).singleElement().satisfies(person -> {
            assertThat(person.staffName()).isEqualTo("Anna");
            assertThat(person.highAlerts()).isEqualTo(2);
        });
        assertThat(overview.days()).hasSize(2);
        assertThat(overview.days().get(1).alerts()).isEqualTo(3);
        assertThat(overview.latest()).hasSize(3);
    }

    @Test
    @DisplayName("periods over 92 days, reversed periods and bad pages are refused")
    void validation() {
        assertThatThrownBy(() -> service.alerts(authentication, RESTAURANT_ID, null, today.minusDays(92), today, null, null, null, null, 0, 10))
                .isInstanceOf(AuthException.class).hasMessageContaining("92");
        assertThatThrownBy(() -> service.alerts(authentication, RESTAURANT_ID, null, today, today.minusDays(1), null, null, null, null, 0, 10))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.alerts(authentication, RESTAURANT_ID, null, today, today, null, null, null, null, -1, 10))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.alerts(authentication, RESTAURANT_ID, null, today, today, null, null, null, null, 0, 101))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.activity(authentication, RESTAURANT_ID, null, today, today, "LAUNDERING", null, 0, 10))
                .isInstanceOf(AuthException.class).hasMessageContaining("type must be one of");
    }

    @Test
    @DisplayName("a review is stored per alert key with who and when; bad keys are refused")
    void review() {
        when(reviews.findByRestaurantIdAndAlertKey(RESTAURANT_ID, "LARGE_REFUND:abc-1")).thenReturn(Optional.empty());
        var alert = service.review(authentication, RESTAURANT_ID, "LARGE_REFUND:abc-1", new ReviewRequest(FraudReviewStatus.CONFIRMED, "Took cash"));
        assertThat(alert.status()).isEqualTo(FraudReviewStatus.CONFIRMED);
        assertThat(alert.reviewedBy()).isEqualTo(REVIEWER);
        assertThat(alert.reviewedByName()).isEqualTo("Owner");
        assertThat(alert.reviewNote()).isEqualTo("Took cash");

        for (String bad : List.of("", "nothing", "UNKNOWN_RULE:abc", "LARGE_REFUND:", "LARGE_REFUND:abc def", "LARGE_REFUND:" + "x".repeat(200),
                "large_refund:abc", "LARGE_REFUND:abc;drop")) {
            assertThatThrownBy(() -> service.review(authentication, RESTAURANT_ID, bad, new ReviewRequest(FraudReviewStatus.REVIEWED, null)))
                    .as(bad)
                    .isInstanceOf(AuthException.class)
                    .satisfies(error -> assertThat(((AuthException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}
