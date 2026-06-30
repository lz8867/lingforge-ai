package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityFieldExtractionServiceTest {

    @Test
    void extractShouldUseRagHitsToProduceStructuredQualityFields() {
        String content = """
                # 项目概述
                文档质量评测系统用于输出可追溯评分和整改建议。

                # 数据模型
                DocumentQualityTask 字段包括 id、documentName、documentType、content、status、createdAt。
                DocumentQualityIssue 字段包括 severity、location、evidence、deduction、suggestion。

                # API 接口
                POST /api/document-quality/evaluate 请求参数包括 documentName、documentType、content。
                响应返回 overallScore、issues、metrics、dimensionScores，错误码包括 EMPTY_CONTENT。

                # 风险与异常处理
                文件解析失败时返回明确错误；LLM Judge 超时后降级为启发式评分；敏感信息必须脱敏。

                # 测试验收
                验收标准包括：完整文档得分不低于 80，缺失接口契约时必须生成 P1 问题。
                """;
        DocumentQualityRagResult ragResult = new DocumentQualityRagService().analyze("技术设计文档", content);
        DocumentQualityFieldExtractionService service = new DocumentQualityFieldExtractionService();

        DocumentQualityFieldExtractionResult result = service.extract(
                "文档质量评测方案",
                "技术设计文档",
                content,
                ragResult
        );

        assertTrue(result.coverageScore() > 50);
        assertTrue(result.isComplete("api_contracts"));
        assertTrue(result.isComplete("data_models"));
        assertTrue(result.isComplete("risk_controls"));
        assertTrue(result.isComplete("acceptance_criteria"));
        assertFalse(result.fieldById("api_contracts").orElseThrow().evidence().isEmpty());
        assertTrue(result.visualizationRows().toString().contains("chunk-"));
    }

    @Test
    void extractShouldNotTreatGenericReturnWordingAsApiContract() {
        String content = """
                # 概述
                系统用于文档质量评测。

                # 风险与异常处理
                解析失败时返回明确错误，模型超时后降级为本地启发式评分。

                # 测试验收
                验收标准包括异常路径必须有提示。
                """;
        DocumentQualityRagResult ragResult = new DocumentQualityRagService().analyze("技术设计文档", content);
        DocumentQualityFieldExtractionResult result = new DocumentQualityFieldExtractionService().extract(
                "接口缺失文档",
                "技术设计文档",
                content,
                ragResult
        );

        assertFalse(result.isComplete("api_contracts"));
    }
}
