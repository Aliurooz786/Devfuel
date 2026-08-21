package com.devfuel.weight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical body-weight reading: kilograms, one decimal, unit {@code kg}.
 * Does not persist or classify text.
 */
public record WeightValue(BigDecimal kilograms) {

    public static final String UNIT = "kg";
    public static final BigDecimal MIN_KG = new BigDecimal("30.0");
    public static final BigDecimal MAX_KG = new BigDecimal("250.0");

    public WeightValue {
        if (kilograms == null) {
            throw new IllegalArgumentException("kilograms is required");
        }
        kilograms = kilograms.setScale(1, RoundingMode.HALF_UP);
        if (kilograms.compareTo(MIN_KG) < 0 || kilograms.compareTo(MAX_KG) > 0) {
            throw new IllegalArgumentException("kilograms out of range: " + kilograms);
        }
    }

    public static Optional<WeightValue> ofKilograms(double raw) {
        if (!Double.isFinite(raw)) {
            return Optional.empty();
        }
        return ofKilograms(BigDecimal.valueOf(raw));
    }

    public static Optional<WeightValue> ofKilograms(BigDecimal raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new WeightValue(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static Optional<WeightValue> ofKilograms(Object raw) {
        return parseNumber(raw).flatMap(WeightValue::ofKilograms);
    }

    /**
     * Reads {@code value} from structured JSON. {@code unit} must be absent or {@code kg}.
     */
    public static Optional<WeightValue> fromStructured(Map<String, ?> structured) {
        if (structured == null || structured.isEmpty()) {
            return Optional.empty();
        }
        Object unit = structured.get("unit");
        if (unit != null && !UNIT.equalsIgnoreCase(String.valueOf(unit).trim())) {
            return Optional.empty();
        }
        return ofKilograms(structured.get("value"));
    }

    public Map<String, Object> toStructured() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("value", kilograms.doubleValue());
        json.put("unit", UNIT);
        return json;
    }

    public double asDouble() {
        return kilograms.doubleValue();
    }

    private static Optional<BigDecimal> parseNumber(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof BigDecimal bd) {
            return Optional.of(bd);
        }
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short) {
            return Optional.of(BigDecimal.valueOf(((Number) raw).longValue()));
        }
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                return Optional.empty();
            }
            return Optional.of(BigDecimal.valueOf(d));
        }
        String text = String.valueOf(raw).trim().replace(',', '.');
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(text, java.math.MathContext.UNLIMITED));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return kilograms.toPlainString() + " " + UNIT.toLowerCase(Locale.ROOT);
    }
}
