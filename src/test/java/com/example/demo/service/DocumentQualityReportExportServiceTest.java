package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityReportExportServiceTest {

    private final DocumentQualityReportExportService service = new DocumentQualityReportExportService();

    @Test
    void exportShouldGenerateHtmlReport() {
        DocumentQualityExportResult export = service.export(new DocumentQualityExportRequest("html", sampleResult()));

        assertTrue(export.success());
        assertEquals("text/html;charset=UTF-8", export.contentType());
        String html = new String(Base64.getDecoder().decode(export.contentBase64()), StandardCharsets.UTF_8);
        assertTrue(html.contains("文档质量评测报告"));
        assertTrue(html.contains("示例文档"));
        assertTrue(html.contains("补充风险说明"));
    }

    @Test
    void exportShouldGenerateJsonReport() {
        DocumentQualityExportResult export = service.export(new DocumentQualityExportRequest("json", sampleResult()));

        assertTrue(export.success());
        assertEquals("application/json;charset=UTF-8", export.contentType());
        String json = new String(Base64.getDecoder().decode(export.contentBase64()), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"documentName\""));
        assertTrue(json.contains("示例文档"));
    }

    @Test
    void exportShouldGeneratePdfReport() {
        DocumentQualityExportResult export = service.export(new DocumentQualityExportRequest("pdf", sampleResult()));

        assertTrue(export.success());
        assertEquals("application/pdf", export.contentType());
        byte[] bytes = Base64.getDecoder().decode(export.contentBase64());
        assertTrue(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    private DocumentQualityResult sampleResult() {
        return new DocumentQualityResult(
                true,
                "已完成文档质量评测。",
                "示例文档",
                "技术设计文档",
                86,
                "良好文档（少量优化）",
                82,
                88,
                91,
                List.of(new DocumentQualityDimensionScore("accuracy", "准确性", 86, 30, "warning", "证据充分", List.of("补充风险说明"))),
                List.of(new DocumentQualityIssue("RISK", "P2", "实用性", "全文", "缺少风险说明", 6, "补充风险说明", "未检测到风险章节")),
                List.of(new DocumentQualityMetric("issue_count", "问题数", 1, "个", "warning", "问题数量")),
                List.of("补充风险说明"),
                Map.of("llmJudgeSource", "heuristic")
        );
    }
}
