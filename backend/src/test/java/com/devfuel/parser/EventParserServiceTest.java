package com.devfuel.parser;

import com.devfuel.common.EventType;
import com.devfuel.config.OpenAiProperties;
import com.devfuel.parser.dto.OpenAiParseResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EventParserServiceTest {

    private final EventParserService service =
            new EventParserService(new OpenAiProperties(), mock(OpenAiParserClient.class));

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
    void normalizeInvalidTypeBecomesUnknown() {
        OpenAiParseResponse response = new OpenAiParseResponse();
        response.setEventType("BANANA");
        response.setStructured(Map.of());

        ParseResult result = service.normalize(response);

        assertEquals(EventType.UNKNOWN, result.eventType());
    }

    @Test
    void missingKeyReturnsParseErrorUnknown() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("");
        EventParserService parser =
                new EventParserService(properties, mock(OpenAiParserClient.class));

        ParseResult result = parser.parse("1 cigarette pee li");

        assertEquals(EventType.UNKNOWN, result.eventType());
        assertEquals(true, result.structured().get("parseError"));
        assertEquals("openai", result.structured().get("source"));
    }

    @Test
    void unknownParseErrorShape() {
        ParseResult result = ParseResult.unknownParseError();
        assertEquals(EventType.UNKNOWN, result.eventType());
        assertTrue(Boolean.TRUE.equals(result.structured().get("parseError")));
    }
}
