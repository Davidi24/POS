package pos.pos.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Today's exchange rates, only so people can see roughly what an amount is in a currency they know (user, 2026-11-19:
 * "just to see how much that is in dollars"). Nothing is ever charged, paid or saved with them: payments stay in the
 * restaurant's own currency. Rates come from Frankfurter (central bank reference rates, once a working day, no key) and are
 * kept for 6 hours; when it doesn't answer, the last ones are used for up to 3 days.
 */
@Slf4j
@Service
public class ExchangeRateService {

    private static final Duration FRESH = Duration.ofHours(6);
    private static final Duration KEEP = Duration.ofDays(3);
    private static final Set<String> ISO_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode).collect(Collectors.toUnmodifiableSet());

    private final ObjectMapper json;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Rates> kept = new ConcurrentHashMap<>();

    public ExchangeRateService(ObjectMapper json, @Value("${app.exchange-rates.url:https://api.frankfurter.dev/v2}") String baseUrl) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** What one unit of each currency is in [to] ("EUR" → 1.099 for to = USD), with the rates' date. */
    public record Rates(String to, LocalDate date, Map<String, BigDecimal> oneIs, Instant fetchedAt) { }

    public static String currencyCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ISO_CODES.contains(code)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a currency");
        return code;
    }

    public Rates ratesIn(String to) {
        String code = currencyCode(to);
        Instant now = Instant.now();
        Rates last = kept.get(code);
        if (last != null && last.fetchedAt().plus(FRESH).isAfter(now)) return last;
        try {
            Rates fresh = fetch(code, now);
            kept.put(code, fresh);
            return fresh;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return lastOrRefuse(last, now, e);
        } catch (IOException | RuntimeException e) {
            return lastOrRefuse(last, now, e);
        }
    }

    private Rates lastOrRefuse(Rates last, Instant now, Exception cause) {
        log.warn("Exchange rates could not be loaded: {}", cause.getMessage());
        if (last != null && last.fetchedAt().plus(KEEP).isAfter(now)) return last;
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Exchange rates aren't available right now.");
    }

    private Rates fetch(String to, Instant now) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/rates?base=" + to))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("the rate service answered " + response.statusCode());
        // [{"date":"2026-11-19","base":"USD","quote":"EUR","rate":0.91}, …]: how many of each quote one [to] buys.
        Map<String, BigDecimal> oneIs = new TreeMap<>();
        oneIs.put(to, BigDecimal.ONE);
        LocalDate date = null;
        for (JsonNode row : json.readTree(response.body())) {
            String quote = row.path("quote").asText("");
            BigDecimal rate = row.path("rate").isNumber() ? row.path("rate").decimalValue() : BigDecimal.ZERO;
            if (!ISO_CODES.contains(quote) || rate.signum() <= 0) continue;
            oneIs.put(quote, BigDecimal.ONE.divide(rate, 10, RoundingMode.HALF_UP).stripTrailingZeros());
            LocalDate day = parseDay(row.path("date").asText(null));
            if (day != null && (date == null || day.isAfter(date))) date = day;
        }
        if (oneIs.size() < 2) throw new IOException("the rate service sent no rates");
        return new Rates(to, date, Map.copyOf(oneIs), now);
    }

    private static LocalDate parseDay(String text) {
        try {
            return text == null ? null : LocalDate.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
