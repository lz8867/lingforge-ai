package com.example.demo.service.impl;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.videos.VideoCreateParams;
import ai.z.openapi.service.videos.VideoObject;
import ai.z.openapi.service.videos.VideosResponse;
import com.example.demo.service.KnowledgeRetrievalResult;
import com.example.demo.service.KnowledgeRetrievalService;
import com.example.demo.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);
    private static final String UPLOAD_DIR = "images";
    static final String DEFAULT_VIDEO_MODEL = "cogvideox-flash";
    static final String DEFAULT_VIDEO_MODEL_LABEL = "本地优先 · 云端兜底";
    static final String DEFAULT_FREE_VIDEO_MODEL = "Wan2.1-T2V-1.3B";
    static final String DEFAULT_FREE_VIDEO_RESOLUTION = "832x480";
    static final String DEFAULT_WAN_VIDEO_SIZE = "832*480";
    static final String DEFAULT_FREE_VIDEO_API_URL = "http://localhost:7861/api/text-to-video";
    static final String PROVIDER_LOCAL_FREE = "local-free";
    static final String PROVIDER_ZHIPU = "zhipu";
    static final String PROVIDER_HYBRID = "hybrid";
    private static final String DEFAULT_ZHIPU_API_KEY = "0f719fd7c596481c8a294161afcf5f0b.YDgi5D3rJajOy4C2";
    static final int POLL_INTERVAL_SECONDS = 2;
    private static final int ZHIPU_VIDEO_SUBMIT_MAX_ATTEMPTS = 2;
    private static final int ZHIPU_VIDEO_SUBMIT_RETRY_DELAY_SECONDS = 2;
    private final String videoModel;
    private final RestTemplate restTemplate;
    private final Map<String, String> environment;
    private KnowledgeRetrievalService knowledgeRetrievalService;
    private static final int VIDEO_RAG_TOP_K = 3;

    @Autowired
    public VideoServiceImpl(@Value("${zhipu.video.model:cogvideox-flash}") String videoModel) {
        this(videoModel, new RestTemplate(), System.getenv(), null);
    }

    @Autowired
    void setKnowledgeRetrievalService(KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    VideoServiceImpl(String videoModel, RestTemplate restTemplate, Map<String, String> environment) {
        this(videoModel, restTemplate, environment, null);
    }

    VideoServiceImpl(String videoModel, RestTemplate restTemplate, Map<String, String> environment, KnowledgeRetrievalService knowledgeRetrievalService) {
        this.videoModel = normalizeConfiguredModel(videoModel);
        this.restTemplate = restTemplate;
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    @Override
    public Map<String, Object> getVideoCapability() {
        String provider = configuredVideoProvider(environment);
        String localModel = configuredFreeVideoModel(environment);
        boolean usesLocalProvider = usesLocalFreeProvider(provider);
        boolean localEndpointConfigured = usesLocalProvider && hasConfiguredValue(environment, "FREE_VIDEO_API_URL");
        LocalVideoHealth localHealth = probeLocalFreeVideoHealth(provider, localModel);
        String effectiveLocalModel = stringValue(localHealth.model()).orElse(localModel);
        boolean localConfigured = usesLocalProvider && (localEndpointConfigured || localHealth.available());
        boolean explicitZhipuKey = hasConfiguredValue(environment, "ZHIPU_API_KEY");
        boolean zhipuConfigured = usesZhipuProvider(provider) && hasUsableZhipuApiKey(environment);
        boolean canGenerate = localConfigured || zhipuConfigured;
        log.info("查询视频生成通道能力: provider={}, localConfigured={}, localHealthChecked={}, zhipuConfigured={}",
                provider, localConfigured, localHealth.checked(), zhipuConfigured);

        List<Map<String, Object>> capabilityChecks = new ArrayList<>();
        if (usesLocalProvider) {
            capabilityChecks.add(buildVideoModelAvailability(
                    effectiveLocalModel,
                    PROVIDER_LOCAL_FREE,
                    localConfigured,
                    localConfigured && localEndpointConfigured && !localHealth.available()
                            ? "已显式配置 FREE_VIDEO_API_URL，提交后以模型服务返回结果为准"
                            : localHealth.reason()
            ));
        }
        if (usesZhipuProvider(provider)) {
            capabilityChecks.add(buildVideoModelAvailability(
                    videoModel,
                    PROVIDER_ZHIPU,
                    zhipuConfigured,
                    explicitZhipuKey
                            ? "已显式配置 ZHIPU_API_KEY，提交后以云端模型额度和服务状态为准"
                            : zhipuConfigured
                                    ? "已检测到默认云端视频密钥配置，提交后以云端模型额度和服务状态为准"
                                    : "未配置 ZHIPU_API_KEY，云端视频模型默认不视为已接入"
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("canGenerate", canGenerate);
        response.put("status", canGenerate ? "CONFIGURED" : "UNCONFIGURED");
        response.put("provider", provider);
        response.put("model", canGenerate && localConfigured ? effectiveLocalModel : videoModel);
        response.put("modelLabel", DEFAULT_VIDEO_MODEL_LABEL);
        response.put("message", canGenerate ? "视频生成通道已配置" : "视频生成通道未接入");
        response.put("description", canGenerate
                ? "当前已接入可提交的视频生成通道，实际成片仍以本地或云端模型返回为准。"
                : "当前未检测到可用视频模型，页面仅保留 Prompt 和参数草稿，不会直接提交生成。");
        response.put("actionSuggestion", "免费可用方案优先接 Wan2.1-T2V-1.3B：启动 scripts/wan_video_server.py，并可按需配置 FREE_VIDEO_API_URL、FREE_VIDEO_STATUS_URL、FREE_VIDEO_HEALTH_URL、FREE_VIDEO_MODEL；如需云端兜底，再配置 ZHIPU_API_KEY。");
        response.put("capabilityChecks", capabilityChecks);
        response.put("modelAvailability", capabilityChecks);
        return response;
    }

    @Override
    public Map<String, Object> submitVideoTask(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> localVideoResponse = null;
        try {
            String prompt = (String) request.get("prompt");
            String duration = (String) request.get("duration");
            String resolution = (String) request.get("resolution");

            if (prompt == null || prompt.isEmpty()) {
                response.put("success", false);
                response.put("message", "请输入视频描述");
                return response;
            }

            KnowledgeRetrievalResult retrievalResult = retrieveForVideoPrompt(prompt);
            log.info("开始生成视频，提示词: {}, 时长: {}, 分辨率: {}", prompt, duration, resolution);

            String provider = configuredVideoProvider(environment);
            if (usesLocalFreeProvider(provider)) {
                localVideoResponse = submitLocalFreeVideoTask(prompt, request, retrievalResult);
                if (Boolean.TRUE.equals(localVideoResponse.get("success")) || !usesZhipuProvider(provider)) {
                    attachRetrievalMeta(localVideoResponse, retrievalResult);
                    return localVideoResponse;
                }
                log.warn("本地免费视频模型不可用，尝试回退智普视频模型，原因: {}", localVideoResponse.get("message"));
            }

            ZhipuAiClient client = createZhipuClient();
            VideoCreateParams videoRequest = buildVideoRequest(prompt, request, retrievalResult);
            String model = videoRequest.getModel();
            log.info("发送请求到智普AI视频生成API");
            log.info("请求参数: 模型={}, 提示词={}, 质量={}, 带音频={}, 分辨率={}, 帧率={}",
                    videoRequest.getModel(), videoRequest.getPrompt(), videoRequest.getQuality(),
                    videoRequest.getWithAudio(), videoRequest.getSize(), videoRequest.getFps());

            VideosResponse videosResponse = submitZhipuVideoWithRetry(client, videoRequest);
            if (videosResponse == null || !videosResponse.isSuccess() || videosResponse.getData() == null) {
                log.error("智普AI视频生成API提交失败: {}", videosResponse);
                return buildZhipuVideoFailureResponse(
                        model,
                        "视频任务提交失败",
                        zhipuVideoSubmitError(videosResponse),
                        localVideoResponse
                );
            }

            VideoObject videoObject = videosResponse.getData();
            log.info("智普AI视频任务提交成功，任务ID: {}, 状态: {}", videoObject.getId(), videoObject.getTaskStatus());
            response.put("success", true);
            response.put("message", "视频任务已提交");
            response.put("taskId", videoObject.getId());
            response.put("status", normalizeStatus(videoObject.getTaskStatus()));
            response.put("provider", PROVIDER_ZHIPU);
            response.put("model", model);
            response.put("videoModelsTried", mergeVideoModelsTried(localVideoResponse, model));
            response.put("modelAvailability", mergeVideoModelAvailability(
                    localVideoResponse,
                    buildVideoModelAvailability(model, PROVIDER_ZHIPU, true, "")
            ));
            response.put("modelUnavailable", false);
            response.put("retrieval", buildRetrievalMetadata(retrievalResult));
            return response;
        } catch (Exception e) {
            log.error("视频任务提交失败: {}", e.getMessage());
            return buildZhipuVideoFailureResponse(
                    videoModel,
                    "视频任务提交失败",
                    e.getMessage(),
                    localVideoResponse
            );
        }
    }

    @Override
    public Map<String, Object> getVideoTaskStatus(String taskId) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (taskId == null || taskId.isBlank()) {
                response.put("success", false);
                response.put("message", "任务ID不能为空");
                return response;
            }

            String provider = configuredVideoProvider(environment);
            if (usesLocalFreeProvider(provider)) {
                Map<String, Object> localStatus = getLocalFreeVideoTaskStatus(taskId);
                if (Boolean.TRUE.equals(localStatus.get("success")) || !usesZhipuProvider(provider)) {
                    return localStatus;
                }
                log.warn("本地免费视频任务查询不可用，尝试按智普任务查询，taskId={}, 原因: {}", taskId, localStatus.get("message"));
            }

            log.info("查询智普AI视频生成结果，任务ID: {}", taskId);
            VideosResponse videosResponse = createZhipuClient().videos().videoGenerationsResult(taskId);
            if (videosResponse == null || videosResponse.getData() == null) {
                log.error("智普AI视频任务查询失败: {}", videosResponse);
                response.put("success", false);
                response.put("message", "视频任务查询失败");
                response.put("error", videosResponse != null && videosResponse.getError() != null
                        ? videosResponse.getError().getMessage()
                        : "智普AI API未返回任务数据");
                response.put("provider", PROVIDER_ZHIPU);
                response.put("model", videoModel);
                response.put("videoModelsTried", List.of(videoModel));
                response.put("modelAvailability", List.of(buildVideoModelAvailability(
                        videoModel,
                        PROVIDER_ZHIPU,
                        false,
                        videosResponse != null && videosResponse.getError() != null
                                ? videosResponse.getError().getMessage()
                                : "智普AI API未返回任务数据"
                )));
                response.put("modelUnavailable", true);
                return response;
            }

            VideoObject data = videosResponse.getData();
            String status = normalizeStatus(data.getTaskStatus());
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("status", status);
            response.put("provider", PROVIDER_ZHIPU);
            response.put("model", videoModel);
            response.put("videoModelsTried", List.of(videoModel));
            response.put("modelAvailability", List.of(buildVideoModelAvailability(videoModel, PROVIDER_ZHIPU, true, "")));
            response.put("modelUnavailable", false);

            if (data.getVideoResult() != null && !data.getVideoResult().isEmpty()) {
                String videoUrl = data.getVideoResult().get(0).getUrl();
                String coverImageUrl = data.getVideoResult().get(0).getCoverImageUrl();
                String localVideoPath = downloadVideoFromUrl(videoUrl);
                if (localVideoPath == null) {
                    response.put("success", false);
                    response.put("status", "FAILED");
                    response.put("message", "视频下载失败");
                    response.put("error", "视频下载失败，请检查网络连接");
                        response.put("modelAvailability", List.of(buildVideoModelAvailability(videoModel, PROVIDER_ZHIPU, false, "视频下载失败")));
                    response.put("modelUnavailable", true);
                    return response;
                }

                response.put("status", "SUCCESS");
                response.put("message", "视频生成成功");
                response.put("video", localVideoPath);
                response.put("coverImageUrl", coverImageUrl);
                response.put("modelAvailability", List.of(buildVideoModelAvailability(videoModel, PROVIDER_ZHIPU, true, "")));
                log.info("智普AI视频生成成功，任务ID: {}", taskId);
                return response;
            }

            if (isFailedStatus(status)) {
                response.put("success", false);
                response.put("message", "视频生成失败");
                response.put("error", "任务状态: " + status);
                response.put("provider", PROVIDER_ZHIPU);
                response.put("model", videoModel);
                response.put("videoModelsTried", List.of(videoModel));
                response.put("modelAvailability", List.of(buildVideoModelAvailability(videoModel, PROVIDER_ZHIPU, false, "任务状态: " + status)));
                response.put("modelUnavailable", true);
                return response;
            }

            response.put("message", "视频生成中，请稍后查询");
        } catch (Exception e) {
            log.error("查询视频任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "视频任务查询失败");
            response.put("error", e.getMessage());
            response.put("provider", PROVIDER_ZHIPU);
            response.put("model", videoModel);
            response.put("videoModelsTried", List.of(videoModel));
            response.put("modelAvailability", List.of(buildVideoModelAvailability(videoModel, PROVIDER_ZHIPU, false, String.valueOf(e.getMessage()))));
            response.put("modelUnavailable", true);
        }
        return response;
    }

    private Map<String, Object> submitLocalFreeVideoTask(String prompt, Map<String, Object> request, KnowledgeRetrievalResult retrievalResult) {
        String apiUrl = configuredFreeVideoApiUrl(environment);
        String model = configuredFreeVideoModel(environment);
        Map<String, Object> requestBody = buildFreeVideoRequest(prompt, request, model, retrievalResult);
        log.info("发送请求到本地免费视频模型 API: model={}, url={}", model, apiUrl);
        try {
            Map<String, Object> responseBody = restTemplate.postForObject(apiUrl, requestBody, Map.class);
            Map<String, Object> response = normalizeLocalVideoResponse(responseBody, model, "视频任务已提交", "本地免费视频模型未返回任务或视频");
            attachRetrievalMeta(response, retrievalResult);
            return response;
        } catch (RestClientResponseException e) {
            String error = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.warn("本地免费视频模型提交失败，模型: {}, 状态: {}, 原因: {}", model, e.getStatusCode(), error);
            return localVideoFailure("本地免费视频模型不可用", error.isBlank() ? e.getMessage() : error, model);
        } catch (Exception e) {
            log.warn("本地免费视频模型提交失败，模型: {}, 原因: {}", model, e.getMessage());
            return localVideoFailure("本地免费视频模型不可用", e.getMessage(), model);
        }
    }

    private Map<String, Object> getLocalFreeVideoTaskStatus(String taskId) {
        String model = configuredFreeVideoModel(environment);
        String statusUrl = configuredFreeVideoStatusUrl(environment, taskId);
        log.info("查询本地免费视频模型任务状态: model={}, taskId={}", model, taskId);
        try {
            Map<String, Object> responseBody = restTemplate.getForObject(statusUrl, Map.class);
            return normalizeLocalVideoResponse(responseBody, model, "视频生成中，请稍后查询", "本地免费视频模型未返回任务状态");
        } catch (RestClientResponseException e) {
            String error = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.warn("查询本地免费视频模型任务失败，模型: {}, 状态: {}, 原因: {}", model, e.getStatusCode(), error);
            return localVideoFailure("视频任务查询失败", error.isBlank() ? e.getMessage() : error, model);
        } catch (Exception e) {
            log.warn("查询本地免费视频模型任务失败，模型: {}, 原因: {}", model, e.getMessage());
            return localVideoFailure("视频任务查询失败", e.getMessage(), model);
        }
    }

    static Map<String, Object> buildFreeVideoRequest(String prompt, Map<String, Object> request, String model) {
        return buildFreeVideoRequest(prompt, request, model, null);
    }

    static Map<String, Object> buildFreeVideoRequest(
            String prompt,
            Map<String, Object> request,
            String model,
            KnowledgeRetrievalResult retrievalResult) {
        String requestedResolution = request.get("resolution") instanceof String ? (String) request.get("resolution") : DEFAULT_FREE_VIDEO_RESOLUTION;
        String resolution = normalizeFreeVideoResolution(model, requestedResolution);
        String duration = request.get("duration") instanceof String ? (String) request.get("duration") : "5s";
        String mode = request.get("mode") instanceof String ? (String) request.get("mode") : "quality";
        boolean withAudio = request.get("withAudio") instanceof Boolean && (Boolean) request.get("withAudio");
        int fps = parseFps(request.get("fps"));
        String basePrompt = buildVideoPrompt(prompt, duration, resolution, mode, withAudio);
        String enhancedPrompt = appendKnowledgeContext(basePrompt, retrievalResult);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", enhancedPrompt);
        requestBody.put("duration", duration);
        requestBody.put("resolution", resolution);
        requestBody.put("size", normalizeFreeVideoSize(model, resolution));
        requestBody.put("mode", mode);
        requestBody.put("fps", fps);
        requestBody.put("withAudio", withAudio);
        return requestBody;
    }

    private Map<String, Object> normalizeLocalVideoResponse(
            Map<String, Object> responseBody,
            String model,
            String defaultMessage,
            String invalidResponseMessage
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("provider", PROVIDER_LOCAL_FREE);
        response.put("model", model);
        response.put("videoModelsTried", List.of(model));
        response.put("modelAvailability", List.of(buildVideoModelAvailability(model, PROVIDER_LOCAL_FREE, true, "")));
        response.put("modelUnavailable", false);

        if (responseBody == null || responseBody.isEmpty()) {
            response.put("success", false);
            response.put("message", invalidResponseMessage);
            response.put("description", freeVideoUnavailableDescription());
            response.put("actionSuggestion", freeVideoActionSuggestion());
            response.put("modelAvailability", List.of(buildVideoModelAvailability(model, PROVIDER_LOCAL_FREE, false, invalidResponseMessage)));
            response.put("modelUnavailable", true);
            return response;
        }

        Object success = responseBody.get("success");
        if (success instanceof Boolean && !((Boolean) success)) {
            response.put("success", false);
            response.put("message", stringValue(responseBody.get("message")).orElse("视频生成失败"));
            response.put("error", stringValue(responseBody.get("error")).orElse(""));
            response.put("description", freeVideoUnavailableDescription());
            response.put("actionSuggestion", freeVideoActionSuggestion());
            response.put("modelAvailability", List.of(buildVideoModelAvailability(model, PROVIDER_LOCAL_FREE, false, stringValue(responseBody.get("error")).orElse("视频生成失败"))));
            response.put("modelUnavailable", true);
            return response;
        }

        Optional<String> video = firstString(responseBody, "video", "videoUrl", "url")
                .map(this::materializeVideoReference)
                .filter(value -> value != null && !value.isBlank());
        if (video.isPresent()) {
            response.put("success", true);
            response.put("status", "SUCCESS");
            response.put("message", "视频生成成功");
            response.put("video", video.get());
            response.put("modelAvailability", List.of(buildVideoModelAvailability(model, PROVIDER_LOCAL_FREE, true, "")));
            response.put("modelUnavailable", false);
            return response;
        }

        Optional<String> taskId = firstString(responseBody, "taskId", "id");
        if (taskId.isPresent()) {
            response.put("success", true);
            response.put("message", defaultMessage);
            response.put("taskId", taskId.get());
            response.put("status", firstString(responseBody, "status", "taskStatus").orElse("PROCESSING"));
            return response;
        }

        response.put("success", false);
        response.put("message", invalidResponseMessage);
        response.put("error", responseBody.toString());
        response.put("description", freeVideoUnavailableDescription());
        response.put("actionSuggestion", freeVideoActionSuggestion());
        response.put("modelAvailability", List.of(buildVideoModelAvailability(model, PROVIDER_LOCAL_FREE, false, responseBody.toString())));
        response.put("modelUnavailable", true);
        return response;
    }

    private static String appendKnowledgeContext(String prompt, KnowledgeRetrievalResult retrievalResult) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        if (retrievalResult == null || !retrievalResult.used() || retrievalResult.matches().isEmpty()) {
            return safePrompt;
        }
        return safePrompt + "\n\n" + KnowledgeRetrievalService.formatContextForPrompt(retrievalResult, 2, 120);
    }

    private static Map<String, Object> buildRetrievalMetadata(KnowledgeRetrievalResult retrievalResult) {
        Map<String, Object> retrieval = new LinkedHashMap<>();
        retrieval.put("used", retrievalResult != null && retrievalResult.used());
        retrieval.put("scene", retrievalResult == null ? "" : retrievalResult.scene());
        retrieval.put("query", retrievalResult == null ? "" : retrievalResult.query());
        retrieval.put("gateReason", retrievalResult == null ? "检索结果缺失" : retrievalResult.gateReason());
        retrieval.put("trace", retrievalResult == null ? List.of("result unavailable") : retrievalResult.trace());
        return retrieval;
    }

    private static void attachRetrievalMeta(Map<String, Object> response, KnowledgeRetrievalResult retrievalResult) {
        response.put("retrieval", buildRetrievalMetadata(retrievalResult));
    }

    private KnowledgeRetrievalResult retrieveForVideoPrompt(String prompt) {
        if (knowledgeRetrievalService == null) {
            return new KnowledgeRetrievalResult("video-generation", prompt == null ? "" : prompt, VIDEO_RAG_TOP_K, false, "统一检索服务未接入", List.of(), List.of("service unavailable"));
        }
        return knowledgeRetrievalService.retrieve("video-generation", prompt, VIDEO_RAG_TOP_K);
    }

    private String materializeVideoReference(String videoReference) {
        if (videoReference.startsWith("http://") || videoReference.startsWith("https://")) {
            return downloadVideoFromUrl(videoReference);
        }
        return videoReference;
    }

    private Map<String, Object> localVideoFailure(String message, String error, String model) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", error == null ? "" : error);
        response.put("provider", PROVIDER_LOCAL_FREE);
        response.put("model", model);
        response.put("description", freeVideoUnavailableDescription());
        response.put("actionSuggestion", freeVideoActionSuggestion());
        response.put("videoModelsTried", List.of(model));
        response.put("modelAvailability", List.of(buildVideoModelAvailability(model, PROVIDER_LOCAL_FREE, false, error == null ? "" : error)));
        response.put("modelUnavailable", true);
        return response;
    }

    private VideosResponse submitZhipuVideoWithRetry(ZhipuAiClient client, VideoCreateParams videoRequest) throws Exception {
        VideosResponse lastResponse = null;
        for (int attempt = 1; attempt <= ZHIPU_VIDEO_SUBMIT_MAX_ATTEMPTS; attempt++) {
            try {
                log.info("第{}次提交智普视频任务 ({}/{})", attempt, attempt, ZHIPU_VIDEO_SUBMIT_MAX_ATTEMPTS);
                lastResponse = client.videos().videoGenerations(videoRequest);
                if (!shouldRetryVideoSubmitResponse(lastResponse) || attempt == ZHIPU_VIDEO_SUBMIT_MAX_ATTEMPTS) {
                    return lastResponse;
                }
                log.warn("智普视频任务提交暂时不可用，{}秒后重试，原因: {}",
                        ZHIPU_VIDEO_SUBMIT_RETRY_DELAY_SECONDS,
                        zhipuVideoSubmitError(lastResponse));
            } catch (Exception e) {
                if (!isTransientVideoSubmitFailure(e.getMessage()) || attempt == ZHIPU_VIDEO_SUBMIT_MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("智普视频任务提交异常，{}秒后重试，原因: {}",
                        ZHIPU_VIDEO_SUBMIT_RETRY_DELAY_SECONDS,
                        e.getMessage());
            }
            TimeUnit.SECONDS.sleep(ZHIPU_VIDEO_SUBMIT_RETRY_DELAY_SECONDS);
        }
        return lastResponse;
    }

    private static boolean shouldRetryVideoSubmitResponse(VideosResponse response) {
        return response != null
                && (response.getData() == null || !response.isSuccess())
                && isTransientVideoSubmitFailure(zhipuVideoSubmitError(response));
    }

    static boolean isTransientVideoSubmitFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("429")
                || normalized.contains("too many request")
                || normalized.contains("temporarily")
                || normalized.contains("overloaded")
                || normalized.contains("try again later")
                || normalized.contains("timeout");
    }

    static Map<String, Object> buildZhipuVideoFailureResponse(
            String model,
            String message,
            String error,
            Map<String, Object> previousFailure
    ) {
        String normalizedModel = model == null || model.isBlank() ? DEFAULT_VIDEO_MODEL : model.trim();
        String normalizedError = error == null || error.isBlank()
                ? "智普AI API调用失败，请检查网络连接或稍后再试"
                : error.trim();

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message == null || message.isBlank() ? "视频任务提交失败" : message);
        response.put("error", normalizedError);
        response.put("provider", PROVIDER_ZHIPU);
        response.put("model", normalizedModel);
        response.put("description", zhipuVideoUnavailableDescription(previousFailure));
        response.put("actionSuggestion", zhipuVideoActionSuggestion(previousFailure, normalizedError));
        response.put("videoModelsTried", mergeVideoModelsTried(previousFailure, normalizedModel));
        response.put("modelAvailability", mergeVideoModelAvailability(
                previousFailure,
                buildVideoModelAvailability(normalizedModel, PROVIDER_ZHIPU, false, normalizedError)
        ));
        response.put("modelUnavailable", true);
        return response;
    }

    private static String zhipuVideoSubmitError(VideosResponse videosResponse) {
        if (videosResponse == null) {
            return "智普AI API调用失败，请检查网络连接或稍后再试";
        }
        if (videosResponse.getError() != null && videosResponse.getError().getMessage() != null
                && !videosResponse.getError().getMessage().isBlank()) {
            return videosResponse.getError().getMessage();
        }
        if (!videosResponse.isSuccess()) {
            return "智普AI API提交失败";
        }
        if (videosResponse.getData() == null) {
            return "提交接口未返回可用任务";
        }
        return "";
    }

    private static String zhipuVideoUnavailableDescription(Map<String, Object> previousFailure) {
        if (hasPreviousVideoFailure(previousFailure)) {
            return "本地免费视频模型服务不可用，云端智谱视频模型也未返回可用任务，当前没有可播放视频结果。";
        }
        return "云端智谱视频模型未返回可用任务，当前没有可播放视频结果。";
    }

    private static String zhipuVideoActionSuggestion(Map<String, Object> previousFailure, String error) {
        String cloudSuggestion = isTransientVideoSubmitFailure(error)
                ? "云端视频模型当前可能过载或被限流，请稍后重试，或降低时长、分辨率后再提交。"
                : "请确认智谱视频模型资源、服务状态和密钥额度正常。";
        if (hasPreviousVideoFailure(previousFailure)) {
            return "请先启动本地文生视频服务并配置 FREE_VIDEO_API_URL、FREE_VIDEO_STATUS_URL、FREE_VIDEO_MODEL；"
                    + "也可以设置 MEDIA_VIDEO_PROVIDER=zhipu 只走云端，或设置 MEDIA_VIDEO_PROVIDER=local-free 只排查本地。"
                    + cloudSuggestion;
        }
        return cloudSuggestion + "如需改走本地免费通道，可设置 MEDIA_VIDEO_PROVIDER=local-free 并启动兼容的视频生成服务。";
    }

    private static boolean hasPreviousVideoFailure(Map<String, Object> previousFailure) {
        if (previousFailure == null || previousFailure.isEmpty()) {
            return false;
        }
        Object availability = previousFailure.get("modelAvailability");
        return availability instanceof List<?> && !((List<?>) availability).isEmpty();
    }

    private static List<String> mergeVideoModelsTried(Map<String, Object> previousFailure, String model) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (previousFailure != null) {
            Object previousModels = previousFailure.get("videoModelsTried");
            if (previousModels instanceof List<?> list) {
                for (Object item : list) {
                    stringValue(item).ifPresent(models::add);
                }
            }
        }
        stringValue(model).ifPresent(models::add);
        return new ArrayList<>(models);
    }

    private static List<Map<String, Object>> mergeVideoModelAvailability(
            Map<String, Object> previousFailure,
            Map<String, Object> currentAvailability
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (previousFailure != null) {
            Object previousAvailability = previousFailure.get("modelAvailability");
            if (previousAvailability instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> entry) {
                        Map<String, Object> copied = new LinkedHashMap<>();
                        entry.forEach((key, value) -> {
                            if (key instanceof String textKey) {
                                copied.put(textKey, value);
                            }
                        });
                        if (!copied.isEmpty()) {
                            result.add(copied);
                        }
                    }
                }
            }
        }
        result.add(currentAvailability);
        return result;
    }

    private static Map<String, Object> buildVideoModelAvailability(String model, String provider, boolean available, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("model", model == null ? "" : model);
        entry.put("provider", provider == null ? "" : provider);
        entry.put("available", available);
        if (reason != null && !reason.isBlank()) {
            entry.put("reason", reason);
        }
        return entry;
    }

    private static Optional<String> firstString(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Optional<String> value = stringValue(values.get(key));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> stringValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text.trim());
        }
        return Optional.empty();
    }

    /**
     * 调用智普AI视频生成API
     * @param prompt 提示词
     * @param duration 时长
     * @param resolution 分辨率
     * @return 视频URL
     */
    private String callZhipuVideoAPI(String prompt, String duration, String resolution) {
        try {
            ZhipuAiClient client = createZhipuClient();

            // 构建视频创建请求参数
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("prompt", prompt);
            requestMap.put("duration", duration);
            requestMap.put("resolution", resolution);
            VideoCreateParams request = buildVideoRequest(prompt, requestMap);

            log.info("发送请求到智普AI视频生成API");
            log.info("请求参数: 模型={}, 提示词={}, 质量={}, 带音频={}, 分辨率={}, 帧率={}",
                    request.getModel(), request.getPrompt(), request.getQuality(),
                    request.getWithAudio(), request.getSize(), request.getFps());

            // 发送请求，添加重试机制处理429错误
            VideosResponse response = null;
            int maxApiRetries = 3; // API调用最多重试3次
            int apiRetryCount = 0;
            int baseDelay = 30000; // 基础延迟30秒

            while (apiRetryCount < maxApiRetries) {
                try {
                    log.info("第{}次尝试调用智普AI视频生成API ({}/{})...", apiRetryCount + 1, apiRetryCount + 1, maxApiRetries);
                    log.info("请求模型: {}", request.getModel());
                    log.info("请求提示词: {}", request.getPrompt());
                    log.info("请求质量: {}", request.getQuality());
                    log.info("请求带音频: {}", request.getWithAudio());
                    log.info("请求分辨率: {}", request.getSize());
                    log.info("请求帧率: {}", request.getFps());

                    response = client.videos().videoGenerations(request);
                    log.info("智普AI视频生成API调用成功");

                    // 检查响应
                    if (response != null) {
                        log.info("响应对象不为空");
                        log.info("响应数据: {}", response.getData());
                    } else {
                        log.error("响应对象为空");
                    }

                    break; // 成功获取响应，退出重试循环
                } catch (ai.z.openapi.service.model.ZAiHttpException e) {
                    log.error("智普AI API错误: {}", e.getMessage());
                    if (e.getMessage().contains("429") || e.getMessage().contains("overloaded") || e.getMessage().contains("temporarily") || e.getMessage().contains("service") || e.getMessage().contains("timeout")) {
                        // 速率限制或服务暂时不可用错误，进行重试
                        apiRetryCount++;
                        int delay = baseDelay * apiRetryCount; // 指数退避
                        log.warn("智普AI API速率限制或服务暂时不可用，{}秒后重试 ({}/{})...", delay/1000, apiRetryCount, maxApiRetries);
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } else {
                        // 其他错误，直接抛出
                        log.error("智普AI API其他错误，不再重试: {}", e.getMessage());
                        throw e;
                    }
                } catch (Exception e) {
                    // 其他异常，进行重试
                    apiRetryCount++;
                    int delay = baseDelay * apiRetryCount;
                    log.error("智普AI API调用异常: {}", e.getMessage());
                    e.printStackTrace();
                    log.warn("{}秒后重试 ({}/{})...", delay/1000, apiRetryCount, maxApiRetries);
                    TimeUnit.MILLISECONDS.sleep(delay);
                }
            }

            // 检查响应是否为空
            if (response == null) {
                log.error("智普AI视频生成API响应为空");
                return null;
            } else if (response.getData() == null) {
                log.error("智普AI视频生成API响应数据为空");
                log.info("响应对象: {}", response);
                return null;
            }

            log.info("智普AI视频生成API响应状态: 成功");
            log.info("视频生成任务ID: {}", response.getData().getId());

            // 获取任务ID
            String taskId = response.getData().getId();

            // 轮询视频生成结果（处理延迟）
            int maxRetries = 30; // 最多轮询30次
            int retryCount = 0;
            while (retryCount < maxRetries) {
                try {
                    TimeUnit.SECONDS.sleep(POLL_INTERVAL_SECONDS);
                    retryCount++;

                    log.info("第{}次轮询视频生成结果，任务ID: {}", retryCount, taskId);
                    VideosResponse videosResponse = client.videos().videoGenerationsResult(taskId);

                    // 检查视频是否生成完成
                    if (videosResponse != null && videosResponse.getData() != null &&
                            videosResponse.getData().getVideoResult() != null &&
                            !videosResponse.getData().getVideoResult().isEmpty()) {
                        // 获取视频URL
                        String videoUrl = videosResponse.getData().getVideoResult().get(0).getUrl();
                        String coverImageUrl = videosResponse.getData().getVideoResult().get(0).getCoverImageUrl();

                        log.info("视频生成成功！");
                        log.info("视频URL: {}", videoUrl);
                        log.info("封面图片URL: {}", coverImageUrl);

                        return videoUrl;
                    } else {
                        log.info("视频生成中，请等待...");
                    }
                } catch (Exception e) {
                    log.error("轮询视频生成结果失败: {}", e.getMessage());
                }
            }

            log.error("视频生成超时，已达到最大轮询次数");
        } catch (Exception zhipuError) {
            log.error("智普AI视频生成API调用失败: {}", zhipuError.getMessage());
            // 打印详细错误信息
            zhipuError.printStackTrace();
        }

        return null;
    }

    private ZhipuAiClient createZhipuClient() {
        return ZhipuAiClient.builder().ofZHIPU()
                .apiKey(configuredZhipuApiKey(environment))
                .build();
    }

    private VideoCreateParams buildVideoRequest(String prompt, Map<String, Object> request) {
        return buildVideoRequest(prompt, request, null);
    }

    private VideoCreateParams buildVideoRequest(String prompt, Map<String, Object> request, KnowledgeRetrievalResult retrievalResult) {
        String resolution = request.get("resolution") instanceof String ? (String) request.get("resolution") : "1920x1080";
        String duration = request.get("duration") instanceof String ? (String) request.get("duration") : "5s";
        String mode = request.get("mode") instanceof String ? (String) request.get("mode") : "quality";
        boolean withAudio = request.get("withAudio") instanceof Boolean && (Boolean) request.get("withAudio");
        int fps = parseFps(request.get("fps"));
        String basePrompt = buildVideoPrompt(prompt, duration, resolution, mode, withAudio);
        String enhancedPrompt = appendKnowledgeContext(basePrompt, retrievalResult);

        return VideoCreateParams.builder()
                .model(videoModel)
                .prompt(enhancedPrompt)
                .quality(mode)
                .withAudio(withAudio)
                .size(resolution)
                .fps(fps)
                .build();
    }

    private static String buildVideoPrompt(String prompt, String duration, String resolution, String mode, boolean withAudio) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        String durationText = normalizeDurationText(duration);
        String aspectText = isWide16By9Resolution(resolution) ? "横向 16:9" : "横向视频";
        String modeText = "speed".equals(mode) ? "节奏清晰，动作简单稳定" : "质量优先，细节丰富，运动自然";
        String audioText = withAudio ? "可加入自然环境音效，与画面节奏一致" : "无音频要求，画面表达完整";

        return String.join("；",
                safePrompt,
                durationText + "短视频",
                aspectText + "构图，主体始终清晰可辨",
                "开场建立场景，中段呈现主体动作，结尾自然收束",
                "镜头运动平滑，可使用轻微推近、横移或跟随镜头，避免突然跳切",
                "主体动作连续，物体比例稳定，人物或动物不要畸形变形",
                "电影感光影，真实景深，色彩协调，细节清楚",
                modeText,
                audioText,
                "不要字幕、不要水印、不要Logo、不要文字贴片、不要闪烁和低清晰度"
        );
    }

    private static String normalizeDurationText(String duration) {
        if (duration == null || duration.isBlank()) {
            return "5秒";
        }
        return duration.trim().replace("s", "秒");
    }

    private static int parseFps(Object value) {
        if (value instanceof Number number) {
            return normalizeFps(number.intValue());
        }
        if (value instanceof String text) {
            try {
                return normalizeFps(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 30;
            }
        }
        return 30;
    }

    private static int normalizeFps(int fps) {
        return fps == 60 ? 60 : 30;
    }

    static String configuredVideoProvider(Map<String, String> environment) {
        return configuredValue(environment, "MEDIA_VIDEO_PROVIDER", PROVIDER_HYBRID).toLowerCase();
    }

    static String configuredFreeVideoApiUrl(Map<String, String> environment) {
        return configuredValue(environment, "FREE_VIDEO_API_URL", DEFAULT_FREE_VIDEO_API_URL);
    }

    static String configuredFreeVideoHealthUrl(Map<String, String> environment) {
        String configured = configuredValue(environment, "FREE_VIDEO_HEALTH_URL", "");
        if (!configured.isBlank()) {
            return configured;
        }
        return trimTrailingSlash(configuredFreeVideoApiUrl(environment)) + "/health";
    }

    static String configuredFreeVideoModel(Map<String, String> environment) {
        return configuredValue(environment, "FREE_VIDEO_MODEL", DEFAULT_FREE_VIDEO_MODEL);
    }

    static String configuredFreeVideoStatusUrl(Map<String, String> environment, String taskId) {
        String configured = configuredValue(environment, "FREE_VIDEO_STATUS_URL", "");
        String template = configured.isBlank() ? configuredFreeVideoApiUrl(environment) + "/status/{taskId}" : configured;
        if (template.contains("{taskId}")) {
            return template.replace("{taskId}", taskId);
        }
        return template.endsWith("/") ? template + taskId : template + "/" + taskId;
    }

    static String configuredZhipuApiKey(Map<String, String> environment) {
        return configuredValue(environment, "ZHIPU_API_KEY", DEFAULT_ZHIPU_API_KEY);
    }

    private static boolean hasUsableZhipuApiKey(Map<String, String> environment) {
        return !configuredZhipuApiKey(environment).isBlank();
    }

    static String freeVideoUnavailableDescription() {
        return "当前默认使用本地免费文生视频模型，但本地 Wan/LTX 兼容服务不可用或未返回可用任务。";
    }

    static String freeVideoActionSuggestion() {
        return "请启动 scripts/wan_video_server.py 接入 Wan2.1-T2V-1.3B，本地服务默认监听 http://localhost:7861/api/text-to-video；如需自定义地址，可配置 FREE_VIDEO_API_URL、FREE_VIDEO_STATUS_URL、FREE_VIDEO_HEALTH_URL、FREE_VIDEO_MODEL；如需临时回退智谱，可设置 MEDIA_VIDEO_PROVIDER=zhipu。";
    }

    private static boolean usesLocalFreeProvider(String provider) {
        return PROVIDER_LOCAL_FREE.equals(provider) || PROVIDER_HYBRID.equals(provider);
    }

    private static boolean usesZhipuProvider(String provider) {
        return PROVIDER_ZHIPU.equals(provider) || PROVIDER_HYBRID.equals(provider);
    }

    private static String configuredValue(Map<String, String> environment, String key, String defaultValue) {
        return Optional.ofNullable(environment.get(key))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    private static boolean hasConfiguredValue(Map<String, String> environment, String key) {
        return Optional.ofNullable(environment.get(key))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .isPresent();
    }

    private LocalVideoHealth probeLocalFreeVideoHealth(String provider, String configuredModel) {
        if (!usesLocalFreeProvider(provider)) {
            return new LocalVideoHealth(false, configuredModel, "当前 provider 不检查本地免费模型", false);
        }

        String healthUrl = configuredFreeVideoHealthUrl(environment);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = restTemplate.getForObject(healthUrl, Map.class);
            if (responseBody == null || responseBody.isEmpty()) {
                return new LocalVideoHealth(false, configuredModel, "Wan2.1 本地健康接口未返回状态", true);
            }

            boolean available = firstBoolean(responseBody, "configured", "canGenerate", "available", "ready")
                    .orElseGet(() -> firstBoolean(responseBody, "success").orElse(false));
            String model = firstString(responseBody, "model", "modelName").orElse(configuredModel);
            String reason = firstString(responseBody, "message", "reason")
                    .orElse(available ? "Wan2.1 本地服务健康检查通过" : "Wan2.1 本地服务尚未就绪");
            return new LocalVideoHealth(available, model, reason, true);
        } catch (Exception e) {
            log.debug("Wan2.1 本地健康检查失败: url={}, reason={}", healthUrl, e.getMessage());
            return new LocalVideoHealth(false, configuredModel,
                    "未检测到默认 Wan2.1 本地服务，请先启动 scripts/wan_video_server.py", true);
        }
    }

    private static String normalizeFreeVideoResolution(String model, String requestedResolution) {
        if (isWanVideoModel(model)) {
            return DEFAULT_FREE_VIDEO_RESOLUTION;
        }
        return requestedResolution == null || requestedResolution.isBlank() ? DEFAULT_FREE_VIDEO_RESOLUTION : requestedResolution.trim();
    }

    private static String normalizeFreeVideoSize(String model, String resolution) {
        if (isWanVideoModel(model)) {
            return DEFAULT_WAN_VIDEO_SIZE;
        }
        return resolution == null || resolution.isBlank() ? DEFAULT_FREE_VIDEO_RESOLUTION : resolution.trim();
    }

    private static boolean isWanVideoModel(String model) {
        return model != null && model.toLowerCase().contains("wan");
    }

    private static boolean isWide16By9Resolution(String resolution) {
        if (resolution == null || resolution.isBlank()) {
            return false;
        }
        String[] parts = resolution.toLowerCase().replace("*", "x").split("x");
        if (parts.length != 2) {
            return false;
        }
        try {
            double width = Double.parseDouble(parts[0].trim());
            double height = Double.parseDouble(parts[1].trim());
            if (width <= 0 || height <= 0) {
                return false;
            }
            return Math.abs((width / height) - (16.0 / 9.0)) < 0.08;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static Optional<Boolean> firstBoolean(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Optional<Boolean> value = booleanValue(values.get(key));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return Optional.of(flag);
        }
        if (value instanceof String text && !text.isBlank()) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized) || "yes".equals(normalized) || "ready".equals(normalized)) {
                return Optional.of(true);
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "unready".equals(normalized)) {
                return Optional.of(false);
            }
        }
        return Optional.empty();
    }

    private record LocalVideoHealth(boolean available, String model, String reason, boolean checked) {
    }

    private String normalizeConfiguredModel(String configuredModel) {
        return configuredModel == null || configuredModel.isBlank() ? DEFAULT_VIDEO_MODEL : configuredModel.trim();
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "PROCESSING" : status;
    }

    private boolean isFailedStatus(String status) {
        return status != null && status.toUpperCase().contains("FAIL");
    }

    /**
     * 下载视频并保存到本地
     * @param videoUrl 视频URL
     * @return 本地视频路径
     */
    private String downloadVideoFromUrl(String videoUrl) {
        try {
            // 生成唯一的文件名
            String fileName = UUID.randomUUID().toString() + ".mp4";
            Path filePath = Paths.get(UPLOAD_DIR, fileName);

            log.info("开始下载视频，URL: {}", videoUrl);
            log.info("保存路径: {}", filePath.toAbsolutePath());

            // 确保上传目录存在
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("创建上传目录: {}", uploadPath.toAbsolutePath());
            }

            // 下载内容
            URI uri = new URI(videoUrl);
            try (InputStream inputStream = uri.toURL().openStream()) {
                // 读取内容
                byte[] content = inputStream.readAllBytes();
                log.info("下载内容大小: {} bytes", content.length);

                // 保存视频文件
                Files.write(filePath, content);
                log.info("视频保存成功: {}", filePath.toAbsolutePath());
            }

            // 检查文件是否存在
            if (Files.exists(filePath)) {
                log.info("文件存在，大小: {} bytes", Files.size(filePath));
                // 返回相对路径
                String localVideoPath = "/api/image/download/" + fileName;
                log.info("返回本地视频路径: {}", localVideoPath);
                return localVideoPath;
            } else {
                log.error("文件不存在: {}", filePath.toAbsolutePath());
                return null;
            }
        } catch (Exception e) {
            log.error("下载视频失败: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


}
