package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityRagServiceTest {

    @Test
    void analyzeShouldChunkDocumentAndRetrieveRelevantEvidenceByVectorSimilarity() {
        DocumentQualityRagService service = new DocumentQualityRagService();

        DocumentQualityRagResult result = service.analyze("技术设计文档", """
                # 概述
                系统用于文档质量评测。

                # API 接口
                POST /api/document-quality/evaluate 接收 documentName、documentType、content。
                响应返回 overallScore、issues、metrics 和 dimensionScores。

                # 风险与异常处理
                文件解析失败时返回明确错误，不伪造解析结果；敏感信息需要脱敏。
                """);

        assertTrue(result.chunkCount() >= 3);
        assertFalse(result.search("接口契约 请求 响应 错误码", 2).isEmpty());
        assertEquals("api_contract", result.evidenceByRequirement().get("api_contract").requirementId());
        assertTrue(result.evidenceByRequirement().get("api_contract").matched());
        assertTrue(result.evidenceByRequirement().get("api_contract").topHits().get(0).content().contains("/api/document-quality/evaluate"));
        assertTrue(result.coverageScore() > 0);
    }

    @Test
    void analyzeShouldNotMarkGenericErrorHandlingAsApiOrDataModelEvidence() {
        DocumentQualityRagService service = new DocumentQualityRagService();

        DocumentQualityRagResult result = service.analyze("技术设计文档", """
                # 概述
                系统用于文档质量评测。

                # 风险与异常处理
                解析失败时返回明确错误，模型超时后降级为本地启发式评分。

                # 测试验收
                验收标准包括异常路径必须有提示。
                """);

        assertFalse(result.evidenceByRequirement().get("api_contract").matched());
        assertFalse(result.evidenceByRequirement().get("data_model").matched());
        assertTrue(result.evidenceByRequirement().get("risk_handling").matched());
        assertTrue(result.evidenceByRequirement().get("acceptance").matched());
    }
}
