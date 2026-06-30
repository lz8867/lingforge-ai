package com.example.demo.service;

import com.example.demo.model.Knowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptOptimizationServiceTest {

    @Test
    void evaluateShouldUseCommonRubricToScoreManualPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 68,
                  "grade": "B",
                  "summary": "目标基本清楚，但缺少输出格式和评估用例。",
                  "criteria": [
                    {
                      "id": "clarity",
                      "name": "目标清晰度",
                      "score": 4,
                      "level": "good",
                      "evidence": "说明了要分析合同风险",
                      "suggestion": "补充成功标准"
                    },
                    {
                      "id": "output_format",
                      "name": "输出格式契约",
                      "score": 2,
                      "level": "risk",
                      "evidence": "没有 JSON 字段约束",
                      "suggestion": "增加 JSON Schema"
                    }
                  ],
                  "weaknesses": [
                    {
                      "criterionId": "output_format",
                      "issue": "缺少可解析输出约束",
                      "priority": "high",
                      "suggestion": "补充字段定义和示例"
                    }
                  ],
                  "optimizationFocus": ["强化输出格式契约", "补充固定评估用例"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of(
                "currentPrompt", "你是合同风险分析助手，请分析合同风险。",
                "data", Map.of(
                        "modelFamily", "通用可移植",
                        "evalCases", "缺少金额的付款条款"
                )
        ));

        assertEquals(true, response.get("success"));
        assertEquals(68, response.get("overallScore"));
        assertEquals("B", response.get("grade"));
        assertInstanceOf(List.class, response.get("criteria"));
        assertInstanceOf(List.class, response.get("weaknesses"));
        assertTrue(client.lastPrompt.contains("你是合同风险分析助手"));
        assertTrue(client.lastPrompt.contains("目标清晰度"));
        assertTrue(client.lastPrompt.contains("输出格式契约"));
        assertTrue(client.lastPrompt.contains("Few-shot 示例"));
        assertTrue(client.lastPrompt.contains("不要要求模型输出隐藏思维链"));
    }

    @Test
    void evaluateShouldRetrieveRagContextAndInjectItIntoEvaluatorPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 82,
                  "grade": "A",
                  "summary": "已结合 RAG 上下文评测。",
                  "criteria": [],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of(
                "currentPrompt", "请优化一个 RAG 问答 Prompt，要求减少幻觉和编造。",
                "data", Map.of(
                        "evaluationGoal", "检查检索上下文是否会拼接进 Prompt",
                        "expectedOutput", "输出可引用资料编号的回答"
                )
        ));

        assertEquals(true, response.get("success"));
        assertTrue(client.lastPrompt.contains("RAG 检索上下文"));
        assertTrue(client.lastPrompt.contains("rag-grounded-answer"));
        assertTrue(client.lastPrompt.contains("只有 RAG 标记为 used=true"));
        assertInstanceOf(Map.class, response.get("ragContext"));
        assertTrue(String.valueOf(response.get("ragContext")).contains("rag-grounded-answer"));
    }

    @Test
    void evaluateShouldSerializeKnowledgeRagMetadataWithJavaTimeSafely() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 86,
                  "grade": "A",
                  "summary": "知识库元数据已安全注入。",
                  "criteria": [],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        KnowledgeRetrievalService retrievalService = new KnowledgeRetrievalService(List.of(knowledge(
                101L,
                "RAG Prompt 评测规则",
                "RAG Prompt 评测需要基于检索上下文回答，资料不足时说明缺口，减少幻觉和编造，并输出 JSON。",
                LocalDateTime.of(2026, 6, 15, 7, 45, 36)
        )), null);
        PromptOptimizationService service = new PromptOptimizationService(client, retrievalService, new ObjectMapper());

        Map<String, Object> response = service.evaluate(Map.of(
                "currentPrompt", "请评测 RAG Prompt，要求基于检索上下文减少幻觉和编造，并输出 JSON。",
                "data", Map.of("evaluationGoal", "验证检索上下文可以安全拼接进评测 Prompt")
        ));

        assertEquals(true, response.get("success"));
        assertTrue(client.lastPrompt.contains("\"updatedAt\" : \"2026-06-15T07:45:36\""));
        assertFalse(String.valueOf(response.get("error")).contains("LocalDateTime"));
    }

    @Test
    void evaluateShouldKeepRagContextCompactForLocalModelPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 78,
                  "grade": "B",
                  "summary": "长知识已压缩后注入。",
                  "criteria": [],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        String longContent = "RAG Prompt 评测需要基于检索上下文减少幻觉和编造，输出 JSON，并在资料不足时说明缺口。".repeat(120);
        KnowledgeRetrievalService retrievalService = new KnowledgeRetrievalService(List.of(knowledge(
                201L,
                "超长 RAG Prompt 评测规则",
                longContent,
                LocalDateTime.of(2026, 6, 15, 8, 10, 0)
        )), null);
        PromptOptimizationService service = new PromptOptimizationService(client, retrievalService, new ObjectMapper());

        Map<String, Object> response = service.evaluate(Map.of(
                "currentPrompt", "请评测 RAG Prompt，要求基于检索上下文减少幻觉和编造，并输出 JSON。",
                "data", Map.of("evaluationGoal", "验证长知识上下文不会撑爆本地模型")
        ));

        assertEquals(true, response.get("success"));
        assertTrue(client.lastPrompt.length() < 6000, () -> "prompt length: " + client.lastPrompt.length());
        assertTrue(client.lastPrompt.contains("超长 RAG Prompt 评测规则"));
        assertTrue(client.lastPrompt.contains("\"updatedAt\" : \"2026-06-15T08:10\""));
    }

    @Test
    void evaluateShouldFallbackWhenLlmIsUnavailable() {
        FakeOllamaChatClient client = new FakeOllamaChatClient(new RuntimeException("connection refused"));
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", "test prompt"));

        assertEquals(true, response.get("success"));
        assertEquals("llm_error_fallback", response.get("modelSource"));
        assertEquals(false, response.get("jsonParsed"));
        assertInstanceOf(Number.class, response.get("overallScore"));
        assertTrue(String.valueOf(response.get("error")).contains("connection refused"));
    }

    @Test
    void evaluateShouldKeepFallbackSummaryReadableWhenModelJsonIsPartial() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 2,
                  "summary": "模型返回了半截 JSON",
                  "criteria": [
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", "请评测 RAG Prompt 并输出 JSON"));

        assertEquals(true, response.get("success"));
        assertEquals(false, response.get("jsonParsed"));
        assertEquals("模型返回非严格 JSON，已使用基础规则生成评测结果", response.get("summary"));
        assertTrue(String.valueOf(response.get("rawModelResponse")).contains("overallScore"));
    }

    @Test
    void evaluateShouldUseOllamaJsonModeAndCompactOversizedPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 72,
                  "grade": "B",
                  "summary": "模型 JSON 评测完成。",
                  "criteria": [],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);
        String oversizedPrompt = "你是合同摘要助手。请只输出 JSON。".repeat(700);

        Map<String, Object> response = service.evaluate(Map.of(
                "currentPrompt", oversizedPrompt,
                "data", Map.of("modelFamily", "通用可移植")
        ));

        assertEquals(true, response.get("success"));
        assertEquals(true, response.get("jsonParsed"));
        assertEquals("llm", response.get("modelSource"));
        assertEquals(2048, client.lastJsonNumPredict);
        assertTrue(client.lastJsonPrompt.contains("客户 Prompt"));
        assertTrue(client.lastJsonPrompt.length() < 9000, () -> "prompt length: " + client.lastJsonPrompt.length());
        assertFalse(client.generateCalled);
    }

    @Test
    void evaluateShouldPreserveOutputContractTailForLongPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 80,
                  "grade": "B",
                  "summary": "长 Prompt 已评测。",
                  "criteria": [],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of(
                "currentPrompt", longContractSummaryPrompt(),
                "data", Map.of("modelFamily", "通用可移植")
        ));

        assertEquals(true, response.get("success"));
        assertTrue(client.lastJsonPrompt.length() < 9000, () -> "prompt length: " + client.lastJsonPrompt.length());
        assertTrue(client.lastJsonPrompt.contains("# Role"));
        assertTrue(client.lastJsonPrompt.contains("# Output JSON Schema"));
        assertTrue(client.lastJsonPrompt.contains("\"structured_summary\""));
        assertTrue(client.lastJsonPrompt.contains("中间省略"));
    }

    @Test
    void evaluateShouldNormalizeFivePointOverallScoreAndFillMissingCriteria() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 4,
                  "grade": "B",
                  "summary": "模型返回了五分制总分和部分评分维度。",
                  "criteria": [
                    {
                      "id": "clarity",
                      "name": "目标清晰度",
                      "score": 4,
                      "level": "good",
                      "evidence": "目标清楚",
                      "suggestion": "保持"
                    }
                  ],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", "test prompt"));

        assertEquals(true, response.get("success"));
        assertEquals(80, response.get("overallScore"));
        List<?> criteria = (List<?>) response.get("criteria");
        assertEquals(10, criteria.size());
        assertTrue(criteria.stream().anyMatch(item -> String.valueOf(item).contains("上下文完整性")));
    }

    @Test
    void evaluateShouldNormalizeTenPointCriteriaAndCapOverallScore() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 102,
                  "grade": "A",
                  "summary": "模型返回了越界总分和十分制维度分。",
                  "criteria": [
                    {"id":"clarity","name":"目标清晰度","score":10,"level":"good","evidence":"目标清楚","suggestion":"保持"},
                    {"id":"context","name":"上下文完整性","score":8,"level":"good","evidence":"上下文较完整","suggestion":"保持"},
                    {"id":"output_format","name":"输出格式契约","score":5,"level":"good","evidence":"格式明确","suggestion":"保持"}
                  ],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", "请基于合同原文输出 JSON 摘要。"));

        assertEquals(true, response.get("success"));
        assertEquals(100, response.get("overallScore"));
        List<?> criteria = (List<?>) response.get("criteria");
        assertEquals(5, criterionScore(criteria, "clarity"));
        assertEquals(4, criterionScore(criteria, "context"));
        assertTrue(criteria.stream()
                .map(String::valueOf)
                .noneMatch(item -> item.contains("score=10")));
    }

    @Test
    void evaluateShouldNormalizeInconsistentHighScoreGrade() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 87,
                  "grade": "B",
                  "summary": "模型分数已达到 A，但等级返回偏低。",
                  "criteria": [],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", "请严格输出 JSON。"));

        assertEquals(true, response.get("success"));
        assertEquals(87, response.get("overallScore"));
        assertEquals("A", response.get("grade"));
    }

    @Test
    void evaluateShouldNotHardOverrideValidLlmScoreWithRuleCalibration() {
        FakeOllamaChatClient client = new FakeOllamaChatClient(lowContractSummaryScoreJson());
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", optimizedContractSummaryPromptForCalibration()));

        assertEquals(true, response.get("success"));
        assertEquals("llm_calibrated", response.get("modelSource"));
        assertEquals(58, response.get("rawModelOverallScore"));
        assertTrue(((Number) response.get("ruleOverallScore")).intValue() >= 85,
                () -> "rule score: " + response.get("ruleOverallScore"));
        assertEquals(true, response.get("scoreCalibrated"));
        assertTrue(((Number) response.get("overallScore")).intValue() > 58);
        assertTrue(((Number) response.get("overallScore")).intValue() < 85,
                () -> "valid llm score should not be hard overridden: " + response.get("overallScore"));
    }

    @Test
    void evaluateShouldRepairContradictoryLowOverallScoreWithHighModelCriteria() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 12,
                  "grade": "C",
                  "summary": "Prompt 已覆盖目标清晰度、上下文完整性、约束与边界、输出格式契约。",
                  "criteria": [
                    {"id":"clarity","name":"目标清晰度","score":4,"level":"good","evidence":"目标明确","suggestion":"保持"},
                    {"id":"context","name":"上下文完整性","score":4,"level":"good","evidence":"上下文完整","suggestion":"保持"},
                    {"id":"constraints","name":"约束与边界","score":5,"level":"good","evidence":"约束完整","suggestion":"保持"},
                    {"id":"output_format","name":"输出格式契约","score":5,"level":"good","evidence":"格式明确","suggestion":"保持"},
                    {"id":"few_shot","name":"Few-shot 示例","score":4,"level":"good","evidence":"有用例","suggestion":"保持"},
                    {"id":"reasoning_policy","name":"推理策略安全","score":4,"level":"good","evidence":"不输出隐藏思维链","suggestion":"保持"},
                    {"id":"eval_readiness","name":"可评测性","score":4,"level":"good","evidence":"可复测","suggestion":"保持"},
                    {"id":"robustness","name":"鲁棒性","score":4,"level":"good","evidence":"覆盖边界","suggestion":"保持"},
                    {"id":"brevity","name":"简洁成本","score":4,"level":"good","evidence":"长度可控","suggestion":"保持"},
                    {"id":"safety","name":"安全合规","score":4,"level":"good","evidence":"安全边界明确","suggestion":"保持"}
                  ],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", optimizedContractSummaryPromptForCalibration()));

        assertEquals(true, response.get("success"));
        assertEquals("llm_calibrated", response.get("modelSource"));
        assertEquals(12, response.get("rawModelOverallScore"));
        assertTrue(((Number) response.get("modelCriteriaOverallScore")).intValue() >= 80);
        assertTrue(((Number) response.get("modelSignalOverallScore")).intValue() > 60);
        assertTrue(((Number) response.get("overallScore")).intValue() > 60,
                () -> "contradictory low overall should be repaired: " + response.get("overallScore"));
        assertTrue(((Number) response.get("overallScore")).intValue() < 90);
    }

    @Test
    void evaluateShouldKeepOverallScoreCloseToDisplayedCriteria() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 34,
                  "grade": "D",
                  "summary": "模型总分较低，但未返回可解释的低维度评分。",
                  "criteria": [],
                  "weaknesses": [],
                  "optimizationFocus": []
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", optimizedContractSummaryPromptForCalibration()));

        assertEquals(true, response.get("success"));
        assertEquals("llm_calibrated", response.get("modelSource"));
        int overallScore = ((Number) response.get("overallScore")).intValue();
        int ruleOverallScore = ((Number) response.get("ruleOverallScore")).intValue();
        assertTrue(ruleOverallScore >= 85, () -> "rule score: " + ruleOverallScore);
        assertTrue(overallScore >= ruleOverallScore - 8,
                () -> "overall should stay close to displayed criteria, overall=" + overallScore + ", rule=" + ruleOverallScore);
        assertTrue(overallScore <= ruleOverallScore);
    }

    @Test
    void evaluateShouldCalibrateExplicitContractRiskPromptWhenModelUnderscoresIt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "overallScore": 40,
                  "grade": "C",
                  "summary": "模型低估了显式约束。",
                  "criteria": [
                    {"id":"clarity","name":"目标清晰度","score":3,"level":"warning","evidence":"目标不够明确","suggestion":"补充任务目标"},
                    {"id":"constraints","name":"约束与边界","score":3,"level":"warning","evidence":"约束不够明确","suggestion":"补充约束"},
                    {"id":"output_format","name":"输出格式契约","score":2,"level":"risk","evidence":"格式不明确","suggestion":"补充 JSON 字段"},
                    {"id":"safety","name":"安全合规","score":2,"level":"risk","evidence":"安全边界不明确","suggestion":"补充安全规则"}
                  ],
                  "weaknesses": [
                    {"criterionId":"output_format","issue":"输出格式不足","priority":"high","suggestion":"补充 JSON"}
                  ],
                  "optimizationFocus": ["补充输出格式"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.evaluate(Map.of("currentPrompt", contractApprovalRiskPrompt()));

        assertEquals(true, response.get("success"));
        assertEquals("llm_calibrated", response.get("modelSource"));
        assertEquals(40, response.get("rawModelOverallScore"));
        assertTrue(((Number) response.get("overallScore")).intValue() > 40);
        assertTrue(((Number) response.get("overallScore")).intValue() < 80);
        List<?> criteria = (List<?>) response.get("criteria");
        assertCriterionScoreAtLeast(criteria, "clarity", 4);
        assertCriterionScoreAtLeast(criteria, "constraints", 5);
        assertCriterionScoreAtLeast(criteria, "output_format", 5);
        assertCriterionScoreAtLeast(criteria, "safety", 5);
        String summary = String.valueOf(response.get("summary"));
        assertTrue(summary.contains("系统按可验证规则校准"));
        assertFalse(summary.contains("低估"));
    }

    @Test
    void optimizeShouldUseLlmToImprovePromptWithEvalLoopContext() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "已强化输出契约",
                  "optimizedPrompt": "# 优化后 Prompt\\n严格 JSON 输出。",
                  "optimizationNotes": ["强化 JSON 输出", "保留固定评估集"],
                  "evalPlan": ["复跑三个固定评估用例"],
                  "risks": ["可能过拟合当前失败样本"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", "# 当前 Prompt\n请分析合同风险。",
                "data", Map.of(
                        "modelFamily", "通用可移植",
                        "objective", "输出结构化合同风险摘要",
                        "successCriteria", "JSON 可解析；不猜测缺失金额",
                        "evalCases", "输入缺少金额的付款条款，要求不猜测金额。",
                        "critique", "当前版本格式漂移",
                        "failureCauses", List.of("format", "reasoning")
                )
        ));

        assertEquals(true, response.get("success"));
        assertEquals("# 优化后 Prompt\n严格 JSON 输出。", response.get("optimizedPrompt"));
        assertEquals("已强化输出契约", response.get("summary"));
        assertEquals(List.of("强化 JSON 输出", "保留固定评估集"), response.get("optimizationNotes"));
        assertTrue(client.lastPrompt.contains("# 当前 Prompt"));
        assertTrue(client.lastPrompt.contains("请根据评测短板，把用户原始 Prompt 改写为"));
        assertTrue(client.lastPrompt.contains("输出必须是完整可复用的 optimizedPrompt 文本，不是注释、总结或补丁片段。"));
        assertTrue(client.lastPrompt.contains("不要强制新增“目标/非目标/优化规则”等通用包装标题"));
        assertFalse(client.lastPrompt.contains("目标与非目标"));
        assertTrue(client.lastPrompt.contains("隐藏思维链") || client.lastPrompt.contains("内部推理"));
        assertTrue(client.lastPrompt.contains("格式漂移"));
    }

    @Test
    void optimizeShouldOnlySendCustomerPromptAndWeaknessesWithoutDiagnosticNoise() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "已按短板最小优化",
                  "optimizedPrompt": "# 优化后 Prompt\\n保留客户原始任务，只补充边界和输出约束。",
                  "optimizationNotes": ["只使用客户 Prompt 和短板摘要"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", "# Role\n你是合同审批风险助手，请基于用户提供的历史审批拒绝记录总结风险点。",
                "data", Map.of(
                        "promptInput", "# Role\n你是合同审批风险助手，请基于用户提供的历史审批拒绝记录总结风险点。",
                        "evaluationGoal", "检查客户 Prompt 是否边界清楚。",
                        "expectedOutput", "沿用客户 Prompt 的输出要求。",
                        "scores", Map.of("instruction", 3, "format", 2),
                        "failureCauses", List.of("format", "ambiguity"),
                        "evaluation", Map.of(
                                "summary", "输出边界不够稳。",
                                "weaknesses", List.of(Map.of(
                                        "criterionId", "output_format",
                                        "issue", "输出格式容易漂移",
                                        "priority", "high",
                                        "suggestion", "保留原有业务字段，只补充严格可解析要求"
                                )),
                                "ragContext", Map.of(
                                        "queryKeywords", List.of("rag", "missing", "info", "executable-playbook"),
                                        "matches", List.of(Map.of(
                                                "metadata", Map.of(
                                                        "category", "治理工具",
                                                        "sourceScene", "project-module-business-knowledge-upgrade",
                                                        "generatedFrom", "current-project",
                                                        "sourceType", "business-playbook-seed"
                                                )
                                        ))
                                )
                        )
                )
        ));

        assertEquals(true, response.get("success"));
        assertTrue(client.lastPrompt.contains("客户原始 Prompt"));
        assertTrue(client.lastPrompt.contains("输出格式容易漂移"));
        assertTrue(client.lastPrompt.contains("保留原有业务字段"));
        assertFalse(client.lastPrompt.contains("promptInput"));
        assertFalse(client.lastPrompt.contains("scores"));
        assertFalse(client.lastPrompt.contains("ragContext"));
        assertFalse(client.lastPrompt.contains("executable-playbook"));
        assertFalse(client.lastPrompt.contains("generatedFrom"));
        assertFalse(client.lastPrompt.contains("sourceScene"));
        assertFalse(client.lastPrompt.contains("missing_info"));
    }

    @Test
    void optimizeShouldPreserveOutputContractTailForLongPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "已按短板优化长 Prompt",
                  "optimizedPrompt": "# Role\\n合同摘要助手\\n# Output JSON Schema\\n{\\\"is_contract\\\": true, \\\"summary_fields\\\": [], \\\"structured_summary\\\": []}",
                  "optimizationNotes": ["保留输出契约"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", longContractSummaryPrompt(),
                "data", Map.of(
                        "failureCauses", List.of("format"),
                        "evaluation", Map.of(
                                "weaknesses", List.of(Map.of(
                                        "criterionId", "output_format",
                                        "issue", "输出契约尾部容易丢失",
                                        "priority", "high",
                                        "suggestion", "保留原始 JSON 输出字段"
                                ))
                        )
                )
        ));

        assertEquals(true, response.get("success"));
        assertTrue(client.lastJsonPrompt.length() < 11000, () -> "prompt length: " + client.lastJsonPrompt.length());
        assertTrue(client.lastJsonPrompt.contains("# Role"));
        assertTrue(client.lastJsonPrompt.contains("# Output JSON Schema"));
        assertTrue(client.lastJsonPrompt.contains("\"structured_summary\""));
        assertTrue(client.lastJsonPrompt.contains("中间省略"));
    }

    @Test
    void optimizeShouldFallbackWhenModelDropsOriginalOutputContract() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "只保留了开头规则",
                  "optimizedPrompt": "# Role\\n你是合同摘要助手。\\n# Task\\n判断是否为合同。",
                  "optimizationNotes": ["模型输出过短"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());
        String currentPrompt = """
                # Role
                你是合同摘要助手。

                # Output JSON Schema
                {
                  "is_contract": true,
                  "contract_type": "string",
                  "summary_fields": [],
                  "structured_summary": []
                }
                """;

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", currentPrompt,
                "data", Map.of(
                        "failureCauses", List.of("format"),
                        "evaluation", Map.of(
                                "weaknesses", List.of(Map.of(
                                        "criterionId", "output_format",
                                        "issue", "输出格式不稳定",
                                        "priority", "high",
                                        "suggestion", "必须保留原有合同摘要 JSON 字段"
                                ))
                        )
                )
        ));

        assertEquals(true, response.get("success"));
        assertEquals("local_validation_fallback", response.get("modelSource"));
        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains("合同摘要专用优化版 Prompt"));
        assertTrue(optimizedPrompt.contains("\"contract_type\""));
        assertTrue(optimizedPrompt.contains("\"summary_fields\""));
        assertTrue(optimizedPrompt.contains("\"structured_summary\""));
        assertFalse(optimizedPrompt.contains("### 强化后的执行约束"));
        assertTrue(String.valueOf(response.get("optimizationNotes")).contains("必须保留原有合同摘要 JSON 字段"));
    }

    @Test
    void optimizeShouldFallbackWhenModelKeepsOldPatchArtifactsInOptimizedPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "模型返回了旧补丁式优化稿",
                  "optimizedPrompt": "# Role\\n你是一名资深合同摘要助手。\\n# Output JSON Schema\\n{\\\"is_contract\\\":true,\\\"contract_type\\\":\\\"string\\\",\\\"summary_fields\\\":{},\\\"structured_summary\\\":{\\\"summary\\\":\\\"\\\",\\\"modules\\\":[{\\\"module_code\\\":\\\"basic_info\\\",\\\"module_name\\\":\\\"基础信息\\\",\\\"ai_generated\\\":true,\\\"points\\\":[]}]},\\\"summary_md\\\":\\\"\\\"}\\n### 输出要求\\n- `is_contract`：保留原字段语义与层级。",
                  "optimizationNotes": ["补充字段说明"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());
        String currentPrompt = contractSummaryPromptWithOldLocalArtifacts();

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", currentPrompt,
                "data", Map.of("failureCauses", List.of("format", "bloat"))
        ));

        assertEquals(true, response.get("success"));
        assertEquals("local_validation_fallback", response.get("modelSource"));
        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains("合同摘要专用优化版 Prompt"));
        assertTrue(optimizedPrompt.contains("\"is_contract\""));
        assertTrue(optimizedPrompt.contains("\"structured_summary\""));
        assertFalse(optimizedPrompt.contains("### 输出要求"));
        assertFalse(optimizedPrompt.contains("保留原字段语义与层级"));
        assertTrue(String.valueOf(response.get("validationError")).contains("过程性补丁"));
    }

    @Test
    void optimizeShouldFallbackWhenModelReturnsOnlyJsonSchemaInsteadOfExecutablePrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "模型只返回了 JSON schema",
                  "optimizedPrompt": "{\\n  \\\"is_contract\\\": true,\\n  \\\"contract_type\\\": \\\"string\\\",\\n  \\\"summary_fields\\\": {},\\n  \\\"structured_summary\\\": {\\\"summary\\\": \\\"\\\", \\\"modules\\\": [{\\\"module_code\\\": \\\"basic_info\\\", \\\"module_name\\\": \\\"基础信息\\\", \\\"ai_generated\\\": true, \\\"points\\\": []}]},\\n  \\\"summary_md\\\": \\\"\\\"\\n}",
                  "optimizationNotes": ["补充 JSON 字段"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", contractSummaryPromptWithOldLocalArtifacts(),
                "data", Map.of("failureCauses", List.of("format", "examples"))
        ));

        assertEquals(true, response.get("success"));
        assertEquals("local_validation_fallback", response.get("modelSource"));
        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains("合同摘要专用优化版 Prompt"));
        assertTrue(optimizedPrompt.contains("固定评测用例"));
        assertFalse(optimizedPrompt.trim().startsWith("{"));
        assertTrue(String.valueOf(response.get("validationError")).contains("只返回"));
    }

    @Test
    void optimizeShouldFallbackWhenOptimizedPromptFieldMissing() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "模型只给了说明，没有返回完整优化 Prompt。",
                  "optimizationNotes": ["缺少 optimizedPrompt 字段"],
                  "evalPlan": ["复测前先清洗输入"],
                  "risks": ["需本地补齐优化后 Prompt"]
                }
                """
        );
        PromptOptimizationService service = new PromptOptimizationService(client, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", "# 你是合同摘要助手。\n请输出结构化合同摘要 JSON。",
                "data", Map.of(
                        "failureCauses", List.of("format", "examples", "ambiguity", "bloat"),
                        "evaluation", Map.of(
                                "weaknesses", List.of(Map.of(
                                        "criterionId", "output_format",
                                        "issue", "输出字段契约不稳定",
                                        "priority", "high",
                                        "suggestion", "保留原始字段，补充边界与示例"
                                ))
                        )
                )
        ));

        assertEquals(true, response.get("success"));
        assertEquals(false, response.get("jsonParsed"));
        assertEquals("local_validation_fallback", response.get("modelSource"));
        assertTrue(String.valueOf(response.get("summary")).contains("模型优化结果未保留原始输出契约"));
        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains("# 你是合同摘要助手。"));
        assertNoOptimizationProcessArtifacts(optimizedPrompt);
    }

    @Test
    void optimizeShouldRetrieveRagContextAndInjectItIntoOptimizerPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "已结合项目 RAG 上下文优化",
                  "optimizedPrompt": "# 优化后 Prompt\\n只依据检索上下文回答。",
                  "optimizationNotes": ["加入 RAG 上下文约束"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", "优化 RAG 可信问答 Prompt，资料不足时不要编造。",
                "data", Map.of(
                        "objective", "让回答基于检索上下文",
                        "successCriteria", "必须说明依据资料编号"
                )
        ));

        assertEquals(true, response.get("success"));
        assertTrue(client.lastPrompt.contains("可选 RAG 工程知识"));
        assertFalse(client.lastPrompt.contains("rag-grounded-answer"));
        assertTrue(client.lastPrompt.contains("资料不足时要明确说明缺口"));
        assertTrue(client.lastPrompt.contains("资料不足时应明确说明无法从资料确认"));
        assertInstanceOf(Map.class, response.get("ragContext"));
        assertTrue(String.valueOf(response.get("ragContext")).contains("rag-grounded-answer"));
    }

    @Test
    void optimizeShouldSkipIrrelevantRagContextBeforeCallingModel() {
        FakeOllamaChatClient client = new FakeOllamaChatClient(new RuntimeException("connection refused"));
        FakeVectorRagDemoService ragService = new FakeVectorRagDemoService(List.of(searchHit(
                "pizza-menu",
                "披萨菜单",
                "芝士披萨、榴莲披萨和门店优惠信息，本周新品第二份半价。",
                0.95
        )));
        PromptOptimizationService service = new PromptOptimizationService(client, ragService);

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", "请优化合同审批风控 Prompt，基于历史审批拒绝记录总结风险点，并严格输出 JSON。",
                "data", Map.of(
                        "objective", "生成合同审批风险规则",
                        "successCriteria", "不得引入历史拒绝记录之外的事实",
                        "failureCauses", List.of("format", "ambiguity")
                )
        ));

        Map<?, ?> ragContext = (Map<?, ?>) response.get("ragContext");
        assertEquals(false, ragContext.get("used"));
        assertTrue(((List<?>) ragContext.get("matches")).isEmpty());
        assertTrue(String.valueOf(ragContext.get("gateReason")).contains("相关性"));
        assertFalse(client.lastPrompt.contains("披萨菜单"));
        assertFalse(client.lastPrompt.contains("芝士披萨"));
        assertFalse(String.valueOf(response.get("optimizedPrompt")).contains("芝士披萨"));
    }

    @Test
    void optimizeShouldUseRelevantRagContextWithGateEvidence() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "已结合相关 RAG 证据优化",
                  "optimizedPrompt": "# 优化后 Prompt\\n基于历史审批拒绝记录生成合同审批风险规则。",
                  "optimizationNotes": ["引用审批拒绝记录和输出 JSON 约束"]
                }
                """);
        FakeVectorRagDemoService ragService = new FakeVectorRagDemoService(List.of(searchHit(
                "contract-approval-risk",
                "合同审批风控 Skill",
                "合同审批风控需要基于历史审批拒绝记录总结风险点，输出 JSON 风险规则，并脱敏展示证据。",
                0.92
        )));
        PromptOptimizationService service = new PromptOptimizationService(client, ragService);

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", "请优化合同审批风控 Prompt，基于历史审批拒绝记录总结审批风险点，输出 JSON。",
                "data", Map.of(
                        "objective", "生成合同审批风险规则",
                        "successCriteria", "风险规则必须可复核且证据脱敏"
                )
        ));

        Map<?, ?> ragContext = (Map<?, ?>) response.get("ragContext");
        assertEquals(true, ragContext.get("used"));
        assertEquals(1, ((List<?>) ragContext.get("matches")).size());
        assertTrue(String.valueOf(ragContext.get("matchedKeywords")).contains("合同"));
        assertTrue(client.lastPrompt.contains("历史审批拒绝记录"));
        assertTrue(client.lastPrompt.contains("只有 RAG 标记为 used=true"));
        assertFalse(client.lastPrompt.contains("contract-approval-risk"));
    }

    @Test
    void optimizeShouldReturnLocalOptimizedPromptWhenLlmIsUnavailable() {
        FakeOllamaChatClient client = new FakeOllamaChatClient(new RuntimeException("connection refused"));
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", "你是合同风险助手，请分析风险。",
                "data", Map.of(
                        "objective", "输出结构化合同风险摘要",
                        "successCriteria", "JSON 可解析；不猜测缺失金额",
                        "failureCauses", List.of("format", "examples"),
                        "evaluation", Map.of(
                                "weaknesses", List.of(Map.of(
                                        "issue", "输出格式契约不足",
                                        "suggestion", "补充 JSON 字段和示例"
                                ))
                        )
                )
        ));

        assertEquals(true, response.get("success"));
        assertEquals(false, response.get("jsonParsed"));
        assertEquals("按短板直接生成可复用优化版 Prompt", response.get("summary"));
        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains("输出 JSON 时不要包裹 Markdown 代码块"));
        assertNoOptimizationProcessArtifacts(optimizedPrompt);
        assertInstanceOf(List.class, response.get("optimizationNotes"));
        assertFalse(((List<?>) response.get("optimizationNotes")).isEmpty());
        assertTrue(String.valueOf(response.get("error")).contains("connection refused"));
    }

    @Test
    void optimizeLocalFallbackShouldRewriteContractSummaryPromptAndRetestAboveTarget() {
        PromptOptimizationService optimizer = new PromptOptimizationService(
                new FakeOllamaChatClient(new RuntimeException("connection refused")),
                new KnowledgeRetrievalService(List.of(), null),
                new ObjectMapper()
        );
        String currentPrompt = contractSummaryPromptWithOldLocalArtifacts();

        Map<String, Object> optimizeResponse = optimizer.optimize(Map.of(
                "currentPrompt", currentPrompt,
                "data", Map.of(
                        "failureCauses", List.of("format", "examples", "ambiguity", "bloat"),
                        "evaluation", Map.of(
                                "summary", "结构化输出要求较多，但复测分数没有提升。",
                                "weaknesses", List.of(
                                        Map.of(
                                                "criterionId", "output_format",
                                                "issue", "字段契约和示例不够可测",
                                                "priority", "high",
                                                "suggestion", "围绕合同摘要 JSON 字段直接重写优化后 Prompt"
                                        ),
                                        Map.of(
                                                "criterionId", "brevity",
                                                "issue", "旧优化结果混入过程性补丁",
                                                "priority", "high",
                                                "suggestion", "删除过程性补丁并保留唯一权威规则"
                                        )
                                )
                        )
                )
        ));

        assertEquals(true, optimizeResponse.get("success"));
        assertEquals("local", optimizeResponse.get("modelSource"));
        String optimizedPrompt = String.valueOf(optimizeResponse.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains("合同摘要专用优化版 Prompt"));
        assertTrue(optimizedPrompt.contains("\"is_contract\""));
        assertTrue(optimizedPrompt.contains("\"structured_summary\""));
        assertTrue(optimizedPrompt.contains("\"module_code\""));
        assertTrue(optimizedPrompt.contains("固定评测用例"));
        assertTrue(optimizedPrompt.contains("非合同输入"));
        assertTrue(optimizedPrompt.length() < 14000, () -> "optimized length: " + optimizedPrompt.length());
        assertFalse(optimizedPrompt.contains("### 强化后的执行约束"));
        assertFalse(optimizedPrompt.contains("### 输出要求"));
        assertFalse(optimizedPrompt.contains("原 Prompt 保留边界"));
        assertFalse(optimizedPrompt.contains("保留原字段语义与层级"));
        assertNoOptimizationProcessArtifacts(optimizedPrompt);

        FakeOllamaChatClient scoringClient = new FakeOllamaChatClient(lowContractSummaryScoreJson());
        PromptOptimizationService scorer = new PromptOptimizationService(scoringClient, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());

        Map<String, Object> before = scorer.evaluate(Map.of("currentPrompt", currentPrompt));
        Map<String, Object> after = scorer.evaluate(Map.of("currentPrompt", optimizedPrompt));

        assertTrue(Number.class.isInstance(before.get("overallScore")));
        assertTrue(Number.class.isInstance(after.get("overallScore")));
        assertTrue(((Number) after.get("overallScore")).intValue() > ((Number) before.get("overallScore")).intValue(),
                () -> "before=" + before.get("overallScore") + ", after=" + after.get("overallScore"));
        assertTrue(((Number) after.get("overallScore")).intValue() < 85,
                () -> "valid low model score should not be hard overridden: " + after.get("overallScore"));
        assertEquals("llm_calibrated", after.get("modelSource"));
        assertCriterionScoreAtLeast((List<?>) after.get("criteria"), "output_format", 5);
        assertCriterionScoreAtLeast((List<?>) after.get("criteria"), "eval_readiness", 5);
    }

    @Test
    void evaluateShouldLocallyScoreOptimizedContractSummaryPromptWhenLlmTimesOut() {
        PromptOptimizationService optimizer = new PromptOptimizationService(
                new FakeOllamaChatClient(new RuntimeException("connection refused")),
                new KnowledgeRetrievalService(List.of(), null),
                new ObjectMapper()
        );
        String currentPrompt = contractSummaryPromptWithOldLocalArtifacts();
        String optimizedPrompt = String.valueOf(optimizer.optimize(Map.of(
                "currentPrompt", currentPrompt,
                "data", Map.of("failureCauses", List.of("format", "examples", "ambiguity", "bloat"))
        )).get("optimizedPrompt"));

        PromptOptimizationService scorer = new PromptOptimizationService(
                new FakeOllamaChatClient(new RuntimeException("Read timed out")),
                new KnowledgeRetrievalService(List.of(), null),
                new ObjectMapper()
        );

        Map<String, Object> response = scorer.evaluate(Map.of("currentPrompt", optimizedPrompt));

        assertEquals(true, response.get("success"));
        assertEquals("llm_error_fallback", response.get("modelSource"));
        assertEquals(false, response.get("jsonParsed"));
        assertTrue(((Number) response.get("overallScore")).intValue() >= 85,
                () -> "score: " + response.get("overallScore"));
        assertCriterionScoreAtLeast((List<?>) response.get("criteria"), "output_format", 5);
        assertCriterionScoreAtLeast((List<?>) response.get("criteria"), "eval_readiness", 5);
        assertTrue(String.valueOf(response.get("error")).contains("Read timed out"));
    }

    @Test
    void optimizeLocalFallbackShouldPreserveOriginalPromptAndAvoidGenericSchemaNoise() {
        FakeOllamaChatClient client = new FakeOllamaChatClient(new RuntimeException("connection refused"));
        PromptOptimizationService service = new PromptOptimizationService(client, new KnowledgeRetrievalService(List.of(), null), new ObjectMapper());
        String currentPrompt = """
                # Role
                你是合同审批风险助手。

                # Task
                基于用户提供的历史审批拒绝记录总结风险点。
                """;

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", currentPrompt,
                "data", Map.of(
                        "evaluation", Map.of(
                                "summary", "输入边界和输出稳定性不足。",
                                "weaknesses", List.of(Map.of(
                                        "criterionId", "constraints",
                                        "issue", "输入边界不够明确",
                                        "priority", "high",
                                        "suggestion", "明确只使用用户提供的历史审批拒绝记录，不引入外部事实"
                                ))
                        ),
                        "failureCauses", List.of("ambiguity")
                )
        ));

        assertEquals(true, response.get("success"));
        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains(currentPrompt.trim()));
        assertTrue(optimizedPrompt.contains("明确只使用用户提供的历史审批拒绝记录"));
        assertNoOptimizationProcessArtifacts(optimizedPrompt);
        assertFalse(optimizedPrompt.contains("\"summary\": \"string\""));
        assertFalse(optimizedPrompt.contains("\"missing_info\""));
        assertFalse(optimizedPrompt.contains("### 当前工程 RAG 检索上下文"));
    }

    @Test
    void optimizeLocalFallbackShouldNotInjectRagDiagnosticsIntoDomainPrompt() {
        FakeOllamaChatClient client = new FakeOllamaChatClient(new RuntimeException("connection refused"));
        FakeVectorRagDemoService ragService = new FakeVectorRagDemoService(List.of(searchHit(
                "contract-summary-rule",
                "合同摘要规则",
                "合同文档摘要质量规则要求基于合同原文输出 JSON 字段，缺失信息使用空字符串或空数组，避免编造。",
                0.93
        )));
        PromptOptimizationService service = new PromptOptimizationService(client, ragService);

        Map<String, Object> response = service.optimize(Map.of(
                "currentPrompt", """
                        # Role
                        你是合同摘要助手。

                        # Task
                        基于合同文档原文输出 JSON 字段摘要，并保证摘要质量。
                        """,
                "data", Map.of(
                        "failureCauses", List.of("format", "ambiguity"),
                        "evaluation", Map.of(
                                "weaknesses", List.of(Map.of(
                                        "criterionId", "output_format",
                                        "issue", "输出格式不稳定",
                                        "priority", "high",
                                        "suggestion", "保留合同摘要字段并严格输出 JSON"
                                ))
                        )
                )
        ));

        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(String.valueOf(response.get("ragContext")).contains("contract-summary-rule"));
        assertFalse(optimizedPrompt.contains("metadata"));
        assertFalse(optimizedPrompt.contains("score"));
        assertFalse(optimizedPrompt.contains("检索 ID"));
    }

    @Test
    void optimizeShouldFallbackToLocalOptimizationWhenJsonParsingFails() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("请使用更清晰的角色、约束和 JSON 格式。");
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.optimize(Map.of("currentPrompt", "test prompt"));

        assertEquals(true, response.get("success"));
        assertEquals(false, response.get("jsonParsed"));
        assertEquals("按短板直接生成可复用优化版 Prompt", response.get("summary"));
        assertNoOptimizationProcessArtifacts(String.valueOf(response.get("optimizedPrompt")));
        assertTrue(String.valueOf(response.get("optimizationNotes")).contains("按通用 Prompt 工程规则补齐"));
        assertFalse(((List<?>) response.get("optimizationNotes")).isEmpty());
        assertInstanceOf(List.class, response.get("optimizationNotes"));
        assertFalse(String.valueOf(response.get("optimizationNotes")).contains("请使用更清晰的角色"));
    }

    @Test
    void optimizeShouldSerializeStructuredPromptWithoutJavaMapSyntax() {
        FakeOllamaChatClient client = new FakeOllamaChatClient("""
                {
                  "summary": "返回了结构化 Prompt",
                  "optimizedPrompt": {
                    "role": "合同风险分析助手",
                    "rules": ["严格 JSON 输出", "不输出隐藏思维链"]
                  },
                  "optimizationNotes": ["结构化内容已保留"]
                }
                """);
        PromptOptimizationService service = new PromptOptimizationService(client);

        Map<String, Object> response = service.optimize(Map.of("currentPrompt", "test prompt"));

        assertEquals(true, response.get("success"));
        String optimizedPrompt = String.valueOf(response.get("optimizedPrompt"));
        assertTrue(optimizedPrompt.contains("\"role\""));
        assertTrue(optimizedPrompt.contains("\"rules\""));
        assertFalse(optimizedPrompt.contains("role="));
    }

    private static class FakeOllamaChatClient extends OllamaChatClient {
        private final String response;
        private final RuntimeException exception;
        private String lastPrompt;

        private FakeOllamaChatClient(String response) {
            super(new RestTemplate(), "http://localhost:11434", "qwen2.5");
            this.response = response;
            this.exception = null;
        }

        private FakeOllamaChatClient(RuntimeException exception) {
            super(new RestTemplate(), "http://localhost:11434", "qwen2.5");
            this.response = null;
            this.exception = exception;
        }

        @Override
        public String generate(String prompt) {
            generateCalled = true;
            lastPrompt = prompt;
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        @Override
        public String generateJson(String prompt, int numPredict) {
            lastPrompt = prompt;
            lastJsonPrompt = prompt;
            lastJsonNumPredict = numPredict;
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        private boolean generateCalled;
        private String lastJsonPrompt;
        private int lastJsonNumPredict;
    }

    private static class FakeVectorRagDemoService extends VectorRagDemoService {
        private final List<SearchHit> matches;

        private FakeVectorRagDemoService(List<SearchHit> matches) {
            super(new FakeOllamaChatClient("{}"));
            this.matches = matches;
        }

        @Override
        public VectorSearchResponse search(String query, int topK) {
            return new VectorSearchResponse(query, topK, matches);
        }
    }

    private static VectorRagDemoService.SearchHit searchHit(
            String id,
            String title,
            String content,
            double score) {
        return new VectorRagDemoService.SearchHit(id, title, content, score, Map.of("source", "test"));
    }

    private static void assertNoOptimizationProcessArtifacts(String optimizedPrompt) {
        assertFalse(optimizedPrompt.contains("你可直接复用的优化后 Prompt"));
        assertFalse(optimizedPrompt.contains("本轮优化要点"));
        assertFalse(optimizedPrompt.contains("基于短板的补强规则"));
        assertFalse(optimizedPrompt.contains("### 强化后的执行约束"));
        assertFalse(optimizedPrompt.contains("保留原字段语义与层级"));
        assertFalse(optimizedPrompt.contains("\n非目标:"));
        assertFalse(optimizedPrompt.contains("\n优化规则:"));
        assertFalse(optimizedPrompt.contains("RAG Gate"));
        assertFalse(optimizedPrompt.contains("评分卡"));
    }

    private static String lowContractSummaryScoreJson() {
        return """
                {
                  "overallScore": 58,
                  "grade": "C",
                  "summary": "当前 Prompt 目标明确，但结构化输出和可评测性仍不足。",
                  "criteria": [
                    {"id":"clarity","name":"目标清晰度","score":3,"level":"warning","evidence":"目标基本存在","suggestion":"补充完成条件"},
                    {"id":"context","name":"上下文完整性","score":3,"level":"warning","evidence":"输入边界不够清晰","suggestion":"补充输入来源"},
                    {"id":"constraints","name":"约束与边界","score":3,"level":"warning","evidence":"边界较分散","suggestion":"集中约束"},
                    {"id":"output_format","name":"输出格式契约","score":2,"level":"risk","evidence":"字段约束不稳定","suggestion":"补充 JSON schema"},
                    {"id":"few_shot","name":"Few-shot 示例","score":2,"level":"risk","evidence":"缺少覆盖边界的示例","suggestion":"补充正常、缺失、非合同示例"},
                    {"id":"reasoning_policy","name":"推理策略安全","score":3,"level":"warning","evidence":"未充分说明","suggestion":"不输出隐藏思维链"},
                    {"id":"eval_readiness","name":"可评测性","score":2,"level":"risk","evidence":"缺少固定用例","suggestion":"补充固定评测用例"},
                    {"id":"robustness","name":"鲁棒性","score":3,"level":"warning","evidence":"缺失信息处理不稳定","suggestion":"补充缺失与歧义规则"},
                    {"id":"brevity","name":"简洁成本","score":2,"level":"risk","evidence":"规则重复","suggestion":"删除重复规则"},
                    {"id":"safety","name":"安全合规","score":3,"level":"warning","evidence":"安全约束不足","suggestion":"补充不编造和不输出法律建议"}
                  ],
                  "weaknesses": [
                    {"criterionId":"output_format","issue":"输出格式不稳定","priority":"high","suggestion":"补充合同摘要 JSON 字段要求"},
                    {"criterionId":"eval_readiness","issue":"缺少固定用例","priority":"high","suggestion":"增加固定评测用例"}
                  ],
                  "optimizationFocus": ["补强输出格式契约", "增加固定评测用例"]
                }
                """;
    }

    private static String optimizedContractSummaryPromptForCalibration() {
        return """
                # 合同摘要专用优化版 Prompt

                你是一名资深合同摘要助手，负责基于合同原文输出结构化合同摘要 JSON。

                # Task
                请判断输入原文是否属于合同原文；若是合同，识别合同类型并输出 `summary_fields` 与 `structured_summary`；若为非合同，输出 `summary_md` 和 `"is_contract": false`。

                # 输入与事实边界
                合同原文是唯一事实来源；不得编造、不得臆测。缺失、未明确的信息输出空字符串或空数组。
                不输出法律建议、不输出审批建议、不输出审批结论。

                # 输出 JSON 契约
                合同场景必须包含 `"is_contract"`、`"summary_fields"`、`"structured_summary"`、`"module_code"`、`"points"`。
                非合同场景只包含 `"summary_md"` 和 `"is_contract"`。

                # 固定评测用例
                正常合同、缺失字段、非合同、歧义类型均需按成功标准输出；四类固定合同之外不得强行套用固定类型。
                """;
    }

    private static Knowledge knowledge(Long id, String title, String content, LocalDateTime updatedAt) {
        Knowledge knowledge = new Knowledge();
        knowledge.setId(id);
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setStatus("published");
        knowledge.setUpdatedAt(updatedAt);
        return knowledge;
    }

    private static void assertCriterionScoreAtLeast(List<?> criteria, String id, int minScore) {
        Object criterion = criteria.stream()
                .filter(item -> String.valueOf(item).contains("id=" + id) || String.valueOf(item).contains("\"id\":\"" + id + "\""))
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(criterion).contains("score=" + minScore)
                || String.valueOf(criterion).matches(".*score=([%d-5]).*".formatted(minScore)));
    }

    private static int criterionScore(List<?> criteria, String id) {
        Object criterion = criteria.stream()
                .filter(item -> String.valueOf(item).contains("id=" + id) || String.valueOf(item).contains("\"id\":\"" + id + "\""))
                .findFirst()
                .orElseThrow();
        Object score = ((Map<?, ?>) criterion).get("score");
        return ((Number) score).intValue();
    }

    private static String contractApprovalRiskPrompt() {
        return """
                你是一个企业合同审批风控助手。你的任务是基于某一审批人在某一合同二级类型下的历史审批拒绝数据，提炼该审批人的个人审批关注点，生成一份结构化的“个人审批 skill”。

                请严格遵守以下规则：
                1. 仅使用输入数据中提供的历史审批拒绝记录，不得引入外部知识，不得臆测审批人的偏好。
                2. 总结该审批人历史上关注并导致拒绝的风险点；即使只有 1 条有效历史拒绝记录，也需要生成对应的个人审批 skill。
                3. 不得输出具体合同编号、客户名称、供应商名称、金额、个人姓名等敏感信息；如需引用历史依据，只输出脱敏后的摘要。
                4. 同义或相近的拒绝原因需要合并为同一风险点。
                5. 每个风险点必须能用于后续新合同审批时判断是否命中，不能只写抽象描述。
                6. 如果只有 1 条历史拒绝记录，也需要生成风险点，但 confidence 应标记为 low，并在历史依据摘要中说明“仅基于单条历史拒绝记录生成”。
                7. 输出结果用于审批参考，不得写成确定性法律结论或强制审批建议。

                输入信息如下：
                审批人ID：{{approver_id}}
                租户ID：{{tenant_id}}
                合同一级类型：{{contract_type_lv1}}
                合同二级类型：{{contract_type_lv2}}
                历史审批拒绝记录：
                {{historical_rejection_records}}

                请按以下 JSON 格式输出：
                {
                  "generate_result": "generated",
                  "risk_rules": [
                    {
                      "risk_name": "风险点名称",
                      "trigger_conditions": ["后续新合同中可用于判断命中的条件1"],
                      "confidence": "high / medium / low",
                      "approval_suggestion": "审批时重点核对什么",
                      "display_copy": "前台展示给审批人的风险提示文案"
                    }
                  ],
                  "safety_notes": [
                    "该结果仅基于历史审批拒绝记录生成",
                    "AI生成内容仅供审批参考，不影响审批人自主判断"
                  ]
                }
                """;
    }

    private static String longContractSummaryPrompt() {
        return """
                # Role
                你是一名资深合同摘要助手，擅长基于合同原文输出适合合同审批页面展示的结构化摘要。

                # Task
                请判断原文是否属于合同文本，并输出结构化合同摘要 JSON。
                """
                + "合同识别、字段提取、缺失信息处理、风险边界和展示规则。\n".repeat(900)
                + """

                # Output JSON Schema
                {
                  "is_contract": true,
                  "contract_type": "string",
                  "summary_fields": [],
                  "structured_summary": [],
                  "summary_md": "string"
                }
                """;
    }

    private static String contractSummaryPromptWithOldLocalArtifacts() {
        return """
                # Role
                你是一名资深合同摘要助手，擅长基于合同原文输出适合合同审批页面展示的结构化摘要。
                你需要基于合同原文生成结构化合同摘要 JSON。

                # Task
                请先判断原文是否属于合同文本；若是合同，请先识别合同类型，再按该类型对应要素提取结构化合同摘要；若不是合同，生成忠实的通用文档概要。

                # Contract Output Schema
                1. 若 `is_contract=true`，必须采用固定输出 schema：仅输出一个合法 JSON 对象，包含 `is_contract`、`contract_type`、`summary_fields`、`structured_summary`。
                2. 若 `is_contract=false`，只输出 `summary_md` 和 `is_contract`。
                3. 四类固定合同必须全量保留对应 summary_fields 字段并保持顺序；原文未明确写明的字段输出空字符串。

                # Structured Summary Output Rules
                1. `structured_summary.summary` 为 1-2 句合同整体摘要，只基于原文，不输出 Markdown。
                2. 固定六个模块必须始终返回并保持顺序：`basic_info`、`subject_price`、`performance`、`breach_liability`、`dispute_resolution`、`other`。
                3. 每个模块必须包含 `module_code`、`module_name`、`ai_generated`、`points`。
                4. 每个 point 必须包含 `name`、`detail`、`ai_generated`。

                # Hard Rules
                1. 只依据合同原文提取，不得编造、补全、猜测原文没有的信息。
                2. 不输出法律建议、审批建议、审批结论、主观评价、推理过程或分类过程。
                3. 仅输出一个合法 JSON 对象，不要输出 Markdown 代码块或解释文字。

                # Output JSON Schema
                合同场景：
                {
                  "is_contract": true,
                  "contract_type": "采购合同",
                  "summary_fields": {
                    "合同主体": "...",
                    "采购标的": "...",
                    "合同金额": "..."
                  },
                  "structured_summary": {
                    "summary": "双方签署采购合同，约定采购标的、价款、付款、交付验收及违约责任等事项。",
                    "modules": [
                      {
                        "module_code": "basic_info",
                        "module_name": "基础信息",
                        "ai_generated": true,
                        "points": [
                          {"name": "甲方", "detail": "采购方为...。", "ai_generated": true}
                        ]
                      }
                    ]
                  }
                }

                非合同场景：
                {
                  "summary_md": "...",
                  "is_contract": false
                }

                ### 强化后的执行约束
                - 补强输出格式契约，要求严格输出 JSON 且不包裹 Markdown 代码块。
                - 增加 Few-shot 示例位，覆盖正常、缺失和歧义场景。
                - 补充目标、非目标、输入边界和缺失信息处理规则。
                - 删除重复提示，保留唯一权威规则来源。

                ### 输出要求
                - `is_contract`：保留原字段语义与层级，按输入可核验的方式输出；信息缺失时返回空值并说明无法判断。
                - `contract_type`：保留原字段语义与层级，按输入可核验的方式输出；信息缺失时返回空值并说明无法判断。
                - `structured_summary`：保留原字段语义与层级，按输入可核验的方式输出；信息缺失时返回空值并说明无法判断。
                """;
    }
}
