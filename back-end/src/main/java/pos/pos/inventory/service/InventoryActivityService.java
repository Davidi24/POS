package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.inventory.dto.InventoryActivityResponse;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.repository.InventoryMovementRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Sums the stock changes of the last days per day for the Inventory overview, counted by the server so the chart
// never depends on how much of the history list the app happened to load.
@Service
@RequiredArgsConstructor
public class InventoryActivityService {

    public static final int MIN_DAYS = 7;
    public static final int MAX_DAYS = 90;

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryMovementRepository inventoryMovementRepository;

    @Transactional(readOnly = true)
    public InventoryActivityResponse activity(Authentication authentication, UUID restaurantId, int days) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        ZoneId zone = zoneOf(restaurant);
        LocalDate to = LocalDate.now(zone);
        LocalDate from = to.minusDays(days - 1L);

        Map<LocalDate, Totals> byDay = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            byDay.put(day, new Totals());
        }
        for (Object[] row : inventoryMovementRepository.dailyCostByType(restaurantId, zone.getId(), from.atStartOfDay(zone).toOffsetDateTime())) {
            Totals totals = byDay.get(LocalDate.parse(String.valueOf(row[0])));
            if (totals == null) {
                continue;
            }
            BigDecimal cost = row[2] instanceof BigDecimal value ? value : new BigDecimal(String.valueOf(row[2]));
            totals.changes += ((Number) row[3]).longValue();
            switch (typeOf(String.valueOf(row[1]))) {
                case PURCHASE, RECEIPT -> totals.received = totals.received.add(cost.max(BigDecimal.ZERO));
                case SALE_CONSUMPTION -> totals.used = totals.used.add(cost.negate().max(BigDecimal.ZERO));
                case WASTE -> totals.wasted = totals.wasted.add(cost.negate().max(BigDecimal.ZERO));
                default -> {
                }
            }
        }

        List<InventoryActivityResponse.Day> list = new ArrayList<>();
        Totals all = new Totals();
        byDay.forEach((day, totals) -> {
            list.add(new InventoryActivityResponse.Day(day, money(totals.received), money(totals.used), money(totals.wasted), totals.changes));
            all.received = all.received.add(totals.received);
            all.used = all.used.add(totals.used);
            all.wasted = all.wasted.add(totals.wasted);
            all.changes += totals.changes;
        });
        return new InventoryActivityResponse(restaurant.getCurrency(), from, to, money(all.received), money(all.used), money(all.wasted),
                all.changes, list);
    }

    private static InventoryMovementType typeOf(String name) {
        try {
            return InventoryMovementType.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return InventoryMovementType.MANUAL_ADJUSTMENT;
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static ZoneId zoneOf(Restaurant restaurant) {
        try {
            return ZoneId.of(restaurant.getTimezone());
        } catch (Exception invalid) {
            return ZoneOffset.UTC;
        }
    }

    private static final class Totals {
        private BigDecimal received = BigDecimal.ZERO;
        private BigDecimal used = BigDecimal.ZERO;
        private BigDecimal wasted = BigDecimal.ZERO;
        private long changes;
    }
}
