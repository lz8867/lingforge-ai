package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityPageTest {

    @Test
    void documentQualityPageShouldExposeRealEvaluationWorkflow() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/document-quality.html"));

        assertTrue(page.contains("文档质量评测"));
        assertTrue(page.contains("选择文档文件"));
        assertTrue(page.contains(".pdf"));
        assertTrue(page.contains("PDF"));
        assertTrue(page.contains("规则引擎"));
        assertTrue(page.contains("LLM-Judge"));
        assertTrue(page.contains("量化指标"));
        assertTrue(page.contains("可视化报告"));
        assertTrue(page.contains("document-file"));
        assertTrue(page.contains("document-content"));
        assertTrue(page.contains("document-content-modal"));
        assertTrue(page.contains("openDocumentContentModal"));
        assertTrue(page.contains("查看解析正文"));
        assertTrue(page.contains("开始质量评测"));
        assertTrue(page.contains("extractDocumentFile"));
        assertTrue(page.contains("new FormData()"));
        assertTrue(page.contains("fetch('/api/document-quality/extract'"));
        assertTrue(page.contains("evaluateExtractedDocument"));
        assertTrue(page.contains("fetch('/api/document-quality/evaluate'"));
        assertTrue(page.contains("renderDimensionBars"));
        assertTrue(page.contains("renderDimensionCards"));
        assertTrue(page.contains("renderIssueTable"));
        assertTrue(page.contains("renderIssueSummary"));
        assertTrue(page.contains("openQualityDetailModal"));
        assertTrue(page.contains("renderMetricCards"));
        assertTrue(page.contains("renderRecommendations"));
        assertTrue(page.contains("renderRagRetrieval"));
        assertTrue(page.contains("renderExtractedFields"));
        assertTrue(page.contains("renderSkillPipeline"));
        assertTrue(page.contains("renderLlmJudgeSummary"));
        assertTrue(page.contains("exportQualityReport"));
        assertTrue(page.contains("fetch('/api/document-quality/export'"));
        assertTrue(page.contains("loadQualityHistory"));
        assertTrue(page.contains("/api/document-quality/history/page"));
        assertTrue(page.contains("loadQualityTrend"));
        assertTrue(page.contains("fetch('/api/document-quality/trend"));
        assertTrue(page.contains("LLM Judge 来源"));
        assertTrue(page.contains("RAG 检索证据"));
        assertTrue(page.contains("结构化字段"));
        assertTrue(page.contains("AI Skill 编排"));
        assertTrue(page.contains("ragRetrieval"));
        assertTrue(page.contains("extractedFields"));
        assertTrue(page.contains("skillPipeline"));
        assertTrue(page.contains("llmJudgeSource"));
        assertTrue(page.contains("scoreWeights"));
        assertFalse(page.contains("提取文档摘要"));
        assertFalse(page.contains("dq-summary-action"));
        assertFalse(page.contains("document-summary-result"));
        assertFalse(page.contains("summarizeDocumentFile"));
        assertFalse(page.contains("fetch('/api/document-quality/summarize'"));
        assertFalse(page.contains("示例报告生成失败"));
    }

    @Test
    void documentQualityHistoryShouldUsePagedNewestFirstTableAndTrendLineChart() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/document-quality.html"));

        assertTrue(page.contains("const qualityHistoryPageSize = 8"));
        assertTrue(page.contains("loadQualityHistory(0)"));
        assertTrue(page.contains("loadQualityHistory(0, { autoLoadTrend: true });"));
        assertTrue(page.contains("let qualityAutoTrendLoaded = false;"));
        assertTrue(page.contains("page=${page}"));
        assertTrue(page.contains("const shouldAutoLoadTrend = options && options.autoLoadTrend === true;"));
        assertTrue(page.contains("&& !qualityAutoTrendLoaded"));
        assertTrue(page.contains("renderHistoryPagination"));
        assertTrue(page.contains("sortHistoryRecordsDesc"));
        assertTrue(page.contains("上一页"));
        assertTrue(page.contains("下一页"));
        assertTrue(page.contains("quality-trend-chart"));
        assertTrue(page.contains("drawQualityTrendLine"));
        assertTrue(page.contains("requestAnimationFrame(() => drawQualityTrendLine(points))"));
        assertFalse(page.contains("renderTrendBars(points)"));
    }

    @Test
    void documentQualityPageShouldKeepLongDetailsBehindDrillDownActions() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/document-quality.html"));

        assertTrue(page.contains("dq-report-panel"));
        assertTrue(page.contains("dq-result-body"));
        assertTrue(page.contains("dq-score-hero"));
        assertTrue(page.contains("dq-dimension-card"));
        assertTrue(page.contains("dq-issue-summary-list"));
        assertTrue(page.contains("dq-detail-modal"));
        assertTrue(page.contains("id=\"quality-detail-modal\""));
        assertTrue(page.contains("id=\"document-content-modal\""));
        assertTrue(page.contains("查看全部问题"));
        assertTrue(page.contains("查看评分明细"));
        assertTrue(page.contains("body[data-app-layout=\"active\"] .dq-shell"));
        assertTrue(page.contains("body[data-app-layout=\"active\"] .dq-grid"));
        assertTrue(page.contains("body[data-app-layout=\"active\"] .dq-report-panel {\n                display: grid;"));
        assertTrue(page.contains("body[data-app-layout=\"active\"] .dq-result-body {\n                overflow-y: auto;"));
        assertTrue(page.contains("scrollbar-gutter: stable"));
        assertTrue(page.contains(".dq-panel {\n            min-height: 0;\n            display: flex;\n            flex-direction: column;\n            background: var(--dq-panel);\n            border: 1px solid var(--dq-border);\n            border-radius: 8px;\n            overflow: visible;"));
    }

    @Test
    void documentQualityShouldBeReachableFromGovernanceNavigation() throws Exception {
        String renderer = Files.readString(Path.of("src/main/resources/static/js/app-layout.js"));

        assertTrue(renderer.contains("document-quality"));
        assertTrue(renderer.contains("/document-quality.html"));
        assertTrue(renderer.contains("文档质量"));
        assertTrue(renderer.contains("评测文档内容质量"));
        assertTrue(renderer.contains("/document-summary.html"));
        assertTrue(renderer.contains("文档摘要"));
    }
}
