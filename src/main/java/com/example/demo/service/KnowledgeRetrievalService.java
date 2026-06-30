package com.example.demo.service;

import com.example.demo.model.Knowledge;
import com.example.demo.model.KnowledgeTag;
import com.example.demo.repository.KnowledgeRepository;
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
import java.util.Set;

@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);
    private static final int VECTOR_SIZE = 128;
    private static final int DEFAULT_TOP_K = 3;
    private static final double KNOWLEDGE_MIN_SCORE = 0.08;
    private static final int DEFAULT_PROMPT_CONTEXT_ITEMS = 3;
    private static final int MAX_CONTEXT_TEXT_LENGTH = 180;

    private final KnowledgeRepository knowledgeRepository;
    private final VectorRagDemoService vectorRagDemoService;
    private final List<Knowledge> fixedKnowledgeRecords;

    @Autowired
    public KnowledgeRetrievalService(
            KnowledgeRepository knowledgeRepository,
            VectorRagDemoService vectorRagDemoService) {
        this(knowledgeRepository, vectorRagDemoService, null);
    }

    KnowledgeRetrievalService(List<Knowledge> fixedKnowledgeRecords, VectorRagDemoService vectorRagDemoService) {
        this(null, vectorRagDemoService, fixedKnowledgeRecords);
    }

    private KnowledgeRetrievalService(
            KnowledgeRepository knowledgeRepository,
            VectorRagDemoService vectorRagDemoService,
            List<Knowledge> fixedKnowledgeRecords) {
        this.knowledgeRepository = knowledgeRepository;
        this.vectorRagDemoService = vectorRagDemoService;
        this.fixedKnowledgeRecords = fixedKnowledgeRecords == null ? null : List.copyOf(fixedKnowledgeRecords);
    }

    public KnowledgeRetrievalResult retrieve(String scene, String query, int topK) {
        String safeScene = safeText(scene, "default");
        String safeQuery = query == null ? "" : query.trim();
        int boundedTopK = normalizeTopK(topK);
        List<String> trace = new ArrayList<>();
        log.info("统一知识检索开始: scene={}, queryLength={}, topK={}", safeScene, safeQuery.length(), boundedTopK);

        if (safeQuery.isBlank()) {
            log.warn("统一知识检索跳过: scene={}, reason=query blank", safeScene);
            return new KnowledgeRetrievalResult(
                    safeScene,
                    safeQuery,
                    boundedTopK,
                    false,
                    "检索问题为空，未执行知识召回。",
                    List.of(),
                    List.of("query blank")
            );
        }

        try {
            List<KnowledgeRetrievalHit> knowledgeHits = retrieveKnowledgeHits(safeQuery, boundedTopK, trace);
            if (!knowledgeHits.isEmpty()) {
                log.info("统一知识检索命中知识库: scene={}, hitCount={}", safeScene, knowledgeHits.size());
                return new KnowledgeRetrievalResult(
                        safeScene,
                        safeQuery,
                        boundedTopK,
                        true,
                        "命中统一知识检索上下文。",
                        knowledgeHits,
                        List.copyOf(trace)
                );
            }

            if (shouldUseDemoFallback(safeScene)) {
                List<KnowledgeRetrievalHit> demoHits = retrieveDemoHits(safeQuery, boundedTopK, trace);
                if (!demoHits.isEmpty()) {
                    log.info("统一知识检索回退 RAG Demo: scene={}, hitCount={}", safeScene, demoHits.size());
                    return new KnowledgeRetrievalResult(
                            safeScene,
                            safeQuery,
                            boundedTopK,
                            true,
                            "知识库未命中，已回退到工程内置 RAG 示例上下文。",
                            demoHits,
                            List.copyOf(trace)
                    );
                }
            }

            log.info("统一知识检索未命中: scene={}, trace={}", safeScene, trace);
            return new KnowledgeRetrievalResult(
                    safeScene,
                    safeQuery,
                    boundedTopK,
                    false,
                    "未检索到可用知识上下文。",
                    List.of(),
                    List.copyOf(trace)
            );
        } catch (RuntimeException e) {
            log.error("统一知识检索失败: scene={}, message={}", safeScene, e.getMessage());
            trace.add("retrieval failed: " + e.getMessage());
            return new KnowledgeRetrievalResult(
                    safeScene,
                    safeQuery,
                    boundedTopK,
                    false,
                    "统一知识检索失败，未注入检索上下文。",
                    List.of(),
                    List.copyOf(trace)
            );
        }
    }

    public static String formatContextForPrompt(KnowledgeRetrievalResult result) {
        return formatContextForPrompt(result, DEFAULT_PROMPT_CONTEXT_ITEMS, MAX_CONTEXT_TEXT_LENGTH);
    }

    public static String formatContextForPrompt(
            KnowledgeRetrievalResult result,
            int maxItems,
            int maxContentLength) {
        String safeScene = safeText(result == null ? "" : result.scene(), "unknown");
        if (result == null || result.matches() == null || result.matches().isEmpty()) {
            String safeReason = result == null ? "检索返回为空" : safeText(result.gateReason(), "检索返回为空");
            return "【统一知识检索上下文】\n场景：" + safeScene + "\n状态：未命中可用上下文，" + safeReason;
        }
        if (!result.used()) {
            return "【统一知识检索上下文】\n场景：" + safeScene + "\n状态：未命中，" + safeText(result.gateReason(), "未提供可用上下文");
        }

        int itemCount = Math.min(Math.max(maxItems, 1), result.matches().size());
        int maxLength = Math.max(maxContentLength, 30);
        StringBuilder sb = new StringBuilder("【统一知识检索上下文】\n");
        sb.append("场景：").append(safeScene).append("；问题：").append(safeText(result.query(), "")).append("；命中：").append(result.matches().size()).append(" 条\n");
        for (int i = 0; i < itemCount; i++) {
            KnowledgeRetrievalHit hit = result.matches().get(i);
            String content = safeText(hit == null ? null : hit.content(), "");
            String limitedContent = content.length() <= maxLength ? content : content.substring(0, maxLength) + "…";
            String sourceName = safeText(hit == null ? "" : hit.sourceName(), "知识来源");
            String title = safeText(hit == null ? null : hit.title(), "未命名标题");
            sb.append(i + 1).append(". [")
                    .append(safeText(hit == null ? null : hit.id(), "unknown"))
                    .append("] ")
                    .append(sourceName)
                    .append(" - ")
                    .append(title)
                    .append("（").append(hit == null ? "" : String.format(Locale.ROOT, "%.2f", hit.score())).append("）\n")
                    .append(limitedContent).append('\n');
        }
        return sb.toString().trim();
    }

    private List<KnowledgeRetrievalHit> retrieveKnowledgeHits(String query, int topK, List<String> trace) {
        if (fixedKnowledgeRecords != null) {
            List<Knowledge> candidates = fixedKnowledgeRecords.stream()
                    .filter(knowledge -> "published".equalsIgnoreCase(safeText(knowledge.getStatus(), "published")))
                    .toList();
            trace.add("loaded published knowledge: " + candidates.size());
            return rankKnowledgeCandidates(query, topK, trace, candidates);
        }

        if (knowledgeRepository == null) {
            trace.add("knowledge repository unavailable");
            return List.of();
        }

        List<Knowledge> candidates = safeKnowledgeList(knowledgeRepository.findByStatus("published"));
        trace.add("loaded published knowledge: " + candidates.size());
        if (candidates.isEmpty()) {
            candidates = safeKnowledgeList(knowledgeRepository.findAllByOrderByUpdatedAtDesc());
            trace.add("published empty, loaded all knowledge: " + candidates.size());
        }
        return rankKnowledgeCandidates(query, topK, trace, candidates);
    }

    private static List<KnowledgeRetrievalHit> rankKnowledgeCandidates(
            String query,
            int topK,
            List<String> trace,
            List<Knowledge> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        double[] queryVector = embed(query);
        List<KnowledgeRetrievalHit> hits = candidates.stream()
                .map(knowledge -> toKnowledgeHit(knowledge, query, queryVector))
                .filter(hit -> hit.score() >= KNOWLEDGE_MIN_SCORE)
                .sorted(Comparator.comparingDouble(KnowledgeRetrievalHit::score).reversed())
                .limit(topK)
                .toList();
        trace.add("ranked knowledge hits: " + hits.size());
        return hits;
    }

    private List<KnowledgeRetrievalHit> retrieveDemoHits(String query, int topK, List<String> trace) {
        if (vectorRagDemoService == null) {
            trace.add("rag demo fallback unavailable");
            return List.of();
        }
        VectorRagDemoService.VectorSearchResponse response = vectorRagDemoService.search(query, topK);
        List<KnowledgeRetrievalHit> hits = response.matches().stream()
                .map(hit -> new KnowledgeRetrievalHit(
                        hit.id(),
                        hit.title(),
                        hit.content(),
                        hit.score(),
                        "rag-demo",
                        "工程内置 RAG 示例",
                        new LinkedHashMap<>(hit.metadata())
                ))
                .toList();
        trace.add("fallback rag demo hits: " + hits.size());
        return hits;
    }

    private static KnowledgeRetrievalHit toKnowledgeHit(Knowledge knowledge, String query, double[] queryVector) {
        String title = safeText(knowledge.getTitle(), "未命名知识");
        String content = safeText(knowledge.getContent(), "");
        String searchableText = title + "\n" + content + "\n" + tagText(knowledge);
        double vectorScore = cosineSimilarity(queryVector, embed(searchableText));
        double keywordScore = keywordOverlapScore(query, searchableText);
        double score = round4(vectorScore * 0.7 + keywordScore * 0.3);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledgeId", knowledge.getId());
        metadata.put("status", safeText(knowledge.getStatus(), ""));
        metadata.put("categoryId", knowledge.getCategoryId());
        metadata.put("updatedAt", knowledge.getUpdatedAt() == null ? "" : knowledge.getUpdatedAt().toString());
        return new KnowledgeRetrievalHit(
                "knowledge:" + knowledge.getId(),
                title,
                content,
                score,
                "knowledge",
                "知识库",
                metadata
        );
    }

    private static String tagText(Knowledge knowledge) {
        if (knowledge.getTags() == null || knowledge.getTags().isEmpty()) {
            return "";
        }
        return knowledge.getTags().stream()
                .map(KnowledgeTag::getName)
                .filter(name -> name != null && !name.isBlank())
                .reduce("", (left, right) -> left + " " + right)
                .trim();
    }

    private static double keywordOverlapScore(String query, String text) {
        Set<String> queryTokens = new LinkedHashSet<>(tokenize(query));
        if (queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> textTokens = new LinkedHashSet<>(tokenize(text));
        long matched = queryTokens.stream().filter(textTokens::contains).count();
        return Math.min(1.0, matched / (double) queryTokens.size());
    }

    private static double[] embed(String text) {
        double[] vector = new double[VECTOR_SIZE];
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), VECTOR_SIZE);
            vector[index] += token.length() > 1 ? 1.4 : 1.0;
        }

        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return vector;
        }
        double scale = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / scale;
        }
        return vector;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        StringBuilder asciiToken = new StringBuilder();
        List<String> cjkChars = new ArrayList<>();

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (isAsciiLetterOrDigit(ch)) {
                asciiToken.append(ch);
                continue;
            }
            flushAsciiToken(tokens, asciiToken);
            if (isCjk(ch)) {
                String value = String.valueOf(ch);
                tokens.add(value);
                cjkChars.add(value);
            }
        }
        flushAsciiToken(tokens, asciiToken);
        for (int i = 0; i < cjkChars.size() - 1; i++) {
            tokens.add(cjkChars.get(i) + cjkChars.get(i + 1));
        }
        return tokens;
    }

    private static void flushAsciiToken(List<String> tokens, StringBuilder asciiToken) {
        if (!asciiToken.isEmpty()) {
            tokens.add(asciiToken.toString());
            asciiToken.setLength(0);
        }
    }

    private static boolean isAsciiLetterOrDigit(char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9';
    }

    private static boolean isCjk(char ch) {
        return ch >= '\u4E00' && ch <= '\u9FFF';
    }

    private static double cosineSimilarity(double[] left, double[] right) {
        if (left.length != right.length || left.length == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < left.length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }

    private static int normalizeTopK(int topK) {
        return topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, 10);
    }

    private static boolean shouldUseDemoFallback(String scene) {
        String safeScene = scene == null ? "" : scene.toLowerCase(Locale.ROOT);
        return safeScene.contains("prompt") || safeScene.contains("rag") || safeScene.contains("demo");
    }

    private static List<Knowledge> safeKnowledgeList(List<Knowledge> values) {
        return values == null ? List.of() : values;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
