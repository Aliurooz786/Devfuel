package com.devfuel.log.dto;

import com.devfuel.common.EventTimePrecision;
import com.devfuel.common.EventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateLogResponse(
        boolean success,
        UUID id,
        EventType eventType,
        String rawText,
        Map<String, Object> structuredJson,
        String source,
        String parserVersion,
        Instant timestamp,
        String imageRef,
        Instant loggedAt,
        EventTimePrecision eventTimePrecision,
        String eventTimezone,
        boolean backdated,
        boolean loggedLater
) {
}
