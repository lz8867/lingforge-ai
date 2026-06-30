package com.example.demo.service;

import java.util.List;
import java.util.Map;

public record DocumentSummaryResult(
        boolean success,
        String message,
        String documentName,
        String documentType,
        String summary,
        List<String> keyPoints,
        List<String> keywords,
        int contentLength,
        String source,
        boolean contract,
        String contractType,
        Map<String, String> summaryFields,
        List<DocumentSummaryModule> structuredModules
) {
}
