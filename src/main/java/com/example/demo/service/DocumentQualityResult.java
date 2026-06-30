package com.example.demo.service;

import java.util.List;
import java.util.Map;

public record DocumentQualityResult(
        boolean success,
        String message,
        String documentName,
        String documentType,
        int overallScore,
        String grade,
        int ruleScore,
        int llmJudgeScore,
        int metricScore,
        List<DocumentQualityDimensionScore> dimensionScores,
        List<DocumentQualityIssue> issues,
        List<DocumentQualityMetric> metrics,
        List<String> recommendations,
        Map<String, Object> visualizationData
) {
}
