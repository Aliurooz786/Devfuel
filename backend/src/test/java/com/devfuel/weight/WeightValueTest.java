package com.devfuel.weight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightValueTest {

    @ParameterizedTest
    @CsvSource({
            "93.5, 93.5",
            "93, 93.0",
            "93.54, 93.5",
            "93.55, 93.6",
            "30, 30.0",
            "250, 250.0",
            "29.96, 30.0",
            "250.04, 250.0"
    })
    void roundsToOneDecimalWithinRange(String input, String expected) {
        WeightValue value = WeightValue.ofKilograms(new BigDecimal(input)).orElseThrow();
        assertEquals(new BigDecimal(expected), value.kilograms());
        assertEquals(Double.parseDouble(expected), value.asDouble());
    }

    @ParameterizedTest
    @ValueSource(strings = {"29.94", "29.9", "250.06", "250.1", "0", "400", "-1", "12.5"})
    void rejectsOutOfRange(String input) {
        assertTrue(WeightValue.ofKilograms(new BigDecimal(input)).isEmpty());
    }

    @Test
    void structuredRoundTrip() {
        WeightValue value = WeightValue.ofKilograms(93.5).orElseThrow();
        Map<String, Object> json = value.toStructured();
        assertEquals(93.5, json.get("value"));
        assertEquals("kg", json.get("unit"));
        WeightValue parsed = WeightValue.fromStructured(json).orElseThrow();
        assertEquals(value, parsed);
    }

    @Test
    void fromStructuredAcceptsNumericStringAndDefaultUnit() {
        WeightValue value = WeightValue.fromStructured(Map.of("value", "93.5")).orElseThrow();
        assertEquals(new BigDecimal("93.5"), value.kilograms());
    }

    @Test
    void fromStructuredRejectsPounds() {
        assertTrue(WeightValue.fromStructured(Map.of("value", 93.5, "unit", "lb")).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void fromStructuredRejectsMissing(Map<String, Object> structured) {
        assertTrue(WeightValue.fromStructured(structured).isEmpty());
    }

    @Test
    void ofKilogramsRejectsNonFinite() {
        assertTrue(WeightValue.ofKilograms(Double.NaN).isEmpty());
        assertTrue(WeightValue.ofKilograms(Double.POSITIVE_INFINITY).isEmpty());
    }
}
