package com.devfuel.temporal;

import com.devfuel.common.EventTimePrecision;

import java.time.Instant;

public record TemporalResolution(
        Instant occurrence,
        EventTimePrecision precision,
        String zone
) {
}
