package com.example.demo.service;

import com.example.demo.model.ModelEvaluationMetric;
import com.example.demo.model.ModelEvaluationRun;
import com.example.demo.repository.ModelEvaluationMetricRepository;
import com.example.demo.repository.ModelEvaluationRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@Service
public class ModelEvaluationService {

    public static final String METRIC_RECALL = "Recall";
    public static final String METRIC_PRECISION = "Precision";
    public static final String METRIC_F1 = "F1";
    public static final String METRIC_TOP1 = "Top1";
    public static final String METRIC_TOP3 = "Top3";
    public static final String METRIC_TOP5 = "Top5";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";
    public static final String GRANULARITY_DAY = "day";
    public static final String GRANULARITY_HOUR = "hour";
    public static final String GROUP_BY_METRIC = "metric";
    public static final String GROUP_BY_SCENE = "scene";
    public static final String GROUP_BY_MODEL = "model";
    private static final Logger log = LoggerFactory.getLogger(ModelEvaluationService.class);
    private static final String METRIC_UNKNOWN = "unknown";
    private static final List<String> DEFAULT_METRICS = List.of(
            METRIC_RECALL,
            METRIC_PRECISION,
            METRIC_F1,
            METRIC_TOP1,
            METRIC_TOP3,
            METRIC_TOP5
    );
    private static final double HIT_RELEVANT_THRESHOLD = 0.35;
    private static final Map<String, String> TREND_METRICS = Map.of(
            "avgRecall", METRIC_RECALL,
            "avgPrecision", METRIC_PRECISION,
            "avgF1", METRIC_F1,
            "avgTop1", METRIC_TOP1,
            "avgTop3", METRIC_TOP3,
            "avgTop5", METRIC_TOP5
    );

    private final ModelEvaluationRunRepository runRepository;
    private final ModelEvaluationMetricRepository metricRepository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final List<ModelEvaluationRun> memoryRuns;
    private final List<ModelEvaluationMetric> memoryMetrics;
    private final AtomicLong runIdSequence;

    public ModelEvaluationService() {
        this(null, null, false, new ArrayList<>(), new ArrayList<>(), new AtomicLong(1));
    }

    @Autowired
    public ModelEvaluationService(
            ModelEvaluationRunRepository runRepository,
            ModelEvaluationMetricRepository metricRepository,
            ObjectMapper objectMapper) {
        this(runRepository, metricRepository, objectMapper, true, null, null, null);
    }

    private ModelEvaluationService(
            ModelEvaluationRunRepository runRepository,
            ModelEvaluationMetricRepository metricRepository,
            boolean enabled,
            List<ModelEvaluationRun> memoryRuns,
            List<ModelEvaluationMetric> memoryMetrics,
            AtomicLong runIdSequence) {
        this(runRepository, metricRepository, new ObjectMapper(), enabled, memoryRuns, memoryMetrics, runIdSequence);
    }

    private ModelEvaluationService(
            ModelEvaluationRunRepository runRepository,
            ModelEvaluationMetricRepository metricRepository,
            ObjectMapper objectMapper,
            boolean enabled,
            List<ModelEvaluationRun> memoryRuns,
            List<ModelEvaluationMetric> memoryMetrics,
            AtomicLong runIdSequence) {
        this.runRepository = runRepository;
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.enabled = enabled;
        this.memoryRuns = memoryRuns;
        this.memoryMetrics = memoryMetrics;
        this.runIdSequence = runIdSequence == null ? new AtomicLong(1) : runIdSequence;
    }

    private static ModelEvaluationService withInMemoryStorage(boolean enabled) {
        return new ModelEvaluationService(null, null, enabled, new ArrayList<>(), new ArrayList<>(), new AtomicLong(1));
    }

    public static ModelEvaluationService disabled() {
        return withInMemoryStorage(false);
    }

    public static ModelEvaluationService inMemory() {
        return withInMemoryStorage(true);
    }

