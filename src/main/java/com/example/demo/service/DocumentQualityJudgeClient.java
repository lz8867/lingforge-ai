package com.example.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentQualityJudgeClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityJudgeClient.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final OllamaChatClient ollamaChatClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    @Autowired
    public DocumentQualityJudgeClient(OllamaChatClient ollamaChatClient) {
        this(ollamaChatClient, new ObjectMapper(), true);
    }

    DocumentQualityJudgeClient(OllamaChatClient ollamaChatClient, ObjectMapper objectMapper, boolean enabled) {
        this.ollamaChatClient = ollamaChatClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public static DocumentQualityJudgeClient disabled() {
        return new DocumentQualityJudgeClient(null, new ObjectMapper(), false);
    }

    public DocumentQualityJudgeResult judge(DocumentQualityJudgeRequest request) {
        if (!enabled || ollamaChatClient == null) {
            return DocumentQualityJudgeResult.fallback("LLM Judge 未启用，使用本地启发式评分。");
        }

        try {
            String prompt = buildJudgePrompt(request);
            String response = ollamaChatClient.generate(prompt);
            DocumentQualityJudgeResult result = parseJudgeResponse(response, request.heuristicDimensions());
            log.info("文档质量 LLM Judge 完成: source={}, dimensionCount={}",
                    result.source(), result.dimensionScores().size());
            return result;
        } catch (Exception e) {
            log.warn("文档质量 LLM Judge 失败，回落本地启发式评分: {}", e.getMessage());
            return DocumentQualityJudgeResult.fallback("LLM Judge 失败：" + e.getMessage());
        }
    }

    private String buildJudgePrompt(DocumentQualityJudgeRequest request) throws JsonProcessingException {
        String evidenceJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ragEvidenceRows(request.ragEvidence()));
        String issuesJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request.issues());
        String dimensionsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request.heuristicDimensions());
        String metricsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request.metrics());
        String extractedFieldsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                request.extractedFields() == null ? List.of() : request.extractedFields()
        );
        return """
                你是企业文档质量 LLM-Judge。请只基于文档正文、规则问题、量化指标、RAG 检索证据和结构化字段抽取结果，对六个维度重新评分。

                评分原则:
                1. 不要编造正文中没有的内容。
                2. 每个维度 0-100 分，低分必须引用 RAG 证据、结构化字段缺口或规则问题。
                3. 如果 RAG 证据不足，要明确说明证据缺口。
                4. 结构化字段的 status=missing 或 risk 时，应影响完整性、准确性或实用性判断。
                5. 输出必须是严格 JSON，不要 Markdown 代码块。

                文档名称: %s
                文档类型: %s

                文档正文:
                <document>
                %s
                </document>

                规则问题:
                <issues_json>
                %s
                </issues_json>

                量化指标:
                <metrics_json>
                %s
                </metrics_json>

                本地启发式维度评分:
                <dimensions_json>
                %s
                </dimensions_json>

                RAG 检索证据:
                <rag_evidence_json>
                %s
                </rag_evidence_json>

                结构化字段抽取:
                <field_extraction_json>
                %s
                </field_extraction_json>

                请只返回 JSON:
                {
                  "summary": "一句话总结评分依据",
                  "dimensionScores": [
                    {
                      "id": "accuracy|completeness|clarity|logic|consistency|practicality",
                      "score": 0,
                      "evidence": "引用 RAG 证据或规则问题说明评分依据",
                      "suggestions": ["可执行整改建议"]
                    }
                  ]
                }
                """.formatted(
                safeText(request.documentName()),
                safeText(request.documentType()),
                truncate(safeText(request.content()), 8000),
                issuesJson,
                metricsJson,
                dimensionsJson,
                evidenceJson,
                extractedFieldsJson
        );
    }

    private DocumentQualityJudgeResult parseJudgeResponse(String response, List<DocumentQualityDimensionScore> heuristicDimensions) throws JsonProcessingException {
        Map<String, DocumentQualityDimensionScore> baseDimensions = new LinkedHashMap<>();
        for (DocumentQualityDimensionScore dimension : heuristicDimensions) {
            baseDimensions.put(dimension.id(), dimension);
        }

        Map<String, Object> root = objectMapper.readValue(extractJsonCandidate(response), MAP_TYPE);
        String summary = safeText(root.get("summary"));
        List<DocumentQualityDimensionScore> dimensions = new ArrayList<>();
        Object rawDimensions = root.get("dimensionScores");
        if (rawDimensions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> entry) {
                    String id = safeText(entry.get("id"));
                    DocumentQualityDimensionScore base = baseDimensions.get(id);
                    if (base == null) {
                        continue;
                    }
                    int score = clamp(asInt(entry.get("score"), base.score()), 0, 100);
                    List<String> suggestions = asStringList(entry.get("suggestions"));
                    dimensions.add(new DocumentQualityDimensionScore(
                            id,
                            base.name(),
                            score,
                            base.weight(),
                            dimensionLevel(score),
                            safeText(entry.get("evidence")),
                            suggestions.isEmpty() ? base.suggestions() : suggestions
                    ));
                }
            }
        }
        if (dimensions.isEmpty()) {
            throw new JsonProcessingException("LLM Judge 未返回可用 dimensionScores") {
            };
        }
        return new DocumentQualityJudgeResult(true, "ollama", summary, dimensions, "LLM Judge 成功");
    }

    private List<Map<String, Object>> ragEvidenceRows(Map<String, DocumentQualityRagEvidence> evidenceByRequirement) {
        if (evidenceByRequirement == null || evidenceByRequirement.isEmpty()) {
            return List.of();
        }
        return evidenceByRequirement.values().stream()
                .map(evidence -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("requirementId", evidence.requirementId());
                    row.put("label", evidence.label());
                    row.put("matched", evidence.matched());
                    row.put("topScore", evidence.topScore());
                    row.put("topHits", evidence.topHits().stream()
                            .map(hit -> Map.of(
                                    "chunkId", hit.chunkId(),
                                    "title", hit.title(),
                                    "content", hit.content(),
                                    "score", hit.score(),
                                    "location", "L" + hit.startLine() + "-L" + hit.endLine()
                            ))
                            .toList());
                    return row;
                })
                .toList();
    }

    private static String extractJsonCandidate(String rawResponse) {
        String text = safeText(rawResponse);
        int fencedStart = text.indexOf("```");
        if (fencedStart >= 0) {
            int jsonStart = text.indexOf('{', fencedStart);
            int fencedEnd = text.lastIndexOf("```");
            if (jsonStart >= 0 && fencedEnd > jsonStart) {
                return text.substring(jsonStart, fencedEnd).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(DocumentQualityJudgeClient::safeText)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String dimensionLevel(int score) {
        if (score >= 90) {
            return "good";
        }
        if (score >= 75) {
            return "warning";
        }
        return "risk";
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n...（已截断）";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
