package pos.pos.integration.robustness;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.integration.support.RequestSamples;
import pos.pos.integration.support.RequestSamples.Variant;
import pos.pos.storage.FloorPlanImageController;
import pos.pos.user.entity.User;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls every endpoint of the API with hostile input — names thousands of characters long, numbers far out of range,
 * wrong types, nulls, broken JSON, ids that don't exist or aren't ids — as a super admin who passes every permission
 * check, so the input reaches the services. Whatever the input, the server must answer with a clear 4xx (or a 2xx when
 * the input happens to be fine), never a 500.
 */
@DisplayName("API robustness: no endpoint answers 500 to bad input")
class ApiRobustnessIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final Pattern VARIABLE = Pattern.compile("\\{([^}/]+)}");
    private static final Set<String> SKIPPED_PATH_PARTS = Set.of(
            "/auth", "/error", "/v3/api-docs", "/swagger", "logout", "/sessions", "password", "/.well-known");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final Map<String, String> ids = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final List<String> expectedStorageUnavailable = new ArrayList<>();
    private int calls;

    @Override
    protected boolean isExpectedServerError(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        Object matchedHandler = result.getRequest().getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler");
        return result.getResponse().getStatus() == 503
                && "GET".equals(result.getRequest().getMethod())
                && result.getRequest().getRequestURI().startsWith("/public/floor-plan-images/")
                && matchedHandler instanceof HandlerMethod handler
                && handler.getBeanType() == FloorPlanImageController.class
                && result.getResponse().getContentAsString().contains("Floor-plan image storage is not configured");
    }

    @Test
    @DisplayName("every endpoint survives hostile input without a server error")
    void everyEndpointSurvivesHostileInput() throws Exception {
        String token = seed();

        List<Map.Entry<RequestMappingInfo, HandlerMethod>> endpoints = new ArrayList<>(handlerMapping.getHandlerMethods().entrySet());
        // Deletes last, so they don't remove the data the other calls use.
        endpoints.sort((a, b) -> Boolean.compare(isDelete(a.getKey()), isDelete(b.getKey())));

        for (var endpoint : endpoints) {
            RequestMappingInfo info = endpoint.getKey();
            HandlerMethod handler = endpoint.getValue();
            if (skipped(info, handler)) {
                continue;
            }
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                methods = Set.of(org.springframework.web.bind.annotation.RequestMethod.GET);
            }
            for (String pattern : info.getPatternValues()) {
                for (var method : methods) {
                    exercise(token, HttpMethod.valueOf(method.name()), pattern, handler);
                }
            }
        }

        System.out.println("API robustness: " + calls + " requests checked");
        assertThat(calls).as("requests made").isGreaterThan(2000);
        assertThat(expectedStorageUnavailable)
                .as("only the two valid-path floor-plan reads should report unconfigured production storage")
                .hasSize(2);
        assertThat(failures).as("unexpected server errors").isEmpty();
    }

    private String seed() throws Exception {
        World world = newWorld("robust");
        String token = superAdminToken();
        Map<String, UUID> menu = menu(world, "Fuzz menu");
        UUID itemId = item(world, menu, "Fuzz dish", "9.50");
        JsonNode order = order(world, world.ownerToken(), Map.of(itemId, 2));
        JsonNode lineItem = order.get("lineItems") == null || order.get("lineItems").isEmpty() ? null : order.get("lineItems").get(0);
        JsonNode payment = post("/restaurants/{r}/orders/{o}/payments", world.restaurantId(), id(order)).as(world.ownerToken())
                .body(Map.of("method", "CARD", "amount", new BigDecimal("5.00"))).expect(201);
        User dummy = staff(world, "dummy", "WAITER");

        ids.put("restaurantId", world.restaurantId().toString());
        ids.put("sourceRestaurantId", world.restaurantId().toString());
        ids.put("branchId", world.branchId().toString());
        ids.put("menuId", menu.get("menuId").toString());
        ids.put("sectionId", menu.get("sectionId").toString());
        ids.put("itemId", itemId.toString());
        ids.put("menuItemId", itemId.toString());
        ids.put("orderId", id(order).toString());
        ids.put("orderNumber", order.get("orderNumber").asText());
        if (lineItem != null) {
            ids.put("lineItemId", lineItem.get("id").asText());
        }
        ids.put("paymentId", payment.get("payment").get("id").asText());
        ids.put("userId", dummy.getId().toString());
        ids.put("restaurantSlug", world.restaurant().getSlug());
        ids.put("slug", world.restaurant().getSlug());
        ids.put("branchCode", world.branch().getCode());
        ids.put("dayOfWeek", "MONDAY");
        ids.put("alertKey", "LARGE_DISCOUNT:" + UUID.randomUUID());

        Response table = post("/restaurants/{r}/branches/{b}/tables", world.restaurantId(), world.branchId()).as(token)
                .body(Map.of("tableNumber", "F1", "capacity", 4, "floor", "Main", "positionX", 10, "positionY", 10)).send();
        if (table.status() == 201 || table.status() == 200) {
            ids.put("tableId", table.json().get("id").asText());
            ids.put("tableCode", "F1");
        }
        Response booking = post("/restaurants/{r}/reservations", world.restaurantId()).as(token).body(Map.of(
                "branchId", world.branchId(),
                "partySize", 2,
                "reservationStart", OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withHour(19).withMinute(0).withSecond(0).withNano(0).toString(),
                "contactName", "Fuzz Guest",
                "contactPhone", "+390000000000")).send();
        if (booking.status() == 201 || booking.status() == 200) {
            ids.put("reservationId", booking.json().get("id").asText());
            JsonNode code = booking.json().get("reservationCode");
            if (code != null && !code.isNull()) {
                ids.put("reservationCode", code.asText());
            }
        }
        return token;
    }

    private void exercise(String token, HttpMethod method, String pattern, HandlerMethod handler) throws Exception {
        Class<?> bodyType = null;
        List<MethodParameter> requestParams = new ArrayList<>();
        for (MethodParameter parameter : handler.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                bodyType = parameter.getParameterType();
            }
            if (parameter.hasParameterAnnotation(RequestParam.class)) {
                requestParams.add(parameter);
            }
        }

        // Real ids with each kind of hostile body (or hostile query values when there is no body).
        for (Variant variant : Variant.values()) {
            if (bodyType == null && requestParams.isEmpty() && variant != Variant.TYPICAL) {
                continue;
            }
            send(token, method, fill(pattern, false), bodyType, requestParams, variant, handler);
        }
        // Ids that don't exist, and values that aren't ids at all.
        if (VARIABLE.matcher(pattern).find()) {
            send(token, method, fill(pattern, true), bodyType, requestParams, Variant.TYPICAL, handler);
            send(token, method, pattern.replaceAll("\\{[^}/]+}", "not-an-id-%27%22%3B--"), bodyType, requestParams, Variant.TYPICAL, handler);
            send(token, method, pattern.replaceAll("\\{[^}/]+}", "A".repeat(600)), bodyType, requestParams, Variant.TYPICAL, handler);
        }
    }

    private void send(String token, HttpMethod method, String path, Class<?> bodyType, List<MethodParameter> requestParams,
                      Variant variant, HandlerMethod handler) throws Exception {
        Call call = call(method, path).as(token);
        for (MethodParameter parameter : requestParams) {
            RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
            String name = annotation != null && !annotation.name().isEmpty() ? annotation.name()
                    : annotation != null && !annotation.value().isEmpty() ? annotation.value() : parameter.getParameterName();
            if (name == null || Map.class.isAssignableFrom(parameter.getParameterType())) {
                continue;
            }
            Object value = queryValue(name, parameter.getParameterType(), variant);
            if (value != null) {
                call.param(name, value);
            }
        }
        if (bodyType != null) {
            String body = switch (variant) {
                case BROKEN_JSON -> "{\"name\": \"x\", ";
                case EMPTY_OBJECT -> "{}";
                default -> objectMapper.writeValueAsString(RequestSamples.sample(bodyType, variant, 0, ids));
            };
            call.body(body);
        }
        Response response;
        try {
            response = call.send();
        } catch (Exception thrown) {
            failures.add(method + " " + path + " [" + variant + "] threw " + thrown);
            return;
        }
        calls++;
        if (isExpectedStorageUnavailable(response, handler)) {
            expectedStorageUnavailable.add(path);
            return;
        }
        if (response.status() >= 500) {
            failures.add(method + " " + path + " [" + variant + "] -> " + response.status() + " " + abbreviate(response.text())
                    + " (" + handler.getShortLogMessage() + ")");
        }
    }

    private boolean isExpectedStorageUnavailable(Response response, HandlerMethod handler) throws Exception {
        return response.status() == 503
                && handler.getBeanType() == FloorPlanImageController.class
                && response.text().contains("Floor-plan image storage is not configured");
    }

    private String fill(String pattern, boolean randomIds) {
        Matcher matcher = VARIABLE.matcher(pattern);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).split(":")[0];
            String value = randomIds ? (name.toLowerCase().endsWith("id") ? UUID.randomUUID().toString() : "missing-" + name)
                    : ids.getOrDefault(name, name.toLowerCase().endsWith("id") ? UUID.randomUUID().toString() : "x");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object queryValue(String name, Class<?> type, Variant variant) {
        boolean hostile = variant == Variant.HUGE_TEXT || variant == Variant.WRONG_TYPES;
        if (type == String.class) {
            return hostile ? "Ω'\";--" + "q".repeat(5000) : "a";
        }
        if (type == UUID.class) {
            return variant == Variant.WRONG_TYPES ? "not-a-uuid" : ids.getOrDefault(name, UUID.randomUUID().toString());
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return switch (variant) {
                case EXTREME_NUMBERS -> "99999999999999999999";
                case NEGATIVE_NUMBERS -> "-2147483648";
                case WRONG_TYPES -> "lots";
                default -> name.equals("size") ? "20" : "0";
            };
        }
        if (type == BigDecimal.class || type == double.class || type == Double.class) {
            return variant == Variant.EXTREME_NUMBERS ? "1e400" : variant == Variant.WRONG_TYPES ? "NaN-ish" : "1.5";
        }
        if (type == boolean.class || type == Boolean.class) {
            return variant == Variant.WRONG_TYPES ? "maybe" : "true";
        }
        if (type == LocalDate.class) {
            return switch (variant) {
                case EXTREME_NUMBERS -> "9999-12-31";
                case NEGATIVE_NUMBERS -> "0001-01-01";
                case WRONG_TYPES -> "2026-02-30";
                default -> LocalDate.now().toString();
            };
        }
        if (type == OffsetDateTime.class || type == Instant.class || type == LocalDateTime.class) {
            return switch (variant) {
                case EXTREME_NUMBERS -> "9999-12-31T23:59:59Z";
                case NEGATIVE_NUMBERS -> "0001-01-01T00:00:00Z";
                case WRONG_TYPES -> "yesterday";
                default -> OffsetDateTime.now(ZoneOffset.UTC).toString();
            };
        }
        if (type == LocalTime.class) {
            return variant == Variant.WRONG_TYPES ? "25:61" : "12:00";
        }
        if (type.isEnum()) {
            return variant == Variant.WRONG_TYPES ? "NOT_A_VALUE" : type.getEnumConstants()[0].toString();
        }
        return null;
    }

    private static boolean isDelete(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().contains(org.springframework.web.bind.annotation.RequestMethod.DELETE);
    }

    private static boolean skipped(RequestMappingInfo info, HandlerMethod handler) {
        for (String pattern : info.getPatternValues()) {
            for (String part : SKIPPED_PATH_PARTS) {
                if (pattern.contains(part)) {
                    return true;
                }
            }
        }
        if (SseEmitter.class.isAssignableFrom(handler.getMethod().getReturnType())) {
            return true;
        }
        if (info.getProducesCondition().getProducibleMediaTypes().contains(MediaType.TEXT_EVENT_STREAM)) {
            return true;
        }
        for (MethodParameter parameter : handler.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestPart.class) || MultipartFile.class.isAssignableFrom(parameter.getParameterType())) {
                return true;
            }
        }
        // Spring's own error controller.
        return handler.getBeanType().getName().startsWith("org.springframework");
    }

    private static String abbreviate(String text) {
        return text == null ? "" : text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

}
