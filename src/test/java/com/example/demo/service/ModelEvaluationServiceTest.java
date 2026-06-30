package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEvaluationServiceTest {

    @Test
    void trendShouldFillDailyBucketsForLookbackWindow() {
        ModelEvaluationService service = ModelEvaluationService.inMemory();
        saveSuccessfulRun(service, "chat", "qwen2.5");

        ModelEvaluationTrend trend = service.trend("chat", "qwen2.5", null, null, 3, "day");

        assertEquals("day", trend.granularity());
        assertEquals(3, trend.points().size());
        assertEquals(LocalDate.now().minusDays(2), trend.points().get(0).date());
        assertEquals(LocalDate.now(), trend.points().get(2).date());
        assertEquals(0, trend.points().get(0).totalRuns());
        assertEquals(1, trend.points().get(2).totalRuns());
        assertEquals("06", trend.points().get(2).label().substring(0, 2));
    }

    @Test
    void trendShouldSupportHourlyGranularityForRecentRuns() {
        ModelEvaluationService service = ModelEvaluationService.inMemory();
        saveSuccessfulRun(service, "chat", "qwen2.5");

        ModelEvaluationTrend trend = service.trend("chat", "qwen2.5", null, null, 1, "hour");

        assertEquals("hour", trend.granularity());
        assertEquals(24, trend.points().size());
        assertTrue(trend.points().stream().anyMatch(point -> point.totalRuns() == 1));
        assertTrue(trend.points().stream().allMatch(point -> point.bucketStart() != null));
        assertTrue(trend.points().stream().allMatch(point -> point.label().contains(":")));
    }

    @Test
    void hourlyTrendLabelsShouldStayUniqueAcrossMultipleDays() {
        ModelEvaluationService service = ModelEvaluationService.inMemory();
        saveSuccessfulRun(service, "chat", "qwen2.5");

        ModelEvaluationTrend trend = service.trend("", "qwen2.5", null, null, 30, "hour", "scene", "avgF1");

        assertEquals(30 * 24, trend.points().size());
        assertEquals(
                trend.points().size(),
                trend.points().stream().map(ModelEvaluationTrendPoint::label).distinct().count()
        );
    }

    @Test
    void facetsShouldListRecordedModelsAndDimensions() {
        ModelEvaluationService service = ModelEvaluationService.inMemory();
        saveSuccessfulRun(service, "chat", "qwen2.5");
        saveSuccessfulRun(service, "prompt", "llama3.1");

        ModelEvaluationFacets facets = service.facets();

        assertEquals(2, facets.models().size());
        assertTrue(facets.models().contains("qwen2.5"));
        assertTrue(facets.models().contains("llama3.1"));
        assertTrue(facets.scenes().contains("chat"));
        assertTrue(facets.scenes().contains("prompt"));
        assertTrue(facets.providers().contains("ollama"));
        assertTrue(facets.requestSources().contains("chat"));
    }

    @Test
    void trendShouldReturnSeriesGroupedBySceneForComparison() {
        ModelEvaluationService service = ModelEvaluationService.inMemory();
        saveSuccessfulRun(service, "chat", "qwen2.5");
        saveSuccessfulRun(service, "prompt", "qwen2.5");

        ModelEvaluationTrend trend = service.trend("",
                "qwen2.5",
                null,
                null,
                7,
                "day",
                "scene",
                "avgF1");

        assertEquals("scene", trend.groupBy());
        assertEquals("avgF1", trend.metric());
        assertEquals(2, trend.series().size());
        assertTrue(trend.series().stream().anyMatch(series -> "chat".equals(series.name())));
        assertTrue(trend.series().stream().anyMatch(series -> "prompt".equals(series.name())));
        assertTrue(trend.series().stream()
                .flatMap(series -> series.points().stream())
                .anyMatch(point -> point.totalRuns() > 0 && point.avgF1() > 0));
    }

    private static void saveSuccessfulRun(ModelEvaluationService service, String scene, String model) {
        service.saveRun(
                scene,
                model,
                "ollama",
                ModelEvaluationService.STATUS_SUCCESS,
                true,
                1200,
                80,
                scene,
                Map.of("input", "test"),
                Map.of("output", "ok"),
                Map.of("source", "test"),
                "",
                Map.of(
                        ModelEvaluationService.METRIC_RECALL, 70.0,
                        ModelEvaluationService.METRIC_PRECISION, 80.0,
                        ModelEvaluationService.METRIC_F1, 74.0,
                        ModelEvaluationService.METRIC_TOP1, 60.0,
                        ModelEvaluationService.METRIC_TOP3, 65.0,
                        ModelEvaluationService.METRIC_TOP5, 68.0
                )
        );
    }
}
