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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.integration.support.RequestSamples;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Restaurant B's owner — who holds every permission inside B — calls every endpoint with restaurant A's ids. None of
 * them may succeed: B must never read or change A's menus, orders, payments, bookings, tables, staff or settings.
 */
@DisplayName("Tenant isolation: one restaurant can never reach another's data")
class TenantIsolationIntegrationTest extends AbstractPosApiIntegrationTest {

    private static final Pattern VARIABLE = Pattern.compile("\\{([^}/]+)}");
    // Registration status is a capability link for the person who applied (it shows only name and status).
    private static final Set<String> SKIPPED_PATH_PARTS = Set.of(
            "/auth", "/error", "/v3/api-docs", "/swagger", "logout", "password", "/.well-known", "/public",
            "/restaurants/registrations/");
    // Ids that identify restaurant A's data; an endpoint with none of them isn't about A.
    private static final Set<String> TENANT_VARIABLES = Set.of(
            "restaurantId", "branchId", "menuId", "sectionId", "itemId", "orderId", "lineItemId", "paymentId", "tableId",
            "reservationId", "userId", "sourceRestaurantId");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final Map<String, String> ids = new LinkedHashMap<>();

    @Test
    @DisplayName("every endpoint refuses another restaurant's owner")
    void ownerOfAnotherRestaurantIsRefusedEverywhere() throws Exception {
        World a = newWorld("tenant-a");
        World b = newWorld("tenant-b");
        seed(a);
        String outsider = b.ownerToken();

        List<String> leaks = new ArrayList<>();
        int checked = 0;
        for (var endpoint : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = endpoint.getKey();
            HandlerMethod handler = endpoint.getValue();
            if (skipped(info, handler)) {
                continue;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!aboutTenant(pattern)) {
                    continue;
                }
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    if (method == RequestMethod.DELETE) {
                        continue;
                    }
                    String path = fill(pattern);
                    Call call = call(HttpMethod.valueOf(method.name()), path).as(outsider);
                    addParams(call, handler);
                    Class<?> bodyType = bodyType(handler);
                    if (bodyType != null) {
                        // A believable body, so the request gets past validation to the access checks.
                        call.body(RequestSamples.sample(bodyType, RequestSamples.Variant.TYPICAL, 0, ids));
                    }
                    Response response = call.send();
                    checked++;
                    if (response.status() < 300) {
                        leaks.add(method + " " + path + " -> " + response.status() + " " + abbreviate(response.text()));
                    }
                }
            }
        }
        // Deletes: run last and only check they never succeed.
        for (var endpoint : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = endpoint.getKey();
            if (skipped(info, endpoint.getValue()) || !info.getMethodsCondition().getMethods().contains(RequestMethod.DELETE)) {
                continue;
            }
            for (String pattern : info.getPatternValues()) {
                if (!aboutTenant(pattern)) {
                    continue;
                }
                String path = fill(pattern);
                Response response = call(HttpMethod.DELETE, path).as(outsider).send();
                checked++;
                if (response.status() < 300) {
                    leaks.add("DELETE " + path + " -> " + response.status());
                }
            }
        }

        System.out.println("Tenant isolation: " + checked + " requests checked");
        assertThat(checked).isGreaterThan(200);
        assertThat(leaks).as("requests from another restaurant that succeeded").isEmpty();

        // Restaurant A's own data is untouched and still readable by its owner.
        JsonNode order = get("/restaurants/{r}/orders/{o}", a.restaurantId(), UUID.fromString(ids.get("orderId"))).as(a.ownerToken()).expect(200);
        assertThat(order.get("status").asText()).isEqualTo("OPEN");
        get("/menus/{m}", UUID.fromString(ids.get("menuId"))).as(a.ownerToken()).expect(200);
    }

    private void seed(World a) throws Exception {
        String token = a.ownerToken();
        Map<String, UUID> menu = menu(a, "Secret menu");
        UUID itemId = item(a, menu, "Secret dish", "19.00");
        JsonNode order = order(a, token, Map.of(itemId, 1));
        JsonNode payment = post("/restaurants/{r}/orders/{o}/payments", a.restaurantId(), id(order)).as(token)
                .body(Map.of("method", "CARD", "amount", new BigDecimal("1.00"))).expect(201);
        User staff = staff(a, "secret", "WAITER");
        ids.put("restaurantId", a.restaurantId().toString());
        ids.put("sourceRestaurantId", a.restaurantId().toString());
        ids.put("branchId", a.branchId().toString());
        ids.put("menuId", menu.get("menuId").toString());
        ids.put("sectionId", menu.get("sectionId").toString());
        ids.put("itemId", itemId.toString());
        ids.put("orderId", id(order).toString());
        ids.put("lineItemId", order.get("lineItems").get(0).get("id").asText());
        ids.put("paymentId", payment.get("payment").get("id").asText());
        ids.put("userId", staff.getId().toString());
        Response table = post("/restaurants/{r}/branches/{b}/tables", a.restaurantId(), a.branchId()).as(token)
                .body(Map.of("tableNumber", "S1", "capacity", 4, "floor", "Main", "positionX", 5, "positionY", 5)).send();
        if (table.status() < 300) {
            ids.put("tableId", table.json().get("id").asText());
        }
        Response booking = post("/restaurants/{r}/reservations", a.restaurantId()).as(token).body(Map.of(
                "branchId", a.branchId(),
                "partySize", 2,
                "reservationStart", OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).withHour(18).withMinute(0).withSecond(0).withNano(0).toString(),
                "contactName", "Secret Guest",
                "contactPhone", "+390000000001")).send();
        if (booking.status() < 300) {
            ids.put("reservationId", booking.json().get("id").asText());
        }
    }

    private boolean aboutTenant(String pattern) {
        Matcher matcher = VARIABLE.matcher(pattern);
        boolean any = false;
        while (matcher.find()) {
            String name = matcher.group(1).split(":")[0];
            if (TENANT_VARIABLES.contains(name)) {
                if (!ids.containsKey(name)) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    private String fill(String pattern) {
        Matcher matcher = VARIABLE.matcher(pattern);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).split(":")[0];
            String value = ids.getOrDefault(name, name.toLowerCase().endsWith("id") ? UUID.randomUUID().toString() : "x");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void addParams(Call call, HandlerMethod handler) {
        for (MethodParameter parameter : handler.getMethodParameters()) {
            RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
            if (annotation == null) {
                continue;
            }
            String name = !annotation.name().isEmpty() ? annotation.name() : !annotation.value().isEmpty() ? annotation.value()
                    : parameter.getParameterName();
            Class<?> type = parameter.getParameterType();
            Object value = null;
            if (type == LocalDate.class) {
                value = LocalDate.now();
            } else if (type == OffsetDateTime.class) {
                value = OffsetDateTime.now(ZoneOffset.UTC);
            } else if (type == UUID.class) {
                value = ids.getOrDefault(name, null);
            } else if (type == Integer.class || type == int.class) {
                value = name.equals("size") ? 10 : name.equals("partySize") ? 2 : 0;
            } else if (type == String.class && annotation.required()) {
                value = "a";
            }
            if (value != null && name != null) {
                call.param(name, value);
            }
        }
    }

    private static Class<?> bodyType(HandlerMethod handler) {
        for (MethodParameter parameter : handler.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                return parameter.getParameterType();
            }
        }
        return null;
    }

    private static boolean skipped(RequestMappingInfo info, HandlerMethod handler) {
        for (String pattern : info.getPatternValues()) {
            for (String part : SKIPPED_PATH_PARTS) {
                if (pattern.contains(part)) {
                    return true;
                }
            }
        }
        if (SseEmitter.class.isAssignableFrom(handler.getMethod().getReturnType())
                || info.getProducesCondition().getProducibleMediaTypes().contains(MediaType.TEXT_EVENT_STREAM)) {
            return true;
        }
        for (MethodParameter parameter : handler.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestPart.class) || MultipartFile.class.isAssignableFrom(parameter.getParameterType())) {
                return true;
            }
        }
        return handler.getBeanType().getName().startsWith("org.springframework");
    }

    private static String abbreviate(String text) {
        return text == null ? "" : text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
