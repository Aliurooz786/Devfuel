package com.devfuel.parser;

import com.devfuel.config.OpenAiProperties;
import com.devfuel.parser.dto.OpenAiChatDtos.ChatResponse;
import com.devfuel.parser.dto.OpenAiParseResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiVisionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVisionClient.class);

    private static final String SYSTEM_PROMPT = """
            You identify food in a photo for a personal life log.
            Return ONLY a JSON object with this shape:
            {"eventType":"FOOD","structured":{"items":[{"item":"samosa","quantity":2}]},"description":"Two samosas on a plate"}

            Rules:
            - eventType must be FOOD if any food is visible, otherwise UNKNOWN.
            - structured.items is an array of {item, quantity?} objects.
            - description is a short plain-language caption of what was eaten.
            - Never estimate calories, macros, nutrition, or give advice.
            - Never moralize the food.
            """;

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiVisionClient(
            RestClient openAiRestClient,
            OpenAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public OpenAiParseResponse classifyImage(byte[] imageBytes, String contentType) {
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        VisionChatRequest request = new VisionChatRequest(
                properties.getVisionModel(),
                List.of(
                        new VisionMessage("system", SYSTEM_PROMPT),
                        new VisionMessage("user", List.of(
                                Map.of("type", "text", "text", "Extract food from this photo."),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                        ))
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
            log.debug("OpenAI Vision raw JSON: {}", content);
            return objectMapper.readValue(content, OpenAiParseResponse.class);
        } catch (RestClientException ex) {
            log.warn("OpenAI Vision HTTP call failed: {}", ex.getMessage());
            throw new OpenAiParseException("OpenAI Vision HTTP call failed", ex);
        } catch (Exception ex) {
            log.warn("OpenAI Vision response parse failed: {}", ex.getMessage());
            throw new OpenAiParseException("OpenAI Vision response parse failed", ex);
        }
    }

    private String extractContent(ChatResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || response.getChoices().get(0).getMessage().content() == null
                || response.getChoices().get(0).getMessage().content().isBlank()) {
            throw new OpenAiParseException("OpenAI Vision returned empty content");
        }
        return response.getChoices().get(0).getMessage().content().trim();
    }

    public record VisionChatRequest(
            String model,
            List<VisionMessage> messages,
            @JsonProperty("response_format") Map<String, String> responseFormat,
            double temperature
    ) {
    }

    public record VisionMessage(String role, Object content) {
    }
}
