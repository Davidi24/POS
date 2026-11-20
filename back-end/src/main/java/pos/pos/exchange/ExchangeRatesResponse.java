package pos.pos.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * The restaurant's own currency, and what one unit of each currency is in [to] (the currency the person also wants to see
 * amounts in). [to], [date] and [rates] are empty when nothing is to be converted.
 */
public record ExchangeRatesResponse(String currency, String to, LocalDate date, Map<String, BigDecimal> rates, String source) { }
