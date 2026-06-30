package com.example.demo.service;

public record ModelEvaluationSummary(
        String scope,
        long totalRuns,
        long successRuns,
        long failureRuns,
        double successRate,
        double averageDurationMs,
        double averageOverallScore,
        double averageRecall,
        double averagePrecision,
        double averageF1,
        double averageTop1,
        double averageTop3,
        double averageTop5
) {
}
