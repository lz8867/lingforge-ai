package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineruDemoPageTest {

    @Test
    void mineruPageShouldExposeDocumentExtractionWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/mineru-demo.html"));

        assertTrue(page.contains("MinerU 文档解析"));
        assertTrue(page.contains("mineru-workbench"));
        assertTrue(page.contains("mineru-upload-panel"));
        assertTrue(page.contains("mineru-preview-panel"));
        assertTrue(page.contains("mineru-block-list"));
        assertTrue(page.contains("mineru-json-preview"));
        assertTrue(page.contains("mineru-markdown-preview"));
        assertTrue(page.contains("mineru-file-input"));
        assertTrue(page.contains("parseMineruDocument"));
        assertTrue(page.contains("new FormData()"));
        assertTrue(page.contains("fetch('/api/mineru/parse'"));
        assertTrue(page.contains("downloadMineruMarkdown"));
        assertTrue(page.contains("downloadMineruJson"));
        assertTrue(page.contains("switchMineruTab"));
        assertTrue(page.contains("acceptedTypes"));
        assertTrue(page.contains(".pdf"));
        assertTrue(page.contains(".docx"));
        assertTrue(page.contains(".md"));
        assertTrue(page.contains(".html"));
        assertFalse(page.contains("text-6xl"));
        assertFalse(page.contains("rounded-lg shadow-lg p-8"));
    }

    @Test
    void mineruShouldBeReachableFromPromptKnowledgeNavigationAndHomeMap() throws Exception {
        String renderer = Files.readString(Path.of("src/main/resources/static/js/app-layout.js"));
        String home = Files.readString(Path.of("src/main/resources/static/index.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(renderer.contains("mineru"));
        assertTrue(renderer.contains("/mineru-demo.html"));
        assertTrue(renderer.contains("文档解析"));
        assertTrue(renderer.contains("把 PDF、DOCX、Markdown 等文档解析成 Markdown、结构块和 JSON。"));
        assertTrue(home.contains("data-app-link=\"mineru\""));
        assertTrue(home.contains("/mineru-demo.html"));
        assertTrue(stylesheet.contains("body[data-app-page=\"mineru-demo\"] .mineru-workbench"));
    }
}
