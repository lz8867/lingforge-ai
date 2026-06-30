package com.example.demo.service;

public record DocumentQualityIssue(
        String id,
        String severity,
        String category,
        String location,
        String description,
        int deduction,
        String suggestion,
        String evidence
) {
}
