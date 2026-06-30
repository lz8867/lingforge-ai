package com.example.demo.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DocumentQualityFieldExtractionResult(
        double coverageScore,
        int completedFieldCount,
        int totalFieldCount,
        List<DocumentQualityExtractedField> fields
) {
    public DocumentQualityFieldExtractionResult {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public boolean isComplete(String fieldId) {
        return fieldById(fieldId)
                .map(field -> "complete".equals(field.status()))
                .orElse(false);
    }

    public Optional<DocumentQualityExtractedField> fieldById(String fieldId) {
        String safeFieldId = fieldId == null ? "" : fieldId.trim();
        return fields.stream()
                .filter(field -> field.id().equals(safeFieldId))
                .findFirst();
    }

    public List<Map<String, Object>> visualizationRows() {
        return fields.stream()
                .map(field -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", field.id());
                    row.put("name", field.name());
                    row.put("status", field.status());
                    row.put("confidence", field.confidence());
                    row.put("values", field.values());
                    row.put("suggestion", field.suggestion());
                    row.put("evidence", field.evidence().stream()
                            .map(evidence -> {
                                Map<String, Object> evidenceRow = new LinkedHashMap<>();
                                evidenceRow.put("chunkId", evidence.chunkId());
                                evidenceRow.put("title", evidence.title());
                                evidenceRow.put("score", evidence.score());
                                evidenceRow.put("location", evidence.location());
                                evidenceRow.put("content", evidence.content());
                                return evidenceRow;
                            })
                            .toList());
                    return row;
                })
                .toList();
    }
}
