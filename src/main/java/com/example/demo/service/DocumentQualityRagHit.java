package com.example.demo.service;

public record DocumentQualityRagHit(
        String chunkId,
        String title,
        String content,
        double score,
        int startLine,
        int endLine
) {
}
