package com.example.demo.controller;

import com.example.demo.service.KnowledgeRetrievalHit;
import com.example.demo.service.KnowledgeRetrievalResult;
import com.example.demo.service.KnowledgeRetrievalService;
import com.example.demo.service.ModelEvaluationService;
import com.example.demo.service.ModelEvaluationRunRecord;
import com.example.demo.service.OllamaChatClient;
import com.example.demo.service.VectorRagDemoService;
import com.example.demo.service.MemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RagDemoControllerTest {

    @Test
    void ragEndpointsShouldUseUnifiedRetrievalEvidence() {
        CapturingOllamaChatClient client = new CapturingOllamaChatClient("基于知识库 RAG 证据回答。");
        VectorRagDemoService ragService = new VectorRagDemoService(client);
        KnowledgeRetrievalService retrievalService = new KnowledgeRetrievalService(null, null) {
            @Override
            public KnowledgeRetrievalResult retrieve(String scene, String query, int topK) {
                return new KnowledgeRetrievalResult(
                        scene,
                        query,
                        topK,
                        true,
                        "命中统一知识检索上下文。",
                        List.of(new KnowledgeRetrievalHit(
                                "knowledge:12",
                                "知识库 RAG 规范",
                                "RAG 回答必须引用检索证据，资料不足时明确说明缺口。",
                                0.93,
                                "knowledge",
                                "知识库",
                                Map.of("status", "published")
                        )),
                        List.of("loaded published knowledge: 1", "ranked knowledge hits: 1")
                );
            }
        };
        RagDemoController controller = new RagDemoController(ragService, retrievalService, null, mock(MemoryService.class));

        Map<String, Object> search = controller.vectorSearch(Map.of("query", "RAG 怎么回答", "topK", 3));
        Map<String, Object> answer = controller.answer(Map.of("question", "RAG 怎么回答", "topK", 3));

        assertEquals(true, search.get("success"));
        assertTrue(String.valueOf(search.get("matches")).contains("knowledge:12"));
        assertTrue(String.valueOf(search.get("matches")).contains("sourceType=knowledge"));
        assertEquals(true, answer.get("success"));
        assertTrue(String.valueOf(answer.get("contexts")).contains("knowledge:12"));
        assertTrue(client.lastPrompt.contains("knowledge:12"));
        assertTrue(client.lastPrompt.contains("资料不足时明确说明缺口"));
    }

    @Test
    void answerShouldRecordModelEvaluationMetrics() {
        CapturingOllamaChatClient client = new CapturingOllamaChatClient("基于知识库 RAG 证据回答。");
        VectorRagDemoService ragService = new VectorRagDemoService(client);
        KnowledgeRetrievalService retrievalService = new KnowledgeRetrievalService(null, null) {
            @Override
            public KnowledgeRetrievalResult retrieve(String scene, String query, int topK) {
                return new KnowledgeRetrievalResult(
                        scene,
                        query,
                        topK,
                        true,
                        "命中统一知识检索上下文。",
                        List.of(new KnowledgeRetrievalHit(
                                "knowledge:12",
                                "知识库 RAG 规范",
                                "RAG 回答必须引用检索证据，资料不足时明确说明缺口。",
                                0.93,
                                "knowledge",
                                "知识库",
                                Map.of("status", "published")
                        )),
                        List.of("loaded published knowledge: 1", "ranked knowledge hits: 1")
                );
            }
        };

        ModelEvaluationService modelEvaluationService = ModelEvaluationService.inMemory();
        RagDemoController controller = new RagDemoController(
                ragService,
                retrievalService,
                null,
                mock(MemoryService.class),
                modelEvaluationService
        );

        Map<String, Object> answer = controller.answer(Map.of(
                "question", "RAG 怎么回答",
                "topK", 3,
                "captureKnowledge", false,
                "userId", "tester"
        ));

        List<ModelEvaluationRunRecord> runs = modelEvaluationService.listRuns(
                "rag",
                null,
                null,
                "rag-answer",
                10
        );

        assertEquals(true, answer.get("success"));
        assertEquals(1, runs.size());
        assertEquals("rag", runs.get(0).scene());
        assertEquals("rag-answer", runs.get(0).requestSource());
        assertEquals("success", runs.get(0).status());
        assertTrue(runs.get(0).metrics().get(ModelEvaluationService.METRIC_RECALL) > 0);
        assertEquals(true, answer.get("trace") != null);
        assertTrue(client.lastPrompt.contains("knowledge:12"));
    }

    private static class CapturingOllamaChatClient extends OllamaChatClient {
        private final String response;
        private String lastPrompt;

        private CapturingOllamaChatClient(String response) {
            super("http://localhost:11434", "qwen2.5", 5000);
            this.response = response;
        }

        @Override
        public String generate(String prompt) {
            lastPrompt = prompt;
            return response;
        }
    }
}
