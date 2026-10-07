package pos.pos.integration.support;

import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds request bodies for any DTO by reflection: a typical one, or one of several hostile variants. */
public final class RequestSamples {

    public enum Variant {
        EMPTY_OBJECT, HUGE_TEXT, EXTREME_NUMBERS, NEGATIVE_NUMBERS, NULLS, WRONG_TYPES, BROKEN_JSON, TYPICAL
    }

    private RequestSamples() {
    }

    public static Object sample(Type type, Variant variant, int depth, Map<String, String> ids) {
        Class<?> raw = rawClass(type);
        if (raw == null) {
            return null;
        }
        if (variant == Variant.NULLS && depth > 0) {
            return null;
        }
        if (raw == String.class) {
            return switch (variant) {
                case HUGE_TEXT -> "Ünïcødé🍝'\"<>;--" + "x".repeat(20_000);
                case WRONG_TYPES -> 12345;
                case EXTREME_NUMBERS, NEGATIVE_NUMBERS -> "-99999999999999999999";
                default -> "Sample";
            };
        }
        if (raw == UUID.class) {
            return variant == Variant.WRONG_TYPES ? "nope" : UUID.randomUUID().toString();
        }
        if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class || raw == short.class || raw == Short.class) {
            return switch (variant) {
                case EXTREME_NUMBERS -> Integer.MAX_VALUE;
                case NEGATIVE_NUMBERS -> Integer.MIN_VALUE;
                case WRONG_TYPES -> "many";
                default -> 1;
            };
        }
        if (raw == BigDecimal.class || raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
            return switch (variant) {
                case EXTREME_NUMBERS -> new BigDecimal("123456789012345678901234567890.123456789");
                case NEGATIVE_NUMBERS -> new BigDecimal("-99999999999999.99");
                case WRONG_TYPES -> "cheap";
                default -> new BigDecimal("1.50");
            };
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return variant == Variant.WRONG_TYPES ? "yes please" : true;
        }
        if (raw.isEnum()) {
            Object[] constants = raw.getEnumConstants();
            return variant == Variant.WRONG_TYPES ? "NOT_A_VALUE" : constants.length == 0 ? null : constants[0].toString();
        }
        if (raw == LocalDate.class) {
            return variant == Variant.EXTREME_NUMBERS ? "9999-12-31" : variant == Variant.NEGATIVE_NUMBERS ? "0001-01-01"
                    : variant == Variant.WRONG_TYPES ? "31/31/2026" : LocalDate.now().plusDays(1).toString();
        }
        if (raw == LocalTime.class) {
            return variant == Variant.WRONG_TYPES ? "noon" : "12:30:00";
        }
        if (raw == OffsetDateTime.class || raw == Instant.class || raw == LocalDateTime.class) {
            return variant == Variant.EXTREME_NUMBERS ? "9999-12-31T23:59:59Z" : variant == Variant.NEGATIVE_NUMBERS ? "0001-01-01T00:00:00Z"
                    : variant == Variant.WRONG_TYPES ? "soon" : OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString();
        }
        if (Collection.class.isAssignableFrom(raw)) {
            Type element = typeArgument(type, 0);
            List<Object> list = new ArrayList<>();
            int count = variant == Variant.HUGE_TEXT ? 3 : 1;
            for (int i = 0; i < count; i++) {
                list.add(sample(element, variant, depth + 1, ids));
            }
            return list;
        }
        if (Map.class.isAssignableFrom(raw)) {
            return Map.of("key", "value");
        }
        if (raw.isArray()) {
            return List.of(sample(raw.getComponentType(), variant, depth + 1, ids));
        }
        if (raw.getName().startsWith("java.") || MultipartFile.class.isAssignableFrom(raw) || depth > 3) {
            return null;
        }
        Map<String, Object> object = new LinkedHashMap<>();
        if (raw.isRecord()) {
            for (RecordComponent component : raw.getRecordComponents()) {
                object.put(component.getName(), fieldValue(component.getName(), component.getGenericType(), variant, depth, ids));
            }
            return object;
        }
        for (Class<?> current = raw; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                object.putIfAbsent(field.getName(), fieldValue(field.getName(), field.getGenericType(), variant, depth, ids));
            }
        }
        return object;
    }

    private static Object fieldValue(String name, Type type, Variant variant, int depth, Map<String, String> ids) {
        Class<?> raw = rawClass(type);
        if (raw == UUID.class && variant != Variant.WRONG_TYPES && ids.containsKey(name)) {
            return ids.get(name);
        }
        return sample(type, variant, depth + 1, ids);
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        if (type instanceof WildcardType wildcard && wildcard.getUpperBounds().length > 0) {
            return rawClass(wildcard.getUpperBounds()[0]);
        }
        return null;
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index) {
            return parameterized.getActualTypeArguments()[index];
        }
        return String.class;
    }

}
