package pos.pos.restaurant.currency;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.currency.CurrencyChangeDtos.ApplyRequest;
import pos.pos.restaurant.currency.CurrencyChangeDtos.ChangeRecord;
import pos.pos.restaurant.currency.CurrencyChangeDtos.Group;
import pos.pos.restaurant.currency.CurrencyChangeDtos.Item;
import pos.pos.restaurant.currency.CurrencyChangeDtos.Preview;
import pos.pos.restaurant.currency.CurrencyChangeDtos.PreviewRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.RestaurantUsageRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Changes a restaurant's currency. Every amount that has no currency of its own (dish, variant and option prices,
 * wages, stock costs, the deposit and fixed fees) is converted at the rate the super admin enters, rounded to the
 * chosen step, and can be corrected one by one before confirming. Past orders, payments and booking money keep the
 * currency they were made in and are never rewritten. Blocked while anything priced in the old currency is still open.
 */
@Service
@RequiredArgsConstructor
public class RestaurantCurrencyService {

    static final Set<BigDecimal> ROUNDING_STEPS = Set.of(
            new BigDecimal("0.01"), new BigDecimal("0.05"), new BigDecimal("0.10"), new BigDecimal("0.50"),
            new BigDecimal("1"), new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("50"), new BigDecimal("100"));

    private static final String ONLY_SUPER_ADMIN = "Only the Super Admin can change the currency";

    // Kinds of amounts, with the largest value their column (or its rule) accepts.
    private static final String DISH = "DISH_PRICE";
    private static final String VARIANT = "VARIANT_PRICE";
    private static final String OPTION = "OPTION_PRICE";
    private static final String PAY = "HOURLY_WAGE";
    private static final String SALARY = "MONTHLY_SALARY";
    private static final String STOCK = "STOCK_COST";
    private static final String SUPPLIER_PRICE = "SUPPLIER_PRICE";
    private static final String DEPOSIT = "BOOKING_DEPOSIT";
    private static final String SETTING = "SETTING";
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999999999999.99");
    private static final BigDecimal MAX_PAY = new BigDecimal("10000000");
    private static final BigDecimal MAX_SMALL_SETTING = new BigDecimal("1000000");
    private static final BigDecimal MAX_DEPOSIT = new BigDecimal("9999999999.99");

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantUsageRepository restaurantUsageRepository;
    private final RestaurantCurrencyChangeRepository changeRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final pos.pos.restaurant.service.RestaurantChangeGuard changes;
    private final pos.pos.recipe.service.RecipeService recipeService;
    private final jakarta.persistence.EntityManager entities;

    @Value("${spring.jpa.properties.hibernate.default_schema:public}")
    private String schemaName;

