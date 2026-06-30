package com.example.demo.service;

import java.util.Map;

public record KnowledgeRetrievalHit(
        String id,
        String title,
        String content,
        double score,
        String sourceType,
        String sourceName,
        Map<String, Object> metadata
) {
}
