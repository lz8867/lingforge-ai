package com.example.demo.controller;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.service.MemoryService;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeControllerTest {

    @Test
    void aiAnswerQuestionShouldCaptureKnowledgeWhenEnabled() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.searchKnowledge("知识库如何支撑RAG？")).thenReturn(List.of());
        when(knowledgeService.aiAnswerQuestion(eq("知识库如何支撑RAG？"), anyList())).thenReturn("基于检索证据回答");

        KnowledgeController controller = new KnowledgeController(knowledgeService, captureService);

        Map<String, Object> request = new HashMap<>();
        request.put("question", "知识库如何支撑RAG？");
        request.put("captureKnowledge", true);
        request.put("knowledgeStatus", "published");
        request.put("userId", "user-007");

        Map<String, Object> response = controller.aiAnswerQuestion(request);

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        assertEquals("AI回答问题成功", response.get("message"));

        ArgumentCaptor<KnowledgeCaptureRequest> captor = ArgumentCaptor.forClass(KnowledgeCaptureRequest.class);
        verify(captureService).capture(captor.capture());
        KnowledgeCaptureRequest capturedRequest = captor.getValue();
        assertEquals("knowledge-controller-answer", capturedRequest.getSourceType());
        assertEquals("knowledge-ai-answer", capturedRequest.getSourceScene());
        assertEquals("/api/knowledge/ai/answer", capturedRequest.getSourceReference());
        assertEquals("published", capturedRequest.getStatus());
        assertEquals("user-007", capturedRequest.getSourceId());
        assertEquals("user-007", capturedRequest.getCreatedBy());
        assertTrue(capturedRequest.getTitle().contains("知识库问答"));
        assertTrue(capturedRequest.getContent().contains("回答"));
    }

    @Test
    void aiAnswerQuestionShouldNotCaptureByDefault() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.searchKnowledge("如何判断是否入库？")).thenReturn(List.of());
        when(knowledgeService.aiAnswerQuestion(eq("如何判断是否入库？"), anyList())).thenReturn("入库应由业务显式开关控制");

        KnowledgeController controller = new KnowledgeController(knowledgeService, captureService);

        Map<String, Object> request = Map.of("question", "如何判断是否入库？");
        Map<String, Object> response = controller.aiAnswerQuestion(request);

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        verifyNoInteractions(captureService);
    }

    @Test
    void aiAnswerQuestionShouldDefaultDraftWhenStatusNotSet() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.searchKnowledge("默认草稿规则")).thenReturn(List.of());
        when(knowledgeService.aiAnswerQuestion(eq("默认草稿规则"), anyList())).thenReturn("默认草稿");

        KnowledgeController controller = new KnowledgeController(knowledgeService, captureService);
        Map<String, Object> request = new HashMap<>();
        request.put("question", "默认草稿规则");
        request.put("captureKnowledge", true);

        Map<String, Object> response = controller.aiAnswerQuestion(request);

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        ArgumentCaptor<KnowledgeCaptureRequest> captor = ArgumentCaptor.forClass(KnowledgeCaptureRequest.class);
        verify(captureService).capture(captor.capture());
        assertEquals("draft", captor.getValue().getStatus());
    }

    @Test
    void aiAnswerQuestionShouldRejectEmptyQuestion() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);

        KnowledgeController controller = new KnowledgeController(knowledgeService, captureService);
        Map<String, Object> response = controller.aiAnswerQuestion(Map.of("question", ""));

        assertFalse(Boolean.TRUE.equals(response.get("success")));
        assertEquals("请提供问题", response.get("message"));
        verifyNoInteractions(knowledgeService);
        verifyNoInteractions(captureService);
    }

    @Test
    void aiGenerateKnowledgeShouldCaptureWhenEnabled() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.aiGenerateKnowledge("请生成项目汇报要点"))
                .thenReturn("汇报要点：进度、风险、里程碑");

        KnowledgeController controller = new KnowledgeController(knowledgeService, captureService);

        Map<String, Object> response = controller.aiGenerateKnowledge(
                new java.util.HashMap<>() {{
                    put("prompt", "请生成项目汇报要点");
                }},
                true,
                "draft",
                "user-001"
        );

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        ArgumentCaptor<KnowledgeCaptureRequest> captor = ArgumentCaptor.forClass(KnowledgeCaptureRequest.class);
        verify(captureService).capture(captor.capture());
        KnowledgeCaptureRequest capturedRequest = captor.getValue();
        assertEquals("knowledge-controller-generate", capturedRequest.getSourceType());
        assertEquals("knowledge-ai-generate", capturedRequest.getSourceScene());
        assertEquals("/api/knowledge/ai/generate", capturedRequest.getSourceReference());
        assertEquals("user-001", capturedRequest.getCreatedBy());
        assertEquals("draft", capturedRequest.getStatus());
        assertTrue(capturedRequest.getTitle().contains("知识库生成"));
        assertTrue(capturedRequest.getContent().contains("汇报要点"));
    }

    @Test
    void aiGenerateKnowledgeShouldNotCaptureByDefault() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.aiGenerateKnowledge("自动化任务排期")).thenReturn("建议优先处理 A/B/C");

        KnowledgeController controller = new KnowledgeController(knowledgeService, captureService);
        Map<String, String> request = new java.util.HashMap<>();
        request.put("prompt", "自动化任务排期");

        Map<String, Object> response = controller.aiGenerateKnowledge(request, false, "draft", "system");

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        verifyNoInteractions(captureService);
    }

    @Test
    void aiSummarizeKnowledgeShouldCaptureDraftByDefaultStatus() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.aiSummarizeKnowledge(88L)).thenReturn("1. 风险可控\n2. 需补充验收");

        KnowledgeController controller = new KnowledgeController(knowledgeService, captureService);

        Map<String, Object> response = controller.aiSummarizeKnowledge(88L, true, "draft", "user-002");

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        ArgumentCaptor<KnowledgeCaptureRequest> captor = ArgumentCaptor.forClass(KnowledgeCaptureRequest.class);
        verify(captureService).capture(captor.capture());
        KnowledgeCaptureRequest captured = captor.getValue();
        assertEquals("knowledge-controller-summarize", captured.getSourceType());
        assertEquals("knowledge-ai-summarize", captured.getSourceScene());
        assertEquals("/api/knowledge/ai/summarize/88", captured.getSourceReference());
        assertEquals("88", captured.getSourceId());
        assertEquals("draft", captured.getStatus());
        assertTrue(captured.getTitle().contains("知识库总结"));
        assertTrue(captured.getContent().contains("知识库 ID: 88"));
    }

    @Test
    void aiGenerateKnowledgeShouldWriteToMemory() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        MemoryService memoryService = mock(MemoryService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.aiGenerateKnowledge("生成发布稿")).thenReturn("已生成发布稿");

        KnowledgeController controller = new KnowledgeController(knowledgeService, memoryService, captureService);
        Map<String, Object> response = controller.aiGenerateKnowledge(
                new java.util.HashMap<>() {{
                    put("prompt", "生成发布稿");
                }},
                false,
                "draft",
                "user-001"
        );

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        verify(memoryService).saveMemory("user-001", "生成发布稿", "knowledge_prompt", "生成发布稿");
        verify(memoryService).saveMemory("user-001", "已生成发布稿", "knowledge_ai_response", null);
        verifyNoInteractions(captureService);
    }

    @Test
    void aiAnswerQuestionShouldWriteToMemory() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        MemoryService memoryService = mock(MemoryService.class);
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);
        when(knowledgeService.searchKnowledge("如何对接知识库")).thenReturn(List.of());
        when(knowledgeService.aiAnswerQuestion(eq("如何对接知识库"), anyList())).thenReturn("可按接口、字段和流程先建索引");

        KnowledgeController controller = new KnowledgeController(knowledgeService, memoryService, captureService);
        Map<String, Object> response = controller.aiAnswerQuestion(Map.of(
                "question", "如何对接知识库",
                "userId", "user-002"
        ));

        assertTrue(Boolean.TRUE.equals(response.get("success")));
        verify(memoryService).saveMemory("user-002", "如何对接知识库", "knowledge_question", null);
        verify(memoryService).saveMemory("user-002", "可按接口、字段和流程先建索引", "knowledge_ai_response", null);
    }
}