    @Transactional(readOnly = true)
    public List<ChangeRecord> history(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return changeRepository.findAllByRestaurantIdOrderByChangedAtDesc(restaurantId).stream()
                .map(change -> new ChangeRecord(change.getId(), change.getOldCurrency(), change.getNewCurrency(), change.getRate(),
                        change.getRoundingStep(), change.getValuesConverted(), change.getChangedAt(), change.getChangedBy(),
                        nameOf(change.getChangedBy())))
                .toList();
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Preview preview(Authentication authentication, UUID restaurantId, PreviewRequest request) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, ONLY_SUPER_ADMIN);
        String newCurrency = checkedCurrency(restaurant, request.newCurrency());
        BigDecimal step = checkedStep(request.roundingStep(), newCurrency);
        List<Group> groups = convert(restaurantId, request.rate(), step, Map.of(), newCurrency);
        List<String> blockers = blockers(restaurantId);
        boolean problems = groups.stream().flatMap(group -> group.items().stream()).anyMatch(item -> item.problem() != null);
        return new Preview(restaurant.getCurrency(), newCurrency, request.rate(), step, blockers.isEmpty() && !problems, blockers, groups,
                groups.stream().mapToInt(group -> group.items().size()).sum(), countRecipes(restaurantId), fingerprint(restaurant,newCurrency,request.rate(),step,groups));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public Preview apply(Authentication authentication, UUID restaurantId, ApplyRequest request) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, ONLY_SUPER_ADMIN);
        changes.lock(restaurant);
        Preview replay=changes.replay(restaurantId,"CURRENCY",request.previewToken(),request,Preview.class);
        if(replay!=null)return replay;
        String oldCurrency = restaurant.getCurrency();
        String newCurrency = checkedCurrency(restaurant, request.newCurrency());
        BigDecimal step = checkedStep(request.roundingStep(), newCurrency);
        List<String> blockers = blockers(restaurantId);
        if (!blockers.isEmpty()) {
            throw new AuthException("Settle these first: " + String.join(", ", blockers), HttpStatus.CONFLICT);
        }

        List<Group> original=convert(restaurantId,request.rate(),step,Map.of(),newCurrency);
        changes.requireCurrent(request.previewToken(),fingerprint(restaurant,newCurrency,request.rate(),step,original));
        Map<String, BigDecimal> overrides = new HashMap<>();
        if (request.overrides() != null) {
            request.overrides().forEach(override -> {
                if(overrides.put(override.kind()+":"+override.id(),override.value())!=null)
                    throw new AuthException("A correction was supplied more than once",HttpStatus.BAD_REQUEST);
            });
        }
        List<Group> groups = convert(restaurantId, request.rate(), step, overrides, newCurrency);
        List<Item> items = groups.stream().flatMap(group -> group.items().stream()).toList();
        Set<String> known = new java.util.HashSet<>();
        items.forEach(item -> known.add(item.kind() + ":" + item.id()));
        overrides.keySet().stream().filter(key -> !known.contains(key)).findFirst().ifPresent(key -> {
            throw new AuthException("Something changed since the preview (" + key + "). Look at the preview again.", HttpStatus.CONFLICT);
        });
        items.stream().filter(item -> item.problem() != null).findFirst().ifPresent(item -> {
            throw new AuthException(item.name() + ": " + item.problem(), HttpStatus.BAD_REQUEST);
        });

        entities.flush();
        save(restaurantId, items, request.rate());
        // JDBC changed ingredient costs: discard the old persistence context before calculating the recipe graph.
        entities.clear();
        recipeService.recalculateAllCosts(restaurantId,restaurantScopeService.currentUserId(authentication),
                pos.pos.restaurant.util.RestaurantCurrencyPrecision.scale(newCurrency));
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("update " + schema() + "restaurants set currency = :currency, version = version + 1, updated_at = :now, updated_by = :actor where id = :r",
                Map.of("currency", newCurrency, "now", now, "actor", actorId, "r", restaurantId));

        RestaurantCurrencyChange change = new RestaurantCurrencyChange();
        change.setRestaurantId(restaurantId);
        change.setOldCurrency(oldCurrency);
        change.setNewCurrency(newCurrency);
        change.setRate(request.rate());
        change.setRoundingStep(step);
        change.setValuesConverted(items.size());
        change.setChangedAt(now);
        change.setChangedBy(actorId);
        changeRepository.save(change);

        Preview result=new Preview(oldCurrency,newCurrency,request.rate(),step,true,List.of(),groups,items.size(),countRecipes(restaurantId),request.previewToken());
        changes.completed(restaurantId,"CURRENCY",request.previewToken(),request,result);
        return result;
    }

    // ---- What's converted ----

    private List<Group> convert(UUID restaurantId, BigDecimal rate, BigDecimal step, Map<String, BigDecimal> overrides, String currency) {
        Map<String, Object> p = Map.of("r", restaurantId);
        String s = schema();
        Converter price = new Converter(rate, step, pos.pos.restaurant.util.RestaurantCurrencyPrecision.scale(currency), overrides);
        Converter exact4 = new Converter(rate, null, 4, overrides);
        Converter exact2 = new Converter(rate, null, pos.pos.restaurant.util.RestaurantCurrencyPrecision.scale(currency), overrides);

        List<Group> groups = new ArrayList<>();
        groups.add(new Group("dishes", "Dish prices", null, jdbc.query(
                "select mi.id, mi.name, m.name menu_name, ms.name section_name, mi.base_price from " + s + "\"menu-items\" mi"
                        + " join " + s + "\"menu-sections\" ms on ms.id = mi.section_id join " + s + "menus m on m.id = ms.menu_id"
                        + " where m.restaurant_id = :r order by m.name, ms.name, mi.name", p,
                (row, i) -> price.item(DISH, row.getString("id"), row.getString("name"),
                        row.getString("menu_name") + " · " + row.getString("section_name"), row.getBigDecimal("base_price"), BigDecimal.ZERO, MAX_PRICE))));
        groups.add(new Group("variants", "Size and variant prices", "Added to the dish price", jdbc.query(
                "select mv.id, mv.name, mi.name item_name, mv.price_delta from " + s + "\"menu-variants\" mv"
                        + " join " + s + "\"menu-items\" mi on mi.id = mv.menu_item_id join " + s + "\"menu-sections\" ms on ms.id = mi.section_id"
                        + " join " + s + "menus m on m.id = ms.menu_id where m.restaurant_id = :r and mv.price_delta is not null and mv.price_delta <> 0"
                        + " order by mi.name, mv.name", p,
                (row, i) -> price.item(VARIANT, row.getString("id"), row.getString("name"), row.getString("item_name"),
                        row.getBigDecimal("price_delta"), MAX_PRICE.negate(), MAX_PRICE))));
        groups.add(new Group("options", "Extras and options", "Added to the dish price", jdbc.query(
                "select oi.id, oi.name, og.name group_name, oi.price_delta from " + s + "\"option-items\" oi"
                        + " join " + s + "\"option-groups\" og on og.id = oi.option_group_id"
                        + " where og.restaurant_id = :r and oi.price_delta is not null and oi.price_delta <> 0 order by og.name, oi.name", p,
                (row, i) -> price.item(OPTION, row.getString("id"), row.getString("name"), row.getString("group_name"),
                        row.getBigDecimal("price_delta"), MAX_PRICE.negate(), MAX_PRICE))));
        groups.add(new Group("wages", "Hourly wages", "Shifts already worked keep the wage they had", jdbc.query(
                "select spr.user_id, trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) person, u.username, spr.hourly_rate"
                        + " from " + s + "staff_pay_rates spr join " + s + "users u on u.id = spr.user_id"
                        + " where spr.restaurant_id = :r and spr.hourly_rate is not null order by person", p,
                (row, i) -> price.item(PAY, row.getString("user_id"), blankOr(row.getString("person"), row.getString("username")), null,
                        row.getBigDecimal("hourly_rate"), BigDecimal.ZERO, MAX_PAY))));
        groups.add(new Group("salaries", "Monthly salaries", "Months already paid keep what they were", jdbc.query(
                "select spr.user_id, trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) person, u.username, spr.monthly_salary"
                        + " from " + s + "staff_pay_rates spr join " + s + "users u on u.id = spr.user_id"
                        + " where spr.restaurant_id = :r and spr.monthly_salary is not null order by person", p,
                (row, i) -> price.item(SALARY, row.getString("user_id"), blankOr(row.getString("person"), row.getString("username")), null,
                        row.getBigDecimal("monthly_salary"), BigDecimal.ZERO, MAX_PAY))));
        groups.add(new Group("stock", "Stock costs", "Cost per unit; recipe costs follow them", jdbc.query(
                "select ii.id, ii.name, ii.base_unit, ii.cost_per_unit from " + s + "inventory_items ii"
                        + " where ii.restaurant_id = :r and ii.deleted_at is null and ii.cost_per_unit <> 0 order by ii.name", p,
                (row, i) -> exact4.item(STOCK, row.getString("id"), row.getString("name"), row.getString("base_unit") == null ? null : "per " + row.getString("base_unit").toLowerCase(Locale.ROOT),
                        row.getBigDecimal("cost_per_unit"), BigDecimal.ZERO, MAX_PRICE))));
        groups.add(new Group("supplierPrices", "Supplier prices", "What each supplier charges per unit of an item", jdbc.query(
                "select si.id, ii.name, sp.name supplier_name, si.price from " + s + "supplier_items si"
                        + " join " + s + "suppliers sp on sp.id = si.supplier_id join " + s + "inventory_items ii on ii.id = si.inventory_item_id"
                        + " where sp.restaurant_id = :r and sp.deleted_at is null and ii.deleted_at is null and si.price is not null and si.price <> 0"
                        + " order by sp.name, ii.name", p,
                (row, i) -> exact4.item(SUPPLIER_PRICE, row.getString("id"), row.getString("name"), "from " + row.getString("supplier_name"),
                        row.getBigDecimal("price"), BigDecimal.ZERO, MAX_PRICE))));

        List<Item> settings = new ArrayList<>();
        jdbc.query("select card_fee_fixed, cash_rounding_enabled, cash_rounding_increment, fraud_refund_amount, service_charge_type,"
                + " service_charge_value, valuable_stock_from from " + s + "settings where restaurant_id = :r", p, row -> {
            settings.add(price.item(SETTING, "card_fee_fixed", "Card fee (fixed part)", "Kept from refunds of booking money",
                    row.getBigDecimal("card_fee_fixed"), BigDecimal.ZERO, MAX_SMALL_SETTING));
            settings.add(price.item(SETTING, "fraud_refund_amount", "Large refund alert", "Fraud Detection flags refunds above this",
                    row.getBigDecimal("fraud_refund_amount"), BigDecimal.ZERO, MAX_SMALL_SETTING));
            settings.add(price.item(SETTING, "valuable_stock_from", "Valuable stock from", "Inventory shows stock worth this much as valuable",
                    row.getBigDecimal("valuable_stock_from"), BigDecimal.ZERO, MAX_SMALL_SETTING));
            if ("FIXED_AMOUNT".equals(row.getString("service_charge_type")) && row.getBigDecimal("service_charge_value") != null) {
                settings.add(price.item(SETTING, "service_charge_value", "Service charge", "Fixed amount per order",
                        row.getBigDecimal("service_charge_value"), BigDecimal.ZERO, MAX_DEPOSIT));
            }
            if (row.getBigDecimal("cash_rounding_increment") != null) {
                settings.add(exact2.item(SETTING, "cash_rounding_increment", "Cash rounding", "Cash totals round to this",
                        row.getBigDecimal("cash_rounding_increment"), new BigDecimal("0.01"), MAX_DEPOSIT));
            }
        });
        settings.addAll(jdbc.query("select rr.id, rr.rule_name, rr.deposit_value from " + s + "\"settings-reservation-rules\" rr"
                        + " join " + s + "settings st on st.id = rr.settings_id where st.restaurant_id = :r and rr.deposit_type = 'FIXED_AMOUNT'"
                        + " and rr.deposit_value is not null order by rr.rule_name", p,
                (row, i) -> price.item(DEPOSIT, row.getString("id"), "Booking deposit", row.getString("rule_name"),
                        row.getBigDecimal("deposit_value"), BigDecimal.ZERO, MAX_DEPOSIT)));
        groups.add(new Group("settings", "Deposits, fees and alerts", null, settings));
        return groups.stream().filter(group -> !group.items().isEmpty()).toList();
    }

    private void save(UUID restaurantId, List<Item> items, BigDecimal rate) {
        String s = schema();
        Map<String, String> sql = Map.of(
                DISH, "update " + s + "\"menu-items\" set base_price = :value where id = cast(:id as uuid)",
                VARIANT, "update " + s + "\"menu-variants\" set price_delta = :value where id = cast(:id as uuid)",
                OPTION, "update " + s + "\"option-items\" set price_delta = :value where id = cast(:id as uuid)",
                PAY, "update " + s + "staff_pay_rates set hourly_rate = :value, updated_at = now() where user_id = cast(:id as uuid) and restaurant_id = :r",
                SALARY, "update " + s + "staff_pay_rates set monthly_salary = :value, updated_at = now() where user_id = cast(:id as uuid) and restaurant_id = :r",
                STOCK, "update " + s + "inventory_items set cost_per_unit = :value where id = cast(:id as uuid) and restaurant_id = :r",
                SUPPLIER_PRICE, "update " + s + "supplier_items set price = :value, updated_at = now() where id = cast(:id as uuid)"
                        + " and supplier_id in (select id from " + s + "suppliers where restaurant_id = :r)",
                DEPOSIT, "update " + s + "\"settings-reservation-rules\" set deposit_value = :value where id = cast(:id as uuid)");
        Map<String, List<Map<String, Object>>> batches = new LinkedHashMap<>();
        for (Item item : items) {
            if (SETTING.equals(item.kind())) {
                // Column names come from the fixed list in convert(), never from the request.
                jdbc.update("update " + s + "settings set " + item.id() + " = :value where restaurant_id = :r",
                        Map.of("value", item.newValue(), "r", restaurantId));
                continue;
            }
            Map<String, Object> params = new HashMap<>();
            params.put("value", item.newValue());
            params.put("id", item.id());
            params.put("r", restaurantId);
            batches.computeIfAbsent(item.kind(), key -> new ArrayList<>()).add(params);
        }
        batches.forEach((kind, rows) -> jdbc.batchUpdate(sql.get(kind), rows.toArray(Map[]::new)));

    }

    /** Converts at the rate, rounds, applies the super admin's correction and checks the limits. */
    private record Converter(BigDecimal rate, BigDecimal step, int scale, Map<String, BigDecimal> overrides) {
        Item item(String kind, String id, String name, String detail, BigDecimal oldValue, BigDecimal min, BigDecimal max) {
            BigDecimal old = oldValue == null ? BigDecimal.ZERO : oldValue;
            BigDecimal converted = old.multiply(rate);
            converted = step == null
                    ? converted.setScale(scale, RoundingMode.HALF_UP)
                    : converted.divide(step, 0, RoundingMode.HALF_UP).multiply(step).setScale(scale, RoundingMode.HALF_UP);
            BigDecimal corrected = overrides.get(kind + ":" + id);
            BigDecimal value = corrected == null ? converted : corrected.setScale(scale, RoundingMode.HALF_UP);
            String problem = corrected!=null && corrected.stripTrailingZeros().scale()>scale ? "must have at most "+scale+" decimal places" : null;
            if (value.compareTo(min) < 0) problem = "must be at least " + min.stripTrailingZeros().toPlainString();
            else if (value.compareTo(max) > 0) problem = "must be at most " + max.stripTrailingZeros().toPlainString();
            return new Item(kind, id, name, detail, old, value, problem);
        }
    }

    // ---- Checks ----

    private List<String> blockers(UUID restaurantId) {
        var money = restaurantUsageRepository.moneyInFlight(restaurantId, OffsetDateTime.now(ZoneOffset.UTC));
        List<String> blockers = new ArrayList<>();
        if (money.openOrders() > 0) blockers.add(plural(money.openOrders(), "open order", "open orders") + " (close or cancel them)");
        if (money.openShifts() > 0) blockers.add(plural(money.openShifts(), "person is", "people are") + " clocked in");
        if (money.pendingBookingPayments() > 0) blockers.add(plural(money.pendingBookingPayments(), "booking payment is", "booking payments are") + " waiting to be paid");
        if (money.depositsOnUpcomingBookings() > 0) blockers.add(plural(money.depositsOnUpcomingBookings(), "upcoming booking has", "upcoming bookings have") + " a deposit in the old currency");
        if (money.waitingPreOrders() > 0) blockers.add(plural(money.waitingPreOrders(), "paid pre-order is", "paid pre-orders are") + " waiting for its booking");
        return blockers;
    }

    private String checkedCurrency(Restaurant restaurant, String requested) {
        String code = requested == null ? "" : requested.trim().toUpperCase(Locale.ROOT);
        try {
            pos.pos.restaurant.util.RestaurantCurrencyPrecision.scale(code);
        } catch (RuntimeException ex) {
            throw new AuthException("Unknown currency " + code + ". Use a 3-letter code such as EUR, ALL or USD.", HttpStatus.BAD_REQUEST);
        }
        if (code.equalsIgnoreCase(restaurant.getCurrency())) {
            throw new AuthException("The restaurant already uses " + code, HttpStatus.BAD_REQUEST);
        }
        return code;
    }

    private static BigDecimal checkedStep(BigDecimal step, String currency) {
        int scale=pos.pos.restaurant.util.RestaurantCurrencyPrecision.scale(currency);
        if(step==null || step.stripTrailingZeros().scale()>scale)throw new AuthException("Choose a rounding step that fits "+currency+" ("+scale+" decimal places)",HttpStatus.BAD_REQUEST);
        return ROUNDING_STEPS.stream().filter(allowed -> allowed.compareTo(step) == 0).findFirst()
                .orElseThrow(() -> new AuthException("roundingStep must be one of 0.01, 0.05, 0.10, 0.50, 1, 5, 10, 50 or 100", HttpStatus.BAD_REQUEST));
    }

    private String fingerprint(Restaurant restaurant,String currency,BigDecimal rate,BigDecimal step,List<Group> groups) {
        return changes.hash(restaurant.getId(),restaurant.getCurrency(),restaurant.getUpdatedAt(),currency,rate.stripTrailingZeros(),step.stripTrailingZeros(),groups);
    }

    private long countRecipes(UUID restaurantId) {
        Long count = jdbc.queryForObject("select count(*) from " + schema() + "recipes where restaurant_id = :r",
                Map.of("r", restaurantId), Long.class);
        return count == null ? 0 : count;
    }

    private String nameOf(UUID userId) {
        if (userId == null) return null;
        return jdbc.query("select trim(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) name, username from " + schema()
                        + "users where id = :id", Map.of("id", userId),
                (row, i) -> blankOr(row.getString("name"), row.getString("username"))).stream().findFirst().orElse(null);
    }

    private String schema() {
        if (!schemaName.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalStateException("Invalid schema");
        return "\"" + schemaName + "\".";
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }
}
