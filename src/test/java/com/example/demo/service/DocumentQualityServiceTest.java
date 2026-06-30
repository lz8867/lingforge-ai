package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityServiceTest {

    @Test
    void evaluateShouldGenerateScoredReportFromDesignDocumentContent() {
        DocumentQualityService service = new DocumentQualityService();

        DocumentQualityResult result = service.evaluate(new DocumentQualityRequest(
                "文档质量智能评测系统设计文档",
                "技术设计文档",
                """
                        1. 项目概述
                        搭建规则引擎 + LLM-Judge + 量化指标 + 可视化报告的自动化文档质量评测系统。

                        2. 整体系统架构
                        接入层、调度层、核心引擎层、数据存储层。

                        3. 核心模块详细设计
                        包含文档解析、规则引擎、LLM评测、量化指标、报告生成。
                        """
        ));

        assertTrue(result.success());
        assertEquals(6, result.dimensionScores().size());
        assertTrue(result.overallScore() < 90);
        assertTrue(result.ruleScore() < 100);
        assertTrue(result.llmJudgeScore() < 100);
        assertTrue(result.metricScore() > 0);
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("MISSING_DATA_MODEL")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("MISSING_API_CONTRACT")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("MISSING_ACCEPTANCE_CRITERIA")));
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.id().equals("heading_count")));
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.id().equals("issue_count")));
        assertTrue(result.recommendations().stream().anyMatch(item -> item.contains("数据模型")));
        assertTrue(result.visualizationData().containsKey("radar"));
        assertTrue(result.visualizationData().containsKey("scoreWeights"));
    }

    @Test
    void evaluateShouldPassWellStructuredDocumentWithEvidenceMetricsAndAcceptance() {
        DocumentQualityService service = new DocumentQualityService();

        DocumentQualityResult result = service.evaluate(new DocumentQualityRequest(
                "评测系统落地方案",
                "技术设计文档",
                """
                        # 概述
                        本方案用于对企业文档进行质量评测，目标是输出可追溯分数、问题清单和整改建议。

                        # 架构与流程
                        用户提交文档后，系统依次完成解析、规则引擎硬校验、LLM-Judge 软评测、量化指标计算和报告生成。

                        # 数据模型
                        DocumentQualityTask 包含 id、documentName、documentType、content、status、createdAt。
                        DocumentQualityIssue 包含 severity、location、evidence、deduction、suggestion。

                        # API 接口
                        POST /api/document-quality/evaluate 接收文档名称、类型和正文，返回总分、六维度评分、问题清单和指标。

                        # 规则与指标
                        必填章节、标题层级、重复段落、敏感信息、平均句长、段落长度和问题数量都会参与评分。

                        # 风险与异常处理
                        当输入为空、疑似泄露密码或包含隐私信息时，系统返回阻断级问题并给出脱敏建议。

                        # 测试验收
                        验收标准包括：缺失章节能识别、敏感信息能阻断、完整文档得分不低于 80、报告 JSON 可被前端渲染。

                        # 可视化报告
                        报告展示雷达图、分数分布、缺陷类型统计、指标卡片和整改建议。
                        """
        ));

        assertTrue(result.success());
        assertTrue(result.overallScore() >= 80);
        assertFalse(result.issues().stream().anyMatch(issue -> issue.id().startsWith("MISSING_")));
        assertTrue(result.dimensionScores().stream().allMatch(dimension -> dimension.score() >= 75));
        assertTrue(result.grade().contains("优质") || result.grade().contains("良好"));
    }

    @Test
    void evaluateShouldExposeRagRetrievalEvidenceAndCoverageMetric() {
        DocumentQualityService service = new DocumentQualityService();

        DocumentQualityResult result = service.evaluate(new DocumentQualityRequest(
                "RAG增强文档质量评测方案",
                "技术设计文档",
                """
                        # 项目概述
                        使用规则引擎、向量检索和 RAG 上下文增强文档质量评测，输出可追溯评分。

                        # 数据模型
                        DocumentQualityTask 保存文档名称、类型、正文、状态和评分结果。
                        DocumentQualityChunk 保存 chunkId、标题、正文片段、向量摘要和来源位置。

                        # API 接口
                        POST /api/document-quality/evaluate 接收 documentName、documentType、content。
                        响应返回 overallScore、grade、issues、metrics、dimensionScores 和 visualizationData。

                        # 风险与异常处理
                        当文件解析失败或向量检索没有命中关键证据时，报告给出明确缺口，不伪造结果。

                        # 测试验收
                        验收标准包括：切块数量可统计、接口契约可检索、缺口证据可追溯、完整文档得分不低于 80。
                        """
        ));

        assertTrue(result.success());
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.id().equals("rag_context_coverage")));
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.id().equals("field_extraction_coverage")));
        assertTrue(result.visualizationData().containsKey("ragRetrieval"));
        assertTrue(result.visualizationData().containsKey("extractedFields"));
        assertTrue(result.visualizationData().containsKey("skillPipeline"));
        assertTrue(String.valueOf(result.visualizationData().get("ragRetrieval")).contains("api_contract"));
        assertTrue(String.valueOf(result.visualizationData().get("extractedFields")).contains("api_contracts"));
        assertTrue(String.valueOf(result.visualizationData().get("skillPipeline")).contains("字段抽取"));
        assertTrue(result.dimensionScores().stream().anyMatch(dimension -> dimension.evidence().contains("RAG")));
    }

    @Test
    void evaluateShouldUseRealJudgeDimensionScoresWhenLlmJudgeSucceeds() {
        DocumentQualityJudgeClient fakeJudge = new DocumentQualityJudgeClient(null) {
            @Override
            public DocumentQualityJudgeResult judge(DocumentQualityJudgeRequest request) {
                return new DocumentQualityJudgeResult(
                        true,
                        "ollama",
                        "模型基于 RAG 证据完成 Judge。",
                        List.of(new DocumentQualityDimensionScore(
                                "completeness",
                                "完整性",
                                82,
                                20,
                                "warning",
                                "LLM Judge: 接口证据充分，但错误码缺失。",
                                List.of("补充错误码表")
                        )),
                        "Judge 成功"
                );
            }
        };
        DocumentQualityService service = new DocumentQualityService(new DocumentQualityRagService(), fakeJudge);

        DocumentQualityResult result = service.evaluate(new DocumentQualityRequest(
                "LLM Judge 验证",
                "技术设计文档",
                """
                        # 概述
                        使用 RAG 证据增强文档质量评测。

                        # 数据模型
                        DocumentQualityTask 包含字段。

                        # API 接口
                        POST /api/document-quality/evaluate 请求返回 issues metrics。

                        # 测试验收
                        验收标准包括 RAG 指标返回。

                        # 风险与异常处理
                        解析失败时返回错误并脱敏。
                        """
        ));

        assertTrue(result.success());
        assertEquals("ollama", result.visualizationData().get("llmJudgeSource"));
        assertEquals("模型基于 RAG 证据完成 Judge。", result.visualizationData().get("llmJudgeSummary"));
        assertTrue(result.dimensionScores().stream()
                .anyMatch(dimension -> dimension.id().equals("completeness") && dimension.score() == 82));
    }

    @Test
    void evaluateShouldBlockSensitiveInformationAndCapOverallScore() {
        DocumentQualityService service = new DocumentQualityService();

        DocumentQualityResult result = service.evaluate(new DocumentQualityRequest(
                "上线说明",
                "业务说明文档",
                """
                        # 概述
                        本文说明上线步骤。

                        # 操作步骤
                        登录测试环境执行脚本，临时 token=demo-secret，联系人手机号 13900000001。

                        # 验收标准
                        页面可访问且日志无报错。
                        """
        ));

        assertTrue(result.success());
        assertTrue(result.overallScore() <= 59);
        assertTrue(result.grade().contains("强制整改"));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.severity().equals("P0")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("SENSITIVE_INFORMATION")));
    }
}
