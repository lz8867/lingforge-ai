package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DocumentQualityEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityEmbeddingClient.class);
    private static final int LOCAL_VECTOR_SIZE = 256;
    private static final String PROVIDER_LOCAL = "local";
    private static final String PROVIDER_OLLAMA = "ollama";
    private static final String PROVIDER_OPENAI = "openai";
    private static final long EXTERNAL_FAILURE_COOLDOWN_NANOS = Duration.ofSeconds(30).toNanos();

    private final RestTemplate restTemplate;
    private final String provider;
    private final String ollamaEmbeddingUrl;
    private final String ollamaModel;
    private final String openAiEmbeddingUrl;
    private final String openAiModel;
    private final String openAiApiKey;
    private volatile long externalRetryAtNanos;

    @Autowired
    public DocumentQualityEmbeddingClient(
            @Value("${document.quality.embedding.provider:ollama}") String provider,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${document.quality.embedding.ollama.model:nomic-embed-text}") String ollamaModel,
            @Value("${document.quality.embedding.openai.base-url:https://api.openai.com/v1}") String openAiBaseUrl,
            @Value("${document.quality.embedding.openai.model:text-embedding-3-small}") String openAiModel,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey,
            @Value("${document.quality.embedding.timeout-ms:1200}") int timeoutMs) {
        this(timeoutRestTemplate(timeoutMs), provider, ollamaBaseUrl, ollamaModel, openAiBaseUrl, openAiModel, openAiApiKey);
    }

    DocumentQualityEmbeddingClient(
            RestTemplate restTemplate,
            String provider,
            String ollamaBaseUrl,
            String ollamaModel,
            String openAiBaseUrl,
            String openAiModel,
            String openAiApiKey) {
        this.restTemplate = restTemplate;
        this.provider = defaultText(provider, PROVIDER_OLLAMA).toLowerCase(Locale.ROOT);
        this.ollamaEmbeddingUrl = normalizeBaseUrl(ollamaBaseUrl, "http://localhost:11434") + "/api/embeddings";
        this.ollamaModel = defaultText(ollamaModel, "nomic-embed-text");
        this.openAiEmbeddingUrl = normalizeBaseUrl(openAiBaseUrl, "https://api.openai.com/v1") + "/embeddings";
        this.openAiModel = defaultText(openAiModel, "text-embedding-3-small");
        this.openAiApiKey = defaultText(openAiApiKey, "");
    }

    public static DocumentQualityEmbeddingClient localOnly() {
        return new DocumentQualityEmbeddingClient(new RestTemplate(), PROVIDER_LOCAL, "", "", "", "", "");
    }

    public EmbeddingResult embed(String text) {
        String safeText = defaultText(text, "");
        log.info("文档质量向量化开始: provider={}, textLength={}", provider, safeText.length());
        if (PROVIDER_LOCAL.equals(provider)) {
            return localFallback(safeText, "使用本地 hash embedding。");
        }
        if (externalTemporarilyUnavailable()) {
            return localFallback(safeText, "外部 embedding 暂不可用，使用本地 hash embedding。");
        }

        try {
            if (PROVIDER_OPENAI.equals(provider)) {
                return embedWithOpenAi(safeText);
            }
            return embedWithOllama(safeText);
        } catch (Exception e) {
            externalRetryAtNanos = System.nanoTime() + EXTERNAL_FAILURE_COOLDOWN_NANOS;
            log.warn("文档质量外部 embedding 失败，回落本地向量: provider={}, reason={}", provider, e.getMessage());
            return localFallback(safeText, "外部 embedding 失败：" + e.getMessage());
        }
    }

    private EmbeddingResult embedWithOllama(String text) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", ollamaModel);
        requestBody.put("prompt", text);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = restTemplate.postForObject(ollamaEmbeddingUrl, requestBody, Map.class);
        double[] vector = extractVector(responseBody == null ? null : responseBody.get("embedding"));
        if (vector.length == 0) {
            throw new IllegalStateException("Ollama embedding 响应缺少 embedding");
        }
        return new EmbeddingResult(vector, "ollama:" + ollamaModel, false, "Ollama embedding 成功");
    }

    private EmbeddingResult embedWithOpenAi(String text) {
        if (openAiApiKey.isBlank()) {
            throw new IllegalStateException("未配置 OPENAI_API_KEY");
        }
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAiModel);
        requestBody.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);
        ResponseEntity<Map> response = restTemplate.postForEntity(openAiEmbeddingUrl, new HttpEntity<>(requestBody, headers), Map.class);
        Map<?, ?> responseBody = response.getBody();
        Object data = responseBody == null ? null : responseBody.get("data");
        if (data instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            double[] vector = extractVector(first.get("embedding"));
            if (vector.length > 0) {
                return new EmbeddingResult(vector, "openai:" + openAiModel, false, "OpenAI embedding 成功");
            }
        }
        throw new IllegalStateException("OpenAI embedding 响应缺少 data[0].embedding");
    }

    private EmbeddingResult localFallback(String text, String message) {
        return new EmbeddingResult(localHashEmbedding(text), "local-hash", true, message);
    }

    private boolean externalTemporarilyUnavailable() {
        return externalRetryAtNanos > System.nanoTime();
    }

    private static RestTemplate timeoutRestTemplate(int timeoutMs) {
        int safeTimeout = Math.max(200, timeoutMs);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeTimeout);
        requestFactory.setReadTimeout(safeTimeout);
        return new RestTemplate(requestFactory);
    }

    static double[] localHashEmbedding(String text) {
        double[] vector = new double[LOCAL_VECTOR_SIZE];
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), LOCAL_VECTOR_SIZE);
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

    private static double[] extractVector(Object value) {
        if (!(value instanceof List<?> list)) {
            return new double[0];
        }
        double[] vector = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Number number) {
                vector[i] = number.doubleValue();
            } else {
                return new double[0];
            }
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
        for (int i = 0; i < cjkChars.size() - 2; i++) {
            tokens.add(cjkChars.get(i) + cjkChars.get(i + 1) + cjkChars.get(i + 2));
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

    private static String normalizeBaseUrl(String baseUrl, String fallback) {
        String normalized = defaultText(baseUrl, fallback);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String defaultText(String value, String fallback) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .orElse(fallback);
    }

    public record EmbeddingResult(double[] vector, String source, boolean fallback, String message) {
    }
}
