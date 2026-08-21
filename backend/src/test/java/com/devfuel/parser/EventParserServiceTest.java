package com.devfuel.parser;

import com.devfuel.common.EventType;
import com.devfuel.config.OpenAiProperties;
import com.devfuel.parser.dto.OpenAiParseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventParserServiceTest {

    private final OpenAiParserClient openAi = mock(OpenAiParserClient.class);
    private final EventParserService service =
            new EventParserService(new OpenAiProperties(), openAi);

    @Test
    void normalizeAcceptsValidType() {
        OpenAiParseResponse response = new OpenAiParseResponse();
        response.setEventType("SMOKING");
        response.setStructured(Map.of("quantity", 1, "unit", "cigarette"));

        ParseResult result = service.normalize(response);

        assertEquals(EventType.SMOKING, result.eventType());
        assertEquals(1, result.structured().get("quantity"));
    }

    @Test
    void normalizeAcceptsFood() {
        OpenAiParseResponse response = new OpenAiParseResponse();
        response.setEventType("FOOD");
        response.setStructured(Map.of("item", "samosa", "quantity", 2));

        ParseResult result = service.normalize(response);

        assertEquals(EventType.FOOD, result.eventType());
        assertEquals("samosa", result.structured().get("item"));
        assertEquals(2, result.structured().get("quantity"));
    }

    @Test
    void normalizeInvalidTypeBecomesUnknown() {
        OpenAiParseResponse response = new OpenAiParseResponse();
        response.setEventType("BANANA");
        response.setStructured(Map.of());

        ParseResult result = service.normalize(response);

        assertEquals(EventType.UNKNOWN, result.eventType());
    }

    @Test
    void missingKeyReturnsParseErrorUnknownForSmoking() {
        ParseResult result = service.parse("1 cigarette pee li");

        assertEquals(EventType.UNKNOWN, result.eventType());
        assertEquals(true, result.structured().get("parseError"));
        assertEquals("openai", result.structured().get("source"));
        verify(openAi, never()).classify(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2 samose kha liye",
            "Kal office lunch me samosa khaya tha"
    })
    void missingKeyLeavesFoodAsUnknownNotWeight(String message) {
        ParseResult result = service.parse(message);

        assertEquals(EventType.UNKNOWN, result.eventType());
        assertEquals(true, result.structured().get("parseError"));
        verify(openAi, never()).classify(anyString());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Weight 93.5, 93.5",
            "93.5 kg, 93.5",
            "Kal weight 93.4, 93.4"
    })
    void weightMatchesWithoutOpenAiKey(String message, double kg) {
        ParseResult result = service.parse(message);

        assertEquals(EventType.WEIGHT, result.eventType());
        assertEquals(kg, result.structured().get("value"));
        assertEquals("kg", result.structured().get("unit"));
        verify(openAi, never()).classify(anyString());
    }

    @Test
    void weightDoesNotCallLlmEvenWhenApiKeyPresent() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("sk-test");
        EventParserService parser = new EventParserService(properties, openAi);

        ParseResult result = parser.parse("Weight 93.5");

        assertEquals(EventType.WEIGHT, result.eventType());
        assertEquals(93.5, result.structured().get("value"));
        verify(openAi, never()).classify(anyString());
    }

    @Test
    void smokingStillGoesToLlmWhenApiKeyPresent() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("sk-test");
        OpenAiParseResponse response = new OpenAiParseResponse();
        response.setEventType("SMOKING");
        response.setStructured(Map.of("quantity", 1, "unit", "cigarette"));
        when(openAi.classify("1 cigarette pee li")).thenReturn(response);
        EventParserService parser = new EventParserService(properties, openAi);

        ParseResult result = parser.parse("1 cigarette pee li");

        assertEquals(EventType.SMOKING, result.eventType());
        assertEquals(1, result.structured().get("quantity"));
        verify(openAi).classify("1 cigarette pee li");
    }

    @Test
    void foodStillGoesToLlmWhenApiKeyPresent() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("sk-test");
        OpenAiParseResponse response = new OpenAiParseResponse();
        response.setEventType("FOOD");
        response.setStructured(Map.of("item", "samosa", "quantity", 2));
        when(openAi.classify("2 samose kha liye")).thenReturn(response);
        EventParserService parser = new EventParserService(properties, openAi);

        ParseResult result = parser.parse("2 samose kha liye");

        assertEquals(EventType.FOOD, result.eventType());
        assertEquals("samosa", result.structured().get("item"));
        verify(openAi).classify("2 samose kha liye");
    }

    @Test
    void unknownParseErrorShape() {
        ParseResult result = ParseResult.unknownParseError();
        assertEquals(EventType.UNKNOWN, result.eventType());
        assertTrue(Boolean.TRUE.equals(result.structured().get("parseError")));
    }
}
