package com.devfuel.weight.dto;

import com.devfuel.common.EventTimePrecision;

import java.time.Instant;
import java.util.UUID;

public record WeightPointResponse(
        UUID id,
        double value,
        String unit,
        Instant timestamp,
        Instant loggedAt,
        EventTimePrecision eventTimePrecision,
        boolean loggedLater,
        String localDate
) {
}
