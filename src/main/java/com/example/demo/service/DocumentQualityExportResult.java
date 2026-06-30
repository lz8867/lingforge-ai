package com.example.demo.service;

public record DocumentQualityExportResult(
        boolean success,
        String message,
        String fileName,
        String contentType,
        String encoding,
        String contentBase64
) {
}
