package pos.pos.restaurant.currency;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Restaurants")
@RestController
@RequestMapping("/restaurants/{restaurantId}/currency")
@RequiredArgsConstructor
public class RestaurantCurrencyController {

    private final RestaurantCurrencyService currencyService;

    @GetMapping("/changes")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Earlier currency changes, newest first")
    public ResponseEntity<List<CurrencyChangeDtos.ChangeRecord>> history(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(currencyService.history(authentication, restaurantId));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: every price converted to a new currency, and what must be settled first")
    public ResponseEntity<CurrencyChangeDtos.Preview> preview(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CurrencyChangeDtos.PreviewRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(currencyService.preview(authentication, restaurantId, request));
    }

    @PostMapping("/change")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: change the currency and convert every price (with corrections from the preview)")
    public ResponseEntity<CurrencyChangeDtos.Preview> change(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CurrencyChangeDtos.ApplyRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(currencyService.apply(authentication, restaurantId, request));
    }
}
