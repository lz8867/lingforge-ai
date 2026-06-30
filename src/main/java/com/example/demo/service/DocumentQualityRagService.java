package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DocumentQualityRagService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityRagService.class);
    private static final double MATCH_THRESHOLD = 0.18;
    private static final int TOP_K = 3;
    private static final Map<String, RequirementQuery> REQUIREMENT_QUERIES = orderedMap(
            "data_model", new RequirementQuery("data_model", "数据模型证据", "数据模型 实体 字段 表结构 DocumentQualityTask DocumentQualityIssue 持久化",
                    List.of("数据模型", "数据结构", "实体", "字段", "表结构", "documentqualitytask", "documentqualityissue", "schema", "model")),
            "api_contract", new RequirementQuery("api_contract", "接口契约证据", "API 接口 请求 响应 入参 出参 错误码 POST GET /api",
                    List.of("api", "接口", "/api", "endpoint", "post /", "get /", "put /", "delete /", "请求参数", "响应结构", "入参", "出参", "错误码")),
            "acceptance", new RequirementQuery("acceptance", "测试验收证据", "测试 验收 验收标准 测试用例 成功标准 失败判定",
                    List.of("测试", "验收", "验收标准", "测试用例", "成功标准", "失败判定", "准入", "退出标准")),
            "risk_handling", new RequirementQuery("risk_handling", "风险异常证据", "风险 异常 降级 回滚 熔断 兜底 排查 脱敏",
                    List.of("风险", "异常", "降级", "回滚", "熔断", "兜底", "排查", "脱敏", "超时", "失败")),
            "structure", new RequirementQuery("structure", "结构化证据", "概述 架构 流程 模块 章节 标题 层级 可视化报告",
                    List.of("概述", "架构", "流程", "模块", "章节", "标题", "层级", "报告")),
            "metrics", new RequirementQuery("metrics", "量化指标证据", "量化指标 平均句长 重复率 可读性 合规通过率 问题数",
                    List.of("量化指标", "指标", "平均句长", "重复率", "可读性", "合规通过率", "问题数", "覆盖率", "权重"))
    );
    private final DocumentQualityEmbeddingClient embeddingClient;

    public DocumentQualityRagService() {
        this(DocumentQualityEmbeddingClient.localOnly());
    }

    @Autowired
    public DocumentQualityRagService(DocumentQualityEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    public DocumentQualityRagResult analyze(String documentType, String content) {
        String safeContent = content == null ? "" : content.trim();
        log.info("文档质量 RAG 分析开始: documentType={}, contentLength={}",
                documentType == null ? "" : documentType, safeContent.length());

        List<DocumentQualityRagResult.DocumentQualityRagChunk> chunks = buildChunks(safeContent);
        Map<String, DocumentQualityRagEvidence> evidence = new LinkedHashMap<>();
        DocumentQualityRagResult searchable = new DocumentQualityRagResult(chunks, Map.of(), 0, embeddingClient);

        for (RequirementQuery requirement : REQUIREMENT_QUERIES.values()) {
            List<DocumentQualityRagHit> hits = searchable.search(requirement.query(), TOP_K);
            double topScore = hits.isEmpty() ? 0 : hits.get(0).score();
            boolean matched = topScore >= MATCH_THRESHOLD && hasRequirementSignal(requirement, hits);
            evidence.put(requirement.id(), new DocumentQualityRagEvidence(
                    requirement.id(),
                    requirement.label(),
                    requirement.query(),
                    matched,
                    topScore,
                    hits
            ));
        }

        double coverage = evidence.isEmpty()
                ? 0
                : evidence.values().stream().filter(DocumentQualityRagEvidence::matched).count() * 100.0 / evidence.size();
        double roundedCoverage = Math.round(coverage * 10.0) / 10.0;
        log.info("文档质量 RAG 分析完成: chunkCount={}, coverageScore={}", chunks.size(), roundedCoverage);
        return new DocumentQualityRagResult(chunks, evidence, roundedCoverage, embeddingClient);
    }

    private List<DocumentQualityRagResult.DocumentQualityRagChunk> buildChunks(String content) {
        List<String> lines = content.lines().toList();
        List<ChunkDraft> drafts = new ArrayList<>();
        String currentTitle = "正文片段";
        StringBuilder currentContent = new StringBuilder();
        int chunkStartLine = 1;
        int lastContentLine = 1;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            int lineNumber = index + 1;
            if (line.isBlank()) {
                continue;
            }

            if (isHeading(line) && !currentContent.isEmpty()) {
                drafts.add(new ChunkDraft(currentTitle, currentContent.toString().trim(), chunkStartLine, lastContentLine));
                currentContent.setLength(0);
                currentTitle = cleanHeading(line);
                chunkStartLine = lineNumber;
            } else if (isHeading(line)) {
                currentTitle = cleanHeading(line);
                chunkStartLine = lineNumber;
            }

            if (!currentContent.isEmpty()) {
                currentContent.append('\n');
            }
            currentContent.append(line);
            lastContentLine = lineNumber;
        }

        if (!currentContent.isEmpty()) {
            drafts.add(new ChunkDraft(currentTitle, currentContent.toString().trim(), chunkStartLine, lastContentLine));
        }

        if (drafts.isEmpty() && !content.isBlank()) {
            drafts.add(new ChunkDraft("正文片段", content, 1, Math.max(1, lines.size())));
        }

        List<DocumentQualityRagResult.DocumentQualityRagChunk> chunks = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            String searchableText = draft.title() + "\n" + draft.content();
            DocumentQualityEmbeddingClient.EmbeddingResult embedding = embeddingClient.embed(searchableText);
            chunks.add(new DocumentQualityRagResult.DocumentQualityRagChunk(
                    "chunk-" + (i + 1),
                    draft.title(),
                    draft.content(),
                    draft.startLine(),
                    draft.endLine(),
                    embedding.vector()
            ));
        }
        return List.copyOf(chunks);
    }

    private boolean isHeading(String line) {
        return line.matches("^(#{1,6}\\s+|\\d+(\\.\\d+)*[.、]\\s*|第[一二三四五六七八九十]+[章节部分]|[一二三四五六七八九十]+[、.]).{1,80}$");
    }

    private String cleanHeading(String line) {
        return line.replaceFirst("^(#{1,6}\\s+|\\d+(\\.\\d+)*[.、]\\s*|第[一二三四五六七八九十]+[章节部分]\\s*|[一二三四五六七八九十]+[、.]\\s*)", "").trim();
    }

    private record ChunkDraft(String title, String content, int startLine, int endLine) {
    }

    private boolean hasRequirementSignal(RequirementQuery requirement, List<DocumentQualityRagHit> hits) {
        return hits.stream()
                .limit(TOP_K)
                .map(hit -> (hit.title() + "\n" + hit.content()).toLowerCase(Locale.ROOT))
                .anyMatch(text -> requirement.keywords().stream()
                        .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                        .anyMatch(text::contains));
    }

    private record RequirementQuery(String id, String label, String query, List<String> keywords) {
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
}
