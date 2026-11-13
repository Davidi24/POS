package pos.pos.unit.report;

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
import pos.pos.payment.mapper.PaymentMapper;
import pos.pos.report.dto.StatisticsDtos.DaySales;
import pos.pos.report.dto.StatisticsDtos.HourSales;
import pos.pos.report.dto.StatisticsDtos.StaffStats;
import pos.pos.report.dto.StatisticsDtos.WeekdaySales;
import pos.pos.report.service.StatisticsRepository;
import pos.pos.report.service.StatisticsService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StatisticsService")
class StatisticsServiceTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @Mock private RestaurantScopeService scope;
    @Mock private StatisticsRepository repository;
    @Mock private PaymentMapper paymentMapper;

    private final Authentication authentication = mock(Authentication.class);
    private StatisticsService service;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(scope, repository, paymentMapper);
        restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setCurrency("eur");
        restaurant.setTimezone("Europe/Rome");
        when(scope.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
    }

    private void assertBadRequest(Runnable action, String message) {
        assertThatThrownBy(action::run).isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining(message);
    }

    @Test
    @DisplayName("the window is restaurant-local days with the same number of days just before")
    void window() {
        var window = service.window(authentication, RESTAURANT_ID, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
        assertThat(window.period().previousFrom()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(window.period().previousTo()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(window.period().currency()).isEqualTo("EUR");
        assertThat(window.current().from()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay(ZoneId.of("Europe/Rome")).toOffsetDateTime());
        assertThat(window.current().to()).isEqualTo(LocalDate.of(2026, 10, 8).atStartOfDay(ZoneId.of("Europe/Rome")).toOffsetDateTime());
        assertThat(window.previous().to()).isEqualTo(window.current().from());
    }

    @Test
    @DisplayName("a daylight-saving day is 23 or 25 hours long, not 24")
    void daylightSaving() {
        var window = service.window(authentication, RESTAURANT_ID, null, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25));
        assertThat(java.time.Duration.between(window.current().from(), window.current().to()).toHours()).isEqualTo(25);
    }

    @Test
    @DisplayName("refuses missing, reversed and too long periods; checks the branch")
    void windowValidation() {
        UUID branchId = UUID.randomUUID();
        assertBadRequest(() -> service.window(authentication, RESTAURANT_ID, null, null, LocalDate.now()), "required");
        assertBadRequest(() -> service.window(authentication, RESTAURANT_ID, null, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1)), "before");
        assertBadRequest(() -> service.window(authentication, RESTAURANT_ID, null, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)), "366");
        service.window(authentication, RESTAURANT_ID, null, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
        service.window(authentication, RESTAURANT_ID, branchId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        verify(scope).requireAccessibleBranch(authentication, RESTAURANT_ID, branchId);
    }

    @Test
    @DisplayName("falls back to UTC when the restaurant's time zone is broken")
    void brokenTimezone() {
        restaurant.setTimezone("Mars/Olympus");
        var window = service.window(authentication, RESTAURANT_ID, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        assertThat(window.period().timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("days without sales are filled with zero, in order")
    void fillDays() {
        List<DaySales> days = StatisticsService.fillDays(
                List.of(new DaySales(LocalDate.of(2026, 1, 2), new BigDecimal("10"), 1, 2)),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        assertThat(days).extracting(DaySales::date)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3));
        assertThat(days.get(0).sales()).isEqualByComparingTo("0");
        assertThat(days.get(1).sales()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("every hour of the day is present")
    void fillHours() {
        List<HourSales> hours = StatisticsService.fillHours(List.of(new HourSales(13, new BigDecimal("99.5"), 3)));
        assertThat(hours).hasSize(24);
        assertThat(hours.get(13).sales()).isEqualByComparingTo("99.50");
        assertThat(hours.get(0).orders()).isZero();
    }

    @Test
    @DisplayName("weekday averages count quiet days as zero")
    void weekdays() {
        // Two Mondays: 100 and nothing; one Tuesday: 30.
        List<DaySales> days = StatisticsService.fillDays(List.of(
                new DaySales(LocalDate.of(2026, 1, 5), new BigDecimal("100"), 4, 8),
                new DaySales(LocalDate.of(2026, 1, 6), new BigDecimal("30"), 1, 2)
        ), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12));
        List<WeekdaySales> weekdays = StatisticsService.weekdays(days);
        assertThat(weekdays).hasSize(7);
        assertThat(weekdays.get(0).day()).isEqualTo("Mon");
        assertThat(weekdays.get(0).sales()).isEqualByComparingTo("100");
        assertThat(weekdays.get(0).averageSales()).isEqualByComparingTo("50.00");
        assertThat(weekdays.get(1).averageSales()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("CSV cells are quoted when needed and spreadsheet formulas are defused")
    void csvCells() {
        assertThat(StatisticsService.csvCell("Pasta")).isEqualTo("Pasta");
        assertThat(StatisticsService.csvCell("Pasta, al dente")).isEqualTo("\"Pasta, al dente\"");
        assertThat(StatisticsService.csvCell("The \"best\"")).isEqualTo("\"The \"\"best\"\"\"");
        assertThat(StatisticsService.csvCell("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(StatisticsService.csvCell("=HYPERLINK(\"http://evil\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(StatisticsService.csvCell("+1+2")).isEqualTo("'+1+2");
        assertThat(StatisticsService.csvCell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(StatisticsService.csvCell("-12.50")).isEqualTo("-12.50");
        assertThat(StatisticsService.csvCell(" \t=SUM(A1)")).isEqualTo("' \t=SUM(A1)");
        assertThat(StatisticsService.csvCell("\u00a0=1+1")).isEqualTo("'\u00a0=1+1");
        assertThat(StatisticsService.csvCell("\n=1+1")).isEqualTo("\"'\n=1+1\"");
        assertThat(StatisticsService.csvCell(null)).isEmpty();
        StringBuilder csv = new StringBuilder();
        StatisticsService.row(csv, "a", null, "c,d");
        assertThat(csv.toString()).isEqualTo("a,,\"c,d\"\n");
    }

    @Test
    @DisplayName("an unknown report is not found")
    void unknownReport() {
        assertThatThrownBy(() -> service.csvReport(authentication, RESTAURANT_ID, null, LocalDate.now(), LocalDate.now(), "secrets"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("staff rows merge every figure per person, best sellers first, unknown people named as former staff")
    void staffRows() {
        UUID anna = UUID.randomUUID();
        UUID ben = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(repository.staffSales(any())).thenReturn(List.of(
                new StatisticsRepository.StaffRow(anna, 2, new BigDecimal("50.00"), 4),
                new StatisticsRepository.StaffRow(ben, 1, new BigDecimal("80.00"), 2)));
        when(repository.staffTips(any())).thenReturn(Map.of(anna, new BigDecimal("5")));
        when(repository.staffDiscounts(any())).thenReturn(Map.of(ben, new BigDecimal("8")));
        when(repository.staffRemovedValue(any())).thenReturn(Map.of(gone, new BigDecimal("12")));
        when(repository.staffRemovedCount(any())).thenReturn(Map.of(gone, 3L));
        when(repository.staffRefunds(any())).thenReturn(Map.of());
        when(repository.staffVoidedOrders(any())).thenReturn(Map.of());
        when(repository.staffHours(any())).thenReturn(Map.of(anna, new BigDecimal("7.333333")));
        when(paymentMapper.names(anyCollection())).thenReturn(Map.of(anna, "Anna", ben, "Ben"));

        var window = service.window(authentication, RESTAURANT_ID, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        List<StaffStats> rows = service.staffRows(window);
        assertThat(rows).extracting(StaffStats::name).containsExactly("Ben", "Anna", "Former staff");
        StaffStats annaRow = rows.get(1);
        assertThat(annaRow.averageTicket()).isEqualByComparingTo("25.00");
        assertThat(annaRow.tips()).isEqualByComparingTo("5.00");
        assertThat(annaRow.hoursWorked()).isEqualByComparingTo("7.33");
        assertThat(rows.get(2).removedItems()).isEqualTo(3);
        assertThat(rows.get(2).orders()).isZero();
        assertThat(rows.get(2).averageTicket()).isEqualByComparingTo("0");
    }
}
