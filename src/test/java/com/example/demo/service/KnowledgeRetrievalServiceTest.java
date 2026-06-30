package com.example.demo.service;

import com.example.demo.model.Knowledge;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRetrievalServiceTest {

    @Test
    void retrieveShouldReturnRankedKnowledgeEvidenceWithTrace() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(List.of(
                knowledge(1L, "部署巡检", "服务状态、CPU 和 Docker 日志用于运维巡检。"),
                knowledge(2L, "RAG 可信问答", "RAG 通过检索上下文回答问题。资料不足时必须明确说明缺口，减少幻觉和编造。"),
                knowledge(3L, "Prompt 输出契约", "Prompt 优化需要明确 JSON 输出格式和失败处理。")
        ), null);

        KnowledgeRetrievalResult result = service.retrieve("knowledge-qa", "RAG 如何减少幻觉和编造", 2);

        assertTrue(result.used());
        assertEquals("knowledge-qa", result.scene());
        assertEquals(2, result.matches().size());
        assertEquals("knowledge:2", result.matches().get(0).id());
        assertTrue(result.matches().get(0).content().contains("资料不足"));
        assertEquals("knowledge", result.matches().get(0).sourceType());
        assertTrue(result.trace().stream().anyMatch(item -> item.contains("published")));
        assertTrue(result.trace().stream().anyMatch(item -> item.contains("ranked")));
    }

    @Test
    void retrieveShouldSkipBlankQueryWithoutRepositoryAccess() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(List.of(), null);

        KnowledgeRetrievalResult result = service.retrieve("chat", "   ", 3);

        assertFalse(result.used());
        assertTrue(result.matches().isEmpty());
        assertTrue(result.gateReason().contains("检索问题为空"));
    }

    private static Knowledge knowledge(Long id, String title, String content) {
        Knowledge knowledge = new Knowledge();
        knowledge.setId(id);
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setStatus("published");
        knowledge.setUpdatedAt(LocalDateTime.now());
        return knowledge;
    }
}
