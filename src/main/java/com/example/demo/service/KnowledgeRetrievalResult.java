package com.example.demo.service;

import java.util.List;

public record KnowledgeRetrievalResult(
        String scene,
        String query,
        int topK,
        boolean used,
        String gateReason,
        List<KnowledgeRetrievalHit> matches,
        List<String> trace
) {
}
