package com.example.demo.service;

import java.util.List;

public record DocumentQualityJudgeResult(
        boolean success,
        String source,
        String summary,
        List<DocumentQualityDimensionScore> dimensionScores,
        String message
) {
    public static DocumentQualityJudgeResult fallback(String message) {
        return new DocumentQualityJudgeResult(false, "heuristic", "", List.of(), message);
    }
}
