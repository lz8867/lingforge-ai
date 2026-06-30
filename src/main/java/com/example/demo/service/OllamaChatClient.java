package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OllamaChatClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);

    private final RestTemplate restTemplate;
    private final String generateUrl;
    private final String model;
    private final Map<String, Object> defaultOptions;

    @Autowired
    public OllamaChatClient(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.chat.options.model:qwen2.5}") String model,
            @Value("${spring.ai.ollama.timeout-ms:60000}") int timeoutMs,
            @Value("${spring.ai.ollama.generate.num-predict:512}") int numPredict) {
        this(timeoutRestTemplate(timeoutMs), baseUrl, model, defaultOptions(numPredict));
    }

    protected OllamaChatClient(String baseUrl, String model, int timeoutMs) {
        this(timeoutRestTemplate(timeoutMs), baseUrl, model);
    }

    OllamaChatClient(RestTemplate restTemplate, String baseUrl, String model) {
        this(restTemplate, baseUrl, model, Map.of());
    }

    OllamaChatClient(RestTemplate restTemplate, String baseUrl, String model, Map<String, ?> defaultOptions) {
        this.restTemplate = restTemplate;
        this.generateUrl = normalizeBaseUrl(baseUrl) + "/api/generate";
        this.model = model;
        this.defaultOptions = sanitizeOptions(defaultOptions);
    }

    public String generate(String prompt) throws Exception {
        return generate(prompt, defaultOptions);
    }

    public String generateJson(String prompt, int numPredict) throws Exception {
        return generate(prompt, optionsWithPredictionBudget(numPredict), "json");
    }

    String generate(String prompt, Map<String, ?> options) throws Exception {
        return generate(prompt, options, "");
    }

    private String generate(String prompt, Map<String, ?> options, String responseFormat) throws Exception {
        Map<String, Object> requestOptions = sanitizeOptions(options);
        log.info(
                "Calling Ollama generate API, url: {}, model: {}, promptLength: {}, responseFormat: {}, optionKeys: {}",
                generateUrl,
                model,
                prompt == null ? 0 : prompt.length(),
                responseFormat == null || responseFormat.isBlank() ? "text" : responseFormat,
                requestOptions.keySet()
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        if (responseFormat != null && !responseFormat.isBlank()) {
            requestBody.put("format", responseFormat);
        }
        if (!requestOptions.isEmpty()) {
            requestBody.put("options", requestOptions);
        }

        try {
            Map<String, Object> responseBody = restTemplate.postForObject(generateUrl, requestBody, Map.class);
            if (responseBody != null && responseBody.containsKey("response")) {
                return (String) responseBody.get("response");
            }
            log.error("Ollama generate API returned invalid response");
            throw new Exception("Invalid response from Ollama API");
        } catch (HttpClientErrorException e) {
            log.error("Ollama generate API failed, status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new Exception("Failed to call Ollama API: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Ollama generate API request failed: {}", e.getMessage());
            throw e;
        }
    }

    public String getModel() {
        return model;
    }

    public String getProvider() {
        return "ollama";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:11434";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static RestTemplate timeoutRestTemplate(int timeoutMs) {
        int safeTimeout = Math.max(500, timeoutMs);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeTimeout);
        requestFactory.setReadTimeout(safeTimeout);
        return new RestTemplate(requestFactory);
    }

    private static Map<String, Object> defaultOptions(int numPredict) {
        if (numPredict <= 0) {
            return Map.of();
        }
        return Map.of("num_predict", Math.max(32, numPredict));
    }

    private Map<String, Object> optionsWithPredictionBudget(int numPredict) {
        Map<String, Object> merged = new HashMap<>(defaultOptions);
        if (numPredict > 0) {
            int requested = Math.max(32, numPredict);
            int existing = 0;
            Object existingValue = merged.get("num_predict");
            if (existingValue instanceof Number number) {
                existing = number.intValue();
            }
            merged.put("num_predict", Math.max(existing, requested));
        }
        return Map.copyOf(merged);
    }

    private static Map<String, Object> sanitizeOptions(Map<String, ?> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new HashMap<>();
        options.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }
}
