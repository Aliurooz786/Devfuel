package com.devfuel.weight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightParserTest {

    private final WeightParser parser = new WeightParser();

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Weight 93.5, 93.5",
            "weight 93.5, 93.5",
            "93.5 kg, 93.5",
            "93.5kg, 93.5",
            "kal weight 93.4, 93.4",
            "Kal weight 93.4, 93.4",
            "15 Aug ko weight 93.5, 93.5",
            "mera vajan 93.5 hai, 93.5",
            "wajan 88, 88.0",
            "wt 91.2, 91.2",
            "93.5 kg hai, 93.5",
            "Weight is 94, 94.0",
            "weight: 92.3, 92.3"
    })
    void extractsKilograms(String input, String expected) {
        WeightValue value = parser.parse(input).orElseThrow(() -> new AssertionError(input));
        assertEquals(expected, value.kilograms().toPlainString(), input);
        assertEquals("kg", WeightValue.UNIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5 kg rice",
            "2 kg paneer",
            "weight loss",
            "400 kg",
            "12.5 kg",
            "93.5 lb",
            "weight 93.5 lb",
            "93.5 pounds",
            "1 cigarette pee li",
            "2 samose kha liye",
            "93.5",
            "ate 93.5",
            "lost 2 kg",
            "  "
    })
    void rejectsNonBodyWeight(String input) {
        assertTrue(parser.parse(input).isEmpty(), input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsBlank(String input) {
        assertTrue(parser.parse(input).isEmpty());
    }

    @Test
    void doesNotParseDatesAsWeight() {
        assertTrue(parser.parse("15 Aug ko mood kharab tha").isEmpty());
    }
}
