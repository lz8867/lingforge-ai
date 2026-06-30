package com.example.demo.controller;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.DTO.PromptRequest;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.MemoryService;
import com.example.demo.service.OllamaChatClient;
import com.example.demo.service.ModelEvaluationRunRecord;
import com.example.demo.service.ModelEvaluationService;
import com.example.demo.service.PromptOptimizationService;
import com.example.demo.service.StubPromptOptimizationService;
import com.example.demo.service.StubOllamaChatClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

class PromptControllerTest {

    @Test
    void testPromptShouldCaptureWhenEnabled() {
        MemoryService memoryService = mock(MemoryService.class);
        OllamaChatClient chatClient = new StubOllamaChatClient("处理完成");
        PromptOptimizationService promptOptimizationService = new StubPromptOptimizationService(
                Map.of("success", true),
                Map.of("success", true)
        );
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);

        PromptController controller = new PromptController(
                memoryService,
                chatClient,
                promptOptimizationService,
                captureService
        );

        PromptRequest request = new PromptRequest();
        request.setTemplate("测试用户: {{name}}");
        request.setVariables(Map.of("name", "Alice"));

        String response = controller.testPrompt(request, "user-001", true, "draft");

        assertEquals("处理完成", response);
        verify(memoryService).buildMemoryContext("user-001");
        verify(memoryService).saveMemory("user-001", "测试用户: Alice", "prompt_template", "测试用户: {{name}}");
        verify(memoryService).saveMemory("user-001", "处理完成", "ai_response", null);

