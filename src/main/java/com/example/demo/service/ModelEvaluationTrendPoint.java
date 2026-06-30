package com.example.demo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ModelEvaluationTrendPoint(
        LocalDate date,
        LocalDateTime bucketStart,
        String label,
        long totalRuns,
        double avgOverallScore,
        double avgRecall,
        double avgPrecision,
        double avgF1,
        double avgTop1,
        double avgTop3,
        double avgTop5,
        double avgDurationMs,
        double successRate
) {
}
