package pos.pos.restaurant.util;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import java.util.Currency;
import java.util.Locale;
/** The current payment/receipt schema supports currencies with zero, one or two fraction digits. */
public final class RestaurantCurrencyPrecision {
 private RestaurantCurrencyPrecision(){}
 public static int scale(String value) {
  try {
   int digits=Currency.getInstance(value.trim().toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
   if(digits<0 || digits>2)throw new IllegalArgumentException();
   return digits;
  }catch(RuntimeException e){throw new AuthException("Use a supported currency with at most two decimal places (for example EUR, ALL, USD or JPY).",HttpStatus.BAD_REQUEST);}
 }
}
