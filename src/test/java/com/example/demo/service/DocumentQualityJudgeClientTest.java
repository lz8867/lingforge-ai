package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityJudgeClientTest {

    @Test
    void judgeShouldSendRagContextToLlmAndParseStructuredDimensionScores() {
        FakeOllamaChatClient ollama = new FakeOllamaChatClient("""
                {
                  "summary": "RAG 证据完整，接口和验收可追溯。",
                  "dimensionScores": [
                    {
                      "id": "completeness",
                      "score": 88,
                      "evidence": "命中接口契约和验收证据",
                      "suggestions": ["补充错误码表"]
                    }
                  ]
                }
                """);
        DocumentQualityJudgeClient client = new DocumentQualityJudgeClient(ollama);
        DocumentQualityRagEvidence evidence = new DocumentQualityRagEvidence(
                "api_contract",
                "接口契约证据",
                "API 接口 请求 响应",
                true,
                0.72,
                List.of(new DocumentQualityRagHit("chunk-1", "API 接口", "POST /api/document-quality/evaluate 返回 issues。", 0.72, 1, 2))
        );
        DocumentQualityJudgeRequest request = new DocumentQualityJudgeRequest(
                "评测方案",
                "技术设计文档",
                "正文",
                List.of(),
                List.of(),
                List.of(new DocumentQualityDimensionScore("completeness", "完整性", 70, 20, "warning", "启发式", List.of("补充接口"))),
                Map.of("api_contract", evidence),
                List.of(Map.of(
                        "id", "api_contracts",
                        "name", "接口契约",
                        "status", "complete",
                        "values", List.of("POST /api/document-quality/evaluate 返回 issues。"),
                        "evidence", List.of(Map.of("chunkId", "chunk-1", "score", 0.72))
                ))
        );

        DocumentQualityJudgeResult result = client.judge(request);

        assertTrue(result.success());
        assertEquals("ollama", result.source());
        assertEquals(88, result.dimensionScores().get(0).score());
        assertTrue(result.summary().contains("RAG"));
        assertTrue(ollama.lastPrompt.contains("RAG 检索证据"));
        assertTrue(ollama.lastPrompt.contains("结构化字段抽取"));
        assertTrue(ollama.lastPrompt.contains("api_contracts"));
        assertTrue(ollama.lastPrompt.contains("POST /api/document-quality/evaluate"));
    }

    private static class FakeOllamaChatClient extends OllamaChatClient {
        private final String response;
        private String lastPrompt;

        private FakeOllamaChatClient(String response) {
            super(new RestTemplate(), "http://localhost:11434", "qwen2.5");
            this.response = response;
        }

        @Override
        public String generate(String prompt) {
            lastPrompt = prompt;
            return response;
        }
    }
}
