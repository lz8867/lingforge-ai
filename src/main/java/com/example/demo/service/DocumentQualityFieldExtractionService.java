package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentQualityFieldExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityFieldExtractionService.class);
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6}\\s+|\\d+(\\.\\d+)*[.、]\\s*|第[一二三四五六七八九十]+[章节部分]|[一二三四五六七八九十]+[、.]).{1,80}$");
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile("(?i)\\b(GET|POST|PUT|DELETE|PATCH)\\s+[/A-Za-z0-9_{}?=&.\\-]+");
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret|token|access[_-]?key|api[_-]?key)\\s*[:=]\\s*\\S+|1[3-9]\\d{9}|\\d{17}[0-9Xx]");
    private static final int MAX_VALUES_PER_FIELD = 6;
    private static final int MAX_EVIDENCE_PER_FIELD = 3;

    private static final List<FieldSpec> FIELD_SPECS = List.of(
            new FieldSpec(
                    "module_name",
                    "模块/主题",
                    "项目 概述 模块 主题 目标 范围",
                    "",
                    List.of("项目", "概述", "模块", "主题", "目标", "范围"),
                    true,
                    "补充文档覆盖的业务模块、系统边界或主题。"
            ),
            new FieldSpec(
                    "api_contracts",
                    "接口契约",
                    "API 接口 请求 响应 入参 出参 错误码 POST GET /api endpoint",
                    "api_contract",
                    List.of("api", "接口", "请求", "响应", "返回", "入参", "出参", "错误码", "post", "get", "/api", "endpoint"),
                    true,
                    "补充接口路径、请求字段、响应字段和错误码。"
            ),
            new FieldSpec(
                    "data_models",
                    "数据模型",
                    "数据模型 实体 字段 表结构 对象 持久化 schema model",
                    "data_model",
                    List.of("数据模型", "数据结构", "实体", "字段", "表结构", "对象", "持久化", "schema", "model"),
                    true,
                    "补充核心实体、关键字段、约束和存储关系。"
            ),
            new FieldSpec(
                    "acceptance_criteria",
                    "验收标准",
                    "测试 验收 验收标准 测试用例 成功标准 失败判定 准入 退出标准",
                    "acceptance",
                    List.of("测试", "验收", "验收标准", "测试用例", "成功标准", "失败判定", "准入", "退出标准"),
                    true,
                    "补充可执行的验收标准、测试用例和失败判定。"
            ),
            new FieldSpec(
                    "risk_controls",
                    "风险与兜底",
                    "风险 异常 降级 回滚 熔断 兜底 排查 脱敏 超时 失败",
                    "risk_handling",
                    List.of("风险", "异常", "降级", "回滚", "熔断", "兜底", "排查", "脱敏", "超时", "失败"),
                    true,
                    "补充异常处理、降级回滚、超时失败和敏感信息处理策略。"
            ),
            new FieldSpec(
                    "metrics",
                    "量化指标",
                    "量化指标 指标 分数 得分 覆盖率 重复率 平均句长 权重 通过率",
                    "metrics",
                    List.of("量化指标", "指标", "分数", "得分", "覆盖率", "重复率", "平均句长", "权重", "通过率"),
                    true,
                    "补充评分指标、权重、阈值和统计口径。"
            ),
            new FieldSpec(
                    "dependencies",
                    "依赖/外部系统",
                    "依赖 外部系统 第三方 服务 组件 MCP RAG Ollama OpenAI 数据源",
                    "structure",
                    List.of("依赖", "外部系统", "第三方", "服务", "组件", "mcp", "rag", "ollama", "openai", "数据源"),
                    false,
                    "说明上游数据源、外部服务、模型和组件依赖。"
            ),
            new FieldSpec(
                    "security_privacy",
                    "安全与隐私",
                    "安全 隐私 权限 密钥 密码 token 脱敏 合规 敏感信息",
                    "risk_handling",
                    List.of("安全", "隐私", "权限", "密钥", "密码", "token", "脱敏", "合规", "敏感信息"),
                    false,
                    "说明权限、脱敏、密钥管理和合规处理。"
            ),
            new FieldSpec(
                    "open_questions",
                    "开放问题",
                    "待确认 未明确 TODO TBD 待补充 问题 争议",
                    "",
                    List.of("待确认", "未明确", "todo", "tbd", "待补充", "问题", "争议"),
                    false,
                    "列出未闭环问题、责任人和计划完成时间。"
            )
    );

    public DocumentQualityFieldExtractionResult extract(
            String documentName,
            String documentType,
            String content,
            DocumentQualityRagResult ragResult) {
        String safeDocumentName = safeText(documentName);
        String safeDocumentType = safeText(documentType);
        String safeContent = safeText(content);
        DocumentQualityRagResult safeRagResult = ragResult == null
                ? new DocumentQualityRagResult(List.of(), Map.of(), 0)
                : ragResult;

        log.info("文档质量字段抽取开始: documentName={}, documentType={}, contentLength={}, chunkCount={}",
                safeDocumentName, safeDocumentType, safeContent.length(), safeRagResult.chunkCount());
        try {
            List<DocumentQualityExtractedField> fields = FIELD_SPECS.stream()
                    .map(spec -> extractField(spec, safeDocumentName, safeContent, safeRagResult))
                    .toList();
            long requiredCount = FIELD_SPECS.stream().filter(FieldSpec::required).count();
            long completedRequiredCount = fields.stream()
                    .filter(field -> isRequired(field.id()))
                    .filter(field -> "complete".equals(field.status()))
                    .count();
            double coverage = requiredCount == 0
                    ? 100
                    : Math.round(completedRequiredCount * 1000.0 / requiredCount) / 10.0;
            log.info("文档质量字段抽取完成: documentName={}, completedRequired={}/{}, coverageScore={}",
                    safeDocumentName, completedRequiredCount, requiredCount, coverage);
            return new DocumentQualityFieldExtractionResult(
                    coverage,
                    (int) completedRequiredCount,
                    (int) requiredCount,
                    fields
            );
        } catch (RuntimeException e) {
            log.warn("文档质量字段抽取失败: documentName={}, message={}", safeDocumentName, e.getMessage());
            return new DocumentQualityFieldExtractionResult(0, 0, requiredFieldCount(), List.of());
        }
    }

    private DocumentQualityExtractedField extractField(
            FieldSpec spec,
            String documentName,
            String content,
            DocumentQualityRagResult ragResult) {
        List<DocumentQualityRagHit> hits = collectRagHits(spec, ragResult);
        List<String> values = "module_name".equals(spec.id())
                ? valuesFromContent(spec, documentName, content)
                : valuesFromRagHits(spec, hits);
        if (values.isEmpty()) {
            values = valuesFromContent(spec, documentName, content);
        }

        String status = fieldStatus(spec, values, content);
        double confidence = confidence(status, hits);
        List<DocumentQualityFieldEvidence> evidence = values.isEmpty()
                ? List.of()
                : hits.stream()
                        .filter(hit -> hitContainsAny(hit, spec.keywords()) || "api_contracts".equals(spec.id()))
                        .limit(MAX_EVIDENCE_PER_FIELD)
                        .map(this::toEvidence)
                        .toList();

        return new DocumentQualityExtractedField(
                spec.id(),
                spec.name(),
                status,
                confidence,
                values,
                evidence,
                "complete".equals(status) ? "已抽取到可用于评分的结构化字段。" : spec.missingSuggestion()
        );
    }

    private List<DocumentQualityRagHit> collectRagHits(FieldSpec spec, DocumentQualityRagResult ragResult) {
        Map<String, DocumentQualityRagHit> hitsByChunkId = new LinkedHashMap<>();
        if (!spec.ragRequirementId().isBlank()) {
            Optional.ofNullable(ragResult.evidenceByRequirement().get(spec.ragRequirementId()))
                    .stream()
                    .flatMap(evidence -> evidence.topHits().stream())
                    .forEach(hit -> hitsByChunkId.putIfAbsent(hit.chunkId(), hit));
        }
        ragResult.search(spec.query(), MAX_EVIDENCE_PER_FIELD).forEach(hit -> hitsByChunkId.putIfAbsent(hit.chunkId(), hit));
        return hitsByChunkId.values().stream()
                .sorted(Comparator.comparingDouble(DocumentQualityRagHit::score).reversed())
                .limit(MAX_EVIDENCE_PER_FIELD)
                .toList();
    }

    private List<String> valuesFromRagHits(FieldSpec spec, List<DocumentQualityRagHit> hits) {
        List<String> values = new ArrayList<>();
        for (DocumentQualityRagHit hit : hits) {
            values.addAll(valuesFromText(spec, hit.title() + "\n" + hit.content()));
        }
        return limitDistinct(values);
    }

    private List<String> valuesFromContent(FieldSpec spec, String documentName, String content) {
        if ("module_name".equals(spec.id())) {
            List<String> moduleValues = new ArrayList<>();
            if (!documentName.isBlank()) {
                moduleValues.add(documentName);
            }
            content.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> HEADING_PATTERN.matcher(line).matches())
                    .findFirst()
                    .ifPresent(moduleValues::add);
            return limitDistinct(moduleValues);
        }
        return valuesFromText(spec, content);
    }

    private List<String> valuesFromText(FieldSpec spec, String text) {
        List<String> values = new ArrayList<>();
        for (String rawLine : safeText(text).split("\\R")) {
            String line = cleanLine(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if ("api_contracts".equals(spec.id())) {
                if (hasEndpointOrApiDetail(line)) {
                    values.add(redactSensitive(line));
                }
            } else if ("security_privacy".equals(spec.id()) && hasSensitiveInformation(line)) {
                values.add("检测到疑似敏感信息，具体值已脱敏。");
            } else if (containsAny(line, spec.keywords())) {
                values.add(redactSensitive(line));
            }
        }
        return limitDistinct(values);
    }

    private String fieldStatus(FieldSpec spec, List<String> values, String content) {
        if ("security_privacy".equals(spec.id()) && hasSensitiveInformation(content)) {
            return "risk";
        }
        if (!values.isEmpty()) {
            return "complete";
        }
        return spec.required() ? "missing" : "not_applicable";
    }

    private double confidence(String status, List<DocumentQualityRagHit> hits) {
        if ("missing".equals(status) || "not_applicable".equals(status)) {
            return 0;
        }
        double score = hits.stream()
                .mapToDouble(DocumentQualityRagHit::score)
                .max()
                .orElse(0.55);
        return Math.round(Math.max(0.55, Math.min(0.99, score)) * 100.0) / 100.0;
    }

    private DocumentQualityFieldEvidence toEvidence(DocumentQualityRagHit hit) {
        return new DocumentQualityFieldEvidence(
                hit.chunkId(),
                hit.title(),
                hit.score(),
                "L" + hit.startLine() + "-L" + hit.endLine(),
                redactSensitive(hit.content())
        );
    }

    private boolean hitContainsAny(DocumentQualityRagHit hit, List<String> keywords) {
        return containsAny(hit.title() + "\n" + hit.content(), keywords);
    }

    private boolean hasEndpointOrApiDetail(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return ENDPOINT_PATTERN.matcher(line).find()
                || (containsAny(normalized, List.of("接口", "api", "endpoint", "/api"))
                && containsAny(normalized, List.of("请求", "响应", "返回", "入参", "出参", "错误码")));
    }

    private boolean hasSensitiveInformation(String text) {
        return SENSITIVE_PATTERN.matcher(safeText(text)).find();
    }

    private List<String> limitDistinct(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            String safeValue = safeText(value);
            if (!safeValue.isBlank()) {
                distinct.add(safeValue);
            }
            if (distinct.size() >= MAX_VALUES_PER_FIELD) {
                break;
            }
        }
        return List.copyOf(distinct);
    }

    private String cleanLine(String line) {
        String cleaned = safeText(line)
                .replaceFirst("^(#{1,6}\\s+|[-*]\\s+|\\d+(\\.\\d+)*[.、]\\s*)", "")
                .trim();
        return cleaned.length() <= 180 ? cleaned : cleaned.substring(0, 180) + "...";
    }

    private String redactSensitive(String text) {
        String value = safeText(text);
        Matcher matcher = SENSITIVE_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, "敏感信息***");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean containsAny(String text, List<String> keywords) {
        String normalized = safeText(text).toLowerCase(Locale.ROOT);
        return keywords.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private boolean isRequired(String fieldId) {
        return FIELD_SPECS.stream()
                .anyMatch(spec -> spec.id().equals(fieldId) && spec.required());
    }

    private int requiredFieldCount() {
        return (int) FIELD_SPECS.stream().filter(FieldSpec::required).count();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record FieldSpec(
            String id,
            String name,
            String query,
            String ragRequirementId,
            List<String> keywords,
            boolean required,
            String missingSuggestion
    ) {
    }
}
