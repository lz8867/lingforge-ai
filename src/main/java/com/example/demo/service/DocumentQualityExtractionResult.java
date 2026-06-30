package com.example.demo.service;

public record DocumentQualityExtractionResult(
        boolean success,
        String message,
        String documentName,
        String documentType,
        String content
) {
}
