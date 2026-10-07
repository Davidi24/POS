package pos.pos.unit.menu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.menu.entity.Menu;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Menu availability")
class MenuAvailabilityTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    @Test
    void allDayAndDateBoundsAreInclusive() {
        Menu menu = new Menu();
        menu.setAvailableFromDate(DAY);
        menu.setAvailableUntilDate(DAY.plusDays(1));

        assertThat(menu.isAvailableAt(DAY.atStartOfDay())).isTrue();
        assertThat(menu.isAvailableAt(DAY.plusDays(1).atTime(LocalTime.MAX))).isTrue();
        assertThat(menu.isAvailableAt(DAY.minusDays(1).atTime(12, 0))).isFalse();
        assertThat(menu.isAvailableAt(DAY.plusDays(2).atTime(12, 0))).isFalse();
        assertThat(menu.isAvailableAt(null)).isFalse();
    }

    @Test
    void boundedDailyHoursIncludeBothEndpoints() {
        Menu menu = menuAt(LocalTime.of(9, 0), LocalTime.of(14, 0));

        assertThat(menu.isAvailableAt(DAY.atTime(8, 59, 59))).isFalse();
        assertThat(menu.isAvailableAt(DAY.atTime(9, 0))).isTrue();
        assertThat(menu.isAvailableAt(DAY.atTime(12, 30))).isTrue();
        assertThat(menu.isAvailableAt(DAY.atTime(14, 0))).isTrue();
        assertThat(menu.isAvailableAt(DAY.atTime(14, 0, 1))).isFalse();
    }

    @Test
    void oneSidedHoursBoundOnlyTheirConfiguredSide() {
        Menu startsAtNine = menuAt(LocalTime.of(9, 0), null);
        Menu endsAtTwo = menuAt(null, LocalTime.of(14, 0));

        assertThat(startsAtNine.isAvailableAt(DAY.atTime(8, 59))).isFalse();
        assertThat(startsAtNine.isAvailableAt(DAY.atTime(9, 0))).isTrue();
        assertThat(startsAtNine.isAvailableAt(DAY.atTime(23, 59))).isTrue();
        assertThat(endsAtTwo.isAvailableAt(DAY.atTime(0, 0))).isTrue();
        assertThat(endsAtTwo.isAvailableAt(DAY.atTime(14, 0))).isTrue();
        assertThat(endsAtTwo.isAvailableAt(DAY.atTime(14, 0, 1))).isFalse();
    }

    @Test
    void overnightHoursCrossMidnight() {
        Menu menu = menuAt(LocalTime.of(22, 0), LocalTime.of(2, 0));

        assertThat(menu.isAvailableAt(DAY.atTime(21, 59, 59))).isFalse();
        assertThat(menu.isAvailableAt(DAY.atTime(22, 0))).isTrue();
        assertThat(menu.isAvailableAt(DAY.plusDays(1).atTime(1, 0))).isTrue();
        assertThat(menu.isAvailableAt(DAY.plusDays(1).atTime(2, 0))).isTrue();
        assertThat(menu.isAvailableAt(DAY.plusDays(1).atTime(2, 0, 1))).isFalse();
    }

    @Test
    void overnightHoursAfterMidnightBelongToThePreviousAvailableDate() {
        Menu menu = menuAt(LocalTime.of(22, 0), LocalTime.of(2, 0));
        menu.setAvailableFromDate(DAY);
        menu.setAvailableUntilDate(DAY);

        assertThat(menu.isAvailableAt(DAY.atTime(23, 0))).isTrue();
        assertThat(menu.isAvailableAt(DAY.plusDays(1).atTime(1, 0))).isTrue();
        assertThat(menu.isAvailableAt(DAY.plusDays(2).atTime(1, 0))).isFalse();
    }

    private static Menu menuAt(LocalTime from, LocalTime until) {
        Menu menu = new Menu();
        menu.setAvailableFrom(from);
        menu.setAvailableUntil(until);
        return menu;
    }
}
