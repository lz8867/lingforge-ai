package com.example.demo.service;

import java.util.List;

public record DocumentQualityExtractedField(
        String id,
        String name,
        String status,
        double confidence,
        List<String> values,
        List<DocumentQualityFieldEvidence> evidence,
        String suggestion
) {
    public DocumentQualityExtractedField {
        values = values == null ? List.of() : List.copyOf(values);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
