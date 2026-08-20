package com.devfuel.parser.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiParseResponse {

    private String eventType;
    private Map<String, Object> structured;
    private String description;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getStructured() {
        return structured;
    }

    public void setStructured(Map<String, Object> structured) {
        this.structured = structured;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