        ArgumentCaptor<KnowledgeCaptureRequest> captor = ArgumentCaptor.forClass(KnowledgeCaptureRequest.class);
        verify(captureService, times(1)).capture(captor.capture());
        KnowledgeCaptureRequest captured = captor.getValue();
        assertEquals("prompt-engine", captured.getSourceType());
        assertEquals("user-001", captured.getSourceId());
        assertEquals("prompt-test", captured.getSourceScene());
        assertEquals("/prompt/test", captured.getSourceReference());
        assertEquals("draft", captured.getStatus());
        assertTrue(captured.getTitle().contains("提示词测试记录"));
        assertTrue(captured.getContent().contains("模板"));
        assertTrue(captured.getContent().contains("测试用户: {{name}}"));
    }

    @Test
    void testPromptShouldNotCaptureByDefault() {
        MemoryService memoryService = mock(MemoryService.class);
        OllamaChatClient chatClient = new StubOllamaChatClient("模型响应");
        PromptOptimizationService promptOptimizationService = new StubPromptOptimizationService(
                Map.of("success", true),
                Map.of("success", true)
        );
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);

        PromptController controller = new PromptController(
                memoryService,
                chatClient,
                promptOptimizationService,
                captureService
        );

        PromptRequest request = new PromptRequest();
        request.setTemplate("固定模板");
        String response = controller.testPrompt(request, "user-default", false, "draft");

        assertEquals("模型响应", response);
        verifyNoInteractions(captureService);
    }

    @Test
    void evaluateAndOptimizeShouldDefaultNoCapture() {
        MemoryService memoryService = mock(MemoryService.class);
        OllamaChatClient chatClient = new StubOllamaChatClient("unused");
        PromptOptimizationService promptOptimizationService = new StubPromptOptimizationService(
                Map.of("success", true, "overallScore", 95),
                Map.of("success", true, "optimizedPrompt", "输出更短的日报")
        );
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);

        Map<String, Object> request = new HashMap<>();
        request.put("currentPrompt", "输出一个简短日报");

        PromptController controller = new PromptController(
                memoryService,
                chatClient,
                promptOptimizationService,
                captureService
        );

        Map<String, Object> evaluateResponse = controller.evaluatePrompt(request, false, "draft");
        Map<String, Object> optimizeResponse = controller.optimizePrompt(request, false, "draft");
        assertEquals(true, evaluateResponse.get("success"));
        assertEquals(true, optimizeResponse.get("success"));
        verifyNoInteractions(captureService);
    }

    @Test
    void evaluateAndOptimizeShouldCaptureWhenEnabled() {
        MemoryService memoryService = mock(MemoryService.class);
        OllamaChatClient chatClient = new StubOllamaChatClient("unused");
        PromptOptimizationService promptOptimizationService = new StubPromptOptimizationService(
                Map.of("success", true, "overallScore", 95),
                Map.of("success", true, "optimizedPrompt", "输出更短的日报")
        );
        KnowledgeCaptureService captureService = mock(KnowledgeCaptureService.class);

        Map<String, Object> request = new HashMap<>();
        request.put("currentPrompt", "输出一个简短日报");

        PromptController controller = new PromptController(
                memoryService,
                chatClient,
                promptOptimizationService,
                captureService
        );

        controller.evaluatePrompt(request, true, "published");
        controller.optimizePrompt(request, true, "published");

        ArgumentCaptor<KnowledgeCaptureRequest> captor = ArgumentCaptor.forClass(KnowledgeCaptureRequest.class);
        verify(captureService, times(2)).capture(captor.capture());
        assertEquals(2, captor.getAllValues().size());

        assertEquals("prompt-evaluate", captor.getAllValues().get(0).getSourceScene());
        assertEquals("prompt-optimize", captor.getAllValues().get(1).getSourceScene());
        assertEquals("published", captor.getAllValues().get(0).getStatus());
        assertEquals("published", captor.getAllValues().get(1).getStatus());
        assertTrue(captor.getAllValues().get(0).getSourceReference().contains("/prompt/evaluate"));
        assertTrue(captor.getAllValues().get(1).getSourceReference().contains("/prompt/optimize"));
    }

    @Test
    void evaluateAndOptimizeShouldPersistModelEvaluationMetrics() {
        MemoryService memoryService = mock(MemoryService.class);
        OllamaChatClient chatClient = new StubOllamaChatClient("unused");
        PromptOptimizationService promptOptimizationService = new StubPromptOptimizationService(
                Map.of("success", true, "overallScore", 95),
                Map.of("success", true, "optimizedPrompt", "输出更短的日报")
        );

        ModelEvaluationService modelEvaluationService = ModelEvaluationService.inMemory();
        PromptController controller = new PromptController(
                memoryService,
                chatClient,
                promptOptimizationService,
                null,
                modelEvaluationService
        );

        Map<String, Object> evaluateRequest = new HashMap<>();
        evaluateRequest.put("currentPrompt", "输出一个简短日报");
        Map<String, Object> optimizeRequest = new HashMap<>();
        optimizeRequest.put("currentPrompt", "输出一个简短日报");

        Map<String, Object> evaluateResponse = controller.evaluatePrompt(evaluateRequest, true, "draft");
        Map<String, Object> optimizeResponse = controller.optimizePrompt(optimizeRequest, true, "draft");

        List<ModelEvaluationRunRecord> evaluateRuns = modelEvaluationService.listRuns(
                "prompt",
                null,
                null,
                "prompt-evaluate",
                10
        );
        List<ModelEvaluationRunRecord> optimizeRuns = modelEvaluationService.listRuns(
                "prompt",
                null,
                null,
                "prompt-optimize",
                10
        );
        List<ModelEvaluationRunRecord> allRuns = modelEvaluationService.listRuns("prompt", null, null, null, 20);

        assertEquals(true, evaluateResponse.get("success"));
        assertEquals(true, optimizeResponse.get("success"));
        assertEquals(1, evaluateRuns.size());
        assertEquals(1, optimizeRuns.size());
        assertEquals(2, allRuns.size());
        assertEquals("prompt-evaluate", evaluateRuns.get(0).requestSource());
        assertEquals("prompt-optimize", optimizeRuns.get(0).requestSource());
        assertEquals("success", evaluateRuns.get(0).status());
        assertEquals("success", optimizeRuns.get(0).status());
        assertTrue(evaluateRuns.get(0).metrics().get(ModelEvaluationService.METRIC_TOP1) > 0);
    }
}
