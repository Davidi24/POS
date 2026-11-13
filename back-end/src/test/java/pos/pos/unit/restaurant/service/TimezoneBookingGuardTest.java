package pos.pos.unit.restaurant.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pos.pos.restaurant.service.TimezoneBookingGuard;
import pos.pos.restaurant.util.RestaurantCurrencyPrecision;
import pos.pos.exception.auth.AuthException;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class TimezoneBookingGuardTest {
 @ParameterizedTest @CsvSource({"2030-03-31T02:30:00Z,Europe/Berlin","2030-10-27T02:30:00Z,Europe/Berlin","2030-03-10T02:30:00Z,America/New_York","2030-11-03T01:30:00Z,America/New_York"})
 void refusesMissingAndRepeatedLocalTimes(String value,String target){
  assertThatThrownBy(()->TimezoneBookingGuard.keepClock(OffsetDateTime.parse(value),ZoneOffset.UTC,ZoneId.of(target)))
   .isInstanceOfSatisfying(AuthException.class,e->assertThat(e.getStatus().value()).isEqualTo(409));
 }
 @ParameterizedTest @CsvSource({"2030-02-01T20:00:00Z,Europe/Berlin,+01:00","2030-07-01T20:00:00Z,Europe/Berlin,+02:00","2030-07-01T20:00:00Z,Asia/Kathmandu,+05:45"})
 void keepsClockAcrossNormalAndFractionalOffsets(String value,String target,String offset){
  var source=OffsetDateTime.parse(value);var moved=TimezoneBookingGuard.keepClock(source,ZoneOffset.UTC,ZoneId.of(target));
  assertThat(moved.toLocalDateTime()).isEqualTo(source.toLocalDateTime());assertThat(moved.getOffset()).isEqualTo(ZoneOffset.of(offset));
 }
 @Test void usesCurrencyMinorUnitsAndRejectsUnsupportedPrecision(){
  assertThat(RestaurantCurrencyPrecision.scale("EUR")).isEqualTo(2);assertThat(RestaurantCurrencyPrecision.scale("jpy")).isZero();
  for(String code:new String[]{"KWD","XXX","ZZZ"})assertThatThrownBy(()->RestaurantCurrencyPrecision.scale(code)).isInstanceOf(AuthException.class);
 }
}
