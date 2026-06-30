package com.example.demo.controller;

import com.example.demo.service.DocumentQualityRequest;
import com.example.demo.service.DocumentQualityResult;
import com.example.demo.service.DocumentQualityExtractionResult;
import com.example.demo.service.DocumentQualityFileService;
import com.example.demo.service.DocumentQualityHistoryRecord;
import com.example.demo.service.DocumentQualityHistoryService;
import com.example.demo.service.DocumentQualityJudgeClient;
import com.example.demo.service.DocumentQualityReportExportService;
import com.example.demo.service.DocumentQualityRagService;
import com.example.demo.service.DocumentQualityRuleConfigService;
import com.example.demo.service.DocumentQualityService;
import com.example.demo.service.DocumentQualityTrendPoint;
import com.example.demo.service.DocumentQualityTrendResult;
import com.example.demo.service.DocumentQualityExportRequest;
import com.example.demo.service.DocumentQualityExportResult;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.MemoryService;
import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.DTO.KnowledgeCaptureResult;
import com.example.demo.model.Knowledge;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentQualityControllerTest {

    @Test
    void evaluateEndpointShouldReturnStructuredQualityReport() {
        DocumentQualityController controller = new DocumentQualityController(
                new DocumentQualityService(),
                new DocumentQualityFileService()
        );

        DocumentQualityResult result = controller.evaluate(new DocumentQualityRequest(
                "知识库操作手册",
                "知识库文档",
                """
                        # 概述
                        介绍系统使用方式。

                        # 操作步骤
                        第一步进入首页，第二步点击评测按钮。
                        """
        ));

        assertTrue(result.success());
        assertTrue(result.overallScore() > 0);
        assertFalse(result.dimensionScores().isEmpty());
        assertFalse(result.metrics().isEmpty());
        assertTrue(result.visualizationData().containsKey("issueTypeDistribution"));
    }

    @Test
    void evaluateEndpointShouldCaptureKnowledgeWhenEnabled() {
        CapturingKnowledgeCaptureService captureService = new CapturingKnowledgeCaptureService();
        DocumentQualityController controller = new DocumentQualityController(
                new DocumentQualityService(),
                new DocumentQualityFileService(),
                new DocumentQualityReportExportService(),
                DocumentQualityHistoryService.disabled(),
                captureService,
                null
        );

        DocumentQualityResult result = controller.evaluate(
                new DocumentQualityRequest(
                        "知识库评测示例",
                        "技术文档",
                        "# 文档标题\n内容要完整并有结构。"
                ),
                true,
                "draft",
                "default_user"
        );

        assertTrue(result.success());
        assertTrue(captureService.captured.get());
        assertEquals("document-quality", captureService.lastRequest.get().getSourceType());
        assertEquals("document-quality-evaluate", captureService.lastRequest.get().getSourceScene());
        assertEquals("draft", captureService.lastRequest.get().getStatus());
        assertTrue(captureService.lastRequest.get().getTitle().contains("评测结果"));
    }

    @Test
    void evaluateEndpointShouldWriteToMemory() {
        MemoryService memoryService = mock(MemoryService.class);
        DocumentQualityController controller = new DocumentQualityController(
                new DocumentQualityService(),
                new DocumentQualityFileService(),
                new DocumentQualityReportExportService(),
                DocumentQualityHistoryService.disabled(),
                null,
                memoryService
        );

        DocumentQualityRequest request = new DocumentQualityRequest(
                "记忆策略文档",
                "技术文档",
                "# 概述\n要记录输入和输出并支持回放。"
        );
        DocumentQualityResult result = controller.evaluate(request, false, "draft", "user-memory");

        assertTrue(result.success());
        verify(memoryService).saveMemory("user-memory", "文档名: 记忆策略文档\n文档类型: 技术文档\n内容摘要: # 概述\n要记录输入和输出并支持回放。", "document-quality_prompt", null);
        verify(memoryService).saveMemory(
                "user-memory",
                "评测状态: true\n综合得分: " + result.overallScore() +
                        "\n评分等级: " + result.grade() +
                        "\n规则得分: " + result.ruleScore() +
                        "\nLLM得分: " + result.llmJudgeScore() +
                        "\n量化得分: " + result.metricScore() +
                        "\n问题数: " + result.issues().size() +
                        "\n建议: " + String.join("；", result.recommendations()),
                "document-quality_result",
                null
        );
    }

    @Test
    void extractEndpointShouldReadUploadedDocumentText() {
        DocumentQualityController controller = new DocumentQualityController(
                new DocumentQualityService(),
                new DocumentQualityFileService()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "知识库操作手册.md",
                "text/markdown",
                "# 概述\n## 操作步骤\n第一步进入首页。".getBytes(StandardCharsets.UTF_8)
        );

        DocumentQualityExtractionResult result = controller.extract(file);

        assertTrue(result.success());
        assertTrue(result.content().contains("操作步骤"));
        assertTrue(result.documentName().contains("知识库操作手册"));
    }

    @Test
    void exportEndpointShouldReturnDownloadableReport() {
        DocumentQualityController controller = new DocumentQualityController(
                new DocumentQualityService(),
                new DocumentQualityFileService(),
                new DocumentQualityReportExportService(),
                DocumentQualityHistoryService.disabled()
        );

        DocumentQualityExportResult result = controller.export(new DocumentQualityExportRequest(
                "html",
                new DocumentQualityResult(
                        true,
                        "已完成",
                        "导出示例",
                        "技术设计文档",
                        88,
                        "良好文档（少量优化）",
                        80,
                        90,
                        95,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("补充验收标准"),
                        Map.of()
                )
        ));

        assertTrue(result.success());
        assertTrue(result.fileName().endsWith(".html"));
        assertTrue(result.contentBase64().length() > 20);
    }

    @Test
    void historyAndTrendEndpointsShouldDelegateToHistoryService() {
        DocumentQualityHistoryService historyService = new DocumentQualityHistoryService() {
            @Override
            public List<DocumentQualityHistoryRecord> recent(int limit) {
                return List.of(new DocumentQualityHistoryRecord(null, "方案A", "技术设计文档", 86, "良好", 1, 80.0, "heuristic"));
            }

            @Override
            public DocumentQualityTrendResult trend(String documentName, String documentType) {
                return new DocumentQualityTrendResult("方案A", 86.0, 8.0, List.of());
            }
        };
        DocumentQualityController controller = new DocumentQualityController(
                new DocumentQualityService(),
                new DocumentQualityFileService(),
                new DocumentQualityReportExportService(),
                historyService
        );

        assertFalse(controller.history(10).isEmpty());
        assertTrue(controller.trend("方案A", "").averageScore() > 0);
    }

    @Test
    void controllerWorkflowShouldExtractEvaluateExportAndPersistHistory() {
        List<DocumentQualityHistoryRecord> records = new ArrayList<>();
        DocumentQualityHistoryService historyService = new DocumentQualityHistoryService() {
            @Override
            public void save(DocumentQualityResult result) {
                records.add(new DocumentQualityHistoryRecord(
                        LocalDateTime.now(),
                        result.documentName(),
                        result.documentType(),
                        result.overallScore(),
                        result.grade(),
                        result.issues().size(),
                        Number.class.cast(result.visualizationData().getOrDefault("ragCoverageScore", 0)).doubleValue(),
                        String.valueOf(result.visualizationData().getOrDefault("llmJudgeSource", "heuristic"))
                ));
            }

            @Override
            public List<DocumentQualityHistoryRecord> recent(int limit) {
                return records.stream().limit(limit).toList();
            }

            @Override
            public DocumentQualityTrendResult trend(String documentName, String documentType) {
                List<DocumentQualityTrendPoint> points = records.stream()
                        .filter(record -> documentName == null || documentName.isBlank() || documentName.equals(record.documentName()))
                        .map(record -> new DocumentQualityTrendPoint(
                                record.createdAt(),
                                record.documentName(),
                                record.documentType(),
                                record.overallScore(),
                                record.grade(),
                                record.issueCount()
                        ))
                        .toList();
                double average = points.stream()
                        .mapToInt(DocumentQualityTrendPoint::overallScore)
                        .average()
                        .orElse(0);
                return new DocumentQualityTrendResult(documentName, average, 0, points);
            }
        };
        DocumentQualityController controller = new DocumentQualityController(
                new DocumentQualityService(
                        new DocumentQualityRagService(),
                        DocumentQualityJudgeClient.disabled(),
                        new DocumentQualityRuleConfigService(),
                        historyService
                ),
                new DocumentQualityFileService(),
                new DocumentQualityReportExportService(),
                historyService
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "技术方案.md",
                "text/markdown",
                """
                        # 项目概述
                        建设文档质量评测系统，覆盖规则引擎、LLM-Judge、RAG 检索和量化指标。

                        # API 接口
                        POST /api/document-quality/evaluate 接收文档名称、类型和正文，返回综合分、问题、指标和建议。

                        # 数据模型
                        DocumentQualityResult 包含 overallScore、dimensionScores、issues、metrics、recommendations。

                        # 验收标准
                        支持 DOCX/PDF/Markdown 解析，支持 HTML/PDF/JSON 导出，评测历史可查询趋势。

                        # 风险与异常
                        外部 LLM 或 embedding 不可用时回落本地启发式评测。
                        """.getBytes(StandardCharsets.UTF_8)
        );

        DocumentQualityExtractionResult extraction = controller.extract(file);
        DocumentQualityResult evaluation = controller.evaluate(new DocumentQualityRequest(
                extraction.documentName(),
                extraction.documentType(),
                extraction.content()
        ));
        DocumentQualityExportResult export = controller.export(new DocumentQualityExportRequest("html", evaluation));
        List<DocumentQualityHistoryRecord> history = controller.history(5);
        DocumentQualityTrendResult trend = controller.trend(extraction.documentName(), "");

        assertTrue(extraction.success());
        assertTrue(extraction.content().contains("/api/document-quality/evaluate"));
        assertTrue(evaluation.success());
        assertTrue(evaluation.overallScore() > 0);
        assertFalse(evaluation.dimensionScores().isEmpty());
        assertFalse(evaluation.metrics().isEmpty());
        assertTrue(export.success());
        assertTrue(export.fileName().endsWith(".html"));
        assertFalse(history.isEmpty());
        assertTrue(history.get(0).documentName().contains("技术方案"));
        assertTrue(trend.points().size() >= 1);
    }

    private static class CapturingKnowledgeCaptureService implements KnowledgeCaptureService {
        private final AtomicBoolean captured = new AtomicBoolean(false);
        private final AtomicReference<KnowledgeCaptureRequest> lastRequest = new AtomicReference<>();

        @Override
        public KnowledgeCaptureResult capture(KnowledgeCaptureRequest request) {
            captured.set(true);
            lastRequest.set(request);
            Knowledge knowledge = new Knowledge();
            knowledge.setId(1L);
            knowledge.setTitle(request.getTitle());
            knowledge.setContent(request.getContent());
            knowledge.setStatus(request.getStatus());
            return KnowledgeCaptureResult.created(knowledge, "ok");
        }
    }
}
