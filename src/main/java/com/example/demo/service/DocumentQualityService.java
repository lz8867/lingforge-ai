package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.Collectors;

@Service
public class DocumentQualityService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityService.class);
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6}\\s+|\\d+(\\.\\d+)*[.、]\\s*|第[一二三四五六七八九十]+[章节部分]|[一二三四五六七八九十]+[、.]).{1,80}$");
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret|token|access[_-]?key|api[_-]?key)\\s*[:=]\\s*\\S+|1[3-9]\\d{9}|\\d{17}[0-9Xx]");
    private static final Pattern TODO_PATTERN = Pattern.compile("(?i)(todo|tbd|待补充|待完善|略|后续补充)");
    private static final Map<String, Integer> SCORE_WEIGHTS = orderedMap(
            "ruleEngine", 40,
            "llmJudge", 50,
            "quantitativeMetrics", 10
    );
    private static final List<DimensionSpec> DIMENSIONS = List.of(
            new DimensionSpec("accuracy", "准确性", 30),
            new DimensionSpec("completeness", "完整性", 20),
            new DimensionSpec("clarity", "清晰性", 15),
            new DimensionSpec("logic", "逻辑性", 15),
            new DimensionSpec("consistency", "一致性", 10),
            new DimensionSpec("practicality", "实用性", 10)
    );
    private final DocumentQualityRagService ragService;
    private final DocumentQualityJudgeClient judgeClient;
    private final DocumentQualityRuleConfigService ruleConfigService;
    private final DocumentQualityHistoryService historyService;
    private final DocumentQualityFieldExtractionService fieldExtractionService;

    public DocumentQualityService() {
        this(
                new DocumentQualityRagService(),
                DocumentQualityJudgeClient.disabled(),
                new DocumentQualityRuleConfigService(),
                DocumentQualityHistoryService.disabled(),
                new DocumentQualityFieldExtractionService()
        );
    }

    public DocumentQualityService(DocumentQualityRagService ragService, DocumentQualityJudgeClient judgeClient) {
        this(ragService, judgeClient, new DocumentQualityRuleConfigService(), DocumentQualityHistoryService.disabled());
    }

    public DocumentQualityService(
            DocumentQualityRagService ragService,
            DocumentQualityJudgeClient judgeClient,
            DocumentQualityRuleConfigService ruleConfigService,
            DocumentQualityHistoryService historyService) {
        this(ragService, judgeClient, ruleConfigService, historyService, new DocumentQualityFieldExtractionService());
    }

    @Autowired
    public DocumentQualityService(
            DocumentQualityRagService ragService,
            DocumentQualityJudgeClient judgeClient,
            DocumentQualityRuleConfigService ruleConfigService,
            DocumentQualityHistoryService historyService,
            DocumentQualityFieldExtractionService fieldExtractionService) {
        this.ragService = ragService;
        this.judgeClient = judgeClient;
        this.ruleConfigService = ruleConfigService;
        this.historyService = historyService;
        this.fieldExtractionService = fieldExtractionService;
    }

    public DocumentQualityResult evaluate(DocumentQualityRequest request) {
        DocumentQualityRequest safeRequest = request == null
                ? new DocumentQualityRequest("", "", "")
                : request;
        String documentName = defaultText(safeRequest.documentName(), "未命名文档");
        String documentType = defaultText(safeRequest.documentType(), "通用文档");
        String content = normalizeContent(safeRequest.content());

        log.info("文档质量评测开始: documentName={}, documentType={}, contentLength={}",
                documentName, documentType, content.length());

        if (content.isBlank()) {
            log.warn("文档质量评测失败: 输入内容为空, documentName={}", documentName);
            return emptyContentResult(documentName, documentType);
        }

        try {
            DocumentProfile profile = buildProfile(content);
            DocumentQualityRagResult ragResult = ragService.analyze(documentType, content);
            DocumentQualityFieldExtractionResult fieldExtraction = fieldExtractionService.extract(
                    documentName,
                    documentType,
                    content,
                    ragResult
            );
            List<DocumentQualityIssue> issues = detectIssues(documentType, content, profile, ragResult, fieldExtraction);
            List<DocumentQualityMetric> metrics = buildMetrics(profile, issues, ragResult, fieldExtraction);
            int ruleScore = ruleScore(issues);
            int metricScore = metricScore(profile, issues);
            List<DocumentQualityDimensionScore> heuristicDimensions = buildDimensionScores(profile, issues, ragResult);
            DocumentQualityJudgeResult judgeResult = judgeClient.judge(new DocumentQualityJudgeRequest(
                    documentName,
                    documentType,
                    content,
                    issues,
                    metrics,
                    heuristicDimensions,
                    ragResult.evidenceByRequirement(),
                    fieldExtraction.visualizationRows()
            ));
            List<DocumentQualityDimensionScore> dimensions = mergeJudgeScores(heuristicDimensions, judgeResult);
            int llmJudgeScore = weightedDimensionScore(dimensions);
            int overallScore = overallScore(ruleScore, llmJudgeScore, metricScore, issues);
            List<String> recommendations = buildRecommendations(issues, dimensions);
            Map<String, Object> visualizationData = buildVisualizationData(
                    ruleScore,
                    llmJudgeScore,
                    metricScore,
                    dimensions,
                    issues,
                    ragResult,
                    judgeResult,
                    fieldExtraction,
                    profile
            );

            DocumentQualityResult result = new DocumentQualityResult(
                    true,
                    "已完成文档质量评测。",
                    documentName,
                    documentType,
                    overallScore,
                    gradeOf(overallScore),
                    ruleScore,
                    llmJudgeScore,
                    metricScore,
                    dimensions,
                    issues,
                    metrics,
                    recommendations,
                    visualizationData
            );
            historyService.save(result);
            log.info("文档质量评测完成: documentName={}, overallScore={}, issueCount={}",
                    documentName, overallScore, issues.size());
            return result;
        } catch (RuntimeException e) {
            log.error("文档质量评测异常: documentName={}, message={}", documentName, e.getMessage());
            return failureResult(documentName, documentType, e.getMessage());
        }
    }

    private DocumentProfile buildProfile(String content) {
        List<String> lines = content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        List<String> paragraphs = splitParagraphs(content);
        List<String> headings = lines.stream()
                .filter(this::isHeading)
                .toList();
        int charCount = nonWhitespaceLength(content);
        int sentenceCount = Math.max(1, splitSentences(content).size());
        double averageSentenceLength = round1((double) charCount / sentenceCount);
        DocumentQualityRuleConfigService.DocumentQualityThresholds thresholds = ruleConfigService.thresholds();
        int longParagraphCount = (int) paragraphs.stream()
                .filter(paragraph -> nonWhitespaceLength(paragraph) > thresholds.longParagraphChars())
                .count();
        double duplicateRate = duplicateRate(paragraphs);

        return new DocumentProfile(
                content,
                content.toLowerCase(Locale.ROOT),
                lines,
                paragraphs,
                headings,
                charCount,
                sentenceCount,
                averageSentenceLength,
                longParagraphCount,
                duplicateRate
        );
    }

    private List<DocumentQualityIssue> detectIssues(
            String documentType,
            String content,
            DocumentProfile profile,
            DocumentQualityRagResult ragResult,
            DocumentQualityFieldExtractionResult fieldExtraction) {
        List<DocumentQualityIssue> issues = new ArrayList<>();
        addRequiredSectionIssues(issues, documentType, profile, ragResult, fieldExtraction);
        addSensitiveInformationIssues(issues, profile.normalizedContent());
        addStructureIssues(issues, profile);
        addReadabilityIssues(issues, profile);
        addConsistencyIssues(issues, profile);
        addPlaceholderIssues(issues, content);

        issues.sort(Comparator
                .comparingInt((DocumentQualityIssue issue) -> severityOrder(issue.severity()))
                .thenComparing(DocumentQualityIssue::id));
        return List.copyOf(issues);
    }

    private void addRequiredSectionIssues(
            List<DocumentQualityIssue> issues,
            String documentType,
            DocumentProfile profile,
            DocumentQualityRagResult ragResult,
            DocumentQualityFieldExtractionResult fieldExtraction) {
        for (DocumentQualityRuleConfigService.RequiredEvidenceRule rule
                : ruleConfigService.requiredEvidenceRulesFor(documentType)) {
            if (!matchesRequiredEvidenceRule(rule, profile.normalizedContent(), ragResult, fieldExtraction)) {
                issues.add(issue(
                        rule.issueId(),
                        rule.severity(),
                        rule.category(),
                        "全文",
                        rule.description(),
                        rule.suggestion(),
                        configuredEvidenceMessage(rule, ragResult)
                ));
            }
        }
    }

    private void addSensitiveInformationIssues(List<DocumentQualityIssue> issues, String normalizedContent) {
        Matcher matcher = SENSITIVE_PATTERN.matcher(normalizedContent);
        if (matcher.find()) {
            issues.add(issue(
                    "SENSITIVE_INFORMATION",
                    "P0",
                    "合规安全",
                    "全文",
                    "文档疑似包含密码、密钥、手机号或身份证等敏感信息。",
                    "立即移除或脱敏敏感信息，并改用密钥管理、权限说明或占位变量。",
                    "检测到疑似敏感字段，证据已脱敏。"
            ));
        }
    }

    private void addStructureIssues(List<DocumentQualityIssue> issues, DocumentProfile profile) {
        int minHeadingCount = ruleConfigService.thresholds().minHeadingCount();
        if (profile.headings().size() < minHeadingCount) {
            issues.add(issue(
                    "WEAK_HEADING_STRUCTURE",
                    "P2",
                    "逻辑结构",
                    "全文",
                    "标题层级不足，读者难以快速定位信息。",
                    "使用一级/二级标题组织背景、方案、规则、指标、风险和验收内容。",
                    "检测到标题数量少于 " + minHeadingCount + "。"
            ));
        }
    }

    private void addReadabilityIssues(List<DocumentQualityIssue> issues, DocumentProfile profile) {
        DocumentQualityRuleConfigService.DocumentQualityThresholds thresholds = ruleConfigService.thresholds();
        for (int i = 0; i < profile.paragraphs().size(); i++) {
            String paragraph = profile.paragraphs().get(i);
            if (nonWhitespaceLength(paragraph) > thresholds.longParagraphChars()) {
                issues.add(issue(
                        "LONG_PARAGRAPH",
                        "P2",
                        "可读性",
                        "第 " + (i + 1) + " 段",
                        "段落过长，影响阅读和问题定位。",
                        "拆分为短段落或列表，并保留每段单一主题。",
                        snippet(paragraph)
                ));
            }
        }

        if (profile.averageSentenceLength() > thresholds.averageSentenceLengthRisk()) {
            issues.add(issue(
                    "LONG_SENTENCE_AVERAGE",
                    "P2",
                    "可读性",
                    "全文",
                    "平均句长偏高，可能存在长句堆叠或表达不够清晰。",
                    "将长句拆成短句，显式标出条件、动作和结果。",
                    "平均句长 " + profile.averageSentenceLength() + " 字。"
            ));
        }
    }

    private void addConsistencyIssues(List<DocumentQualityIssue> issues, DocumentProfile profile) {
        if (profile.duplicateRate() >= ruleConfigService.thresholds().duplicateRate()) {
            issues.add(issue(
                    "DUPLICATE_CONTENT",
                    "P2",
                    "一致性",
                    "全文",
                    "检测到重复段落比例偏高，可能存在复制粘贴或冗余内容。",
                    "合并重复表达，保留唯一事实和结论。",
                    "重复率 " + round1(profile.duplicateRate() * 100) + "%。"
            ));
        }
    }

    private void addPlaceholderIssues(List<DocumentQualityIssue> issues, String content) {
        Matcher matcher = TODO_PATTERN.matcher(content);
        if (matcher.find()) {
            issues.add(issue(
                    "PLACEHOLDER_CONTENT",
                    "P2",
                    "实用性",
                    "全文",
                    "文档存在待补充或占位表达，说明内容尚未闭环。",
                    "将占位内容替换为明确结论、责任人、时间点或验收口径。",
                    "检测到占位表达：" + matcher.group()
            ));
        }
    }

    private List<DocumentQualityMetric> buildMetrics(
            DocumentProfile profile,
            List<DocumentQualityIssue> issues,
            DocumentQualityRagResult ragResult,
            DocumentQualityFieldExtractionResult fieldExtraction) {
        return List.of(
                metric("char_count", "正文字符数", profile.charCount(), "字", "info", "去除空白后的正文规模。"),
                metric("paragraph_count", "段落数", profile.paragraphs().size(), "段", "info", "按空行和换行归并后的段落数量。"),
                metric("heading_count", "标题数", profile.headings().size(), "个", profile.headings().size() >= ruleConfigService.thresholds().minHeadingCount() ? "good" : "warning", "可用于判断结构化程度。"),
                metric("average_sentence_length", "平均句长", profile.averageSentenceLength(), "字/句", profile.averageSentenceLength() <= ruleConfigService.thresholds().averageSentenceLengthWarning() ? "good" : "warning", "句长越高，可读性风险越大。"),
                metric("long_paragraph_count", "长段落数", profile.longParagraphCount(), "段", profile.longParagraphCount() == 0 ? "good" : "warning", "超过 " + ruleConfigService.thresholds().longParagraphChars() + " 字的段落数量。"),
                metric("duplicate_rate", "重复率", round1(profile.duplicateRate() * 100), "%", profile.duplicateRate() < ruleConfigService.thresholds().duplicateRate() ? "good" : "warning", "近似重复段落占比。"),
                metric("rag_chunk_count", "RAG切块数", ragResult.chunkCount(), "个", ragResult.chunkCount() >= 3 ? "good" : "warning", "用于向量检索的文档片段数量。"),
                metric("rag_context_coverage", "RAG证据覆盖率", ragResult.coverageScore(), "%", ragResult.coverageScore() >= 70 ? "good" : "warning", "关键评测需求通过向量检索命中正文证据的比例。"),
                metric("field_extraction_coverage", "字段抽取覆盖率", fieldExtraction.coverageScore(), "%", fieldExtraction.coverageScore() >= 70 ? "good" : "warning", "RAG 后结构化字段抽取覆盖核心质量字段的比例。"),
                metric("issue_count", "问题数", issues.size(), "个", issues.isEmpty() ? "good" : "warning", "规则引擎和 Judge rubric 归集的问题数量。"),
                metric("compliance_pass_rate", "合规通过率", compliancePassRate(issues), "%", hasP0(issues) ? "risk" : "good", "未命中阻断级合规问题时通过率为高。")
        );
    }

    private int ruleScore(List<DocumentQualityIssue> issues) {
        int deduction = issues.stream()
                .mapToInt(DocumentQualityIssue::deduction)
                .sum();
        return clamp(100 - deduction, 0, 100);
    }

    private int metricScore(DocumentProfile profile, List<DocumentQualityIssue> issues) {
        int score = 100;
        DocumentQualityRuleConfigService.DocumentQualityThresholds thresholds = ruleConfigService.thresholds();
        if (profile.headings().size() < thresholds.minHeadingCount()) {
            score -= 12;
        }
        score -= Math.min(20, profile.longParagraphCount() * 5);
        if (profile.averageSentenceLength() > thresholds.averageSentenceLengthRisk()) {
            score -= 10;
        } else if (profile.averageSentenceLength() > thresholds.averageSentenceLengthWarning()) {
            score -= 5;
        }
        if (profile.duplicateRate() >= thresholds.duplicateRate()) {
            score -= 12;
        }
        score -= Math.min(20, issues.size() * 2);
        return clamp(score, 0, 100);
    }

    private List<DocumentQualityDimensionScore> buildDimensionScores(
            DocumentProfile profile,
            List<DocumentQualityIssue> issues,
            DocumentQualityRagResult ragResult) {
        return DIMENSIONS.stream()
                .map(spec -> dimensionScore(spec, profile, issues, ragResult))
                .toList();
    }

    private DocumentQualityDimensionScore dimensionScore(
            DimensionSpec spec,
            DocumentProfile profile,
            List<DocumentQualityIssue> issues,
            DocumentQualityRagResult ragResult) {
        int score = 92;
        List<DocumentQualityIssue> relatedIssues = issues.stream()
                .filter(issue -> affectsDimension(spec.id(), issue))
                .toList();
        for (DocumentQualityIssue issue : relatedIssues) {
            score -= dimensionDeduction(issue);
        }
        if ("clarity".equals(spec.id()) && profile.averageSentenceLength() > 70) {
            score -= 5;
        }
        if ("logic".equals(spec.id()) && profile.headings().size() >= 4) {
            score += 4;
        }
        int evidenceBonus = ragDimensionEvidenceBonus(spec.id(), ragResult);
        score += evidenceBonus;
        if (requiresRagEvidence(spec.id()) && evidenceBonus == 0) {
            score -= 4;
        }
        if (issues.isEmpty()) {
            score += 4;
        }
        score = clamp(score, hasP0(relatedIssues) ? 35 : 55, 100);

        List<String> suggestions = relatedIssues.stream()
                .map(DocumentQualityIssue::suggestion)
                .distinct()
                .limit(3)
                .toList();
        if (suggestions.isEmpty()) {
            suggestions = List.of("保持当前结构，并在后续版本继续补充可验证证据。");
        }

        String issueEvidence = relatedIssues.isEmpty()
                ? "未发现明显扣分项。"
                : relatedIssues.stream()
                        .map(DocumentQualityIssue::description)
                        .limit(2)
                        .collect(Collectors.joining("；"));
        String evidence = issueEvidence + " " + ragEvidenceSummary(spec.id(), ragResult);

        return new DocumentQualityDimensionScore(
                spec.id(),
                spec.name(),
                score,
                spec.weight(),
                dimensionLevel(score),
                evidence,
                suggestions
        );
    }

    private int weightedDimensionScore(List<DocumentQualityDimensionScore> dimensions) {
        int totalWeight = dimensions.stream()
                .mapToInt(DocumentQualityDimensionScore::weight)
                .sum();
        double weighted = dimensions.stream()
                .mapToDouble(dimension -> dimension.score() * dimension.weight())
                .sum();
        return (int) Math.round(weighted / Math.max(1, totalWeight));
    }

    private List<DocumentQualityDimensionScore> mergeJudgeScores(
            List<DocumentQualityDimensionScore> heuristicDimensions,
            DocumentQualityJudgeResult judgeResult) {
        if (judgeResult == null || !judgeResult.success() || judgeResult.dimensionScores().isEmpty()) {
            return heuristicDimensions;
        }
        Map<String, DocumentQualityDimensionScore> judgedById = judgeResult.dimensionScores().stream()
                .collect(Collectors.toMap(
                        DocumentQualityDimensionScore::id,
                        dimension -> dimension,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return heuristicDimensions.stream()
                .map(base -> mergeJudgeScore(base, judgedById.get(base.id())))
                .toList();
    }

    private DocumentQualityDimensionScore mergeJudgeScore(
            DocumentQualityDimensionScore base,
            DocumentQualityDimensionScore judged) {
        if (judged == null) {
            return base;
        }
        int score = clamp(judged.score(), 0, 100);
        List<String> suggestions = judged.suggestions() == null || judged.suggestions().isEmpty()
                ? base.suggestions()
                : judged.suggestions();
        String evidence = defaultText(judged.evidence(), base.evidence());
        return new DocumentQualityDimensionScore(
                base.id(),
                base.name(),
                score,
                base.weight(),
                dimensionLevel(score),
                evidence,
                suggestions
        );
    }

    private int overallScore(int ruleScore, int llmJudgeScore, int metricScore, List<DocumentQualityIssue> issues) {
        int score = (int) Math.round(ruleScore * 0.4 + llmJudgeScore * 0.5 + metricScore * 0.1);
        if (hasP0(issues)) {
            return Math.min(score, 59);
        }
        long p1Count = issues.stream()
                .filter(issue -> "P1".equals(issue.severity()))
                .count();
        if (p1Count >= 3) {
            return Math.min(score, 79);
        }
        return clamp(score, 0, 100);
    }

    private List<String> buildRecommendations(List<DocumentQualityIssue> issues, List<DocumentQualityDimensionScore> dimensions) {
        LinkedHashSet<String> recommendations = new LinkedHashSet<>();
        issues.stream()
                .sorted(Comparator.comparingInt(issue -> severityOrder(issue.severity())))
                .map(DocumentQualityIssue::suggestion)
                .forEach(recommendations::add);
        dimensions.stream()
                .filter(dimension -> dimension.score() < 80)
                .flatMap(dimension -> dimension.suggestions().stream())
                .forEach(recommendations::add);
        if (recommendations.isEmpty()) {
            recommendations.add("当前文档结构和质量较好，建议保留证据链、接口契约和验收标准，后续用历史评测做趋势对比。");
        }
        return recommendations.stream()
                .limit(8)
                .toList();
    }

    private Map<String, Object> buildVisualizationData(
            int ruleScore,
            int llmJudgeScore,
            int metricScore,
            List<DocumentQualityDimensionScore> dimensions,
            List<DocumentQualityIssue> issues,
            DocumentQualityRagResult ragResult,
            DocumentQualityJudgeResult judgeResult,
            DocumentQualityFieldExtractionResult fieldExtraction,
            DocumentProfile profile
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scoreWeights", SCORE_WEIGHTS);
        data.put("scoreBreakdown", orderedMap(
                "ruleEngine", ruleScore,
                "llmJudge", llmJudgeScore,
                "quantitativeMetrics", metricScore
        ));
        data.put("radar", dimensions.stream()
                .map(dimension -> orderedMap(
                        "id", dimension.id(),
                        "name", dimension.name(),
                        "score", dimension.score(),
                        "weight", dimension.weight()
                ))
                .toList());
        data.put("issueTypeDistribution", distribution(issues, DocumentQualityIssue::category));
        data.put("severityDistribution", distribution(issues, DocumentQualityIssue::severity));
        data.put("ragChunkCount", ragResult.chunkCount());
        data.put("ragCoverageScore", ragResult.coverageScore());
        data.put("ragRetrieval", ragResult.visualizationRows());
        data.put("fieldExtractionCoverage", fieldExtraction.coverageScore());
        data.put("extractedFields", fieldExtraction.visualizationRows());
        data.put("skillPipeline", buildSkillPipeline(profile, ragResult, fieldExtraction, issues, judgeResult));
        data.put("llmJudgeSource", judgeResult == null ? "heuristic" : judgeResult.source());
        data.put("llmJudgeSummary", judgeResult == null ? "" : judgeResult.summary());
        data.put("llmJudgeMessage", judgeResult == null ? "" : judgeResult.message());
        return data;
    }

    private List<Map<String, Object>> buildSkillPipeline(
            DocumentProfile profile,
            DocumentQualityRagResult ragResult,
            DocumentQualityFieldExtractionResult fieldExtraction,
            List<DocumentQualityIssue> issues,
            DocumentQualityJudgeResult judgeResult) {
        return List.of(
                orderedMap(
                        "stage", "文本解析",
                        "status", "completed",
                        "summary", "已获得可评测正文，字符数 " + profile.charCount()
                ),
                orderedMap(
                        "stage", "RAG 检索",
                        "status", ragResult.coverageScore() >= 70 ? "completed" : "attention",
                        "summary", "切块 " + ragResult.chunkCount() + " 个，证据覆盖率 " + ragResult.coverageScore() + "%"
                ),
                orderedMap(
                        "stage", "字段抽取 Skill",
                        "status", fieldExtraction.coverageScore() >= 70 ? "completed" : "attention",
                        "summary", "核心字段完成 " + fieldExtraction.completedFieldCount() + "/" + fieldExtraction.totalFieldCount()
                ),
                orderedMap(
                        "stage", "规则引擎",
                        "status", issues.stream().anyMatch(issue -> "P0".equals(issue.severity())) ? "blocked" : "completed",
                        "summary", "生成问题 " + issues.size() + " 个"
                ),
                orderedMap(
                        "stage", "Prompt + RAG Judge",
                        "status", judgeResult != null && judgeResult.success() ? "completed" : "degraded",
                        "summary", judgeResult == null ? "使用本地启发式评分" : defaultText(judgeResult.message(), judgeResult.source())
                ),
                orderedMap(
                        "stage", "报告输出",
                        "status", "completed",
                        "summary", "已汇总分数、问题、字段、证据和整改建议"
                )
        );
    }

    private DocumentQualityResult emptyContentResult(String documentName, String documentType) {
        DocumentQualityIssue issue = new DocumentQualityIssue(
                "EMPTY_CONTENT",
                "P1",
                "完整性",
                "全文",
                "文档正文为空，无法进行质量评测。",
                30,
                "粘贴或上传需要评测的文档正文后重新评测。",
                "未收到正文内容。"
        );
        return new DocumentQualityResult(
                false,
                "文档正文不能为空。",
                documentName,
                documentType,
                0,
                "无法评测",
                0,
                0,
                0,
                List.of(),
                List.of(issue),
                List.of(),
                List.of(issue.suggestion()),
                Map.of()
        );
    }

    private DocumentQualityResult failureResult(String documentName, String documentType, String message) {
        return new DocumentQualityResult(
                false,
                "文档质量评测失败：" + defaultText(message, "未知错误"),
                documentName,
                documentType,
                0,
                "无法评测",
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of("请检查文档内容后重试。"),
                Map.of()
        );
    }

    private boolean affectsDimension(String dimensionId, DocumentQualityIssue issue) {
        if ("P0".equals(issue.severity())) {
            return "accuracy".equals(dimensionId) || "consistency".equals(dimensionId) || "practicality".equals(dimensionId);
        }
        return switch (dimensionId) {
            case "accuracy" -> "合规安全".equals(issue.category());
            case "completeness" -> issue.id().startsWith("MISSING_") || "完整性".equals(issue.category());
            case "clarity" -> "可读性".equals(issue.category());
            case "logic" -> "逻辑结构".equals(issue.category()) || issue.id().contains("ACCEPTANCE");
            case "consistency" -> "一致性".equals(issue.category());
            case "practicality" -> "实用性".equals(issue.category())
                    || issue.id().contains("API")
                    || issue.id().contains("ACCEPTANCE")
                    || issue.id().contains("DATA_MODEL");
            default -> false;
        };
    }

    private int dimensionDeduction(DocumentQualityIssue issue) {
        return switch (issue.severity()) {
            case "P0" -> 32;
            case "P1" -> 13;
            case "P2" -> 7;
            default -> 3;
        };
    }

    private DocumentQualityIssue issue(
            String id,
            String severity,
            String category,
            String location,
            String description,
            String suggestion,
            String evidence
    ) {
        return new DocumentQualityIssue(
                id,
                severity,
                category,
                location,
                description,
                severityDeduction(severity),
                suggestion,
                evidence
        );
    }

    private DocumentQualityMetric metric(String id, String name, double value, String unit, String level, String description) {
        return new DocumentQualityMetric(id, name, value, unit, level, description);
    }

    private boolean matchesRequiredEvidenceRule(
            DocumentQualityRuleConfigService.RequiredEvidenceRule rule,
            String normalizedContent,
            DocumentQualityRagResult ragResult,
            DocumentQualityFieldExtractionResult fieldExtraction) {
        return switch (rule.matcher()) {
            case "api_contract" -> hasExtractedFieldEvidence(fieldExtraction, "api_contracts")
                    || hasApiContractEvidence(normalizedContent, ragResult);
            case "risk_handling" -> hasExtractedFieldEvidence(fieldExtraction, "risk_controls")
                    || hasRiskHandlingEvidence(normalizedContent, ragResult);
            case "all_keyword_groups" -> allKeywordGroupsMatch(normalizedContent, rule.keywordGroups());
            default -> keywordGroupsMatchAny(normalizedContent, rule.keywordGroups())
                    || hasExtractedFieldEvidence(fieldExtraction, fieldIdForRequirementId(rule.ragRequirementId()))
                    || (!rule.ragRequirementId().isBlank()
                    && ragHitContains(ragResult, rule.ragRequirementId(), flattenKeywords(rule.keywordGroups())));
        };
    }

    private String configuredEvidenceMessage(
            DocumentQualityRuleConfigService.RequiredEvidenceRule rule,
            DocumentQualityRagResult ragResult) {
        String fallback = defaultText(rule.evidenceMessage(), "配置化规则未检索到对应正文证据。");
        if (rule.ragRequirementId().isBlank()) {
            return fallback;
        }
        return missingRagEvidence(rule.ragRequirementId(), ragResult, fallback);
    }

    private boolean keywordGroupsMatchAny(String normalizedContent, List<List<String>> keywordGroups) {
        return keywordGroups.stream()
                .anyMatch(group -> containsAny(normalizedContent, group));
    }

    private boolean allKeywordGroupsMatch(String normalizedContent, List<List<String>> keywordGroups) {
        return !keywordGroups.isEmpty() && keywordGroups.stream()
                .allMatch(group -> containsAny(normalizedContent, group));
    }

    private List<String> flattenKeywords(List<List<String>> keywordGroups) {
        return keywordGroups.stream()
                .flatMap(List::stream)
                .toList();
    }

    private boolean hasExtractedFieldEvidence(DocumentQualityFieldExtractionResult fieldExtraction, String fieldId) {
        return fieldExtraction != null
                && fieldId != null
                && !fieldId.isBlank()
                && fieldExtraction.isComplete(fieldId);
    }

    private String fieldIdForRequirementId(String requirementId) {
        return switch (defaultText(requirementId, "")) {
            case "data_model" -> "data_models";
            case "api_contract" -> "api_contracts";
            case "acceptance" -> "acceptance_criteria";
            case "risk_handling" -> "risk_controls";
            case "metrics" -> "metrics";
            default -> "";
        };
    }

    private boolean hasDataModelEvidence(String normalizedContent, DocumentQualityRagResult ragResult) {
        return containsAny(normalizedContent, List.of("数据模型", "数据结构", "实体", "字段", "表结构", "er图", "documentqualitytask"))
                || ragHitContains(ragResult, "data_model", List.of("数据模型", "数据结构", "实体", "字段", "表结构", "documentqualitytask"));
    }

    private boolean hasApiContractEvidence(String normalizedContent, DocumentQualityRagResult ragResult) {
        return hasApiContract(normalizedContent)
                || (ragHitContains(ragResult, "api_contract", List.of("接口", "api", "/api", "post ", "get "))
                && ragHitContains(ragResult, "api_contract", List.of("请求", "响应", "返回", "入参", "出参", "错误码")));
    }

    private boolean hasRiskHandlingEvidence(String normalizedContent, DocumentQualityRagResult ragResult) {
        return hasRiskHandling(normalizedContent)
                || (ragHitContains(ragResult, "risk_handling", List.of("风险", "异常", "回滚", "降级", "熔断", "兜底", "脱敏"))
                && ragHitContains(ragResult, "risk_handling", List.of("处理", "方案", "策略", "解决", "排查", "返回")));
    }

    private boolean hasAcceptanceEvidence(String normalizedContent, DocumentQualityRagResult ragResult) {
        return containsAny(normalizedContent, List.of("验收", "测试", "测试用例", "成功标准", "验收标准", "准入", "退出标准"))
                || ragHitContains(ragResult, "acceptance", List.of("验收", "测试", "测试用例", "成功标准", "验收标准", "准入", "退出标准"));
    }

    private boolean hasRequirementEvidence(DocumentQualityRagResult ragResult, String requirementId) {
        DocumentQualityRagEvidence evidence = ragResult.evidenceByRequirement().get(requirementId);
        return evidence != null && evidence.matched();
    }

    private boolean ragHitContains(DocumentQualityRagResult ragResult, String requirementId, List<String> keywords) {
        DocumentQualityRagEvidence evidence = ragResult.evidenceByRequirement().get(requirementId);
        if (evidence == null || !evidence.matched()) {
            return false;
        }
        return evidence.topHits().stream()
                .map(hit -> (hit.title() + "\n" + hit.content()).toLowerCase(Locale.ROOT))
                .anyMatch(text -> keywords.stream().map(keyword -> keyword.toLowerCase(Locale.ROOT)).anyMatch(text::contains));
    }

    private String missingRagEvidence(String requirementId, DocumentQualityRagResult ragResult, String fallback) {
        DocumentQualityRagEvidence evidence = ragResult.evidenceByRequirement().get(requirementId);
        if (evidence == null || evidence.topHits().isEmpty()) {
            return fallback;
        }
        DocumentQualityRagHit topHit = evidence.topHits().get(0);
        return fallback + " 最接近片段：" + topHit.chunkId() + "，相似度 " + topHit.score() + "，内容：" + snippet(topHit.content());
    }

    private int ragDimensionEvidenceBonus(String dimensionId, DocumentQualityRagResult ragResult) {
        long matchedCount = ragRequirementIdsForDimension(dimensionId).stream()
                .filter(requirementId -> hasRequirementEvidence(ragResult, requirementId))
                .count();
        return (int) Math.min(6, matchedCount * 3);
    }

    private boolean requiresRagEvidence(String dimensionId) {
        return !ragRequirementIdsForDimension(dimensionId).isEmpty();
    }

    private String ragEvidenceSummary(String dimensionId, DocumentQualityRagResult ragResult) {
        List<DocumentQualityRagEvidence> matchedEvidence = ragRequirementIdsForDimension(dimensionId).stream()
                .map(requirementId -> ragResult.evidenceByRequirement().get(requirementId))
                .filter(evidence -> evidence != null && evidence.matched() && !evidence.topHits().isEmpty())
                .toList();
        if (matchedEvidence.isEmpty()) {
            return "RAG 未检索到该维度的强相关证据。";
        }
        return "RAG命中证据："
                + matchedEvidence.stream()
                .map(evidence -> evidence.label() + "@" + evidence.topHits().get(0).chunkId() + "(" + evidence.topScore() + ")")
                .collect(Collectors.joining("；"));
    }

    private List<String> ragRequirementIdsForDimension(String dimensionId) {
        return switch (dimensionId) {
            case "accuracy" -> List.of("risk_handling");
            case "completeness" -> List.of("data_model", "api_contract", "acceptance");
            case "clarity" -> List.of("metrics", "structure");
            case "logic" -> List.of("structure", "acceptance");
            case "consistency" -> List.of("structure");
            case "practicality" -> List.of("api_contract", "risk_handling", "acceptance");
            default -> List.of();
        };
    }

    private boolean hasApiContract(String normalizedContent) {
        boolean mentionsApi = containsAny(normalizedContent, List.of("接口", "api", "endpoint", "/api"));
        boolean hasContractDetail = containsAny(normalizedContent, List.of("请求", "响应", "返回", "入参", "出参", "错误码", "post ", "get ", "post /", "get /"));
        return mentionsApi && hasContractDetail;
    }

    private boolean hasRiskHandling(String normalizedContent) {
        return containsAny(normalizedContent, List.of("风险", "异常", "回滚", "降级", "熔断", "兜底"))
                && containsAny(normalizedContent, List.of("处理", "方案", "策略", "解决", "排查", "脱敏"));
    }

    private boolean isHeading(String line) {
        return HEADING_PATTERN.matcher(line).matches();
    }

    private List<String> splitParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        for (String block : content.split("\\R\\s*\\R+")) {
            String text = block.trim();
            if (!text.isBlank()) {
                paragraphs.add(text);
            }
        }
        if (paragraphs.size() <= 1) {
            return content.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .toList();
        }
        return List.copyOf(paragraphs);
    }

    private List<String> splitSentences(String content) {
        return Pattern.compile("[。！？!?；;\\n]+")
                .splitAsStream(content)
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .toList();
    }

    private double duplicateRate(List<String> paragraphs) {
        if (paragraphs.isEmpty()) {
            return 0;
        }
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0;
        for (String paragraph : paragraphs) {
            String normalized = paragraph.toLowerCase(Locale.ROOT)
                    .replaceAll("[\\s\\p{Punct}，。！？；：、（）《》“”‘’]", "");
            if (normalized.length() < 20) {
                continue;
            }
            if (!seen.add(normalized)) {
                duplicates++;
            }
        }
        return round1((double) duplicates / Math.max(1, paragraphs.size()));
    }

    private int nonWhitespaceLength(String text) {
        return defaultText(text, "").replaceAll("\\s+", "").length();
    }

    private double compliancePassRate(List<DocumentQualityIssue> issues) {
        if (hasP0(issues)) {
            return 0;
        }
        long securityIssues = issues.stream()
                .filter(issue -> "合规安全".equals(issue.category()))
                .count();
        return securityIssues == 0 ? 100 : 80;
    }

    private List<Map<String, Object>> distribution(List<DocumentQualityIssue> issues, java.util.function.Function<DocumentQualityIssue, String> classifier) {
        Map<String, Long> counts = issues.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> DocumentQualityService.<String, Object>orderedMap("name", entry.getKey(), "value", entry.getValue()))
                .toList();
    }

    private boolean containsAny(String text, List<String> keywords) {
        String safeText = defaultText(text, "");
        return keywords.stream().anyMatch(safeText::contains);
    }

    private boolean hasP0(List<DocumentQualityIssue> issues) {
        return issues.stream().anyMatch(issue -> "P0".equals(issue.severity()));
    }

    private int severityDeduction(String severity) {
        return switch (severity) {
            case "P0" -> 45;
            case "P1" -> 12;
            case "P2" -> 6;
            default -> 3;
        };
    }

    private int severityOrder(String severity) {
        return switch (severity) {
            case "P0" -> 0;
            case "P1" -> 1;
            case "P2" -> 2;
            default -> 3;
        };
    }

    private String gradeOf(int score) {
        if (score >= 90) {
            return "优质文档（无需整改）";
        }
        if (score >= 80) {
            return "良好文档（少量优化）";
        }
        if (score >= 60) {
            return "合格文档（需重点优化）";
        }
        return "劣质文档（强制整改）";
    }

    private String dimensionLevel(int score) {
        if (score >= 90) {
            return "good";
        }
        if (score >= 75) {
            return "warning";
        }
        return "risk";
    }

    private String snippet(String text) {
        String compact = defaultText(text, "").replaceAll("\\s+", " ").trim();
        return compact.length() <= 90 ? compact : compact.substring(0, 90) + "...";
    }

    private String normalizeContent(String content) {
        return defaultText(content, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private String defaultText(String value, String fallback) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .orElse(fallback);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @SafeVarargs
    private static <K, V> Map<K, V> orderedMap(Object... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) entries[i];
            @SuppressWarnings("unchecked")
            V value = (V) entries[i + 1];
            map.put(key, value);
        }
        return map;
    }

    private record DimensionSpec(String id, String name, int weight) {
    }

    private record DocumentProfile(
            String content,
            String normalizedContent,
            List<String> lines,
            List<String> paragraphs,
            List<String> headings,
            int charCount,
            int sentenceCount,
            double averageSentenceLength,
            int longParagraphCount,
            double duplicateRate
    ) {
    }
}
