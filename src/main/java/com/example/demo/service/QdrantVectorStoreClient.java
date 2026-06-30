package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class QdrantVectorStoreClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStoreClient.class);
    private static final String PROVIDER_QDRANT = "qdrant";
    private static final String PROVIDER_LOCAL = "local";

    private final RestTemplate restTemplate;
    private final String provider;
    private final String baseUrl;
    private final String collection;

    @Autowired
    public QdrantVectorStoreClient(
            @Value("${rag.vector.provider:local}") String provider,
            @Value("${rag.vector.qdrant.url:http://localhost:6333}") String baseUrl,
            @Value("${rag.vector.qdrant.collection:spring_ai_rag_chunks}") String collection,
            @Value("${rag.vector.qdrant.timeout-ms:1200}") int timeoutMs) {
        this(timeoutRestTemplate(timeoutMs), provider, baseUrl, collection);
    }

    QdrantVectorStoreClient(RestTemplate restTemplate, String provider, String baseUrl, String collection) {
        this.restTemplate = restTemplate;
        this.provider = defaultText(provider, PROVIDER_LOCAL).toLowerCase(Locale.ROOT);
        this.baseUrl = normalizeBaseUrl(baseUrl, "http://localhost:6333");
        this.collection = defaultText(collection, "spring_ai_rag_chunks");
    }

    public static QdrantVectorStoreClient disabled() {
        return new QdrantVectorStoreClient(new RestTemplate(), PROVIDER_LOCAL, "http://localhost:6333", "spring_ai_rag_chunks");
    }

    public boolean enabled() {
        return PROVIDER_QDRANT.equals(provider);
    }

    public String provider() {
        return provider;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String collection() {
        return collection;
    }

    public StoreStatus status() {
        log.info("RAG Qdrant 状态检查开始: provider={}, collection={}", provider, collection);
        if (!enabled()) {
            return new StoreStatus(false, "Qdrant 未启用，当前使用本地向量索引。");
        }
        try {
            restTemplate.getForEntity(collectionUrl(), Map.class);
            log.info("RAG Qdrant 状态可用: collection={}", collection);
            return new StoreStatus(true, "Qdrant collection 可访问。");
        } catch (Exception e) {
            log.warn("RAG Qdrant 状态不可用: collection={}, reason={}", collection, e.getMessage());
            return new StoreStatus(false, "Qdrant 不可用：" + e.getMessage());
        }
    }

    public UpsertResult upsert(List<VectorPoint> points) {
        int pointCount = points == null ? 0 : points.size();
        log.info("RAG Qdrant 写入开始: enabled={}, pointCount={}", enabled(), pointCount);
        if (!enabled()) {
            return new UpsertResult(false, "Qdrant 未启用，已跳过远端写入。");
        }
        if (pointCount == 0) {
            return new UpsertResult(true, "没有需要写入 Qdrant 的向量。");
        }

        try {
            ensureCollection(points.get(0).vector().length);
            List<Map<String, Object>> qdrantPoints = points.stream()
                    .map(this::toQdrantPoint)
                    .toList();
            restTemplate.put(collectionUrl() + "/points?wait=true", Map.of("points", qdrantPoints));
            log.info("RAG Qdrant 写入完成: pointCount={}", qdrantPoints.size());
            return new UpsertResult(true, "已写入 Qdrant 向量库。");
        } catch (Exception e) {
            log.warn("RAG Qdrant 写入失败，保留本地索引: reason={}", e.getMessage());
            return new UpsertResult(false, "Qdrant 写入失败：" + e.getMessage());
        }
    }

    public SearchResult search(double[] vector, int topK) {
        log.info("RAG Qdrant 检索开始: enabled={}, topK={}", enabled(), topK);
        if (!enabled()) {
            return new SearchResult(false, "Qdrant 未启用。", List.of());
        }
        if (vector == null || vector.length == 0) {
            return new SearchResult(false, "查询向量为空。", List.of());
        }

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("vector", toList(vector));
            requestBody.put("limit", topK);
            requestBody.put("with_payload", true);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    collectionUrl() + "/points/search",
                    requestBody,
                    Map.class
            );
            List<VectorRagDemoService.SearchHit> hits = parseSearchHits(response);
            log.info("RAG Qdrant 检索完成: hitCount={}", hits.size());
            return new SearchResult(true, "Qdrant 检索成功。", hits);
        } catch (Exception e) {
            log.warn("RAG Qdrant 检索失败，准备回退本地索引: reason={}", e.getMessage());
            return new SearchResult(false, "Qdrant 检索失败：" + e.getMessage(), List.of());
        }
    }

    public DeleteResult deleteImported() {
        log.info("RAG Qdrant 导入片段清理开始: enabled={}", enabled());
        if (!enabled()) {
            return new DeleteResult(false, "Qdrant 未启用，已跳过远端清理。");
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "filter",
                    Map.of("must", List.of(Map.of(
                            "key", "sourceType",
                            "match", Map.of("value", "imported")
                    )))
            );
            restTemplate.postForObject(collectionUrl() + "/points/delete?wait=true", requestBody, Map.class);
            log.info("RAG Qdrant 导入片段清理完成");
            return new DeleteResult(true, "已清理 Qdrant 中的导入片段。");
        } catch (Exception e) {
            log.warn("RAG Qdrant 导入片段清理失败: reason={}", e.getMessage());
            return new DeleteResult(false, "Qdrant 清理失败：" + e.getMessage());
        }
    }

    private void ensureCollection(int vectorSize) {
        StoreStatus current = status();
        if (current.available()) {
            return;
        }
        Map<String, Object> requestBody = Map.of(
                "vectors",
                Map.of(
                        "size", vectorSize,
                        "distance", "Cosine"
                )
        );
        restTemplate.put(collectionUrl(), requestBody);
    }

    private Map<String, Object> toQdrantPoint(VectorPoint point) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", point.id());
        payload.put("title", point.title());
        payload.put("content", point.content());
        payload.putAll(point.metadata());

        Map<String, Object> qdrantPoint = new LinkedHashMap<>();
        qdrantPoint.put("id", UUID.nameUUIDFromBytes(point.id().getBytes(StandardCharsets.UTF_8)).toString());
        qdrantPoint.put("vector", toList(point.vector()));
        qdrantPoint.put("payload", payload);
        return qdrantPoint;
    }

    private static List<VectorRagDemoService.SearchHit> parseSearchHits(Map<String, Object> response) {
        Object result = response == null ? null : response.get("result");
        if (!(result instanceof List<?> rows)) {
            return List.of();
        }

        List<VectorRagDemoService.SearchHit> hits = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item)) {
                continue;
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            Object payloadValue = item.get("payload");
            Map<?, ?> payload = payloadValue instanceof Map<?, ?> map ? map : Map.of();
            payload.forEach((key, value) -> metadata.put(String.valueOf(key), String.valueOf(value)));
            String id = defaultText(metadata.get("chunkId"), String.valueOf(item.get("id")));
            String title = defaultText(metadata.get("title"), "Qdrant 命中文档");
            String content = defaultText(metadata.get("content"), "");
            double score = item.get("score") instanceof Number number ? round(number.doubleValue()) : 0;
            hits.add(new VectorRagDemoService.SearchHit(id, title, content, score, metadata));
        }
        return hits;
    }

    private String collectionUrl() {
        return baseUrl + "/collections/" + collection;
    }

    private static List<Double> toList(double[] vector) {
        List<Double> values = new ArrayList<>(vector.length);
        for (double value : vector) {
            values.add(value);
        }
        return values;
    }

    private static RestTemplate timeoutRestTemplate(int timeoutMs) {
        int safeTimeout = Math.max(200, timeoutMs);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeTimeout);
        requestFactory.setReadTimeout(safeTimeout);
        return new RestTemplate(requestFactory);
    }

    private static String normalizeBaseUrl(String value, String fallback) {
        String normalized = defaultText(value, fallback);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String defaultText(String value, String fallback) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .orElse(fallback);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record VectorPoint(String id, String title, String content, double[] vector, Map<String, String> metadata) {
    }

    public record StoreStatus(boolean available, String message) {
    }

    public record UpsertResult(boolean success, String message) {
    }

    public record SearchResult(boolean success, String message, List<VectorRagDemoService.SearchHit> hits) {
    }

    public record DeleteResult(boolean success, String message) {
    }
}
