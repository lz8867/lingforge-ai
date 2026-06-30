package com.example.demo.service.impl;

import com.example.demo.service.KnowledgeRetrievalHit;
import com.example.demo.service.KnowledgeRetrievalResult;
import com.example.demo.service.KnowledgeRetrievalService;
import com.example.demo.service.OllamaChatClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceImplTest {

    @Test
    void aiAnswerQuestionShouldUseUnifiedRetrievalContext() {
        CapturingOllamaChatClient client = new CapturingOllamaChatClient("基于知识库证据回答。");
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
                                "knowledge:7",
                                "RAG 可信问答",
                                "资料不足时要明确说明缺口，不能编造。",
                                0.91,
                                "knowledge",
                                "知识库",
                                Map.of("status", "published")
                        )),
                        List.of("query", "ranked")
                );
            }
        };
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(null, null, null, client, retrievalService);

        String answer = service.aiAnswerQuestion("RAG 资料不足怎么办？", List.of());

        assertEquals("基于知识库证据回答。", answer);
        assertTrue(client.lastPrompt.contains("统一知识检索上下文"));
        assertTrue(client.lastPrompt.contains("knowledge:7"));
        assertTrue(client.lastPrompt.contains("资料不足时要明确说明缺口"));
        assertTrue(client.lastPrompt.contains("如果检索上下文不足"));
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
