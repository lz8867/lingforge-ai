package com.example.demo.service;

import com.example.demo.model.DocumentQualityEvaluationRecord;
import com.example.demo.repository.DocumentQualityEvaluationRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class DocumentQualityHistoryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityHistoryService.class);

    private final DocumentQualityEvaluationRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final List<DocumentQualityEvaluationRecord> memoryRecords;

    public DocumentQualityHistoryService() {
        this(null, new ObjectMapper(), false, null);
    }

    @Autowired
    public DocumentQualityHistoryService(DocumentQualityEvaluationRecordRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, true, null);
    }

    private DocumentQualityHistoryService(
            DocumentQualityEvaluationRecordRepository repository,
            ObjectMapper objectMapper,
            boolean enabled,
            List<DocumentQualityEvaluationRecord> memoryRecords) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.memoryRecords = memoryRecords;
    }

    public static DocumentQualityHistoryService disabled() {
        return new DocumentQualityHistoryService();
    }

    static DocumentQualityHistoryService inMemory() {
        return new DocumentQualityHistoryService(null, new ObjectMapper(), true, new ArrayList<>());
    }

    public void save(DocumentQualityResult result) {
        if (!enabled || result == null || !result.success()) {
            return;
        }
        try {
            DocumentQualityEvaluationRecord record = new DocumentQualityEvaluationRecord();
            record.setDocumentName(safeText(result.documentName(), "未命名文档"));
            record.setDocumentType(safeText(result.documentType(), "通用文档"));
            record.setOverallScore(result.overallScore());
            record.setGrade(result.grade());
            record.setRuleScore(result.ruleScore());
            record.setLlmJudgeScore(result.llmJudgeScore());
            record.setMetricScore(result.metricScore());
            record.setIssueCount(result.issues() == null ? 0 : result.issues().size());
            record.setRagCoverageScore(asDouble(result.visualizationData(), "ragCoverageScore"));
            record.setLlmJudgeSource(safeText(String.valueOf(result.visualizationData().get("llmJudgeSource")), "heuristic"));
            record.setResultJson(toJson(result));
            record.setCreatedAt(LocalDateTime.now());
            if (memoryRecords != null) {
                memoryRecords.add(record);
            } else if (repository != null) {
                repository.save(record);
            }
        } catch (RuntimeException e) {
            log.warn("文档质量历史保存失败: {}", e.getMessage());
        }
    }

    public List<DocumentQualityHistoryRecord> recent(int limit) {
        if (!enabled) {
            return List.of();
        }
        if (memoryRecords != null) {
            return memoryRecords.stream()
                    .sorted(Comparator.comparing(DocumentQualityEvaluationRecord::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed())
                    .limit(Math.max(1, Math.min(100, limit)))
                    .map(this::toHistoryRecord)
                    .toList();
        }
        if (repository == null) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(100, limit)));
        return repository.findAllByOrderByCreatedAtDesc(pageable).stream()
                .map(this::toHistoryRecord)
                .toList();
    }

    public DocumentQualityHistoryPage page(int page, int size) {
        if (!enabled) {
            return new DocumentQualityHistoryPage(0, normalizeSize(size), 0, 0, List.of());
        }
        int safePage = Math.max(0, page);
        int safeSize = normalizeSize(size);
        if (memoryRecords != null) {
            List<DocumentQualityHistoryRecord> sortedRecords = memoryRecords.stream()
                    .sorted(Comparator.comparing(DocumentQualityEvaluationRecord::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed())
                    .map(this::toHistoryRecord)
                    .toList();
            long totalElements = sortedRecords.size();
            int totalPages = totalPages(totalElements, safeSize);
            List<DocumentQualityHistoryRecord> records = sortedRecords.stream()
                    .skip((long) safePage * safeSize)
                    .limit(safeSize)
                    .toList();
            return new DocumentQualityHistoryPage(safePage, safeSize, totalElements, totalPages, records);
        }
        if (repository == null) {
            return new DocumentQualityHistoryPage(safePage, safeSize, 0, 0, List.of());
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<DocumentQualityHistoryRecord> records = repository.findAll(pageable).stream()
                .map(this::toHistoryRecord)
                .toList();
        long totalElements = repository.count();
        return new DocumentQualityHistoryPage(safePage, safeSize, totalElements, totalPages(totalElements, safeSize), records);
    }

    public DocumentQualityTrendResult trend(String documentName, String documentType) {
        if (!enabled) {
            return new DocumentQualityTrendResult("暂无历史数据", 0, 0, List.of());
        }
        String safeName = safeText(documentName, "");
        String safeType = safeText(documentType, "");
        List<DocumentQualityEvaluationRecord> records;
        String scope;
        if (memoryRecords != null) {
            records = memoryRecords.stream()
                    .filter(record -> safeName.isBlank() || safeName.equals(record.getDocumentName()))
                    .filter(record -> !safeName.isBlank() || safeType.isBlank() || safeType.equals(record.getDocumentType()))
                    .sorted(Comparator.comparing(DocumentQualityEvaluationRecord::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            scope = !safeName.isBlank() ? safeName : !safeType.isBlank() ? safeType : "内存历史评测";
        } else if (repository == null) {
            records = List.of();
            scope = "暂无历史数据";
        } else if (!safeName.isBlank()) {
            records = repository.findByDocumentNameOrderByCreatedAtAsc(safeName);
            scope = safeName;
        } else if (!safeType.isBlank()) {
            records = repository.findByDocumentTypeOrderByCreatedAtAsc(safeType);
            scope = safeType;
        } else {
            records = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20)).stream()
                    .sorted(java.util.Comparator.comparing(DocumentQualityEvaluationRecord::getCreatedAt,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .toList();
            scope = "最近 20 次评测";
        }
        List<DocumentQualityTrendPoint> points = records.stream()
                .map(this::toTrendPoint)
                .toList();
        double average = round1(points.stream()
                .mapToInt(DocumentQualityTrendPoint::overallScore)
                .average()
                .orElse(0));
        double delta = points.size() < 2
                ? 0
                : points.get(points.size() - 1).overallScore() - points.get(0).overallScore();
        return new DocumentQualityTrendResult(scope, average, round1(delta), points);
    }

    private DocumentQualityHistoryRecord toHistoryRecord(DocumentQualityEvaluationRecord record) {
        return new DocumentQualityHistoryRecord(
                record.getCreatedAt(),
                record.getDocumentName(),
                record.getDocumentType(),
                record.getOverallScore(),
                record.getGrade(),
                record.getIssueCount(),
                record.getRagCoverageScore(),
                record.getLlmJudgeSource()
        );
    }

    private DocumentQualityTrendPoint toTrendPoint(DocumentQualityEvaluationRecord record) {
        return new DocumentQualityTrendPoint(
                record.getCreatedAt(),
                record.getDocumentName(),
                record.getDocumentType(),
                record.getOverallScore(),
                record.getGrade(),
                record.getIssueCount()
        );
    }

    private String toJson(DocumentQualityResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private double asDouble(Map<String, Object> data, String key) {
        if (data == null) {
            return 0;
        }
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() || "null".equals(value) ? fallback : value.trim();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(50, size));
    }

    private int totalPages(long totalElements, int size) {
        if (totalElements <= 0) {
            return 0;
        }
        return (int) Math.ceil(totalElements / (double) size);
    }
}
