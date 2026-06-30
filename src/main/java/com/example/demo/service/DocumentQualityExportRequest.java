package com.example.demo.service;

public record DocumentQualityExportRequest(
        String format,
        DocumentQualityResult result
) {
}
