package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HttpTripoSplatGateway implements TripoSplatGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpTripoSplatGateway.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public HttpTripoSplatGateway(@Value("${modeling.triposplat.base-url:}") String baseUrl) {
        this(new RestTemplate(), baseUrl);
    }

    HttpTripoSplatGateway(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    @Override
    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    @Override
    public String serviceUrl() {
        return baseUrl;
    }

    @Override
    public TripoSplatGenerationResult generate(String jobId, Path inputImage, Path outputDir, ModelingJobRequest request) {
        log.info("调用 TripoSplat 适配服务: jobId={}, configured={}, numGaussians={}, steps={}",
                jobId, isConfigured(), request.numGaussians(), request.steps());
        if (!isConfigured()) {
            log.warn("TripoSplat 适配服务未配置: jobId={}", jobId);
            return TripoSplatGenerationResult.unavailable("TripoSplat 服务未配置，请设置 modeling.triposplat.base-url 或启动 scripts/triposplat_service.py。");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new FileSystemResource(inputImage));
        body.add("job_id", jobId);
        body.add("output_dir", outputDir.toAbsolutePath().toString());
        body.add("prompt", request.prompt());
        body.add("kind", request.kind());
        body.add("quality", request.quality());
        body.add("seed", String.valueOf(request.seed()));
        body.add("steps", String.valueOf(request.steps()));
        body.add("guidance_scale", String.valueOf(request.guidanceScale()));
        body.add("num_gaussians", String.valueOf(request.numGaussians()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/generate",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                log.error("TripoSplat 适配服务返回空响应: jobId={}", jobId);
                return TripoSplatGenerationResult.failure("TripoSplat 服务返回空响应。");
            }

            boolean success = Boolean.parseBoolean(String.valueOf(readValue(responseBody, "success", false)));
            String message = String.valueOf(readValue(responseBody, "message", success ? "TripoSplat 生成完成" : "TripoSplat 生成失败"));
            if (!success) {
                log.warn("TripoSplat 适配服务返回失败: jobId={}, message={}", jobId, message);
                return TripoSplatGenerationResult.failure(message);
            }

            return TripoSplatGenerationResult.success(
                    message,
                    parseArtifacts(responseBody.get("outputs")),
                    parseMetrics(responseBody.get("metrics"))
            );
        } catch (HttpStatusCodeException e) {
            String bodyText = e.getResponseBodyAsString();
            log.error("TripoSplat 适配服务 HTTP 失败: jobId={}, status={}, body={}", jobId, e.getStatusCode(), firstLine(bodyText));
            if (e.getStatusCode().value() == 503) {
                return TripoSplatGenerationResult.unavailable("TripoSplat 服务未就绪：" + firstLine(bodyText));
            }
            return TripoSplatGenerationResult.failure("TripoSplat 服务调用失败：" + e.getStatusCode());
        } catch (Exception e) {
            log.error("TripoSplat 适配服务请求异常: jobId={}, message={}", jobId, e.getMessage());
            return TripoSplatGenerationResult.unavailable("TripoSplat 服务不可用：" + e.getMessage());
        }
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private static List<ModelingArtifact> parseArtifacts(Object outputs) {
        if (!(outputs instanceof List<?> items)) {
            return List.of();
        }
        List<ModelingArtifact> artifacts = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = String.valueOf(readValue(map, "name", ""));
            if (name.isBlank()) {
                continue;
            }
            String type = String.valueOf(readValue(map, "type", "application/octet-stream"));
            String role = String.valueOf(readValue(map, "role", "artifact"));
            long bytes = parseLong(map.get("bytes"));
            artifacts.add(new ModelingArtifact(name, type, bytes, null, role));
        }
        return artifacts;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMetrics(Object metrics) {
        if (metrics instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static Object readValue(Map<?, ?> map, String key, Object fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value;
    }

    private static String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.lines().findFirst().orElse(value);
    }
}
