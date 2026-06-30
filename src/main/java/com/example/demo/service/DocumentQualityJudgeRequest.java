package com.example.demo.service;

import java.util.List;
import java.util.Map;

public record DocumentQualityJudgeRequest(
        String documentName,
        String documentType,
        String content,
        List<DocumentQualityIssue> issues,
        List<DocumentQualityMetric> metrics,
        List<DocumentQualityDimensionScore> heuristicDimensions,
        Map<String, DocumentQualityRagEvidence> ragEvidence,
        List<Map<String, Object>> extractedFields
) {
}
