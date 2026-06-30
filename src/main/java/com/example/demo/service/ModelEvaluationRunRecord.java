package com.example.demo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record ModelEvaluationRunRecord(
        long id,
        String scene,
        String model,
        String provider,
        String status,
        boolean success,
        long durationMs,
        int overallScore,
        String requestSource,
        String errorMessage,
        String requestJson,
        String responseJson,
        String metadataJson,
        Map<String, Double> metrics,
        LocalDateTime createdAt
) {
}
