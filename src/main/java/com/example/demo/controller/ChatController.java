package com.example.demo.controller;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.KnowledgeRetrievalResult;
import com.example.demo.service.KnowledgeRetrievalService;
import com.example.demo.service.MemoryService;
import com.example.demo.service.ModelEvaluationService;
import com.example.demo.service.OllamaChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int CHAT_RAG_TOP_K = 4;

    private final MemoryService memoryService;
    private final OllamaChatClient ollamaChatClient;
    private final KnowledgeCaptureService knowledgeCaptureService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ModelEvaluationService modelEvaluationService;

    @Autowired
    public ChatController(
            MemoryService memoryService,
            OllamaChatClient ollamaChatClient,
            KnowledgeCaptureService knowledgeCaptureService,
            KnowledgeRetrievalService knowledgeRetrievalService,
            ModelEvaluationService modelEvaluationService) {
        this.memoryService = memoryService;
        this.ollamaChatClient = ollamaChatClient;
        this.knowledgeCaptureService = knowledgeCaptureService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.modelEvaluationService = modelEvaluationService;
    }

    public ChatController(
            MemoryService memoryService,
            OllamaChatClient ollamaChatClient,
            KnowledgeCaptureService knowledgeCaptureService,
            KnowledgeRetrievalService knowledgeRetrievalService) {
        this(memoryService, ollamaChatClient, knowledgeCaptureService, knowledgeRetrievalService, ModelEvaluationService.disabled());
    }

    @GetMapping("/chat")
    public Map<String, Object> chat(
            @RequestParam(defaultValue = "你好，请介绍一下你自己") String message,
            @RequestParam(defaultValue = "default_user") String userId,
            @RequestParam(defaultValue = "false") boolean captureKnowledge,
            @RequestParam(defaultValue = "draft") String knowledgeStatus) {

        Map<String, Object> response = new HashMap<>();
        long startMs = System.currentTimeMillis();
        boolean success = false;
        KnowledgeRetrievalResult retrievalResult = emptyRetrievalResult(message);

        try {
            String memoryContext = memoryService.buildMemoryContext(userId);
            retrievalResult = knowledgeRetrievalService.retrieve("chat", message, CHAT_RAG_TOP_K);
            String aiPrompt = buildChatPrompt(message, memoryContext, retrievalResult);
            String aiResponse = ollamaChatClient.generate(aiPrompt);

            if (captureKnowledge) {
                captureConversationKnowledge(userId, message, aiResponse, knowledgeStatus);
            }

            memoryService.saveMemory(userId, message, "user_input", null);
            memoryService.saveMemory(userId, aiResponse, "ai_response", null);

            response.put("success", true);
            response.put("response", aiResponse);
            response.put("retrievalUsed", retrievalResult.used());
            response.put("retrievalGateReason", retrievalResult.gateReason());
            response.put("retrievalTrace", retrievalResult.trace());
            success = true;
        } catch (Exception e) {
            log.error("Chat error: {}", e.getMessage());
            String fallbackResponse = "您好！我是一个基于Spring Boot 的聊天应用。由于AI服务暂时不可用，我无法提供AI功能，但我可以帮助您测试系统功能。\n\n"
                    + "您的消息：" + message + "\n"
                    + "用户ID：" + userId + "\n"
                    + "错误信息：" + e.getMessage();
            memoryService.saveMemory(userId, message, "user_input", null);
            memoryService.saveMemory(userId, fallbackResponse, "ai_response", null);
            response.put("success", false);
            response.put("message", "服务暂时不可用，请稍后重试");
            response.put("details", e.getMessage());
            response.put("response", fallbackResponse);
            retrievalResult = fallbackRetrievalResult(message, e);
            if (captureKnowledge) {
                captureConversationKnowledge(userId, message, fallbackResponse, knowledgeStatus);
            }
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            Map<String, Double> metrics = modelEvaluationService.retrievalMetrics(retrievalResult);
            int overallScore = modelEvaluationService.estimateOverallScore(metrics, success);
            modelEvaluationService.saveRun(
                    "chat",
                    safeText(ollamaChatClient.getModel(), "unknown"),
                    safeText(ollamaChatClient.getProvider(), "unknown"),
                    success ? ModelEvaluationService.STATUS_SUCCESS : ModelEvaluationService.STATUS_FAILURE,
                    success,
                    durationMs,
                    overallScore,
                    "chat",
                    buildChatRequestPayload(message, userId, captureKnowledge, knowledgeStatus),
                    response,
                    buildChatMetadata(message, userId, retrievalResult),
                    success ? "" : String.valueOf(response.getOrDefault("details", "")),
                    metrics
            );
        }

        return response;
    }

    @GetMapping("/api/test")
    public Map<String, Object> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Test endpoint works!");
        return response;
    }

    private static String buildChatPrompt(String message, String memoryContext, KnowledgeRetrievalResult retrievalResult) {
        String safeMessage = message == null ? "" : message.trim();
        String safeMemory = memoryContext == null ? "" : memoryContext.trim();
        StringBuilder prompt = new StringBuilder("你是一个稳健的业务助理，先按历史对话理解用户意图，再在知识检索上下文内回答问题。");
        if (!safeMemory.isBlank()) {
            prompt.append("\n【历史对话】\n").append(safeMemory);
        }
        prompt.append("\n").append(KnowledgeRetrievalService.formatContextForPrompt(retrievalResult));
        prompt.append("\n【用户问题】\n").append(safeMessage);
        prompt.append("\n【回答约束】\n1. 优先基于上下文作答；2. 缺口明确说明；3. 不要编造。");
        return prompt.toString();
    }

    private void captureConversationKnowledge(
            String userId,
            String message,
            String aiResponse,
            String knowledgeStatus) {
        if (knowledgeCaptureService == null) {
            return;
        }
        KnowledgeCaptureRequest request = new KnowledgeCaptureRequest();
        request.setTitle(buildCaptureTitle("对话记录：", message));
        request.setContent(buildCaptureContent(message, aiResponse));
        request.setSourceType("chat");
        request.setSourceId(userId);
        request.setSourceScene("chat");
        request.setSourceReference("conversation");
        request.setCreatedBy(userId);
        request.setStatus(knowledgeStatus);
        request.setCategoryId(null);
        request.setPublish("published".equalsIgnoreCase(knowledgeStatus));
        knowledgeCaptureService.capture(request);
    }

    private static String buildCaptureTitle(String prefix, String message) {
        if (message == null) {
            return prefix + "空消息";
        }
        String normalized = message.trim();
        if (normalized.isBlank()) {
            return prefix + "空消息";
        }
        String shortMessage = normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
        return prefix + shortMessage;
    }

    private static String buildCaptureContent(String message, String aiResponse) {
        String safeMessage = message == null ? "" : message.trim();
        String safeResponse = aiResponse == null ? "" : aiResponse.trim();
        return "用户问题：\n" + safeMessage + "\n\nAI 回答：\n" + safeResponse;
    }

    private static Map<String, Object> buildChatRequestPayload(
            String message,
            String userId,
            boolean captureKnowledge,
            String knowledgeStatus) {
        return Map.of(
                "message", message == null ? "" : message,
                "userId", userId == null ? "default_user" : userId,
                "captureKnowledge", captureKnowledge,
                "knowledgeStatus", knowledgeStatus == null ? "draft" : knowledgeStatus
        );
    }

    private static Map<String, Object> buildChatMetadata(String message, String userId, KnowledgeRetrievalResult retrievalResult) {
        return Map.of(
                "userId", userId == null ? "default_user" : userId,
                "messageLength", message == null ? 0 : message.length(),
                "topK", retrievalResult == null ? CHAT_RAG_TOP_K : retrievalResult.topK(),
                "usedKnowledge", retrievalResult != null && retrievalResult.used(),
                "trace", retrievalResult == null ? List.of() : retrievalResult.trace(),
                "gateReason", retrievalResult == null ? "unknown" : retrievalResult.gateReason()
        );
    }

    private static KnowledgeRetrievalResult emptyRetrievalResult(String query) {
        return new KnowledgeRetrievalResult(
                "chat",
                query,
                CHAT_RAG_TOP_K,
                false,
                "未执行知识检索。",
                List.of(),
                List.of("chat start")
        );
    }

    private static KnowledgeRetrievalResult fallbackRetrievalResult(String query, Exception exception) {
        String detail = exception == null ? "unknown" : String.valueOf(exception.getMessage());
        return new KnowledgeRetrievalResult(
                "chat",
                query,
                CHAT_RAG_TOP_K,
                false,
                "聊天流程失败",
                List.of(),
                List.of(detail)
        );
    }

    private static String safeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String safe = value.trim();
        return safe.isBlank() ? fallback : safe;
    }
}
