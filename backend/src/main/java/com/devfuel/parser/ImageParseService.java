package com.devfuel.parser;

import com.devfuel.common.EventType;
import com.devfuel.config.OpenAiProperties;
import com.devfuel.parser.dto.OpenAiParseResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ImageParseService {

    private static final Logger log = LoggerFactory.getLogger(ImageParseService.class);
    private static final String FALLBACK_TEXT = "image log";

    private final OpenAiProperties openAiProperties;
    private final OpenAiVisionClient openAiVisionClient;
    private final ObjectMapper objectMapper;

    public ImageParseService(
            OpenAiProperties openAiProperties,
            OpenAiVisionClient openAiVisionClient,
            ObjectMapper objectMapper
    ) {
        this.openAiProperties = openAiProperties;
        this.openAiVisionClient = openAiVisionClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Never throws. Always returns a persistable image parse result.
     */
    public ImageParseResult parse(byte[] imageBytes, String contentType) {
        if (!openAiProperties.hasApiKey()) {
            log.warn("Skipping OpenAI Vision; OPENAI_API_KEY missing. Using UNKNOWN.");
            return ImageParseResult.failed();
        }

        try {
            OpenAiParseResponse response = openAiVisionClient.classifyImage(imageBytes, contentType);
            Map<String, Object> raw = objectMapper.convertValue(response, new TypeReference<>() {
            });
            EventType eventType = toEventType(response.getEventType());
            Map<String, Object> structured = response.getStructured() == null
                    ? Collections.emptyMap()
                    : response.getStructured();
            String description = response.getDescription() == null || response.getDescription().isBlank()
                    ? FALLBACK_TEXT
                    : response.getDescription().trim();
            return new ImageParseResult(eventType, structured, description, raw);
        } catch (Exception ex) {
            log.warn("OpenAI Vision classification failed; saving UNKNOWN. cause={}", ex.getMessage());
            return ImageParseResult.failed();
        }
    }

    private EventType toEventType(String raw) {
        if (raw == null || raw.isBlank()) {
            return EventType.UNKNOWN;
        }
        try {
            return EventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid eventType from Vision: {}", raw);
            return EventType.UNKNOWN;
        }
    }

    public record ImageParseResult(
            EventType eventType,
            Map<String, Object> structured,
            String rawText,
            Map<String, Object> rawModelOutput
    ) {
        public static ImageParseResult failed() {
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("parseError", true);
            structured.put("source", "openai");
            Map<String, Object> raw = new LinkedHashMap<>(structured);
            return new ImageParseResult(EventType.UNKNOWN, structured, FALLBACK_TEXT, raw);
        }
    }
}
