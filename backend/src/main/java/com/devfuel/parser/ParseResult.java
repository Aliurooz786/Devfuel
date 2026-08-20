package com.devfuel.parser;

import com.devfuel.common.EventType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ParseResult(EventType eventType, Map<String, Object> structured) {

    public static ParseResult of(EventType eventType, Map<String, Object> structured) {
        Map<String, Object> copy = structured == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(structured));
        return new ParseResult(eventType, copy);
    }

    public static ParseResult unknownParseError() {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("parseError", true);
        structured.put("source", "openai");
        return of(EventType.UNKNOWN, structured);
    }
}
