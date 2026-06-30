package com.example.demo.service.impl;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.DTO.KnowledgeCaptureResult;
import com.example.demo.model.Knowledge;
import com.example.demo.repository.KnowledgeRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class KnowledgeCaptureServiceImplTest {

    @Test
    void captureShouldDefaultToDraft() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.existsByTitleAndContentIgnoreCase("核心配置", "配置说明：支持向量检索和上下文约束。")).thenReturn(false);
        when(repository.save(any(Knowledge.class))).thenAnswer(invocation -> {
            Knowledge knowledge = invocation.getArgument(0);
            knowledge.setId(1L);
            return knowledge;
        });

        KnowledgeCaptureServiceImpl service = new KnowledgeCaptureServiceImpl(repository);
        KnowledgeCaptureRequest request = createRequest("核心配置", "配置说明：支持向量检索和上下文约束。", false);

        KnowledgeCaptureResult result = service.capture(request);

        assertTrue(result.isCaptured());
        assertEquals("CREATED", result.getAction());
        assertEquals("draft", result.getKnowledge().getStatus());
        assertEquals("核心配置", result.getKnowledge().getTitle());
    }

    @Test
    void captureShouldSkipDuplicateContent() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.existsByTitleAndContentIgnoreCase("重复标题", "重复内容")).thenReturn(true);

        KnowledgeCaptureServiceImpl service = new KnowledgeCaptureServiceImpl(repository);
        KnowledgeCaptureRequest request = createRequest("重复标题", "重复内容", false);

        KnowledgeCaptureResult result = service.capture(request);

        assertFalse(result.isCaptured());
        assertEquals(KnowledgeCaptureResult.ACTION_SKIPPED_DUPLICATE, result.getAction());
    }

    @Test
    void captureShouldPublishWhenRequested() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.existsByTitleAndContentIgnoreCase("发布项", "会发布")).thenReturn(false);
        when(repository.save(any(Knowledge.class))).thenAnswer(invocation -> {
            Knowledge knowledge = invocation.getArgument(0);
            knowledge.setId(2L);
            return knowledge;
        });

        KnowledgeCaptureServiceImpl service = new KnowledgeCaptureServiceImpl(repository);
        KnowledgeCaptureRequest request = createRequest("发布项", "会发布", true);

        KnowledgeCaptureResult result = service.capture(request);

        assertTrue(result.isCaptured());
        assertEquals("published", result.getKnowledge().getStatus());
        verify(repository).save(any(Knowledge.class));
    }

    private static KnowledgeCaptureRequest createRequest(String title, String content, boolean publish) {
        KnowledgeCaptureRequest request = new KnowledgeCaptureRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setStatus("draft");
        request.setSourceType("chat");
        request.setSourceId("user_01");
        request.setSourceScene("test");
        request.setCreatedBy("user_01");
        request.setPublish(publish);
        request.setCategoryId(1L);
        request.setMetadata("source=unit-test");
        request.setSourceReference("unit");
        return request;
    }
}
