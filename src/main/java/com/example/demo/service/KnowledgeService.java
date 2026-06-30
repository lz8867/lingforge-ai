package com.example.demo.service;

import com.example.demo.model.Knowledge;
import com.example.demo.model.KnowledgeCategory;
import com.example.demo.model.KnowledgeTag;

import java.util.List;
import java.util.Map;

public interface KnowledgeService {

    // 知识库相关方法
    List<Knowledge> getAllKnowledge();
    List<Knowledge> getKnowledgeByCategory(Long categoryId);
    List<Knowledge> getKnowledgeByStatus(String status);
    List<Knowledge> searchKnowledge(String keyword);
    List<Knowledge> getKnowledgeByTag(String tagName);
    Knowledge getKnowledgeById(Long id);
    Knowledge createKnowledge(Knowledge knowledge);
    Knowledge updateKnowledge(Knowledge knowledge);
    void deleteKnowledge(Long id);

    // 分类相关方法
    List<KnowledgeCategory> getAllCategories();
    List<KnowledgeCategory> getCategoriesByParent(Long parentId);
    KnowledgeCategory getCategoryById(Long id);
    KnowledgeCategory createCategory(KnowledgeCategory category);
    KnowledgeCategory updateCategory(KnowledgeCategory category);
    void deleteCategory(Long id);

    // 标签相关方法
    List<KnowledgeTag> getAllTags();
    KnowledgeTag getTagById(Long id);
    KnowledgeTag createTag(KnowledgeTag tag);
    KnowledgeTag updateTag(KnowledgeTag tag);
    void deleteTag(Long id);
    List<KnowledgeTag> searchTags(String keyword);

    // 统计相关方法
    Map<String, Object> getKnowledgeStatistics();

    // AI相关方法
    String aiGenerateKnowledge(String prompt);
    String aiSummarizeKnowledge(Long knowledgeId);
    String aiAnswerQuestion(String question, List<Knowledge> contextKnowledge);
}
