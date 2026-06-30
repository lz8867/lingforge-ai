package com.example.demo.controller;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.DTO.PromptRequest;
import com.example.demo.config.PromptConstants;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.MemoryService;
import com.example.demo.service.ModelEvaluationService;
import com.example.demo.service.OllamaChatClient;
import com.example.demo.service.PromptOptimizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/prompt")
public class PromptController {

    private static final Logger log = LoggerFactory.getLogger(PromptController.class);
    private static final String PROMPT_MODEL = "qwen2.5";
    private static final String PROMPT_PROVIDER = "ollama";

    private final MemoryService memoryService;
    private final OllamaChatClient ollamaChatClient;
    private final PromptOptimizationService promptOptimizationService;
    private final KnowledgeCaptureService knowledgeCaptureService;
    private final ModelEvaluationService modelEvaluationService;

    public PromptController(
            MemoryService memoryService,
            OllamaChatClient ollamaChatClient,
            PromptOptimizationService promptOptimizationService,
            KnowledgeCaptureService knowledgeCaptureService) {
        this(memoryService, ollamaChatClient, promptOptimizationService, knowledgeCaptureService, ModelEvaluationService.disabled());
    }

    @Autowired
    public PromptController(
            MemoryService memoryService,
            OllamaChatClient ollamaChatClient,
            PromptOptimizationService promptOptimizationService,
            @Autowired(required = false) KnowledgeCaptureService knowledgeCaptureService,
            @Autowired(required = false) ModelEvaluationService modelEvaluationService) {
        this.memoryService = memoryService;
        this.ollamaChatClient = ollamaChatClient;
        this.promptOptimizationService = promptOptimizationService;
        this.knowledgeCaptureService = knowledgeCaptureService;
        this.modelEvaluationService = modelEvaluationService == null ? ModelEvaluationService.disabled() : modelEvaluationService;
    }

    @PostMapping("/test")
    public String testPrompt(
            @RequestBody PromptRequest request,
            @RequestParam(defaultValue = "default_user") String userId,
            @RequestParam(defaultValue = "false") boolean captureKnowledge,
            @RequestParam(defaultValue = "draft") String knowledgeStatus) {
        long startMs = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = "";
        String processedPrompt = request == null ? "" : processTemplate(request.getTemplate(), request.getVariables());
        String safeProcessedPrompt = processedPrompt == null ? "" : processedPrompt;
        String fallbackPrompt = safeProcessedPrompt;
        String response;

        Map<String, Object> responsePayload = new HashMap<>();

        try {
            memoryService.buildMemoryContext(userId);
            response = ollamaChatClient.generate(safeProcessedPrompt);
            success = true;
            responsePayload.put("success", true);
            responsePayload.put("response", response);
            responsePayload.put("mode", "prompt-test");
            memoryService.saveMemory(userId, safeProcessedPrompt, "prompt_template", request == null ? null : request.getTemplate());
            memoryService.saveMemory(userId, response, "ai_response", null);
            capturePromptTestKnowledge(request, safeProcessedPrompt, response, userId, knowledgeStatus, captureKnowledge);
        } catch (Exception e) {
            log.error("Prompt test error: {}", e.getMessage());
            success = false;
            errorMessage = e.getMessage();
            responsePayload.put("success", false);
            responsePayload.put("error", errorMessage);
            // 发生错误时，使用备用响应
            String fallbackResponse = "提示模板测试失败。\n\n" +
                    "处理后的提示：" + safeProcessedPrompt + "\n" +
                    "用户ID：" + userId + "\n" +
                    "错误信息：" + errorMessage;
            String backupTemplate = request == null ? null : request.getTemplate();
            response = fallbackResponse;
            responsePayload.put("response", response);
            responsePayload.put("mode", "prompt-test");
            memoryService.saveMemory(userId, fallbackPrompt, "prompt_template", backupTemplate);
            memoryService.saveMemory(userId, fallbackResponse, "ai_response", null);
            capturePromptTestKnowledge(request, safeProcessedPrompt, fallbackResponse, userId, knowledgeStatus, captureKnowledge);
        } finally {
            modelEvaluationService.saveRun(
                    "prompt",
                    safeText(ollamaChatClient.getModel(), PROMPT_MODEL),
                    safeText(ollamaChatClient.getProvider(), PROMPT_PROVIDER),
                    success ? ModelEvaluationService.STATUS_SUCCESS : ModelEvaluationService.STATUS_FAILURE,
                    success,
                    safeDurationMs(startMs),
                    modelEvaluationService.estimateOverallScore(modelEvaluationService.promptMetrics(Map.of(
                            "response", responsePayload.get("response"),
                            "summary", responsePayload.getOrDefault("response", ""),
                            "success", success
                    ), success), success),
                    "prompt-test",
                    buildPromptTestRequestPayload(request, userId, captureKnowledge, knowledgeStatus),
                    responsePayload,
                    buildPromptRunMetadata("test", userId, mapPromptRequest(request), safeProcessedPrompt, success),
                    errorMessage,
                    modelEvaluationService.promptMetrics(Map.of(
                            "response", responsePayload.get("response"),
                            "summary", responsePayload.getOrDefault("response", ""),
                            "success", success
                    ), success)
            );
        }

        return response;
    }

