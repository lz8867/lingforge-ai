package com.example.demo.service;

import java.util.List;

public record ModelEvaluationTrendSeries(
        String name,
        String groupValue,
        String metric,
        long totalRuns,
        List<ModelEvaluationTrendPoint> points
) {
}
