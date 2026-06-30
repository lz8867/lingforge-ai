package com.example.demo.service.impl;

import com.example.demo.model.Knowledge;
import com.example.demo.model.KnowledgeCategory;
import com.example.demo.model.KnowledgeTag;
import com.example.demo.repository.KnowledgeCategoryRepository;
import com.example.demo.repository.KnowledgeRepository;
import com.example.demo.repository.KnowledgeTagRepository;
import com.example.demo.service.KnowledgeRetrievalHit;
import com.example.demo.service.KnowledgeRetrievalResult;
import com.example.demo.service.KnowledgeRetrievalService;
import com.example.demo.service.KnowledgeService;
import com.example.demo.service.OllamaChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeCategoryRepository categoryRepository;
    private final KnowledgeTagRepository tagRepository;
    private final OllamaChatClient ollamaChatClient;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    @Autowired
    public KnowledgeServiceImpl(KnowledgeRepository knowledgeRepository,
                               KnowledgeCategoryRepository categoryRepository,
                               KnowledgeTagRepository tagRepository,
                               OllamaChatClient ollamaChatClient,
                               KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeRepository = knowledgeRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.ollamaChatClient = ollamaChatClient;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    public KnowledgeServiceImpl(KnowledgeRepository knowledgeRepository,
                               KnowledgeCategoryRepository categoryRepository,
                               KnowledgeTagRepository tagRepository,
                               OllamaChatClient ollamaChatClient) {
        this(
                knowledgeRepository,
                categoryRepository,
                tagRepository,
                ollamaChatClient,
                new KnowledgeRetrievalService(knowledgeRepository, null)
        );
    }

    // 知识库相关方法
    @Override
    public List<Knowledge> getAllKnowledge() {
        return knowledgeRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Knowledge> getKnowledgeByCategory(Long categoryId) {
        return knowledgeRepository.findByCategoryId(categoryId);
    }

    @Override
    public List<Knowledge> getKnowledgeByStatus(String status) {
        return knowledgeRepository.findByStatus(status);
    }

    @Override
    public List<Knowledge> searchKnowledge(String keyword) {
        List<Knowledge> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();

        // 搜索标题
        List<Knowledge> titleResults = knowledgeRepository.findByTitleContaining(keyword);
        titleResults.forEach(knowledge -> {
            if (!ids.contains(knowledge.getId())) {
                ids.add(knowledge.getId());
                result.add(knowledge);
            }
        });

        // 搜索内容
        List<Knowledge> contentResults = knowledgeRepository.findByContentContaining(keyword);
        contentResults.forEach(knowledge -> {
            if (!ids.contains(knowledge.getId())) {
                ids.add(knowledge.getId());
                result.add(knowledge);
            }
        });

        // 搜索标签
        List<Knowledge> tagResults = knowledgeRepository.findByTagName(keyword);
        tagResults.forEach(knowledge -> {
            if (!ids.contains(knowledge.getId())) {
                ids.add(knowledge.getId());
                result.add(knowledge);
            }
        });

        return result;
    }

    @Override
    public List<Knowledge> getKnowledgeByTag(String tagName) {
        return knowledgeRepository.findByTagName(tagName);
    }

    @Override
    public Knowledge getKnowledgeById(Long id) {
        return knowledgeRepository.findById(id).orElse(null);
    }

    @Override
    public Knowledge createKnowledge(Knowledge knowledge) {
        LocalDateTime now = LocalDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        knowledge.setVersion(1);
        knowledge.setStatus(knowledge.getStatus() != null ? knowledge.getStatus() : "draft");
        return knowledgeRepository.save(knowledge);
    }

    @Override
    public Knowledge updateKnowledge(Knowledge knowledge) {
        LocalDateTime now = LocalDateTime.now();
        knowledge.setUpdatedAt(now);
        if (knowledge.getVersion() == null) {
            knowledge.setVersion(1);
        } else {
            knowledge.setVersion(knowledge.getVersion() + 1);
        }
        return knowledgeRepository.save(knowledge);
    }

    @Override
    public void deleteKnowledge(Long id) {
        knowledgeRepository.deleteById(id);
    }

    // 分类相关方法
    @Override
    public List<KnowledgeCategory> getAllCategories() {
        return categoryRepository.findAllByOrderBySortAsc();
    }

    @Override
    public List<KnowledgeCategory> getCategoriesByParent(Long parentId) {
        return categoryRepository.findByParentIdOrderBySortAsc(parentId);
    }

    @Override
    public KnowledgeCategory getCategoryById(Long id) {
        return categoryRepository.findById(id).orElse(null);
    }

    @Override
    public KnowledgeCategory createCategory(KnowledgeCategory category) {
        LocalDateTime now = LocalDateTime.now();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        category.setLevel(category.getLevel() != null ? category.getLevel() : 1);
        category.setSort(category.getSort() != null ? category.getSort() : 0);
        return categoryRepository.save(category);
    }

    @Override
    public KnowledgeCategory updateCategory(KnowledgeCategory category) {
        LocalDateTime now = LocalDateTime.now();
        category.setUpdatedAt(now);
        return categoryRepository.save(category);
    }

    @Override
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    // 标签相关方法
    @Override
    public List<KnowledgeTag> getAllTags() {
        return tagRepository.findAll();
    }

    @Override
    public KnowledgeTag getTagById(Long id) {
        return tagRepository.findById(id).orElse(null);
    }

    @Override
    public KnowledgeTag createTag(KnowledgeTag tag) {
        LocalDateTime now = LocalDateTime.now();
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        return tagRepository.save(tag);
    }

    @Override
    public KnowledgeTag updateTag(KnowledgeTag tag) {
        LocalDateTime now = LocalDateTime.now();
        tag.setUpdatedAt(now);
        return tagRepository.save(tag);
    }

    @Override
    public void deleteTag(Long id) {
        tagRepository.deleteById(id);
    }

    @Override
    public List<KnowledgeTag> searchTags(String keyword) {
        return tagRepository.findByNameContaining(keyword);
    }

    // 统计相关方法
    @Override
    public Map<String, Object> getKnowledgeStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 总知识库数量
        long totalKnowledge = knowledgeRepository.count();
        stats.put("totalKnowledge", totalKnowledge);

        // 分类统计
        List<KnowledgeCategory> categories = categoryRepository.findAll();
        Map<Long, Integer> categoryStats = new HashMap<>();
        categories.forEach(category -> {
            long count = knowledgeRepository.findByCategoryId(category.getId()).size();
            categoryStats.put(category.getId(), (int) count);
        });
        stats.put("categoryStats", categoryStats);

        // 标签统计
        List<KnowledgeTag> tags = tagRepository.findAll();
        Map<Long, Integer> tagStats = new HashMap<>();
        tags.forEach(tag -> {
            long count = knowledgeRepository.findByTagName(tag.getName()).size();
            tagStats.put(tag.getId(), (int) count);
        });
        stats.put("tagStats", tagStats);

        // 状态统计
        long draftCount = knowledgeRepository.findByStatus("draft").size();
        long publishedCount = knowledgeRepository.findByStatus("published").size();
        stats.put("draftCount", draftCount);
        stats.put("publishedCount", publishedCount);

        return stats;
    }

    // AI相关方法实现
    @Override
    public String aiGenerateKnowledge(String prompt) {
        try {
            String fullPrompt = "请根据以下提示生成一份详细的知识库内容：\n" + prompt + "\n\n要求：\n1. 内容结构清晰，逻辑严谨\n2. 包含必要的背景信息和详细说明\n3. 语言规范，表达准确\n4. 内容具有实用性和参考价值";
            return ollamaChatClient.generate(fullPrompt);
        } catch (Exception e) {
            e.printStackTrace();
            return "AI生成失败：" + e.getMessage();
        }
    }

    @Override
    public String aiSummarizeKnowledge(Long knowledgeId) {
        try {
            Knowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
            if (knowledge == null) {
                return "知识库不存在";
            }

            String fullPrompt = "请总结以下知识库内容，提取核心要点：\n\n标题：" + knowledge.getTitle() + "\n\n内容：" + knowledge.getContent() + "\n\n要求：\n1. 总结简洁明了，抓住重点\n2. 保持原文的关键信息\n3. 使用列表形式呈现核心要点\n4. 语言流畅，易于理解";
            return ollamaChatClient.generate(fullPrompt);
        } catch (Exception e) {
            e.printStackTrace();
            return "AI总结失败：" + e.getMessage();
        }
    }

    @Override
    public String aiAnswerQuestion(String question, List<Knowledge> contextKnowledge) {
        String safeQuestion = question == null ? "" : question.trim();
        log.info("知识库 AI 问答开始: questionLength={}", safeQuestion.length());
        try {
            KnowledgeRetrievalResult retrievalResult = knowledgeRetrievalService.retrieve("knowledge-qa", safeQuestion, 5);
            List<KnowledgeRetrievalHit> hits = retrievalResult.used()
                    ? retrievalResult.matches()
                    : legacyContextHits(contextKnowledge);
            String prompt = buildKnowledgeAnswerPrompt(safeQuestion, retrievalResult, hits);
            String answer = ollamaChatClient.generate(prompt);
            log.info("知识库 AI 问答完成: retrievalUsed={}, contextCount={}", retrievalResult.used(), hits.size());
            return answer;
        } catch (Exception e) {
            log.warn("知识库 AI 问答失败: {}", e.getMessage());
            return "AI回答失败：" + e.getMessage();
        }
    }

    private static String buildKnowledgeAnswerPrompt(
            String question,
            KnowledgeRetrievalResult retrievalResult,
            List<KnowledgeRetrievalHit> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是知识库问答助手。请只基于【统一知识检索上下文】回答问题。\n");
        prompt.append("如果检索上下文不足以回答，请明确说明缺口，不要编造。\n\n");
        prompt.append("【检索信息】\n");
        prompt.append("scene: ").append(retrievalResult.scene()).append("\n");
        prompt.append("used: ").append(retrievalResult.used()).append("\n");
        prompt.append("gateReason: ").append(retrievalResult.gateReason()).append("\n");
        prompt.append("trace: ").append(String.join(" / ", retrievalResult.trace())).append("\n\n");
        prompt.append("【统一知识检索上下文】\n");
        if (hits.isEmpty()) {
            prompt.append("- 未命中可用知识上下文。\n");
        } else {
            for (int i = 0; i < hits.size(); i++) {
                KnowledgeRetrievalHit hit = hits.get(i);
                prompt.append(i + 1)
                        .append(". [")
                        .append(hit.id())
                        .append("] ")
                        .append(hit.title())
                        .append(" source=")
                        .append(hit.sourceType())
                        .append(" score=")
                        .append(hit.score())
                        .append("\n")
                        .append(truncate(hit.content(), 1200))
                        .append("\n\n");
            }
        }
        prompt.append("【问题】\n").append(question).append("\n\n");
        prompt.append("【回答要求】\n");
        prompt.append("1. 先给出直接答案。\n");
        prompt.append("2. 列出依据的知识编号。\n");
        prompt.append("3. 如果上下文不足，明确说明还缺哪些资料。\n");
        prompt.append("4. 不要输出检索上下文以外的事实。");
        return prompt.toString();
    }

    private static List<KnowledgeRetrievalHit> legacyContextHits(List<Knowledge> contextKnowledge) {
        if (contextKnowledge == null || contextKnowledge.isEmpty()) {
            return List.of();
        }
        return contextKnowledge.stream()
                .limit(5)
                .map(KnowledgeServiceImpl::toLegacyContextHit)
                .toList();
    }

    private static KnowledgeRetrievalHit toLegacyContextHit(Knowledge knowledge) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("knowledgeId", knowledge.getId());
        return new KnowledgeRetrievalHit(
                "legacy-knowledge:" + knowledge.getId(),
                knowledge.getTitle() == null ? "未命名知识" : knowledge.getTitle(),
                knowledge.getContent() == null ? "" : knowledge.getContent(),
                0,
                "legacy-keyword",
                "关键词检索兜底",
                metadata
        );
    }

    private static String truncate(String value, int maxLength) {
        String safeValue = value == null ? "" : value;
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength) + "\n...（已截断）";
    }
}
