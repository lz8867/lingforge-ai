package com.example.demo.service;

import java.util.List;

public record DocumentQualityRagEvidence(
        String requirementId,
        String label,
        String query,
        boolean matched,
        double topScore,
        List<DocumentQualityRagHit> topHits
) {
}
