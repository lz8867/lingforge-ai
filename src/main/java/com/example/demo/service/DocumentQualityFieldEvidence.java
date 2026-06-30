package com.example.demo.service;

public record DocumentQualityFieldEvidence(
        String chunkId,
        String title,
        double score,
        String location,
        String content
) {
}
