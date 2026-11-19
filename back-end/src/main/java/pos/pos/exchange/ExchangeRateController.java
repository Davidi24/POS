package pos.pos.exchange;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.scope.ActorScopeService;

import java.util.Map;
import java.util.UUID;

/** Exchange rates to see amounts in another currency on one's own device (user, 2026-11-19); any signed-in person. */
@Tag(name = "Exchange rates")
@RestController
@RequestMapping("/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;
    private final ActorScopeService actorScopeService;
    private final RestaurantRepository restaurantRepository;

    @GetMapping
    @Operation(summary = "The restaurant's currency and today's rates into another currency (only for showing, never for payments)")
    public ExchangeRatesResponse rates(@RequestParam(required = false) String to, Authentication authentication) {
        UUID restaurantId = actorScopeService.resolve(authentication).restaurantId();
        String currency = restaurantId == null ? null : restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .map(Restaurant::getCurrency).map(String::trim).orElse(null);
        if (to == null || to.isBlank()) return new ExchangeRatesResponse(currency, null, null, Map.of(), null);
        String target = ExchangeRateService.currencyCode(to);
        if (target.equals(currency)) return new ExchangeRatesResponse(currency, target, null, Map.of(), null);
        ExchangeRateService.Rates rates = exchangeRateService.ratesIn(target);
        return new ExchangeRatesResponse(currency, target, rates.date(), rates.oneIs(), "Frankfurter");
    }
}
