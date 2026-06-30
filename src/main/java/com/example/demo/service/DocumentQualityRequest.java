package com.example.demo.service;

public record DocumentQualityRequest(
        String documentName,
        String documentType,
        String content
) {
}