    public Long saveRun(
            String scene,
            String model,
            String provider,
            String status,
            boolean success,
            long durationMs,
            int overallScore,
            String requestSource,
            Object requestPayload,
            Object responsePayload,
            Object metadataPayload,
            String errorMessage,
            Map<String, Double> metrics) {

        if (!enabled) {
            return null;
        }
        if (runRepository == null && memoryRuns == null) {
            log.warn("评测落库未开启：runRepository 和内存存储都不可用，无法落库。" );
            return null;
        }

        ModelEvaluationRun run = new ModelEvaluationRun();
        run.setScene(safeText(scene, METRIC_UNKNOWN));
        run.setModel(safeText(model, "unknown"));
        run.setProvider(safeText(provider, "unknown"));
        run.setStatus(safeText(status, success ? STATUS_SUCCESS : STATUS_FAILURE));
        run.setSuccess(success);
        run.setDurationMs(Math.max(0, durationMs));
        run.setOverallScore(clampScore(overallScore));
        run.setRequestSource(safeText(requestSource, ""));
        run.setRequestJson(toJson(requestPayload));
        run.setResponseJson(toJson(responsePayload));
        run.setMetadataJson(toJson(metadataPayload));
        run.setErrorMessage(safeText(errorMessage, ""));
        run.setCreatedAt(LocalDateTime.now());

        Map<String, Double> normalizedMetrics = normalizeMetrics(metrics);
        long runId;

        if (runRepository != null) {
            ModelEvaluationRun saved = runRepository.save(run);
            runId = saved.getId();
            saveMetrics(saved.getId(), normalizedMetrics);
            return runId;
        }

        run.setId(runIdSequence.getAndIncrement());
        memoryRuns.add(run);
        runId = run.getId();
        saveMemoryMetrics(runId, normalizedMetrics);
        return runId;
    }

    public List<ModelEvaluationRunRecord> listRuns(
            String scene,
            String model,
            String provider,
            String requestSource,
            int limit) {
        if (!enabled) {
            return List.of();
        }
        List<ModelEvaluationRun> runs = queryRuns(scene, model, provider);
        List<ModelEvaluationRun> filteredRuns = runs.stream()
                .filter(run -> matchText(requestSource, run.getRequestSource()))
                .toList();
        int safeLimit = safeLimit(limit);
        List<Long> runIds = filteredRuns.stream()
                .limit(safeLimit)
                .map(ModelEvaluationRun::getId)
                .toList();
        Map<Long, Map<String, Double>> metricMap = loadMetricsMap(runIds);
        return filteredRuns.stream()
                .limit(safeLimit)
                .map(run -> toRecord(run, metricMap.get(run.getId())))
                .toList();
    }

