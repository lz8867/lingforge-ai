package com.example.demo.controller;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.KnowledgeRetrievalResult;
import com.example.demo.service.KnowledgeRetrievalService;
import com.example.demo.service.MemoryService;
import com.example.demo.service.ModelEvaluationService;
import com.example.demo.service.VectorRagDemoService;
import com.example.demo.service.VectorRagDemoService.RagAnswerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagDemoController {

    private static final Logger log = LoggerFactory.getLogger(RagDemoController.class);
    private static final String RAG_MODEL = "qwen2.5";
    private static final String RAG_PROVIDER = "ollama";

    private final VectorRagDemoService vectorRagDemoService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final KnowledgeCaptureService knowledgeCaptureService;
    private final MemoryService memoryService;
    private final ModelEvaluationService modelEvaluationService;

    @Autowired
    public RagDemoController(
            VectorRagDemoService vectorRagDemoService,
            KnowledgeRetrievalService knowledgeRetrievalService,
            @Autowired(required = false) KnowledgeCaptureService knowledgeCaptureService,
            MemoryService memoryService) {
        this(vectorRagDemoService, knowledgeRetrievalService, knowledgeCaptureService, memoryService, ModelEvaluationService.disabled());
    }

    public RagDemoController(
            VectorRagDemoService vectorRagDemoService,
            KnowledgeRetrievalService knowledgeRetrievalService,
            KnowledgeCaptureService knowledgeCaptureService,
            MemoryService memoryService,
            ModelEvaluationService modelEvaluationService) {
        this.vectorRagDemoService = vectorRagDemoService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.knowledgeCaptureService = knowledgeCaptureService;
        this.memoryService = memoryService;
        this.modelEvaluationService = modelEvaluationService == null ? ModelEvaluationService.disabled() : modelEvaluationService;
    }

    @PostMapping("/vector-search")
    public Map<String, Object> vectorSearch(@RequestBody Map<String, Object> request) {
        String query = readString(request, "query");
        int topK = readTopK(request);
        log.info("Received vector search demo request, queryLength: {}, topK: {}", query.length(), topK);

        long startMs = System.currentTimeMillis();
        boolean success = false;
        KnowledgeRetrievalResult retrievalResult = fallbackRetrievalResult("rag-demo", query, "查询为空，不执行知识检索。");
        Map<String, Object> response = new HashMap<>();

        if (query.isBlank()) {
            log.warn("Vector search demo request rejected because query is blank");
            response.put("success", false);
            response.put("message", "请输入检索问题");
            response.put("query", query);
            response.put("topK", topK);
            response.put("trace", retrievalResult.trace());
            response.put("gateReason", retrievalResult.gateReason());
            saveRagRun("rag-vector-search", buildVectorSearchRequestPayload(query, topK), response, retrievalResult, success, startMs);
            return response;
        }

        try {
            retrievalResult = knowledgeRetrievalService.retrieve("rag-demo", query, topK);
            response.put("success", true);
            response.put("message", "向量检索成功");
            response.put("query", retrievalResult.query());
            response.put("topK", retrievalResult.topK());
            response.put("matches", retrievalResult.matches());
            response.put("trace", retrievalResult.trace());
            response.put("gateReason", retrievalResult.gateReason());
            response.put("vectorStore", "unified-knowledge");
            response.put("embeddingSource", "knowledge-retrieval");
            success = true;
        } catch (Exception e) {
            log.error("Vector search demo request failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "向量检索失败");
            response.put("error", e.getMessage());
            retrievalResult = fallbackRetrievalResult("rag-demo", query, e == null ? "unknown" : e.getMessage());
        } finally {
            saveRagRun("rag-vector-search", buildVectorSearchRequestPayload(query, topK), response, retrievalResult, success, startMs);
        }
        return response;
    }

    @PostMapping("/answer")
    public Map<String, Object> answer(@RequestBody Map<String, Object> request) {
        String question = readString(request, "question");
        int topK = readTopK(request);
        boolean captureKnowledge = readBoolean(request, "captureKnowledge");
        String userId = readString(request, "userId", "default_user");
        log.info("Received RAG answer demo request, questionLength: {}, topK: {}", question.length(), topK);

        long startMs = System.currentTimeMillis();
        boolean success = false;
        KnowledgeRetrievalResult retrievalResult = fallbackRetrievalResult("rag-demo", question, "问题为空，不执行知识检索。");
        Map<String, Object> response = new HashMap<>();

        if (question.isBlank()) {
            log.warn("RAG answer demo request rejected because question is blank");
            response.put("success", false);
            response.put("message", "请输入 RAG 问题");
            response.put("error", "question is blank");
            response.put("trace", retrievalResult.trace());
            response.put("gateReason", retrievalResult.gateReason());
            saveRagRun("rag-answer", buildRagRequestPayload(question, topK, captureKnowledge, userId), response, retrievalResult, success, startMs);
            return response;
        }

        try {
            retrievalResult = knowledgeRetrievalService.retrieve("rag-demo", question, topK);
            RagAnswerResponse answerResponse = vectorRagDemoService.answerWithKnowledgeContexts(
                    question,
                    topK,
                    retrievalResult.matches()
            );
            if (!userId.isBlank()) {
                memoryService.saveMemory(userId, question, "rag_question", null);
                memoryService.saveMemory(userId, answerResponse.answer(), "rag_ai_response", null);
            }
            if (captureKnowledge) {
                captureRagKnowledge(question, answerResponse.answer(), userId);
            }
            response.put("success", answerResponse.success());
            response.put("message", answerResponse.message());
            response.put("question", answerResponse.question());
            response.put("answer", answerResponse.answer());
            response.put("contexts", answerResponse.contexts());
            response.put("prompt", answerResponse.prompt());
            response.put("trace", retrievalResult.trace());
            response.put("gateReason", retrievalResult.gateReason());
            success = answerResponse.success();
        } catch (Exception e) {
            log.error("RAG answer demo request failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "RAG 回答失败");
            response.put("error", e.getMessage());
            response.put("trace", retrievalResult.trace());
            response.put("gateReason", retrievalResult.gateReason());
            retrievalResult = fallbackRetrievalResult("rag-demo", question, e == null ? "unknown" : e.getMessage());
        } finally {
            saveRagRun("rag-answer", buildRagRequestPayload(question, topK, captureKnowledge, userId), response, retrievalResult, success, startMs);
        }
        return response;
    }

    @GetMapping("/knowledge/status")
    public Map<String, Object> knowledgeStatus() {
        log.info("Received RAG knowledge status request");
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("message", "向量库状态查询成功");
            response.put("status", vectorRagDemoService.vectorStoreStatus());
        } catch (Exception e) {
            log.error("RAG knowledge status request failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "向量库状态查询失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/knowledge/chunks")
    public Map<String, Object> knowledgeChunks() {
        log.info("Received RAG knowledge chunks request");
        Map<String, Object> response = new HashMap<>();
        try {
            VectorRagDemoService.KnowledgeChunksResponse chunks = vectorRagDemoService.knowledgeChunks();
            response.put("success", true);
            response.put("message", "Chunk 列表查询成功");
            response.put("totalChunks", chunks.totalChunks());
            response.put("importedChunks", chunks.importedChunks());
            response.put("chunks", chunks.chunks());
        } catch (Exception e) {
            log.error("RAG knowledge chunks request failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Chunk 列表查询失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/knowledge/ingest")
    public Map<String, Object> ingestKnowledge(@RequestBody Map<String, Object> request) {
        String documentName = readString(request, "documentName");
        String content = readString(request, "content");
        String source = readString(request, "source");
        log.info("Received RAG knowledge ingest request, documentName={}, contentLength={}",
                documentName, content.length());

        Map<String, Object> response = new HashMap<>();
        try {
            VectorRagDemoService.KnowledgeIngestResponse ingest = vectorRagDemoService.ingestDocument(
                    documentName,
                    content,
                    source
            );
            response.put("success", ingest.success());
            response.put("message", ingest.message());
            response.put("documentName", ingest.documentName());
            response.put("chunkCount", ingest.chunkCount());
            response.put("totalChunks", ingest.totalChunks());
            response.put("chunks", ingest.chunks());
            response.put("vectorStore", ingest.vectorStore());
        } catch (Exception e) {
            log.error("RAG knowledge ingest request failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "知识入库失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/knowledge/clear")
    public Map<String, Object> clearKnowledge() {
        log.info("Received RAG knowledge clear request");
        Map<String, Object> response = new HashMap<>();
        try {
            VectorRagDemoService.KnowledgeClearResponse clear = vectorRagDemoService.clearKnowledgeBase();
            response.put("success", true);
            response.put("message", clear.message());
            response.put("removedChunkCount", clear.removedChunkCount());
            response.put("totalChunks", clear.totalChunks());
        } catch (Exception e) {
            log.error("RAG knowledge clear request failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "知识清理失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    private void saveRagRun(
            String requestSource,
            Map<String, Object> requestPayload,
            Map<String, Object> responsePayload,
            KnowledgeRetrievalResult retrievalResult,
            boolean success,
            long startMs) {
        Map<String, Double> metrics = modelEvaluationService.retrievalMetrics(retrievalResult);
        modelEvaluationService.saveRun(
                "rag",
                RAG_MODEL,
                RAG_PROVIDER,
                success ? ModelEvaluationService.STATUS_SUCCESS : ModelEvaluationService.STATUS_FAILURE,
                success,
                safeDurationMs(startMs),
                modelEvaluationService.estimateOverallScore(metrics, success),
                requestSource,
                requestPayload,
                responsePayload,
                buildRagRunMetadata(requestSource, retrievalResult, success),
                success ? "" : String.valueOf(responsePayload.getOrDefault("error", "rag run failed")),
                metrics
        );
    }

    private static Map<String, Object> buildRagRunMetadata(
            String requestSource,
            KnowledgeRetrievalResult retrievalResult,
            boolean success) {
        return Map.of(
                "endpoint", requestSource,
                "usedKnowledge", retrievalResult != null && retrievalResult.used(),
                "trace", retrievalResult == null ? List.of() : retrievalResult.trace(),
                "traceCount", retrievalResult == null || retrievalResult.trace() == null ? 0 : retrievalResult.trace().size(),
                "gateReason", retrievalResult == null ? "unknown" : retrievalResult.gateReason(),
                "topK", retrievalResult == null ? 0 : retrievalResult.topK(),
                "success", success
        );
    }

    private static Map<String, Object> buildVectorSearchRequestPayload(String query, int topK) {
        return Map.of(
                "query", safeText(query),
                "topK", topK,
                "captureKnowledge", false
        );
}

    private static Map<String, Object> buildRagRequestPayload(String question, int topK, boolean captureKnowledge, String userId) {
        return Map.of(
                "question", safeText(question),
                "topK", topK,
                "captureKnowledge", captureKnowledge,
                "userId", safeText(userId)
        );
    }

    private void captureRagKnowledge(String question, String answer, String userId) {
        if (knowledgeCaptureService == null) {
            return;
        }
        KnowledgeCaptureRequest request = new KnowledgeCaptureRequest();
        request.setTitle(buildRagCaptureTitle(question));
        request.setContent(buildRagCaptureContent(question, answer));
        request.setSourceType("rag-demo");
        request.setSourceId(userId);
        request.setSourceScene("rag-answer");
        request.setSourceReference("vector-search");
        request.setCreatedBy(userId == null || userId.isBlank() ? "default_user" : userId);
        request.setStatus("draft");
        request.setPublish(false);
        knowledgeCaptureService.capture(request);
    }

    private static String buildRagCaptureTitle(String question) {
        String safeQuestion = question == null ? "" : question.trim();
        if (safeQuestion.isBlank()) {
            return "RAG 回答记录";
        }
        String shortQuestion = safeQuestion.length() > 48 ? safeQuestion.substring(0, 48) : safeQuestion;
        return "RAG 回答：" + shortQuestion;
    }

    private static String buildRagCaptureContent(String question, String answer) {
        String safeQuestion = question == null ? "" : question.trim();
        String safeAnswer = answer == null ? "" : answer.trim();
        return "问题：\n" + safeQuestion + "\n\n回答：\n" + safeAnswer;
    }

    private static String readString(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String readString(Map<String, Object> request, String key, String fallback) {
        Object value = request == null ? null : request.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static int readTopK(Map<String, Object> request) {
        Object value = request == null ? null : request.get("topK");
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value != null) {
            try {
                return Math.max(1, Integer.parseInt(String.valueOf(value)));
            } catch (NumberFormatException ignored) {
                return 3;
            }
        }
        return 3;
    }

    private static boolean readBoolean(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return false;
    }

    private static KnowledgeRetrievalResult fallbackRetrievalResult(String scene, String query, String reason) {
        return new KnowledgeRetrievalResult(
                scene,
                query,
                3,
                false,
                safeText(reason, "未命中检索"),
                List.of(),
                List.of(reason == null ? "rag workflow fallback" : reason)
        );
    }

    private static long safeDurationMs(long startMs) {
        return Math.max(0, System.currentTimeMillis() - startMs);
    }

    private static String safeText(String value) {
        return safeText(value, "");
    }

    private static String safeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String safe = value.trim();
        return safe.isBlank() ? fallback : safe;
    }
}
