package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSummaryPageTest {

    @Test
    void documentSummaryPageShouldProvideStandaloneUploadAndSummaryWorkflow() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/document-summary.html"));

        assertTrue(page.contains("文档摘要"));
        assertTrue(page.contains("选择摘要文件"));
        assertTrue(page.contains("accept=\".docx,.pdf\""));
        assertTrue(page.contains("summary-file"));
        assertTrue(page.contains("summary-upload-button"));
        assertTrue(page.contains("summary-result-panel"));
        assertTrue(page.contains("summary-source"));
        assertTrue(page.contains("summary-key-points"));
        assertTrue(page.contains("summary-keywords"));
        assertTrue(page.contains("summary-fields"));
        assertTrue(page.contains("structured-summary-modules"));
        assertTrue(page.contains("extractDocumentSummary"));
        assertTrue(page.contains("fetch('/api/document-summary/summarize'"));
        assertTrue(page.contains("renderSummaryResult"));
        assertTrue(page.contains("renderSummaryFields"));
        assertTrue(page.contains("renderStructuredModules"));
        assertTrue(page.contains("renderSummaryFailure"));
        assertTrue(page.contains("模型摘要"));
        assertTrue(page.contains("本地摘要"));
        assertTrue(page.contains("AI 工程提示词"));
        assertTrue(page.contains("结构化合同摘要 JSON"));
        assertTrue(page.contains("先判断是否属于合同文本"));
        assertTrue(page.contains("销售、采购、服务、租赁合同按固定字段提取"));
        assertTrue(page.contains("标的与价款"));
        assertTrue(page.contains("履行方式"));
        assertTrue(page.contains("违约责任"));
        assertTrue(page.contains("争议解决"));
        assertTrue(page.contains("不输出隐藏思维链"));
        assertFalse(page.contains("/api/document-quality/summarize"));
    }

    @Test
    void documentSummaryShouldBeReachableFromPromptKnowledgeNavigation() throws Exception {
        String renderer = Files.readString(Path.of("src/main/resources/static/js/app-layout.js"));

        assertTrue(renderer.contains("document-summary"));
        assertTrue(renderer.contains("/document-summary.html"));
        assertTrue(renderer.contains("文档摘要"));
        assertTrue(renderer.contains("提取 PDF、DOCX 文档摘要、核心要点和关键词。"));
    }
}
