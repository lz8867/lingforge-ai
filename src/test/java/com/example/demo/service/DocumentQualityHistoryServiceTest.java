package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityHistoryServiceTest {

    @Test
    void saveShouldPersistEvaluationSummaryAndResultJson() {
        DocumentQualityHistoryService service = DocumentQualityHistoryService.inMemory();

        service.save(sampleResult("方案A", 82));

        List<DocumentQualityHistoryRecord> records = service.recent(10);
        assertEquals(1, records.size());
        assertEquals("方案A", records.get(0).documentName());
        assertEquals(82, records.get(0).overallScore());
    }

    @Test
    void trendShouldReturnOrderedScorePointsAndAverageScore() {
        DocumentQualityHistoryService service = DocumentQualityHistoryService.inMemory();
        service.save(sampleResult("方案A", 72));
        service.save(sampleResult("方案A", 88));

        DocumentQualityTrendResult trend = service.trend("方案A", "");

        assertEquals("方案A", trend.scope());
        assertEquals(2, trend.points().size());
        assertEquals(80.0, trend.averageScore());
        assertTrue(trend.deltaScore() > 0);
    }

    @Test
    void recentShouldReturnRepositoryRecordsAsDtos() {
        DocumentQualityHistoryService service = DocumentQualityHistoryService.inMemory();
        service.save(sampleResult("方案A", 91));

        List<DocumentQualityHistoryRecord> records = service.recent(10);

        assertFalse(records.isEmpty());
        assertEquals("方案A", records.get(0).documentName());
        assertEquals(91, records.get(0).overallScore());
    }

    @Test
    void pageShouldReturnNewestRecordsFirstWithPaginationMetadata() {
        DocumentQualityHistoryService service = DocumentQualityHistoryService.inMemory();
        for (int i = 1; i <= 11; i++) {
            service.save(sampleResult("方案" + i, 60 + i));
        }

        DocumentQualityHistoryPage page = service.page(0, 5);

        assertEquals(0, page.page());
        assertEquals(5, page.size());
        assertEquals(11, page.totalElements());
        assertEquals(3, page.totalPages());
        assertEquals(5, page.records().size());
        assertEquals("方案11", page.records().get(0).documentName());
        assertEquals("方案7", page.records().get(4).documentName());
    }

    private DocumentQualityResult sampleResult(String documentName, int score) {
        return new DocumentQualityResult(
                true,
                "已完成",
                documentName,
                "技术设计文档",
                score,
                "良好文档（少量优化）",
                80,
                84,
                90,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("ragCoverageScore", 80.0, "llmJudgeSource", "heuristic")
        );
    }
}
