package com.example.demo.service;

public record DocumentQualityMetric(
        String id,
        String name,
        double value,
        String unit,
        String level,
        String description
) {
}
