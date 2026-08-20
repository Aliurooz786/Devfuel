package com.devfuel.parser;

import com.devfuel.config.OpenAiProperties;
import com.devfuel.parser.dto.OpenAiChatDtos;
import com.devfuel.parser.dto.OpenAiChatDtos.ChatRequest;
import com.devfuel.parser.dto.OpenAiChatDtos.ChatResponse;
import com.devfuel.parser.dto.OpenAiChatDtos.Message;
import com.devfuel.parser.dto.OpenAiParseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiParserClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiParserClient.class);

    private static final String SYSTEM_PROMPT = """
            You classify personal life-log messages into exactly one event type.
            Return ONLY a JSON object with this shape:
            {"eventType":"<TYPE>","structured":{...}}

            Allowed eventType values:
            SMOKING, FOOD, WEIGHT, EXERCISE, ALCOHOL, MOOD, SLEEP, NOTE, UNKNOWN

            Rules:
            - Understand Hinglish and English.
            - Extract obvious structured fields when present.
            - Never add advice, calories, coaching, or commentary.
            - Use NOTE for freeform notes that are not another type.
            - Use UNKNOWN when classification is unclear.
            - structured may be an empty object.

            Examples:
            "1 cigarette pee li" -> {"eventType":"SMOKING","structured":{"quantity":1,"unit":"cigarette"}}
            "2 samose kha liye" -> {"eventType":"FOOD","structured":{"item":"samosa","quantity":2}}
            "Weight 93.5" -> {"eventType":"WEIGHT","structured":{"value":93.5,"unit":"kg"}}
            "Mood bahut kharab hai" -> {"eventType":"MOOD","structured":{"mood":"kharab"}}
            "30 min walk kari" -> {"eventType":"EXERCISE","structured":{"activity":"walk","durationMinutes":30}}
            """;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiParserClient(
            RestClient openAiRestClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Calls OpenAI Chat Completions and returns the parsed model JSON.
     * Throws on HTTP/transport/parse failures so the service can fall back to UNKNOWN.
     */
    public OpenAiParseResponse classify(String rawText) {
        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(
                        new Message("system", SYSTEM_PROMPT),
                        new Message("user", rawText)
                ),
                Map.of("type", "json_object"),
                0.0
        );

        try {
            ChatResponse response = openAiRestClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            String content = extractContent(response);
            log.debug("OpenAI raw classification JSON: {}", content);
            return objectMapper.readValue(content, OpenAiParseResponse.class);
        } catch (RestClientException ex) {
            log.warn("OpenAI HTTP call failed: {}", ex.getMessage());
            throw new OpenAiParseException("OpenAI HTTP call failed", ex);
        } catch (Exception ex) {
            log.warn("OpenAI response parse failed: {}", ex.getMessage());
            throw new OpenAiParseException("OpenAI response parse failed", ex);
        }
    }

    private String extractContent(ChatResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || response.getChoices().get(0).getMessage().content() == null
                || response.getChoices().get(0).getMessage().content().isBlank()) {
            throw new OpenAiParseException("OpenAI returned empty content");
        }
        return response.getChoices().get(0).getMessage().content().trim();
    }
}
