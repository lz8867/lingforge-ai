package com.example.demo.service;

import java.time.LocalDateTime;

public record DocumentQualityTrendPoint(
        LocalDateTime createdAt,
        String documentName,
        String documentType,
        int overallScore,
        String grade,
        int issueCount
) {
}
