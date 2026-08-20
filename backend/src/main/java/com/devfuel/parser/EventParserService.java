package com.devfuel.parser;

import com.devfuel.common.EventType;
import com.devfuel.config.OpenAiProperties;
import com.devfuel.parser.dto.OpenAiParseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

@Service
public class EventParserService {

    private static final Logger log = LoggerFactory.getLogger(EventParserService.class);

    private final OpenAiProperties openAiProperties;
    private final OpenAiParserClient openAiParserClient;

    public EventParserService(OpenAiProperties openAiProperties, OpenAiParserClient openAiParserClient) {
        this.openAiProperties = openAiProperties;
        this.openAiParserClient = openAiParserClient;
    }

    /**
     * Classify raw user text. Never throws — always returns a persistable ParseResult.
     */
    public ParseResult parse(String rawText) {
        if (!openAiProperties.hasApiKey()) {
            log.warn("Skipping OpenAI parse; OPENAI_API_KEY missing. Using UNKNOWN.");
            return ParseResult.unknownParseError();
        }

        try {
            OpenAiParseResponse response = openAiParserClient.classify(rawText);
            return normalize(response);
        } catch (Exception ex) {
            log.warn("OpenAI classification failed; saving UNKNOWN. cause={}", ex.getMessage());
            return ParseResult.unknownParseError();
        }
    }

    ParseResult normalize(OpenAiParseResponse response) {
        if (response == null) {
            return ParseResult.unknownParseError();
        }

        EventType eventType = toEventType(response.getEventType());
        Map<String, Object> structured = response.getStructured() == null
                ? Collections.emptyMap()
                : response.getStructured();

        return ParseResult.of(eventType, structured);
    }

    private EventType toEventType(String raw) {
        if (raw == null || raw.isBlank()) {
            return EventType.UNKNOWN;
        }
        try {
            return EventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid eventType from model: {}", raw);
            return EventType.UNKNOWN;
        }
    }
}
