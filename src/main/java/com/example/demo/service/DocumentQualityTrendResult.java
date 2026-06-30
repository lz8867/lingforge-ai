package com.example.demo.service;

import java.util.List;

public record DocumentQualityTrendResult(
        String scope,
        double averageScore,
        double deltaScore,
        List<DocumentQualityTrendPoint> points
) {
}