    @GetMapping("/templates")
    public Map<String, Object> getTemplates() {
        Map<String, Object> response = new HashMap<>();
        response.put("templates", PromptConstants.getTemplates());
        return response;
    }

    @PostMapping("/evaluate")
    public Map<String, Object> evaluatePrompt(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "false") boolean captureKnowledge,
            @RequestParam(defaultValue = "draft") String knowledgeStatus) {
        log.info("Prompt evaluation endpoint called");
        long startMs = System.currentTimeMillis();
        boolean success = false;
        String userId = readText(request, "userId", "default_user");
        Map<String, Object> response = new HashMap<>();

        try {
            response = promptOptimizationService.evaluate(request);
            success = Boolean.TRUE.equals(response.get("success"));
            if (Boolean.FALSE.equals(response.get("success"))) {
                log.warn("Prompt evaluation endpoint returned failure response");
            }
        } catch (Exception e) {
            log.error("Prompt evaluation endpoint failed: {}", e.getMessage());
            response = new HashMap<>();
            response.put("success", false);
            response.put("message", "大模型评测失败");
            response.put("error", e.getMessage());
            success = false;
        } finally {
            modelEvaluationService.saveRun(
                    "prompt",
                    safeText(ollamaChatClient.getModel(), PROMPT_MODEL),
                    safeText(ollamaChatClient.getProvider(), PROMPT_PROVIDER),
                    success ? ModelEvaluationService.STATUS_SUCCESS : ModelEvaluationService.STATUS_FAILURE,
                    success,
                    safeDurationMs(startMs),
                    modelEvaluationService.estimateOverallScore(modelEvaluationService.promptMetrics(response, success), success),
                    "prompt-evaluate",
                    buildPromptEvaluationRequestPayload(request, userId, captureKnowledge, knowledgeStatus, "evaluate"),
                    response == null ? Map.of() : response,
                    buildPromptRunMetadata("evaluate", userId, request, null, success),
                    success ? "" : String.valueOf(response == null ? "prompt evaluate failed" : response.getOrDefault("error", "")),
                    modelEvaluationService.promptMetrics(response, success)
            );
        }
        if (captureKnowledge) {
            capturePromptKnowledge(response, knowledgeStatus, "prompt-evaluate", "提示词评测记录");
        }
        return response;
    }

    @PostMapping("/optimize")
    public Map<String, Object> optimizePrompt(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "false") boolean captureKnowledge,
            @RequestParam(defaultValue = "draft") String knowledgeStatus) {
        log.info("Prompt optimization endpoint called");
        long startMs = System.currentTimeMillis();
        boolean success = false;
        String userId = readText(request, "userId", "default_user");
        Map<String, Object> response = new HashMap<>();

        try {
            response = promptOptimizationService.optimize(request);
            success = Boolean.TRUE.equals(response.get("success"));
            if (Boolean.FALSE.equals(response.get("success"))) {
                log.warn("Prompt optimization endpoint returned failure response");
            }
        } catch (Exception e) {
            log.error("Prompt optimization endpoint failed: {}", e.getMessage());
            response = new HashMap<>();
            response.put("success", false);
            response.put("message", "大模型优化失败");
            response.put("error", e.getMessage());
            success = false;
        } finally {
            Map<String, Object> safeResponse = response == null ? Map.of() : response;
            Map<String, Double> metrics = modelEvaluationService.promptMetrics(safeResponse, success);
            modelEvaluationService.saveRun(
                    "prompt",
                    safeText(ollamaChatClient.getModel(), PROMPT_MODEL),
                    safeText(ollamaChatClient.getProvider(), PROMPT_PROVIDER),
                    success ? ModelEvaluationService.STATUS_SUCCESS : ModelEvaluationService.STATUS_FAILURE,
                    success,
                    safeDurationMs(startMs),
                    modelEvaluationService.estimateOverallScore(metrics, success),
                    "prompt-optimize",
                    buildPromptEvaluationRequestPayload(request, userId, captureKnowledge, knowledgeStatus, "optimize"),
                    safeResponse,
                    buildPromptRunMetadata("optimize", userId, request, null, success),
                    success ? "" : String.valueOf(safeResponse.getOrDefault("error", "prompt optimize failed")),
                    metrics
            );
        }
        if (captureKnowledge) {
            capturePromptKnowledge(response, knowledgeStatus, "prompt-optimize", "提示词优化记录");
        }
        return response;
    }

    @GetMapping("/templates/{id}")
    public Map<String, Object> getTemplate(@PathVariable String id) {
        Map<String, Object> template = PromptConstants.getTemplate(id);
        if (template == null) {
            throw new IllegalArgumentException("Template not found with id: " + id);
        }
        return template;
    }

    private String processTemplate(String template, Map<String, String> variables) {
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }

    private void capturePromptTestKnowledge(
            PromptRequest request,
            String processedPrompt,
            String response,
            String userId,
            String knowledgeStatus,
            boolean captureKnowledge) {
        if (!captureKnowledge || knowledgeCaptureService == null) {
            return;
        }
        Map<String, Object> syntheticRequest = new HashMap<>();
        syntheticRequest.put("template", request == null ? "" : request.getTemplate());
        syntheticRequest.put("variables", request == null ? null : request.getVariables());
        syntheticRequest.put("userId", userId == null || userId.isBlank() ? "default_user" : userId);

        String safeResponse = response == null ? "" : response;
        String title = buildPromptTestCaptureTitle(processedPrompt);
        Object safeVariables = syntheticRequest.get("variables");
        if (safeVariables == null) {
            safeVariables = Map.of();
        }
        capturePromptKnowledge(Map.of(
                "success", true,
                "response", safeResponse,
                "message", safeResponse,
                "template", readText(syntheticRequest, "template", ""),
                "currentPrompt", processedPrompt,
                "userId", syntheticRequest.getOrDefault("userId", "system"),
                "variables", safeVariables
        ), knowledgeStatus, "prompt-test", title);
    }

    private void capturePromptKnowledge(
            Map<String, Object> response,
            String knowledgeStatus,
            String sourceScene,
            String titlePrefix) {
        if (knowledgeCaptureService == null) {
            return;
        }
        String normalizedStatus = normalizeStatus(knowledgeStatus);
        Map<String, Object> safeResponse = response == null ? Map.of() : response;
        KnowledgeCaptureRequest captureRequest = new KnowledgeCaptureRequest();
        captureRequest.setSourceType("prompt-engine");
        captureRequest.setSourceId(readText(safeResponse, "userId", "default_user"));
        captureRequest.setSourceScene(sourceScene);
        captureRequest.setSourceReference("/prompt/" + sourceScene.replace("prompt-", ""));
        captureRequest.setCreatedBy(readText(safeResponse, "userId", "system"));
        captureRequest.setStatus(normalizedStatus);
        captureRequest.setPublish("published".equalsIgnoreCase(normalizedStatus));

        captureRequest.setTitle(buildPromptKnowledgeTitle(titlePrefix, safeResponse));
        captureRequest.setContent(buildPromptKnowledgeContent(sourceScene, safeResponse));
        knowledgeCaptureService.capture(captureRequest);
    }

    private static String buildPromptKnowledgeTitle(String titlePrefix, Map<String, Object> request) {
        String sourcePrompt = readText(request, "currentPrompt", null);
        if (sourcePrompt == null || sourcePrompt.isBlank()) {
            sourcePrompt = readText(request, "template", "提示词测试");
        }
        String normalized = sourcePrompt.trim();
        if (normalized.isEmpty()) {
            return titlePrefix;
        }
        return titlePrefix + "：" + (normalized.length() > 45 ? normalized.substring(0, 45) : normalized);
    }

    private static String buildPromptKnowledgeContent(String sourceScene, Map<String, Object> response) {
        String safeSourceScene = sourceScene == null ? "prompt" : sourceScene;
        String currentPrompt = readText(response, "currentPrompt", "");
        String template = readText(response, "template", "");
        String userId = readText(response, "userId", "system");
        String summary = readText(response, "summary", "");
        String optimizedPrompt = readText(response, "optimizedPrompt", "");
        String message = readText(response, "message", "");
        String responseText = readText(response, "response", "");
        String safeResponseText = message.isBlank() ? responseText : message;
        return "来源: " + safeSourceScene + "\n" +
                "用户: " + userId + "\n" +
                "当前 Prompt: " + currentPrompt + "\n" +
                "模板: " + template + "\n" +
                (optimizedPrompt.isBlank() ? "\n评测摘要: " + summary : "\n优化后 Prompt: " + optimizedPrompt) +
                "\n模型返回: " + safeResponseText;
    }

    private static String buildPromptTestCaptureTitle(String processedPrompt) {
        String safePrompt = processedPrompt == null ? "" : processedPrompt.trim();
        if (safePrompt.isBlank()) {
            return "提示词测试记录";
        }
        String head = safePrompt.length() > 45 ? safePrompt.substring(0, 45) : safePrompt;
        return "提示词测试记录：" + head;
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return "draft";
        }
        String normalized = status.trim();
        return normalized.isBlank() ? "draft" : normalized;
    }

    private static String readText(Map<String, Object> request, String key, String fallback) {
        Object value = request == null ? null : request.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static Map<String, Object> buildPromptTestRequestPayload(
            PromptRequest request,
            String userId,
            boolean captureKnowledge,
            String knowledgeStatus) {
        String template = request == null || request.getTemplate() == null ? "" : request.getTemplate();
        return Map.of(
                "scene", "prompt-test",
                "template", template,
                "variables", request == null || request.getVariables() == null ? Map.of() : request.getVariables(),
                "userId", userId == null || userId.isBlank() ? "default_user" : userId,
                "captureKnowledge", captureKnowledge,
                "knowledgeStatus", knowledgeStatus == null ? "draft" : knowledgeStatus
        );
    }

    private static Map<String, Object> buildPromptEvaluationRequestPayload(
            Map<String, Object> request,
            String userId,
            boolean captureKnowledge,
            String knowledgeStatus,
            String scene) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        return Map.of(
                "scene", scene,
                "currentPrompt", safeRequest.getOrDefault("currentPrompt", ""),
                "template", safeRequest.getOrDefault("template", ""),
                "variables", safeRequest.getOrDefault("variables", Map.of()),
                "userId", userId == null || userId.isBlank() ? "default_user" : userId,
                "captureKnowledge", captureKnowledge,
                "knowledgeStatus", knowledgeStatus == null ? "draft" : knowledgeStatus
        );
    }

    private static Map<String, Object> buildPromptRunMetadata(
            String sourceScene,
            String userId,
            Map<String, Object> request,
            String fallbackPrompt,
            boolean success) {
        String safePrompt = fallbackPrompt == null ? readText(request, "currentPrompt", "") : fallbackPrompt;
        return Map.of(
                "sourceScene", sourceScene,
                "userId", userId == null || userId.isBlank() ? "default_user" : userId,
                "currentPromptLength", safePrompt.length(),
                "success", success
        );
    }

    private static Map<String, Object> mapPromptRequest(PromptRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> value = new HashMap<>();
        value.put("currentPrompt", request.getTemplate() == null ? "" : request.getTemplate());
        value.put("template", request.getTemplate() == null ? "" : request.getTemplate());
        value.put("variables", request.getVariables() == null ? Map.of() : request.getVariables());
        return value;
    }

    private static long safeDurationMs(long startMs) {
        return Math.max(0, System.currentTimeMillis() - startMs);
    }

    private static String safeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String safeValue = value.trim();
        return safeValue.isBlank() ? fallback : safeValue;
    }
}
