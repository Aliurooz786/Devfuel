package com.devfuel.weight.dto;

import java.util.List;

public record WeightHistoryResponse(
        String unit,
        List<WeightPointResponse> points
) {
}
