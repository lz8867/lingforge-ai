package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityRuleConfigServiceTest {

    @Test
    void evaluateShouldApplyRequiredEvidenceRulesFromJsonConfig() {
        DocumentQualityRuleConfigService ruleConfigService = DocumentQualityRuleConfigService.fromJson("""
                {
                  "thresholds": {
                    "minHeadingCount": 1,
                    "longParagraphChars": 240,
                    "duplicateRate": 0.12,
                    "averageSentenceLengthWarning": 70,
                    "averageSentenceLengthRisk": 90
                  },
                  "documentTypes": [
                    {
                      "typeKeywords": ["发布"],
                      "requiredEvidence": [
                        {
                          "issueId": "MISSING_ROLLOUT_WINDOW",
                          "severity": "P1",
                          "category": "完整性",
                          "description": "发布方案缺少上线窗口。",
                          "suggestion": "补充上线窗口、执行人和回滚截止时间。",
                          "evidenceMessage": "配置化规则未检测到上线窗口。",
                          "matcher": "keyword_or_rag",
                          "ragRequirementId": "structure",
                          "keywordGroups": [["上线窗口", "发布时间", "发布窗口"]]
                        }
                      ]
                    }
                  ]
                }
                """);
        DocumentQualityService service = new DocumentQualityService(
                new DocumentQualityRagService(),
                DocumentQualityJudgeClient.disabled(),
                ruleConfigService,
                DocumentQualityHistoryService.disabled()
        );

        DocumentQualityResult result = service.evaluate(new DocumentQualityRequest(
                "支付发布方案",
                "发布方案",
                """
                        # 概述
                        本文说明支付模块发布步骤。

                        # 回滚方案
                        发布失败后执行回滚脚本。
                        """
        ));

        assertTrue(result.success());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.id().equals("MISSING_ROLLOUT_WINDOW")));
    }
}
