package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorRagDemoServiceTest {

    @Test
    void searchShouldReturnVectorRankedChunksForSemanticQuestion() {
        VectorRagDemoService service = new VectorRagDemoService(new FakeOllamaChatClient("unused"));

        VectorRagDemoService.VectorSearchResponse response = service.search("RAG 如何减少幻觉", 3);

        assertEquals("RAG 如何减少幻觉", response.query());
        assertEquals(3, response.topK());
        assertEquals(3, response.matches().size());
        assertEquals("rag-grounded-answer", response.matches().get(0).id());
        assertTrue(response.matches().get(0).score() >= response.matches().get(1).score());
        assertTrue(response.matches().get(0).content().contains("资料不足"));
    }

    @Test
    void answerShouldBuildGroundedPromptAndReturnContexts() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("基于资料，RAG 先检索上下文再回答。");
        VectorRagDemoService service = new VectorRagDemoService(client);

        VectorRagDemoService.RagAnswerResponse response = service.answer("RAG 如何减少幻觉", 2);

        assertTrue(response.success());
        assertEquals("基于资料，RAG 先检索上下文再回答。", response.answer());
        assertEquals(2, response.contexts().size());
        assertEquals("rag-grounded-answer", response.contexts().get(0).id());
        assertTrue(client.lastPrompt.contains("只允许依据【检索上下文】回答"));
        assertTrue(client.lastPrompt.contains("如果上下文不足以回答，请明确说无法从资料确认"));
        assertTrue(client.lastPrompt.contains("RAG 如何减少幻觉"));
        assertTrue(client.lastPrompt.contains("rag-grounded-answer"));
    }

    @Test
    void answerShouldKeepRetrievedContextWhenLlmFails() {
        FakeOllamaChatClient client = new FakeOllamaChatClient(new RuntimeException("connection refused"));
        VectorRagDemoService service = new VectorRagDemoService(client);

        VectorRagDemoService.RagAnswerResponse response = service.answer("向量检索怎么排序", 2);

        assertFalse(response.success());
        assertTrue(response.message().contains("大模型回答失败"));
        assertTrue(response.answer().contains("connection refused"));
        assertFalse(response.contexts().isEmpty());
        assertTrue(response.prompt().contains("向量检索怎么排序"));
    }

    @Test
    void ingestDocumentShouldCreateChunksAndMakeThemSearchable() {
        VectorRagDemoService service = new VectorRagDemoService(new FakeOllamaChatClient("unused"));

        VectorRagDemoService.KnowledgeIngestResponse ingest = service.ingestDocument(
                "合同质量规则",
                """
                        合同质量评审规则要求付款条款必须写清付款节点、付款条件和验收标准。

                        如果资料缺少验收标准，RAG 回答必须引用证据，并把缺口标记为 missing_info，不能猜测。
                        """,
                "manual"
        );

        assertTrue(ingest.success());
        assertEquals("合同质量规则", ingest.documentName());
        assertTrue(ingest.chunkCount() >= 1);
        assertTrue(ingest.totalChunks() >= ingest.chunkCount() + 4);
        assertTrue(ingest.chunks().get(0).metadata().containsKey("chunkIndex"));

        VectorRagDemoService.VectorSearchResponse response = service.search("付款条款缺少验收标准怎么办", 5);

        assertTrue(response.matches().stream().anyMatch(hit ->
                hit.title().contains("合同质量规则")
                        && hit.content().contains("验收标准")
                        && "manual".equals(hit.metadata().get("source"))));
        assertTrue(response.message().contains("本地向量索引") || response.message().contains("Qdrant"));
    }

    @Test
    void clearKnowledgeBaseShouldRemoveImportedChunksButKeepBuiltInSamples() {
        VectorRagDemoService service = new VectorRagDemoService(new FakeOllamaChatClient("unused"));
        VectorRagDemoService.KnowledgeIngestResponse ingest = service.ingestDocument(
                "临时知识",
                "临时知识只用于测试清理动作。检索时应该先能看到，清理后应该消失。",
                "manual"
        );

        VectorRagDemoService.KnowledgeClearResponse clear = service.clearKnowledgeBase();

        assertEquals(ingest.chunkCount(), clear.removedChunkCount());
        assertEquals(4, clear.totalChunks());
        assertTrue(service.knowledgeChunks().chunks().stream()
                .noneMatch(hit -> "临时知识".equals(hit.metadata().get("documentName"))));
        assertTrue(service.search("RAG 如何减少幻觉", 3).matches().stream()
                .anyMatch(hit -> "local-sample".equals(hit.metadata().get("source"))));
    }

    @Test
    void vectorStoreStatusShouldExposeLocalAndQdrantReadiness() {
        VectorRagDemoService service = new VectorRagDemoService(new FakeOllamaChatClient("unused"));

        VectorRagDemoService.VectorStoreStatus status = service.vectorStoreStatus();

        assertEquals("local", status.activeStore());
        assertEquals("local", status.provider());
        assertEquals(4, status.totalChunks());
        assertEquals(0, status.importedChunks());
        assertFalse(status.qdrantAvailable());
        assertTrue(status.message().contains("本地向量索引"));
    }

    private static class FakeOllamaChatClient extends OllamaChatClient {
        private final String response;
        private final RuntimeException exception;
        private String lastPrompt;

        private FakeOllamaChatClient(String response) {
            super(new RestTemplate(), "http://localhost:11434", "qwen2.5");
            this.response = response;
            this.exception = null;
        }

        private FakeOllamaChatClient(RuntimeException exception) {
            super(new RestTemplate(), "http://localhost:11434", "qwen2.5");
            this.response = null;
            this.exception = exception;
        }

        @Override
        public String generate(String prompt) {
            lastPrompt = prompt;
            if (exception != null) {
                throw exception;
            }
            return response;
        }
    }
}
