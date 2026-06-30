package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagDemoPageTest {

    @Test
    void ragDemoPageShouldExposeVectorSearchAndRagWorkflows() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/rag-demo.html"));

        assertTrue(page.contains("rag-twin-workbench"));
        assertTrue(page.contains("rag-twin-status-strip"));
        assertTrue(page.contains("rag-twin-topology-stage"));
        assertTrue(page.contains("rag-twin-flow-strip"));
        assertTrue(page.contains("rag-twin-node"));
        assertTrue(page.contains("drawRagTwinCanvas"));
        assertTrue(page.contains("updateRagTwinStatus"));
        assertTrue(page.contains("ragTwinState"));
        assertTrue(page.contains("数字孪生检索链路"));
        assertTrue(page.contains("知识入库"));
        assertTrue(page.contains("向量化"));
        assertTrue(page.contains("相似度召回"));
        assertTrue(page.contains("生成回答"));
        assertTrue(page.contains("证据下钻"));
        assertTrue(page.contains("知识库入库"));
        assertTrue(page.contains("向量库状态"));
        assertTrue(page.contains("Chunk 列表"));
        assertTrue(page.contains("检索调试"));
        assertTrue(page.contains("引用证据"));
        assertTrue(page.contains("Qdrant"));
        assertTrue(page.contains("RAG 回答"));
        assertTrue(page.contains("runVectorSearch"));
        assertTrue(page.contains("runRagAnswer"));
        assertTrue(page.contains("runIngestDocument"));
        assertTrue(page.contains("loadVectorStoreStatus"));
        assertTrue(page.contains("loadKnowledgeChunks"));
        assertTrue(page.contains("clearKnowledgeBase"));
        assertTrue(page.contains("fetch('/api/rag/vector-search'"));
        assertTrue(page.contains("fetch('/api/rag/answer'"));
        assertTrue(page.contains("fetch('/api/rag/knowledge/ingest'"));
        assertTrue(page.contains("fetch('/api/rag/knowledge/status'"));
        assertTrue(page.contains("fetch('/api/rag/knowledge/chunks'"));
        assertTrue(page.contains("fetch('/api/rag/knowledge/clear'"));
        assertTrue(page.contains("相似度"));
        assertTrue(page.contains("检索上下文"));
    }

    @Test
    void ragDemoShouldBeReachableFromIndex() throws Exception {
        String index = Files.readString(Path.of("src/main/resources/static/index.html"));

        assertTrue(index.contains("/rag-demo.html"));
        assertTrue(index.contains("向量检索 / RAG"));
    }

    @Test
    void ragDemoPageShouldKeepChunkListInsideWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/rag-demo.html"));

        assertFalse(page.contains("</section>\n</header>"));
        assertTrue(page.contains("body[data-app-page=\"rag-demo\"] #knowledge-chunks"));
        assertTrue(page.contains("min-width: 0;"));
        assertTrue(page.contains("max-width: 100%;"));
        assertTrue(page.contains("overflow-wrap: anywhere;"));
        assertTrue(page.contains("background: rgba(2, 8, 23, 0.36) !important;"));
    }

    @Test
    void ragDemoPageShouldUseCompactSingleViewportWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/rag-demo.html"));

        assertTrue(page.contains("height: calc(100vh - 56px);"));
        assertTrue(page.contains("clamp(180px, 24vh, 220px)"));
        assertTrue(page.contains("flex: 1 1 auto;"));
        assertTrue(page.contains("grid-template-rows: minmax(0, 0.44fr) minmax(0, 0.56fr);"));
        assertTrue(page.contains("rag-twin-qa-grid"));
        assertTrue(page.contains("rag-twin-evidence-grid"));
        assertTrue(page.contains(".rag-twin-action-grid {"));
        assertTrue(page.contains("flex: 0 0 auto;"));
        assertTrue(page.contains("#knowledge-content"));
        assertTrue(page.contains("#vector-query"));
        assertTrue(page.contains("#rag-question"));
    }

    @Test
    void ragDemoStatusStripShouldKeepSummaryTextReadable() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/rag-demo.html"));

        assertTrue(page.contains("grid-auto-rows: minmax(72px, auto);"));
        assertTrue(page.contains("min-height: 72px !important;"));
        assertTrue(page.contains("min-height: 72px;"));
        assertTrue(page.contains("overflow: visible;"));
        assertTrue(page.contains("line-height: 1.12;"));
    }
}
