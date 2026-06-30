package com.example.demo.controller;

import com.example.demo.model.Knowledge;
import com.example.demo.model.KnowledgeCategory;
import com.example.demo.model.KnowledgeTag;
import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.DTO.KnowledgeCaptureResult;
import com.example.demo.service.KnowledgeService;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;
    private final KnowledgeCaptureService knowledgeCaptureService;
    private final MemoryService memoryService;

    @Autowired
    public KnowledgeController(
            KnowledgeService knowledgeService,
            @Autowired(required = false) KnowledgeCaptureService knowledgeCaptureService) {
        this(knowledgeService, null, knowledgeCaptureService);
    }

    public KnowledgeController(
            KnowledgeService knowledgeService,
            MemoryService memoryService,
            @Autowired(required = false) KnowledgeCaptureService knowledgeCaptureService) {
        this.knowledgeService = knowledgeService;
        this.memoryService = memoryService;
        this.knowledgeCaptureService = knowledgeCaptureService;
    }

    // 知识库相关接口
    @GetMapping("/list")
    public Map<String, Object> getKnowledgeList() {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<Knowledge> knowledgeList = knowledgeService.getAllKnowledge();
            response.put("success", true);
            response.put("data", knowledgeList);
            response.put("message", "获取知识库列表成功");
        } catch (Exception e) {
            log.error("获取知识库列表失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取知识库列表失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/category/{id}")
    public Map<String, Object> getKnowledgeByCategory(@PathVariable Long id) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<Knowledge> knowledgeList = knowledgeService.getKnowledgeByCategory(id);
            response.put("success", true);
            response.put("data", knowledgeList);
            response.put("message", "获取分类知识库成功");
        } catch (Exception e) {
            log.error("获取分类知识库失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取分类知识库失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/search")
    public Map<String, Object> searchKnowledge(@RequestParam String keyword) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<Knowledge> knowledgeList = knowledgeService.searchKnowledge(keyword);
            response.put("success", true);
            response.put("data", knowledgeList);
            response.put("message", "搜索知识库成功");
        } catch (Exception e) {
            log.error("搜索知识库失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "搜索知识库失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/tag/{tagName}")
    public Map<String, Object> getKnowledgeByTag(@PathVariable String tagName) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<Knowledge> knowledgeList = knowledgeService.getKnowledgeByTag(tagName);
            response.put("success", true);
            response.put("data", knowledgeList);
            response.put("message", "获取标签知识库成功");
        } catch (Exception e) {
            log.error("获取标签知识库失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取标签知识库失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/detail/{id}")
    public Map<String, Object> getKnowledgeDetail(@PathVariable Long id) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            Knowledge knowledge = knowledgeService.getKnowledgeById(id);
            if (knowledge != null) {
                response.put("success", true);
                response.put("data", knowledge);
                response.put("message", "获取知识库详情成功");
            } else {
                response.put("success", false);
                response.put("message", "知识库不存在");
            }
        } catch (Exception e) {
            log.error("获取知识库详情失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取知识库详情失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/create")
    public Map<String, Object> createKnowledge(@RequestBody Knowledge knowledge) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            Knowledge createdKnowledge = knowledgeService.createKnowledge(knowledge);
            response.put("success", true);
            response.put("data", createdKnowledge);
            response.put("message", "创建知识库成功");
        } catch (Exception e) {
            log.error("创建知识库失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "创建知识库失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PutMapping("/update")
    public Map<String, Object> updateKnowledge(@RequestBody Knowledge knowledge) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            Knowledge updatedKnowledge = knowledgeService.updateKnowledge(knowledge);
            response.put("success", true);
            response.put("data", updatedKnowledge);
            response.put("message", "更新知识库成功");
        } catch (Exception e) {
            log.error("更新知识库失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "更新知识库失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteKnowledge(@PathVariable Long id) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            knowledgeService.deleteKnowledge(id);
            response.put("success", true);
            response.put("message", "删除知识库成功");
        } catch (Exception e) {
            log.error("删除知识库失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除知识库失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    // AI相关接口
    @PostMapping("/ai/generate")
    public Map<String, Object> aiGenerateKnowledge(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "false") boolean captureKnowledge,
            @RequestParam(defaultValue = "draft") String knowledgeStatus,
            @RequestParam(defaultValue = "system") String userId) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            String prompt = request.get("prompt");
            if (prompt == null || prompt.isEmpty()) {
                response.put("success", false);
                response.put("message", "请提供生成提示");
                return response;
            }

            String generatedContent = knowledgeService.aiGenerateKnowledge(prompt);
            saveKnowledgeMemory(userId, prompt, "knowledge_prompt", prompt);
            saveKnowledgeMemory(userId, generatedContent, "knowledge_ai_response", null);
            response.put("success", true);
            response.put("message", "AI生成知识库内容成功");
            response.put("content", generatedContent);
            if (captureKnowledge) {
                captureKnowledgeGenerate(prompt, generatedContent, knowledgeStatus, userId);
            }
        } catch (Exception e) {
            log.error("AI生成知识库内容失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "AI生成知识库内容失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/ai/summarize/{id}")
    public Map<String, Object> aiSummarizeKnowledge(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean captureKnowledge,
            @RequestParam(defaultValue = "draft") String knowledgeStatus,
            @RequestParam(defaultValue = "system") String userId) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            String summary = knowledgeService.aiSummarizeKnowledge(id);
            saveKnowledgeMemory(userId, String.valueOf(id), "knowledge_prompt", "knowledge-summary");
            saveKnowledgeMemory(userId, summary, "knowledge_summary", null);
            response.put("success", true);
            response.put("message", "AI总结知识库内容成功");
            response.put("summary", summary);
            if (captureKnowledge) {
                captureKnowledgeSummarize(id, summary, knowledgeStatus, userId);
            }
        } catch (Exception e) {
            log.error("AI总结知识库内容失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "AI总结知识库内容失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    private void captureKnowledgeGenerate(
            String prompt,
            String generatedContent,
            String knowledgeStatus,
            String userId) {
        if (knowledgeCaptureService == null) {
            return;
        }
        KnowledgeCaptureRequest captureRequest = new KnowledgeCaptureRequest();
        captureRequest.setTitle(buildKnowledgeGenerateCaptureTitle(prompt));
        captureRequest.setContent(buildKnowledgeGenerateCaptureContent(prompt, generatedContent));
        captureRequest.setSourceType("knowledge-controller-generate");
        captureRequest.setSourceId(defaultText(userId, "system"));
        captureRequest.setSourceScene("knowledge-ai-generate");
        captureRequest.setSourceReference("/api/knowledge/ai/generate");
        captureRequest.setCreatedBy(defaultText(userId, "system"));
        String normalizedStatus = defaultText(knowledgeStatus, "draft");
        captureRequest.setStatus(normalizedStatus);
        captureRequest.setPublish("published".equalsIgnoreCase(normalizedStatus));
        knowledgeCaptureService.capture(captureRequest);
    }

    private void captureKnowledgeSummarize(
            Long knowledgeId,
            String summary,
            String knowledgeStatus,
            String userId) {
        if (knowledgeCaptureService == null) {
            return;
        }
        KnowledgeCaptureRequest captureRequest = new KnowledgeCaptureRequest();
        captureRequest.setTitle(buildKnowledgeSummarizeCaptureTitle(knowledgeId, summary));
        captureRequest.setContent(buildKnowledgeSummarizeCaptureContent(knowledgeId, summary));
        captureRequest.setSourceType("knowledge-controller-summarize");
        captureRequest.setSourceId(String.valueOf(knowledgeId));
        captureRequest.setSourceScene("knowledge-ai-summarize");
        captureRequest.setSourceReference("/api/knowledge/ai/summarize/" + knowledgeId);
        captureRequest.setCreatedBy(defaultText(userId, "system"));
        String normalizedStatus = defaultText(knowledgeStatus, "draft");
        captureRequest.setStatus(normalizedStatus);
        captureRequest.setPublish("published".equalsIgnoreCase(normalizedStatus));
        knowledgeCaptureService.capture(captureRequest);
    }

    private static String buildKnowledgeGenerateCaptureTitle(String prompt) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        if (safePrompt.isBlank()) {
            return "知识库生成：未命名任务";
        }
        String shortPrompt = safePrompt.length() > 38 ? safePrompt.substring(0, 38) : safePrompt;
        return "知识库生成：" + shortPrompt;
    }

    private static String buildKnowledgeGenerateCaptureContent(String prompt, String generatedContent) {
        String safePrompt = prompt == null ? "" : prompt;
        String safeGenerated = generatedContent == null ? "" : generatedContent;
        return "提示：\n" + safePrompt + "\n\n生成内容：\n" + safeGenerated;
    }

    private static String buildKnowledgeSummarizeCaptureTitle(Long id, String summary) {
        String safeSummary = summary == null ? "" : summary.trim();
        if (safeSummary.isBlank()) {
            return "知识库总结：" + id;
        }
        String shortSummary = safeSummary.length() > 32 ? safeSummary.substring(0, 32) : safeSummary;
        return "知识库总结：" + shortSummary;
    }

    private static String buildKnowledgeSummarizeCaptureContent(Long id, String summary) {
        String safeSummary = summary == null ? "" : summary;
        return "知识库 ID: " + id + "\n\n" + safeSummary;
    }

    private static String defaultText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.trim();
        return text.isBlank() ? fallback : text;
    }

    @PostMapping("/ai/answer")
    public Map<String, Object> aiAnswerQuestion(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            String question = (String) request.get("question");
            if (question == null || question.isEmpty()) {
                response.put("success", false);
                response.put("message", "请提供问题");
                return response;
            }
            String userId = readText(request, "userId", "system");

            // 获取相关知识库内容作为上下文
            List<Knowledge> contextKnowledge = knowledgeService.searchKnowledge(question);
            String answer = knowledgeService.aiAnswerQuestion(question, contextKnowledge);
            saveKnowledgeMemory(userId, question, "knowledge_question", null);
            saveKnowledgeMemory(userId, answer, "knowledge_ai_response", null);
            boolean captureKnowledge = toBoolean(request, "captureKnowledge");
            String knowledgeStatus = readText(request, "knowledgeStatus", "draft");
            if (captureKnowledge) {
                captureKnowledgeAnswer(question, answer, knowledgeStatus, request);
            }

            response.put("success", true);
            response.put("message", "AI回答问题成功");
            response.put("answer", answer);
            response.put("contextCount", contextKnowledge.size());
        } catch (Exception e) {
            log.error("AI回答问题失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "AI回答问题失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    private void captureKnowledgeAnswer(
            String question,
            String answer,
            String knowledgeStatus,
            Map<String, Object> request) {
        if (knowledgeCaptureService == null) {
            return;
        }
        String safeQuestion = question == null ? "" : question.trim();
        String safeAnswer = answer == null ? "" : answer.trim();
        String status = knowledgeStatus == null ? "draft" : knowledgeStatus;
        KnowledgeCaptureRequest captureRequest = new KnowledgeCaptureRequest();
        captureRequest.setTitle(buildKnowledgeAnswerCaptureTitle(safeQuestion));
        captureRequest.setContent(buildKnowledgeAnswerCaptureContent(safeQuestion, safeAnswer));
        captureRequest.setSourceType("knowledge-controller-answer");
        captureRequest.setSourceId(readText(request, "userId", "default_user"));
        captureRequest.setSourceScene("knowledge-ai-answer");
        captureRequest.setSourceReference("/api/knowledge/ai/answer");
        captureRequest.setCreatedBy(readText(request, "userId", "system"));
        captureRequest.setStatus(status);
        captureRequest.setPublish("published".equalsIgnoreCase(status));
        knowledgeCaptureService.capture(captureRequest);
    }

    private static String buildKnowledgeAnswerCaptureTitle(String question) {
        String safeQuestion = question == null ? "" : question;
        String shortQuestion = safeQuestion.length() > 42 ? safeQuestion.substring(0, 42) : safeQuestion;
        return "知识库问答：" + shortQuestion;
    }

    private static String buildKnowledgeAnswerCaptureContent(String question, String answer) {
        String safeQuestion = question == null ? "" : question;
        String safeAnswer = answer == null ? "" : answer;
        return "问题：\n" + safeQuestion + "\n\n回答：\n" + safeAnswer;
    }

    private static String readText(Map<String, Object> request, String key, String fallback) {
        Object value = request == null ? null : request.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static boolean toBoolean(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void saveKnowledgeMemory(String userId, String content, String type, String metadata) {
        if (memoryService == null || content == null || type == null) {
            return;
        }
        String safeUserId = defaultText(userId, "system");
        memoryService.saveMemory(safeUserId, content, type, metadata);
    }

    @PostMapping("/capture")
    public Map<String, Object> captureKnowledge(@RequestBody KnowledgeCaptureRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (request == null) {
            response.put("success", false);
            response.put("message", "请求内容不能为空");
            return response;
        }
        if (knowledgeCaptureService == null) {
            response.put("success", false);
            response.put("message", "知识库采集服务暂不可用");
            return response;
        }
        try {
            KnowledgeCaptureResult result = knowledgeCaptureService.capture(request);
            response.put("success", KnowledgeCaptureResult.ACTION_FAILED.equals(result.getAction()) ? false : true);
            response.put("action", result.getAction());
            response.put("message", result.getMessage());
            if (result.getKnowledge() != null) {
                response.put("data", result.getKnowledge());
            }
        } catch (Exception e) {
            log.error("知识入库采集失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "知识采集失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/capture/batch")
    public Map<String, Object> batchCaptureKnowledge(@RequestBody List<KnowledgeCaptureRequest> requests) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (requests == null || requests.isEmpty()) {
            response.put("success", false);
            response.put("message", "请至少提供一条知识采集内容");
            return response;
        }
        if (knowledgeCaptureService == null) {
            response.put("success", false);
            response.put("message", "知识库采集服务暂不可用");
            return response;
        }
        int captured = 0;
        int skipped = 0;
        int failed = 0;
        List<KnowledgeCaptureResult> results = requests.stream().map(request -> {
            KnowledgeCaptureResult result = knowledgeCaptureService.capture(request);
            if (result.isCaptured()) {
                return result;
            }
            return result;
        }).toList();
        for (KnowledgeCaptureResult result : results) {
            if (KnowledgeCaptureResult.ACTION_CREATED.equals(result.getAction()) && result.isCaptured()) {
                captured++;
            } else if (KnowledgeCaptureResult.ACTION_FAILED.equals(result.getAction())) {
                failed++;
            } else {
                skipped++;
            }
        }
        response.put("success", true);
        response.put("action", results.isEmpty() ? "" : "BATCH_COMPLETED");
        response.put("message", "知识批量采集处理完成。");
        response.put("captured", captured);
        response.put("skipped", skipped);
        response.put("failed", failed);
        response.put("results", results);
        return response;
    }

    // 分类相关接口
    @GetMapping("/categories")
    public Map<String, Object> getCategories() {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<KnowledgeCategory> categories = knowledgeService.getAllCategories();
            response.put("success", true);
            response.put("data", categories);
            response.put("message", "获取分类列表成功");
        } catch (Exception e) {
            log.error("获取分类列表失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取分类列表失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/categories/parent/{id}")
    public Map<String, Object> getCategoriesByParent(@PathVariable Long id) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<KnowledgeCategory> categories = knowledgeService.getCategoriesByParent(id);
            response.put("success", true);
            response.put("data", categories);
            response.put("message", "获取子分类列表成功");
        } catch (Exception e) {
            log.error("获取子分类列表失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取子分类列表失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/categories/create")
    public Map<String, Object> createCategory(@RequestBody KnowledgeCategory category) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            KnowledgeCategory createdCategory = knowledgeService.createCategory(category);
            response.put("success", true);
            response.put("data", createdCategory);
            response.put("message", "创建分类成功");
        } catch (Exception e) {
            log.error("创建分类失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "创建分类失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PutMapping("/categories/update")
    public Map<String, Object> updateCategory(@RequestBody KnowledgeCategory category) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            KnowledgeCategory updatedCategory = knowledgeService.updateCategory(category);
            response.put("success", true);
            response.put("data", updatedCategory);
            response.put("message", "更新分类成功");
        } catch (Exception e) {
            log.error("更新分类失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "更新分类失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/categories/delete/{id}")
    public Map<String, Object> deleteCategory(@PathVariable Long id) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            knowledgeService.deleteCategory(id);
            response.put("success", true);
            response.put("message", "删除分类成功");
        } catch (Exception e) {
            log.error("删除分类失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除分类失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    // 标签相关接口
    @GetMapping("/tags")
    public Map<String, Object> getTags() {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<KnowledgeTag> tags = knowledgeService.getAllTags();
            response.put("success", true);
            response.put("data", tags);
            response.put("message", "获取标签列表成功");
        } catch (Exception e) {
            log.error("获取标签列表失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取标签列表失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/tags/search")
    public Map<String, Object> searchTags(@RequestParam String keyword) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            List<KnowledgeTag> tags = knowledgeService.searchTags(keyword);
            response.put("success", true);
            response.put("data", tags);
            response.put("message", "搜索标签成功");
        } catch (Exception e) {
            log.error("搜索标签失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "搜索标签失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PostMapping("/tags/create")
    public Map<String, Object> createTag(@RequestBody KnowledgeTag tag) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            KnowledgeTag createdTag = knowledgeService.createTag(tag);
            response.put("success", true);
            response.put("data", createdTag);
            response.put("message", "创建标签成功");
        } catch (Exception e) {
            log.error("创建标签失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "创建标签失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @PutMapping("/tags/update")
    public Map<String, Object> updateTag(@RequestBody KnowledgeTag tag) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            KnowledgeTag updatedTag = knowledgeService.updateTag(tag);
            response.put("success", true);
            response.put("data", updatedTag);
            response.put("message", "更新标签成功");
        } catch (Exception e) {
            log.error("更新标签失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "更新标签失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/tags/delete/{id}")
    public Map<String, Object> deleteTag(@PathVariable Long id) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            knowledgeService.deleteTag(id);
            response.put("success", true);
            response.put("message", "删除标签成功");
        } catch (Exception e) {
            log.error("删除标签失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除标签失败");
            response.put("error", e.getMessage());
        }
        return response;
    }

    // 统计相关接口
    @GetMapping("/statistics")
    public Map<String, Object> getKnowledgeStatistics() {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            Map<String, Object> statistics = knowledgeService.getKnowledgeStatistics();
            response.put("success", true);
            response.put("data", statistics);
            response.put("message", "获取知识库统计信息成功");
        } catch (Exception e) {
            log.error("获取知识库统计信息失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取知识库统计信息失败");
            response.put("error", e.getMessage());
        }
        return response;
    }
}