    public ModelEvaluationSummary summary(String scene, String model, String provider, String requestSource) {
        if (!enabled) {
            return new ModelEvaluationSummary(
                    scope(scene, model, provider),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }
        List<ModelEvaluationRun> runs = queryRuns(scene, model, provider).stream()
                .filter(run -> matchText(requestSource, run.getRequestSource()))
                .toList();
        if (runs.isEmpty()) {
            return new ModelEvaluationSummary(
                    scope(scene, model, provider),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        Map<Long, Map<String, Double>> metricMap = loadMetricsMap(ids(runs));
        long successCount = runs.stream().filter(ModelEvaluationRun::isSuccess).count();
        long total = runs.size();

        double avgDurationMs = round2(runs.stream()
                .mapToLong(ModelEvaluationRun::getDurationMs)
                .average()
                .orElse(0));
        double avgOverall = round2(runs.stream()
                .mapToDouble(ModelEvaluationRun::getOverallScore)
                .average()
                .orElse(0));
        double avgRecall = averageMetric(runs, metricMap, METRIC_RECALL);
        double avgPrecision = averageMetric(runs, metricMap, METRIC_PRECISION);
        double avgF1 = averageMetric(runs, metricMap, METRIC_F1);
        double avgTop1 = averageMetric(runs, metricMap, METRIC_TOP1);
        double avgTop3 = averageMetric(runs, metricMap, METRIC_TOP3);
        double avgTop5 = averageMetric(runs, metricMap, METRIC_TOP5);
        double successRate = round2(total == 0 ? 0 : (successCount * 100.0 / total));

        return new ModelEvaluationSummary(
                scope(scene, model, provider),
                total,
                successCount,
                total - successCount,
                successRate,
                avgDurationMs,
                avgOverall,
                avgRecall,
                avgPrecision,
                avgF1,
                avgTop1,
                avgTop3,
                avgTop5
        );
    }

    public ModelEvaluationTrend trend(String scene, String model, String provider, String requestSource, int days) {
        return trend(scene, model, provider, requestSource, days, GRANULARITY_DAY);
    }

    public ModelEvaluationTrend trend(
            String scene,
            String model,
            String provider,
            String requestSource,
            int days,
            String granularity) {
        return trend(scene, model, provider, requestSource, days, granularity, GROUP_BY_METRIC, "avgF1");
    }

    public ModelEvaluationTrend trend(
            String scene,
            String model,
            String provider,
            String requestSource,
            int days,
            String granularity,
            String groupBy,
            String metric) {
        String safeGranularity = safeGranularity(granularity);
        String safeGroupBy = safeGroupBy(groupBy);
        String safeMetric = safeMetric(metric);
        if (!enabled) {
            return new ModelEvaluationTrend(
                    scope(scene, model, provider),
                    Math.max(1, days),
                    safeGranularity,
                    safeGroupBy,
                    safeMetric,
                    List.of(),
                    List.of()
            );
        }
        int safeDays = safeDays(days);
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> buckets = trendBuckets(now, safeDays, safeGranularity);
        LocalDateTime startAt = buckets.isEmpty() ? now : buckets.get(0);

        List<ModelEvaluationRun> runs = queryRuns(scene, model, provider).stream()
                .filter(run -> run.getCreatedAt() != null && !run.getCreatedAt().isBefore(startAt))
                .filter(run -> matchText(requestSource, run.getRequestSource())
                )
                .toList();
        Map<Long, Map<String, Double>> metricMap = loadMetricsMap(ids(runs));
        Map<LocalDateTime, List<ModelEvaluationRun>> grouped = new LinkedHashMap<>();

        for (ModelEvaluationRun run : runs) {
            LocalDateTime bucketStart = trendBucketStart(run.getCreatedAt(), safeGranularity);
            grouped.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(run);
        }
        List<ModelEvaluationTrendPoint> points = buckets.stream()
                .map(bucket -> buildTrendPoint(bucket, safeGranularity, grouped.getOrDefault(bucket, List.of()), metricMap))
                .toList();
        List<ModelEvaluationTrendSeries> series = buildTrendSeries(buckets, safeGranularity, safeGroupBy, safeMetric, runs, metricMap, points);
        return new ModelEvaluationTrend(scope(scene, model, provider), safeDays, safeGranularity, safeGroupBy, safeMetric, points, series);
    }

    public ModelEvaluationFacets facets() {
        if (!enabled) {
            return new ModelEvaluationFacets(List.of(), List.of(), List.of(), List.of());
        }
        List<ModelEvaluationRun> runs = queryByAll();
        return new ModelEvaluationFacets(
                distinctValues(runs, ModelEvaluationRun::getScene),
                distinctValues(runs, ModelEvaluationRun::getModel),
                distinctValues(runs, ModelEvaluationRun::getProvider),
                distinctValues(runs, ModelEvaluationRun::getRequestSource)
        );
    }

    public Map<String, Double> retrievalMetrics(KnowledgeRetrievalResult retrievalResult) {
        if (retrievalResult == null || retrievalResult.matches() == null || retrievalResult.matches().isEmpty()) {
            return zeroMetrics();
        }
        if (!retrievalResult.used()) {
            return zeroMetrics();
        }

        List<KnowledgeRetrievalHit> matches = retrievalResult.matches();
        int topK = Math.max(1, retrievalResult.topK());
        int relevant = (int) matches.stream().filter(hit -> hit.score() >= HIT_RELEVANT_THRESHOLD).count();
        double precision = clamp2(relevant * 100.0 / matches.size());
        double recall = clamp2(relevant * 100.0 / topK);
        double top1 = clamp2(bestScore(matches, 1));
        double top3 = clamp2(bestScore(matches, 3));
        double top5 = clamp2(bestScore(matches, 5));
        double f1 = harmonicMean(precision, recall);

        return new LinkedHashMap<>(Map.of(
                METRIC_RECALL, recall,
                METRIC_PRECISION, precision,
                METRIC_F1, f1,
                METRIC_TOP1, top1,
                METRIC_TOP3, top3,
                METRIC_TOP5, top5
        ));
    }

    public Map<String, Double> promptMetrics(Map<String, Object> response, boolean success) {
        double overall = parseOverallScore(response, success);
        double precision = overall;
        double recall = success ? clamp2(overall * 0.92) : 0;
        double f1 = harmonicMean(precision, recall);
        double top1 = clamp2(overall);
        double top3 = clamp2(overall * 0.95);
        double top5 = clamp2(overall * 0.9);

        return new LinkedHashMap<>(Map.of(
                METRIC_RECALL, recall,
                METRIC_PRECISION, precision,
                METRIC_F1, f1,
                METRIC_TOP1, top1,
                METRIC_TOP3, top3,
                METRIC_TOP5, top5
        ));
    }

    public int estimateOverallScore(Map<String, Double> metrics, boolean success) {
        if (!success) {
            return 0;
        }
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (String metric : DEFAULT_METRICS) {
            Double value = metrics.get(metric);
            total += value == null ? 0 : value;
        }
        return (int) Math.round(total / DEFAULT_METRICS.size());
    }

    private void saveMetrics(Long runId, Map<String, Double> metrics) {
        if (runId == null || runId <= 0 || metrics == null || metrics.isEmpty() || metricRepository == null) {
            return;
        }
        metrics.forEach((metricName, metricValue) -> {
            if (metricName == null || metricName.isBlank()) {
                return;
            }
            ModelEvaluationMetric metric = new ModelEvaluationMetric();
            metric.setRunId(runId);
            metric.setMetricName(metricName);
            metric.setMetricValue(metricValue);
            metricRepository.save(metric);
        });
    }

    private void saveMemoryMetrics(Long runId, Map<String, Double> metrics) {
        if (runId == null || runId <= 0 || metrics == null || metrics.isEmpty() || memoryMetrics == null) {
            return;
        }
        metrics.forEach((metricName, metricValue) -> {
            ModelEvaluationMetric metric = new ModelEvaluationMetric();
            metric.setRunId(runId);
            metric.setMetricName(metricName);
            metric.setMetricValue(metricValue);
            memoryMetrics.add(metric);
        });
    }

    private Map<Long, Map<String, Double>> loadMetricsMap(List<Long> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = runIds.stream().toList();
        List<ModelEvaluationMetric> metrics;
        if (metricRepository != null) {
            metrics = metricRepository.findByRunIdIn(ids);
        } else if (memoryMetrics != null) {
            metrics = memoryMetrics.stream()
                    .filter(metric -> ids.contains(metric.getRunId()))
                    .toList();
        } else {
            metrics = List.of();
        }

        Map<Long, Map<String, Double>> metricMap = new HashMap<>();
        for (ModelEvaluationMetric metric : metrics) {
            if (metric == null || metric.getRunId() == null || metric.getMetricName() == null) {
                continue;
            }
            metricMap.computeIfAbsent(metric.getRunId(), ignored -> new LinkedHashMap<>())
                    .put(metric.getMetricName(), clamp2(metric.getMetricValue()));
        }
        return metricMap;
    }

    private List<ModelEvaluationRun> queryRuns(String scene, String model, String provider) {
        if (!enabled) {
            return List.of();
        }
        String safeScene = safeText(scene, "");
        String safeModel = safeText(model, "");
        String safeProvider = safeText(provider, "");
        if (runRepository == null) {
            return queryByAll().stream()
                    .filter(run -> matchText(safeScene, run.getScene()))
                    .filter(run -> matchText(safeModel, run.getModel()))
                    .filter(run -> matchText(safeProvider, run.getProvider()))
                    .toList();
        }

        if (!safeScene.isBlank() && !safeModel.isBlank() && !safeProvider.isBlank()) {
            return queryBy(runRepository::findBySceneAndModelAndProviderOrderByCreatedAtDesc, safeScene, safeModel, safeProvider);
        }
        if (!safeScene.isBlank() && !safeProvider.isBlank()) {
            return runRepository.findBySceneAndProviderOrderByCreatedAtDesc(safeScene, safeProvider);
        }
        if (!safeModel.isBlank() && !safeProvider.isBlank()) {
            return runRepository.findByModelAndProviderOrderByCreatedAtDesc(safeModel, safeProvider);
        }
        if (!safeProvider.isBlank()) {
            return runRepository.findByProviderOrderByCreatedAtDesc(safeProvider);
        }
        if (!safeScene.isBlank() && !safeModel.isBlank()) {
            return runRepository.findBySceneAndModelOrderByCreatedAtDesc(safeScene, safeModel);
        }
        if (!safeScene.isBlank()) {
            return runRepository.findBySceneOrderByCreatedAtDesc(safeScene);
        }
        if (!safeModel.isBlank()) {
            return runRepository.findByModelOrderByCreatedAtDesc(safeModel);
        }
        return queryByAll();
    }

    private List<ModelEvaluationRun> queryBy(
            QueryFunction function,
            String scene,
            String model,
            String provider) {
        if (runRepository == null) {
            return memoryRuns == null
                    ? List.of()
                    : memoryRuns.stream()
                    .filter(run -> matchText(scene, run.getScene()))
                    .filter(run -> matchText(model, run.getModel()))
                    .filter(run -> matchText(provider, run.getProvider()))
                    .sorted(Comparator.comparing(ModelEvaluationRun::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        return function.apply(scene, model, provider);
    }

    private List<ModelEvaluationRun> queryByAll() {
        if (runRepository != null) {
            return runRepository.findAllByOrderByCreatedAtDesc();
        }
        if (memoryRuns == null) {
            return List.of();
        }
        return memoryRuns.stream()
                .filter(run -> run != null)
                .sorted(Comparator.comparing(ModelEvaluationRun::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @FunctionalInterface
    private interface QueryFunction {
        List<ModelEvaluationRun> apply(String scene, String model, String provider);
    }

    private ModelEvaluationRunRecord toRecord(ModelEvaluationRun run, Map<String, Double> metrics) {
        Map<String, Double> metricMap = normalizeMetrics(metrics);
        Map<String, Double> safeMetrics = new LinkedHashMap<>();
        for (String metricName : DEFAULT_METRICS) {
            safeMetrics.put(metricName, metricMap.getOrDefault(metricName, 0.0));
        }
        return new ModelEvaluationRunRecord(
                run.getId() == null ? 0 : run.getId(),
                safeText(run.getScene(), ""),
                safeText(run.getModel(), ""),
                safeText(run.getProvider(), ""),
                safeText(run.getStatus(), STATUS_FAILURE),
                run.isSuccess(),
                run.getDurationMs(),
                run.getOverallScore(),
                run.getRequestSource(),
                safeText(run.getErrorMessage(), ""),
                safeText(run.getRequestJson(), ""),
                safeText(run.getResponseJson(), ""),
                safeText(run.getMetadataJson(), ""),
                safeMetrics,
                run.getCreatedAt() == null ? LocalDateTime.now() : run.getCreatedAt()
        );
    }

    private ModelEvaluationTrendPoint buildTrendPoint(
            LocalDateTime bucketStart,
            String granularity,
            List<ModelEvaluationRun> runs,
            Map<Long, Map<String, Double>> metricMap) {
        long total = runs.size();
        LocalDate date = bucketStart.toLocalDate();
        String label = trendLabel(bucketStart, granularity);
        if (total == 0) {
            return new ModelEvaluationTrendPoint(date, bucketStart, label, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        long success = runs.stream().filter(ModelEvaluationRun::isSuccess).count();
        double successRate = round2(success * 100.0 / total);
        double avgOverall = round2(runs.stream()
                .mapToInt(ModelEvaluationRun::getOverallScore)
                .average()
                .orElse(0));
        double avgDuration = round2(runs.stream()
                .mapToLong(ModelEvaluationRun::getDurationMs)
                .average()
                .orElse(0));
        double recall = averageMetric(runs, metricMap, METRIC_RECALL);
        double precision = averageMetric(runs, metricMap, METRIC_PRECISION);
        double f1 = averageMetric(runs, metricMap, METRIC_F1);
        double top1 = averageMetric(runs, metricMap, METRIC_TOP1);
        double top3 = averageMetric(runs, metricMap, METRIC_TOP3);
        double top5 = averageMetric(runs, metricMap, METRIC_TOP5);

        return new ModelEvaluationTrendPoint(
                date,
                bucketStart,
                label,
                total,
                avgOverall,
                recall,
                precision,
                f1,
                top1,
                top3,
                top5,
                avgDuration,
                successRate
        );
    }

    private List<ModelEvaluationTrendSeries> buildTrendSeries(
            List<LocalDateTime> buckets,
            String granularity,
            String groupBy,
            String metric,
            List<ModelEvaluationRun> runs,
            Map<Long, Map<String, Double>> metricMap,
            List<ModelEvaluationTrendPoint> aggregatePoints) {
        if (GROUP_BY_METRIC.equals(groupBy)) {
            return TREND_METRICS.entrySet().stream()
                    .map(entry -> new ModelEvaluationTrendSeries(
                            entry.getValue(),
                            entry.getKey(),
                            entry.getKey(),
                            aggregatePoints.stream().mapToLong(ModelEvaluationTrendPoint::totalRuns).sum(),
                            aggregatePoints
                    ))
                    .toList();
        }

        Function<ModelEvaluationRun, String> classifier = GROUP_BY_MODEL.equals(groupBy)
                ? ModelEvaluationRun::getModel
                : ModelEvaluationRun::getScene;
        Map<String, List<ModelEvaluationRun>> groupedRuns = new LinkedHashMap<>();
        runs.stream()
                .sorted(Comparator.comparing(run -> safeText(classifier.apply(run), ""), String.CASE_INSENSITIVE_ORDER))
                .forEach(run -> {
                    String value = safeText(classifier.apply(run), "unknown");
                    groupedRuns.computeIfAbsent(value, ignored -> new ArrayList<>()).add(run);
                });

        return groupedRuns.entrySet().stream()
                .map(entry -> {
                    Map<LocalDateTime, List<ModelEvaluationRun>> groupedByBucket = new LinkedHashMap<>();
                    for (ModelEvaluationRun run : entry.getValue()) {
                        LocalDateTime bucketStart = trendBucketStart(run.getCreatedAt(), granularity);
                        groupedByBucket.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(run);
                    }
                    List<ModelEvaluationTrendPoint> points = buckets.stream()
                            .map(bucket -> buildTrendPoint(bucket, granularity, groupedByBucket.getOrDefault(bucket, List.of()), metricMap))
                            .toList();
                    return new ModelEvaluationTrendSeries(
                            entry.getKey(),
                            entry.getKey(),
                            metric,
                            entry.getValue().size(),
                            points
                    );
                })
                .toList();
    }

    private double averageMetric(List<ModelEvaluationRun> runs, Map<Long, Map<String, Double>> metricMap, String metricName) {
        if (runs == null || runs.isEmpty() || metricMap == null || metricMap.isEmpty()) {
            return 0;
        }
        double sum = 0;
        int count = 0;
        for (ModelEvaluationRun run : runs) {
            Double metric = metricMap
                    .getOrDefault(run.getId(), Map.of())
                    .get(metricName);
            if (metric != null) {
                sum += metric;
            }
            count++;
        }
        return round2(count == 0 ? 0 : sum / count);
    }

    private List<Long> ids(List<ModelEvaluationRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return List.of();
        }
        return runs.stream()
                .map(ModelEvaluationRun::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Map<String, Double> normalizeMetrics(Map<String, Double> metrics) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        if (metrics != null) {
            metrics.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    normalized.put(key, clamp2(value));
                }
            });
        }
        for (String metricName : DEFAULT_METRICS) {
            normalized.putIfAbsent(metricName, 0.0);
        }
        return normalized;
    }

    private Map<String, Double> zeroMetrics() {
        Map<String, Double> metrics = new LinkedHashMap<>();
        for (String metric : DEFAULT_METRICS) {
            metrics.put(metric, 0.0);
        }
        return metrics;
    }

    private double bestScore(List<KnowledgeRetrievalHit> matches, int limit) {
        if (matches == null || matches.isEmpty() || limit <= 0) {
            return 0;
        }
        int topLimit = Math.min(limit, matches.size());
        double sum = 0;
        for (int i = 0; i < topLimit; i++) {
            sum += matches.get(i).score();
        }
        return sum / topLimit * 100;
    }

    private double parseOverallScore(Map<String, Object> response, boolean success) {
        if (!success) {
            return 0;
        }
        Object overall = response == null ? null : response.get("overallScore");
        double score = asDouble(overall);
        if (score >= 0 && score <= 1) {
            score = score * 100;
        }
        if (score >= 0) {
            return clamp2(score);
        }
        if (response != null && response.get("optimizedPrompt") != null) {
            return 78;
        }
        if (response != null && response.get("summary") != null) {
            return 70;
        }
        return 60;
    }

    private double harmonicMean(double precision, double recall) {
        if (precision <= 0 || recall <= 0) {
            return 0;
        }
        double p = precision / 100;
        double r = recall / 100;
        return clamp2(2 * p * r / (p + r) * 100);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return -1;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("评测落库序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private boolean matchText(String target, String actual) {
        String safeTarget = safeText(target, "");
        if (safeTarget.isBlank()) {
            return true;
        }
        return safeTarget.equalsIgnoreCase(safeText(actual, ""));
    }

    private List<String> distinctValues(List<ModelEvaluationRun> runs, Function<ModelEvaluationRun, String> mapper) {
        if (runs == null || runs.isEmpty()) {
            return List.of();
        }
        return runs.stream()
                .map(mapper)
                .map(value -> safeText(value, ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<LocalDateTime> trendBuckets(LocalDateTime now, int days, String granularity) {
        List<LocalDateTime> buckets = new ArrayList<>();
        if (GRANULARITY_HOUR.equals(granularity)) {
            int bucketCount = Math.max(1, Math.min(24 * 30, days * 24));
            LocalDateTime start = now.truncatedTo(ChronoUnit.HOURS).minusHours(bucketCount - 1L);
            for (int index = 0; index < bucketCount; index++) {
                buckets.add(start.plusHours(index));
            }
            return buckets;
        }

        LocalDate startDate = now.toLocalDate().minusDays(days - 1L);
        for (int index = 0; index < days; index++) {
            buckets.add(startDate.plusDays(index).atStartOfDay());
        }
        return buckets;
    }

    private LocalDateTime trendBucketStart(LocalDateTime createdAt, String granularity) {
        if (GRANULARITY_HOUR.equals(granularity)) {
            return createdAt.truncatedTo(ChronoUnit.HOURS);
        }
        return createdAt.toLocalDate().atStartOfDay();
    }

    private String trendLabel(LocalDateTime bucketStart, String granularity) {
        if (GRANULARITY_HOUR.equals(granularity)) {
            return String.format(Locale.ROOT,
                    "%02d-%02d %02d:00",
                    bucketStart.getMonthValue(),
                    bucketStart.getDayOfMonth(),
                    bucketStart.getHour());
        }
        return String.format(Locale.ROOT, "%02d-%02d", bucketStart.getMonthValue(), bucketStart.getDayOfMonth());
    }

    private String safeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String safe = value.trim();
        return safe.isBlank() ? fallback : safe;
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(200, limit));
    }

    private int safeDays(int days) {
        return Math.max(1, Math.min(365, days));
    }

    private String safeGranularity(String granularity) {
        String safeValue = safeText(granularity, GRANULARITY_DAY).toLowerCase(Locale.ROOT);
        return GRANULARITY_HOUR.equals(safeValue) ? GRANULARITY_HOUR : GRANULARITY_DAY;
    }

    private String safeGroupBy(String groupBy) {
        String safeValue = safeText(groupBy, GROUP_BY_METRIC).toLowerCase(Locale.ROOT);
        if (GROUP_BY_SCENE.equals(safeValue) || GROUP_BY_MODEL.equals(safeValue)) {
            return safeValue;
        }
        return GROUP_BY_METRIC;
    }

    private String safeMetric(String metric) {
        String safeValue = safeText(metric, "avgF1");
        return TREND_METRICS.containsKey(safeValue) ? safeValue : "avgF1";
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private double clamp2(double value) {
        return Math.max(0, Math.min(100, round2(value)));
    }

    private String scope(String scene, String model, String provider) {
        String safeScene = safeText(scene, "");
        String safeModel = safeText(model, "");
        String safeProvider = safeText(provider, "");
        if (!safeScene.isBlank() || !safeModel.isBlank() || !safeProvider.isBlank()) {
            return String.format(Locale.ROOT, "scene=%s model=%s provider=%s",
                    safeScene.isBlank() ? "all" : safeScene,
                    safeModel.isBlank() ? "all" : safeModel,
                    safeProvider.isBlank() ? "all" : safeProvider);
        }
        return "all";
    }
}
