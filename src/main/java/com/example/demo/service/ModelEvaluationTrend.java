package com.example.demo.service;

import java.util.List;

public record ModelEvaluationTrend(
        String scope,
        int lookbackDays,
        String granularity,
        String groupBy,
        String metric,
        List<ModelEvaluationTrendPoint> points,
        List<ModelEvaluationTrendSeries> series
) {
    public ModelEvaluationTrend(
            String scope,
            int lookbackDays,
            String granularity,
            List<ModelEvaluationTrendPoint> points
    ) {
        this(scope, lookbackDays, granularity, "", "", points, List.of());
    }
}
