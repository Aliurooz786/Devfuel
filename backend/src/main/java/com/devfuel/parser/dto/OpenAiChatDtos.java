package com.devfuel.parser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Minimal Chat Completions request/response shapes for OpenAI HTTP calls.
 */
public final class OpenAiChatDtos {

    private OpenAiChatDtos() {
    }

    public record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("response_format") Map<String, String> responseFormat,
            double temperature
    ) {
    }

    public record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        private List<Choice> choices;

        public List<Choice> getChoices() {
            return choices;
        }

        public void setChoices(List<Choice> choices) {
            this.choices = choices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }
    }
}
