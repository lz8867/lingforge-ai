package com.example.demo.service.impl;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.image.CreateImageRequest;
import ai.z.openapi.service.image.Image;
import ai.z.openapi.service.image.ImageResponse;
import ai.z.openapi.service.image.ImageResult;
import com.example.demo.service.KnowledgeRetrievalResult;
import com.example.demo.service.KnowledgeRetrievalService;
import com.example.demo.service.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImageServiceImpl implements ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageServiceImpl.class);
    private static final String UPLOAD_DIR = "images";
    private static final String DEFAULT_ZHIPU_API_KEY = "0f719fd7c596481c8a294161afcf5f0b.YDgi5D3rJajOy4C2";
    private static final String DEFAULT_IMAGE_MODELS = "cogview-4-250304,cogview-3-flash";
    private static final String DEFAULT_VISION_MODELS = "glm-4v-flash,glm-4v";
    private static final String DEFAULT_FREE_IMAGE_API_URL = "http://localhost:7860/sdapi/v1/txt2img";
    private static final String DEFAULT_FREE_IMAGE_MODEL = "flux.1-schnell";
    private static final String DEFAULT_PUBLIC_IMAGE_MODEL = "flux";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_VISION_MODEL = "qwen2.5vl:3b";
    private static final String DEFAULT_OLLAMA_VISION_MODELS = "qwen2.5vl:7b,qwen2.5vl:3b";
    private static final int MIN_VISION_IMAGE_SIDE = 32;
    private static final String PROVIDER_LOCAL_FREE = "local-free";
    private static final String PROVIDER_PUBLIC_FREE = "public-free";
    private static final String PROVIDER_ZHIPU = "zhipu";
    private static final String PROVIDER_HYBRID = "hybrid";

    private final RestTemplate restTemplate;
    private final Map<String, String> environment;
    private KnowledgeRetrievalService knowledgeRetrievalService;
    private static final int IMAGE_RAG_TOP_K = 3;

    public ImageServiceImpl() {
        this(new RestTemplate(), System.getenv(), null);
    }

    ImageServiceImpl(RestTemplate restTemplate) {
        this(restTemplate, System.getenv(), null);
    }

    ImageServiceImpl(RestTemplate restTemplate, Map<String, String> environment) {
        this(restTemplate, environment, null);
    }

    ImageServiceImpl(RestTemplate restTemplate, Map<String, String> environment, KnowledgeRetrievalService knowledgeRetrievalService) {
        this.restTemplate = restTemplate;
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        // 创建上传目录（如果不存在）
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", e.getMessage());
        }
    }

    @Autowired
    void setKnowledgeRetrievalService(KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    @Override
    public Map<String, Object> generateImage(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        java.util.List<Map<String, Object>> modelAvailability = new java.util.ArrayList<>();

        try {
            String prompt = (String) request.get("prompt");
            String size = (String) request.get("size");
            String count = (String) request.get("count");
            String style = (String) request.get("style");

            if (prompt == null || prompt.isEmpty()) {
                response.put("success", false);
                response.put("message", "请输入图片描述");
                return response;
            }

            // 确定图片数量
            int imageCount = 1;
            if (count != null && (count.equals("2") || count.equals("4"))) {
                imageCount = Integer.parseInt(count);
            }

            KnowledgeRetrievalResult retrievalResult = retrieveForImagePrompt(prompt);
            String retrievalContext = retrievalResult != null && retrievalResult.used()
                    ? KnowledgeRetrievalService.formatContextForPrompt(retrievalResult, IMAGE_RAG_TOP_K, 140)
                    : "";
            log.info("开始生成图片，提示词: {}, 大小: {}, 数量: {}, 风格: {}", prompt, size, imageCount, style);
            try {
                String detailedPrompt = buildImagePrompt(prompt, style, size);
                String detailedPromptWithContext = appendKnowledgeContext(detailedPrompt, retrievalContext);
                String imageSize = normalizeImageSize(size);
                List<String> attemptedModels = new ArrayList<>();
                String lastImageError = "";
                String provider = configuredImageProvider(environment);

                if (usesLocalFreeProvider(provider)) {
                    ImageGenerationResult freeResult = callFreeImageAPI(detailedPromptWithContext, imageSize, imageCount);
                    attemptedModels.add(freeResult.model());
                    modelAvailability.add(buildModelAvailability(freeResult.model(), PROVIDER_LOCAL_FREE, freeResult.success(), freeResult.error()));
                    if (freeResult.success()) {
                        clearImageFailureFields(response);
                        response.put("success", true);
                        response.put("message", "图片生成成功");
                        response.put("image", freeResult.images().get(0));
                        response.put("images", freeResult.images());
                        response.put("enhancedPrompt", detailedPrompt);
                        response.put("provider", PROVIDER_LOCAL_FREE);
                        response.put("imageModel", freeResult.model());
                        response.put("imageModelsTried", attemptedModels);
                        response.put("modelAvailability", modelAvailability);
                        response.put("retrieval", buildRetrievalMetadata(retrievalResult));
                        log.info("本地免费图片模型生成成功，模型: {}, 数量: {}", freeResult.model(), freeResult.images().size());
                        return response;
                    }
                    lastImageError = freeResult.error();
                    response.put("modelUnavailable", true);
                    response.put("message", freeResult.message());
                    response.put("description", freeResult.description());
                    response.put("actionSuggestion", freeResult.actionSuggestion());
                    response.put("provider", PROVIDER_LOCAL_FREE);
                    response.put("imageModel", freeResult.model());
                    response.put("imageModelsTried", attemptedModels);
                    response.put("error", lastImageError);
                    response.put("modelAvailability", modelAvailability);
                    if (!usesZhipuProvider(provider) && !usesPublicFreeProvider(provider)) {
                        response.put("modelUnavailable", true);
                        response.put("success", false);
                        return response;
                    }
                }

                if (usesZhipuProvider(provider)) {
                    response.put("provider", PROVIDER_ZHIPU);
                    // 显式配置为 zhipu 或 hybrid 时才尝试付费/额度模型。
                    try {
                        log.info("使用智普 AI 文生图 API 生成图片，数量: {}", imageCount);
                        ZhipuAiClient client = ZhipuAiClient.builder().ofZHIPU()
                                .apiKey(zhipuApiKey())
                                .build();

                        for (String imageModel : imageModelCandidates()) {
                            attemptedModels.add(imageModel);
                            modelAvailability.add(buildModelAvailability(imageModel, PROVIDER_ZHIPU, false, ""));
                            List<String> generatedImages = new ArrayList<>();
                            String modelError = "";
                            String zhipuPrompt = appendKnowledgeContext(
                                    buildZhipuImagePrompt(prompt, style, size, imageModel),
                                    retrievalContext
                            );
                            try {
                                for (int i = 0; i < imageCount; i++) {
                                    CreateImageRequest requestNew = CreateImageRequest.builder().prompt(zhipuPrompt)
                                            .model(imageModel).size(imageSize)
                                            .build();

                                    ImageResponse image = client.images().createImage(requestNew);
                                    log.info("发送请求到智普 AI 文生图 API: model={}, index={}, size={}", imageModel, i + 1, imageSize);

                                    if (!image.isSuccess()) {
                                        lastImageError = String.valueOf(image);
                                        modelError = lastImageError;
                                        log.error("智普 AI 文生图 API 返回错误，模型: {}, 响应: {}", imageModel, image);
                                        continue;
                                    }

                                    if (image.getData() == null) {
                                        lastImageError = "智普 AI 文生图 API 响应中没有 data 字段";
                                        modelError = lastImageError;
                                        log.error("智普 AI 文生图 API 响应中没有 'data' 字段，模型: {}", imageModel);
                                        continue;
                                    }

                                    ImageResult data1 = image.getData();

                                    log.info("智普 AI 文生图 API 响应内容: {}", data1);
                                    if (CollectionUtils.isEmpty(data1.getData())) {
                                        lastImageError = "智普 AI 文生图 API 返回的 data 列表为空";
                                        modelError = lastImageError;
                                        log.error("智普 AI 文生图 API 返回的 data 列表为空，模型: {}", imageModel);
                                        continue;
                                    }

                                    for (Image imageResult : data1.getData()) {
                                        String localImagePath = extractGeneratedImageReference(imageResult).orElse(null);
                                        if (localImagePath != null) {
                                            generatedImages.add(localImagePath);
                                        } else {
                                            String itemError = imageResultDownloadFailureReason(imageResult);
                                            lastImageError = itemError;
                                            modelError = itemError;
                                        }
                                    }
                                }

                                if (!generatedImages.isEmpty()) {
                                    clearImageFailureFields(response);
                                    response.put("success", true);
                                    response.put("message", "图片生成成功");
                                    response.put("image", generatedImages.get(0));
                                    response.put("images", generatedImages);
                                    response.put("enhancedPrompt", zhipuPrompt);
                                    response.put("provider", PROVIDER_ZHIPU);
                                    response.put("imageModel", imageModel);
                                    response.put("imageModelsTried", attemptedModels);
                                    modelAvailability = markModelAvailability(modelAvailability, imageModel, true, "模型返回有效图片");
                                    response.put("modelAvailability", modelAvailability);
                                    response.put("modelUnavailable", false);
                                    response.put("retrieval", buildRetrievalMetadata(retrievalResult));
                                    log.info("智普 AI 文生图 API 图片生成成功，模型: {}, 数量: {}", imageModel, generatedImages.size());
                                    return response;
                                }
                                if (modelError.isBlank()) {
                                    modelError = "智普 AI 文生图 API 未返回可下载或可保存的图片";
                                }
                                modelAvailability = markModelAvailability(modelAvailability, imageModel, false, modelError);
                            } catch (Exception modelErrorException) {
                                lastImageError = modelErrorException.getMessage();
                                modelError = lastImageError == null ? "" : lastImageError;
                                modelAvailability = markModelAvailability(modelAvailability, imageModel, false, lastImageError);
                                log.error("智普 AI 文生图 API 调用失败，模型: {}, 原因: {}", imageModel, modelErrorException.getMessage());
                            }
                        }
                        response.put("modelUnavailable", true);
                        response.put("imageModelsTried", attemptedModels);
                        response.put("modelAvailability", modelAvailability);
                        response.put("error", lastImageError);
                    } catch (Exception zhipuError) {
                        response.put("modelUnavailable", true);
                        response.put("error", zhipuError.getMessage());
                        response.put("modelAvailability", modelAvailability);
                        log.error("智普 AI 文生图 API 调用失败: {}", zhipuError.getMessage());
                    }
                }

                if (usesPublicFreeProvider(provider)) {
                    ImageGenerationResult publicResult = callPublicFreeImageAPI(
                            appendKnowledgeContext(buildPublicImagePrompt(prompt, style), retrievalContext),
                            imageSize,
                            imageCount
                    );
                    attemptedModels.add(publicResult.model());
                    modelAvailability.add(buildModelAvailability(
                            publicResult.model(),
                            PROVIDER_PUBLIC_FREE,
                            publicResult.success(),
                            publicResult.error()
                    ));
                    if (publicResult.success()) {
                        clearImageFailureFields(response);
                        response.put("success", true);
                        response.put("message", "图片生成成功");
                        response.put("image", publicResult.images().get(0));
                        response.put("images", publicResult.images());
                        response.put("enhancedPrompt", detailedPrompt);
                        response.put("provider", PROVIDER_PUBLIC_FREE);
                        response.put("imageModel", publicResult.model());
                        response.put("imageModelsTried", attemptedModels);
                        response.put("modelAvailability", normalizeModelAvailability(modelAvailability));
                        response.put("modelUnavailable", false);
                        response.put("retrieval", buildRetrievalMetadata(retrievalResult));
                        log.info("公网免费图片模型生成成功，模型: {}, 数量: {}", publicResult.model(), publicResult.images().size());
                        return response;
                    }
                    lastImageError = publicResult.error();
                    response.put("modelUnavailable", true);
                    response.put("message", publicResult.message());
                    response.put("description", publicResult.description());
                    response.put("actionSuggestion", publicResult.actionSuggestion());
                    response.put("provider", PROVIDER_PUBLIC_FREE);
                    response.put("imageModel", publicResult.model());
                    response.put("imageModelsTried", attemptedModels);
                    response.put("error", lastImageError);
                    response.put("modelAvailability", normalizeModelAvailability(modelAvailability));
                }

            } catch (Exception e) {
                log.error("图片生成提示词构建失败: {}", e.getMessage());
            }
            String imageError = Objects.toString(response.get("error"), "");
            if (isImageQuotaError(imageError)) {
                response.put("message", "图片模型资源不可用");
                response.put("description", imageQuotaDescription());
                response.put("actionSuggestion", imageQuotaActionSuggestion());
                response.put("modelUnavailable", true);
                response.put("modelAvailability", modelAvailability);
            }
            response.put("success", false);
            response.putIfAbsent("message", "图片生成失败");
            response.putIfAbsent("error", "图片模型未返回可用图片");
            response.putIfAbsent("modelUnavailable", true);
            response.putIfAbsent("modelAvailability", modelAvailability);
        } catch (Exception e) {
            log.error("图片生成失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "图片生成失败");
            response.put("error", e.getMessage());
            response.put("modelUnavailable", true);
            response.put("modelAvailability", normalizeModelAvailability(modelAvailability));
        }

        return response;
    }

    @Override
    public ResponseEntity<Resource> downloadImage(String fileName) {
        try {
            Path filePath = Paths.get(UPLOAD_DIR, fileName);
            log.info("提供文件，文件路径: {}", filePath.toAbsolutePath());

            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                log.error("文件不存在: {}", filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            // 检查文件大小
            long fileSize = Files.size(filePath);
            log.info("文件大小: {} bytes", fileSize);

            Resource resource = new UrlResource(filePath.toUri());
            log.info("资源创建成功: {}", resource.exists());

            if (resource.exists()) {
                // 根据文件扩展名设置内容类型
                MediaType contentType;
                if (fileName.endsWith(".mp4")) {
                    contentType = MediaType.valueOf("video/mp4");
                } else if (fileName.endsWith(".mov")) {
                    contentType = MediaType.valueOf("video/quicktime");
                } else if (fileName.endsWith(".avi")) {
                    contentType = MediaType.valueOf("video/x-msvideo");
                } else if (fileName.endsWith(".wmv")) {
                    contentType = MediaType.valueOf("video/x-ms-wmv");
                } else if (fileName.endsWith(".flv")) {
                    contentType = MediaType.valueOf("video/x-flv");
                } else if (fileName.endsWith(".webm")) {
                    contentType = MediaType.valueOf("video/webm");
                } else {
                    // 默认认为是图片
                    contentType = MediaType.IMAGE_PNG;
                }

                log.info("返回文件资源: {}, 内容类型: {}", fileName, contentType);
                return ResponseEntity.ok()
                        .contentType(contentType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                        .body(resource);
            } else {
                log.error("资源不存在: {}", fileName);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("提供文件失败: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    private static String appendKnowledgeContext(String prompt, String retrievalContext) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        String safeContext = retrievalContext == null ? "" : retrievalContext.trim();
        if (safeContext.isBlank()) {
            return safePrompt;
        }
        if (safePrompt.contains("【统一知识检索上下文】")) {
            return safePrompt;
        }
        return safePrompt + "\n\n" + safeContext;
    }

    private static Map<String, Object> buildRetrievalMetadata(KnowledgeRetrievalResult retrievalResult) {
        Map<String, Object> retrieval = new LinkedHashMap<>();
        retrieval.put("used", retrievalResult != null && retrievalResult.used());
        retrieval.put("scene", retrievalResult == null ? "" : retrievalResult.scene());
        retrieval.put("query", retrievalResult == null ? "" : retrievalResult.query());
        retrieval.put("gateReason", retrievalResult == null ? "检索结果缺失" : retrievalResult.gateReason());
        return retrieval;
    }

    private KnowledgeRetrievalResult retrieveForImagePrompt(String prompt) {
        if (knowledgeRetrievalService == null) {
            return new KnowledgeRetrievalResult(
                    "image-generation",
                    prompt == null ? "" : prompt,
                    IMAGE_RAG_TOP_K,
                    false,
                    "统一检索服务未接入",
                    List.of(),
                    List.of("service unavailable")
            );
        }
        return knowledgeRetrievalService.retrieve("image-generation", prompt, IMAGE_RAG_TOP_K);
    }

    private ImageGenerationResult callFreeImageAPI(String prompt, String imageSize, int imageCount) {
        String model = configuredFreeImageModel(environment);
        String apiUrl = configuredFreeImageApiUrl(environment);
        try {
            Map<String, Object> request = buildFreeImageRequest(prompt, imageSize, imageCount, model);
            log.info("发送请求到本地免费图片模型 API: model={}, url={}, count={}, size={}", model, apiUrl, imageCount, imageSize);
            Map<String, Object> responseBody = restTemplate.postForObject(apiUrl, request, Map.class);
            List<String> images = materializeGeneratedImages(responseBody);
            if (!images.isEmpty()) {
                return ImageGenerationResult.success(images, model);
            }
            return ImageGenerationResult.failure(
                    "本地免费图片模型未返回图片",
                    freeImageUnavailableDescription(),
                    "Response missing images/image/imageUrl fields",
                    model,
                    freeImageActionSuggestion()
            );
        } catch (RestClientResponseException e) {
            String error = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.warn("本地免费图片模型调用失败，模型: {}, 状态: {}, 原因: {}", model, e.getStatusCode(), error);
            return ImageGenerationResult.failure(
                    "本地免费图片模型不可用",
                    freeImageUnavailableDescription(),
                    error.isBlank() ? e.getMessage() : error,
                    model,
                    freeImageActionSuggestion()
            );
        } catch (Exception e) {
            log.warn("本地免费图片模型调用失败，模型: {}, 原因: {}", model, e.getMessage());
            return ImageGenerationResult.failure(
                    "本地免费图片模型不可用",
                    freeImageUnavailableDescription(),
                    e.getMessage(),
                    model,
                    freeImageActionSuggestion()
            );
        }
    }

    private ImageGenerationResult callPublicFreeImageAPI(String prompt, String imageSize, int imageCount) {
        String model = configuredPublicImageModel(environment);
        List<String> images = new ArrayList<>();
        String lastError = "";
        for (int i = 0; i < Math.max(1, imageCount); i++) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                String imageUrl = buildPublicImageUrl(prompt, imageSize, model, System.currentTimeMillis() + i + attempt);
                log.info("发送请求到公网免费图片模型 API: model={}, index={}, attempt={}, url={}", model, i + 1, attempt, imageUrl);
                ImageDownloadResult downloadResult = downloadPublicImageFromUrl(imageUrl);
                if (downloadResult.success()) {
                    images.add(downloadResult.localPath());
                    break;
                }
                lastError = downloadResult.error();
                log.warn("公网免费图片模型结果下载失败，模型: {}, index={}, attempt={}, reason={}", model, i + 1, attempt, lastError);
                if (attempt == 1 && isPublicQueueFull(lastError)) {
                    sleepBeforePublicRetry();
                    continue;
                }
                break;
            }
        }
        if (!images.isEmpty()) {
            return ImageGenerationResult.success(images.stream().distinct().toList(), model);
        }
        return ImageGenerationResult.failure(
                "公网免费图片模型不可用",
                publicImageUnavailableDescription(),
                lastError.isBlank() ? "Public image provider response missing downloadable image" : lastError,
                model,
                publicImageActionSuggestion()
        );
    }

    private List<String> materializeGeneratedImages(Map<String, Object> responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return List.of();
        }
        List<String> images = new ArrayList<>();
        Object imageList = responseBody.get("images");
        if (imageList instanceof List<?> values) {
            for (Object value : values) {
                saveGeneratedImageReference(value).ifPresent(images::add);
            }
        }
        saveGeneratedImageReference(responseBody.get("image")).ifPresent(images::add);
        saveGeneratedImageReference(responseBody.get("imageUrl")).ifPresent(images::add);
        return images.stream().distinct().toList();
    }

    private Optional<String> saveGeneratedImageReference(Object value) {
        if (!(value instanceof String imageValue) || imageValue.isBlank()) {
            return Optional.empty();
        }
        String trimmed = imageValue.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return Optional.ofNullable(downloadImageFromUrl(trimmed));
        }
        return saveBase64Image(trimmed);
    }

    private Optional<String> saveBase64Image(String imageValue) {
        try {
            String base64 = imageValue;
            String extension = "png";
            int commaIndex = imageValue.indexOf(',');
            if (imageValue.startsWith("data:image/") && commaIndex > 0) {
                int typeStart = "data:image/".length();
                int typeEnd = imageValue.indexOf(';', typeStart);
                if (typeEnd > typeStart) {
                    extension = imageValue.substring(typeStart, typeEnd).replace("jpeg", "jpg");
                }
                base64 = imageValue.substring(commaIndex + 1);
            }
            byte[] content = Base64.getDecoder().decode(base64);
            String fileName = UUID.randomUUID() + "." + extension;
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(fileName);
            Files.write(filePath, content);
            log.info("本地免费图片模型结果保存成功: {}, size={}", filePath.toAbsolutePath(), content.length);
            return Optional.of("/api/image/download/" + fileName);
        } catch (Exception e) {
            log.warn("保存本地免费图片模型结果失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> extractGeneratedImageReference(Image imageResult) {
        if (imageResult == null) {
            return Optional.empty();
        }

        String imageUrl = trimToEmpty(imageResult.getUrl());
        if (!imageUrl.isBlank()) {
            if (imageUrl.startsWith("data:")) {
                Optional<String> localImagePath = saveBase64Image(imageUrl);
                if (localImagePath.isPresent()) {
                    return localImagePath;
                }
            } else {
                String localImagePath = downloadImageFromUrl(imageUrl);
                if (localImagePath != null && !localImagePath.isBlank()) {
                    return Optional.of(localImagePath);
                }
            }
        }

        String base64Image = trimToEmpty(imageResult.getB64Json());
        if (!base64Image.isBlank()) {
            return saveBase64Image(base64Image);
        }

        return Optional.empty();
    }

    private static String trimToEmpty(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private static String imageResultDownloadFailureReason(Image imageResult) {
        if (imageResult == null) {
            return "文生图 API 响应中的图片项为空";
        }
        String imageUrl = trimToEmpty(imageResult.getUrl());
        String base64Image = trimToEmpty(imageResult.getB64Json());
        if (!imageUrl.isBlank() && !base64Image.isBlank()) {
            return "文生图 API 响应中的图片项包含 URL 与 base64，但均处理失败";
        }
        if (!imageUrl.isBlank()) {
            return "文生图 API 响应中的图片链接无法解析或下载";
        }
        if (!base64Image.isBlank()) {
            return "文生图 API 返回 base64 内容但未能生成图片文件";
        }
        return "文生图 API 响应中的图片项没有可下载内容";
    }

    @Override
    public Map<String, Object> describeImage(MultipartFile image, String type, String language) {

        Map<String, Object> response = new HashMap<>();
        java.util.List<Map<String, Object>> modelAvailability = new java.util.ArrayList<>();

        try {
            if (image.isEmpty()) {
                response.put("success", false);
                response.put("message", "请上传图片");
                return response;
            }

            // 验证文件类型
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                response.put("success", false);
                response.put("message", "请上传图片文件");
                return response;
            }

            // 验证文件大小
            if (image.getSize() > 10 * 1024 * 1024) {
                response.put("success", false);
                response.put("message", "图片大小不能超过 10MB");
                return response;
            }

            log.info("开始分析图片，类型: {}, 语言: {}", type, language);

            // 读取图片数据
            byte[] imageBytes = image.getBytes();
            String base64Image = normalizeVisionImageBase64(imageBytes)
                    .orElseGet(() -> Base64.getEncoder().encodeToString(imageBytes));

            VisionApiResult visionResult = callVisionAPI(base64Image, type, language);
            modelAvailability = visionResult.modelAvailability();

            response.put("success", visionResult.success());
            response.put("message", visionResult.message());
            response.put("description", visionResult.description());
            response.put("provider", visionResult.provider());
            response.put("visionModelsTried", visionResult.modelsTried());
            response.put("modelAvailability", modelAvailability);
            response.put("modelUnavailable", !visionResult.success());
            if (!visionResult.success()) {
                Optional<String> localSummary = buildLocalImageSummary(imageBytes, type, language);
                if (localSummary.isPresent()) {
                    response.put("success", false);
                    response.put("message", visionResult.message());
                    response.put("description", localSummary.get());
                    response.put("fallback", true);
                    response.put("model", "local-basic-image-summary");
                    response.put("modelUnavailable", true);
                    response.put("modelMessage", visionResult.message());
                    response.put("actionSuggestion", visionResult.actionSuggestion());
                    response.put("error", visionResult.error());
                    response.put("modelAvailability", modelAvailability);
                    log.warn("视觉模型不可用，已返回本地基础描述，原因: {}", visionResult.message());
                    return response;
                }
                response.put("error", visionResult.error());
                response.put("actionSuggestion", visionResult.actionSuggestion());
                response.put("modelUnavailable", true);
                response.put("modelAvailability", modelAvailability);
                log.warn("图片描述生成失败，原因: {}", visionResult.message());
                return response;
            }

            response.put("model", visionResult.model());
            response.put("modelAvailability", modelAvailability);
            response.put("modelUnavailable", false);
            log.info("图片描述生成成功，模型: {}", visionResult.model());
        } catch (IOException e) {
            log.error("图片处理失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "图片处理失败");
            response.put("error", e.getMessage());
            response.put("modelAvailability", normalizeModelAvailability(modelAvailability));
        } catch (Exception e) {
            log.error("图片描述生成失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "图片描述生成失败");
            response.put("error", e.getMessage());
            response.put("modelAvailability", normalizeModelAvailability(modelAvailability));
        }

        return response;
    }

    /**
     * 调用配置的图片理解模型分析图片
     * @param base64Image  base64编码的图片
     * @param type 描述类型
     * @param language 语言
     * @return 图片描述
     */
    private VisionApiResult callVisionAPI(String base64Image, String type, String language) {
        String provider = configuredVisionProvider(environment);
        VisionApiResult ollamaResult = null;
        if (usesOllamaProvider(provider)) {
            ollamaResult = callOllamaVisionAPI(base64Image, type, language);
            if (ollamaResult.success() || !usesZhipuProvider(provider)) {
                return ollamaResult;
            }
        }
        if (usesZhipuProvider(provider)) {
            VisionApiResult zhipuResult = callZhipuVisionAPI(base64Image, type, language);
            if (ollamaResult != null) {
                return mergeVisionResults(ollamaResult, zhipuResult, provider);
            }
            return zhipuResult;
        }
        return VisionApiResult.failure(
                "图片理解模型不可用",
                visionUnavailableDescription(language),
                "Unsupported vision provider: " + provider,
                "",
                List.of(),
                List.of(buildModelAvailability("", "unsupported", false, "unsupported vision provider: " + provider)),
                visionActionSuggestion(),
                provider
        );
    }

    private static VisionApiResult mergeVisionResults(
            VisionApiResult first,
            VisionApiResult second,
            String configuredProvider
    ) {
        List<String> modelsTried = new ArrayList<>();
        modelsTried.addAll(first.modelsTried());
        modelsTried.addAll(second.modelsTried());

        List<Map<String, Object>> modelAvailability = new ArrayList<>();
        modelAvailability.addAll(first.modelAvailability());
        modelAvailability.addAll(second.modelAvailability());

        return new VisionApiResult(
                second.success(),
                second.message(),
                second.description(),
                second.error(),
                second.model(),
                modelsTried.stream().filter(model -> model != null && !model.isBlank()).distinct().toList(),
                second.actionSuggestion(),
                second.success() ? second.provider() : configuredProvider,
                normalizeModelAvailability(modelAvailability)
        );
    }

    private VisionApiResult callOllamaVisionAPI(String base64Image, String type, String language) {
        List<String> models = configuredOllamaVisionModels(environment);
        String apiUrl = normalizeBaseUrl(configuredOllamaBaseUrl(environment)) + "/api/generate";
        List<String> attemptedModels = new ArrayList<>();
        List<Map<String, Object>> modelAvailability = new ArrayList<>();
        String lastModel = models.isEmpty() ? configuredOllamaVisionModel(environment) : models.get(0);
        String lastError = "";

        for (String model : models) {
            attemptedModels.add(model);
            lastModel = model;
            try {
                Map<String, Object> request = buildOllamaVisionRequest(base64Image, type, language, model);
                log.info("发送请求到本地 Ollama 视觉模型，模型: {}", model);
                Map<String, Object> responseBody = restTemplate.postForObject(apiUrl, request, Map.class);
                Object response = responseBody == null ? null : responseBody.get("response");
                if (response instanceof String description && !description.isBlank()) {
                    log.info("本地 Ollama 视觉模型返回描述，模型: {}, length: {}", model, description.length());
                    modelAvailability.add(buildModelAvailability(model, "ollama", true, ""));
                    return VisionApiResult.success(
                            description.trim(),
                            model,
                            "ollama",
                            attemptedModels,
                            modelAvailability
                    );
                }
                lastError = "Ollama response missing response field";
                modelAvailability.add(buildModelAvailability(model, "ollama", false, lastError));
                log.warn("本地 Ollama 视觉模型未返回描述，模型: {}", model);
            } catch (RestClientResponseException e) {
                String error = e.getResponseBodyAsString(StandardCharsets.UTF_8);
                lastError = error.isBlank() ? e.getMessage() : error;
                modelAvailability.add(buildModelAvailability(model, "ollama", false, lastError));
                log.warn("本地 Ollama 视觉模型调用失败，模型: {}, 状态: {}, 原因: {}", model, e.getStatusCode(), lastError);
            } catch (Exception e) {
                lastError = e.getMessage();
                modelAvailability.add(buildModelAvailability(model, "ollama", false, lastError));
                log.warn("本地 Ollama 视觉模型调用失败，模型: {}, 原因: {}", model, e.getMessage());
            }
        }

        return VisionApiResult.failure(
                "本地免费图片理解模型不可用",
                ollamaVisionUnavailableDescription(language),
                lastError,
                lastModel,
                attemptedModels,
                modelAvailability,
                visionActionSuggestion(),
                "ollama"
        );
    }

    /**
     * 调用智普AI视觉理解API分析图片
     * @param base64Image  base64编码的图片
     * @param type 描述类型
     * @param language 语言
     * @return 图片描述
     */
    private VisionApiResult callZhipuVisionAPI(String base64Image, String type, String language) {
        String lastError = "";
        List<String> attemptedModels = new ArrayList<>();
        List<Map<String, Object>> modelAvailability = new ArrayList<>();
        for (String model : visionModelCandidates()) {
            try {
            attemptedModels.add(model);
            modelAvailability.add(buildModelAvailability(model, PROVIDER_ZHIPU, false, ""));
            // 智普AI视觉理解API地址
            String zhipuApiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

            // 构建请求参数
            Map<String, Object> zhipuRequest = new HashMap<>();
            zhipuRequest.put("model", model);

            // 构建消息
            java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();

            // 系统消息
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", buildVisionSystemPrompt(language));
            messages.add(systemMessage);

            // 用户消息
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");

            // 构建内容
            java.util.List<Map<String, Object>> content = new java.util.ArrayList<>();

            // 文本部分
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");

            String prompt = buildVisionPrompt(type, language);
            textContent.put("text", prompt);
            content.add(textContent);



            // 图片部分
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            Map<String, String> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:image/png;base64," + base64Image);
            imageContent.put("image_url", imageUrl);
            content.add(imageContent);

            userMessage.put("content", content);
            messages.add(userMessage);

            zhipuRequest.put("messages", messages);
            zhipuRequest.put("temperature", visionTemperature(type));
            zhipuRequest.put("max_tokens", visionMaxTokens(type));

            // 发送请求到智普AI API
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + zhipuApiKey());

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(zhipuRequest, headers);

            log.info("发送请求到智普AI视觉理解API，模型: {}", model);
            org.springframework.http.ResponseEntity<Map> zhipuResponse = restTemplate.postForEntity(zhipuApiUrl, entity, Map.class);
            log.info("智普AI视觉理解API响应状态: {}, 模型: {}", zhipuResponse.getStatusCode(), model);

            if (zhipuResponse.getStatusCode().is2xxSuccessful() && zhipuResponse.getBody() != null) {
                Map<String, Object> responseBody = zhipuResponse.getBody();
                log.info("智普AI视觉理解API响应内容: {}", responseBody);

                if (responseBody.containsKey("choices")) {
                    java.util.List<Map<String, Object>> choices = (java.util.List<Map<String, Object>>) responseBody.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        if (choice.containsKey("message")) {
                            Map<String, Object> message = (Map<String, Object>) choice.get("message");
                            if (message.containsKey("content")) {
                                String description = (String) message.get("content");
                                log.info("智普AI视觉理解API返回描述，模型: {}, length: {}", model, description.length());
                                modelAvailability = markModelAvailability(modelAvailability, model, true, "模型返回有效描述");
                                return VisionApiResult.success(
                                        description,
                                        model,
                                        PROVIDER_ZHIPU,
                                        attemptedModels,
                                        modelAvailability
                                );
                            }
                        }
                    }
                }
            } else {
                log.error("智普AI视觉理解API返回错误: {}", zhipuResponse.getStatusCode());
                lastError = "HTTP " + zhipuResponse.getStatusCode();
                modelAvailability = markModelAvailability(modelAvailability, model, false, "HTTP " + zhipuResponse.getStatusCode());
            }
            } catch (RestClientResponseException zhipuError) {
                String responseBody = zhipuError.getResponseBodyAsString(StandardCharsets.UTF_8);
                lastError = responseBody.isBlank() ? zhipuError.getMessage() : responseBody;
                modelAvailability = markModelAvailability(modelAvailability, model, false, lastError);
                log.warn("智普AI视觉理解API调用失败，模型: {}, 状态: {}, 原因: {}", model, zhipuError.getStatusCode(), lastError);
                if (isVisionQuotaError(lastError)) {
                    return VisionApiResult.failure(
                            "图片理解模型资源不可用",
                            visionQuotaDescription(language),
                            lastError,
                            model,
                            attemptedModels,
                            modelAvailability,
                            "请检查智谱账号余额或资源包，或切换到有视觉额度的 API Key 后重试",
                            PROVIDER_ZHIPU
                    );
                }
                if (isVisionModelUnavailableError(lastError)) {
                    return VisionApiResult.failure(
                            "图片理解模型不可用",
                            visionUnavailableDescription(language),
                            lastError,
                            model,
                            attemptedModels,
                            modelAvailability,
                            "请配置支持 image_url 图片输入的视觉模型后重试",
                            PROVIDER_ZHIPU
                    );
                }
            } catch (Exception zhipuError) {
                lastError = zhipuError.getMessage();
                modelAvailability = markModelAvailability(modelAvailability, model, false, lastError);
                log.error("智普AI视觉理解API调用失败，模型: {}, 原因: {}", model, zhipuError.getMessage());
            }
        }

        return VisionApiResult.failure(
                "图片理解模型不可用",
                visionUnavailableDescription(language),
                lastError,
                attemptedModels.isEmpty() ? "" : attemptedModels.get(attemptedModels.size() - 1),
                attemptedModels,
                modelAvailability,
                "请配置支持 image_url 图片输入的视觉模型后重试",
                PROVIDER_ZHIPU
        );
    }

    static String buildImagePrompt(String prompt, String style, String size) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        String enrichedPrompt = enrichImagePrompt(safePrompt);
        String styleText = imageStyleText(style);
        String composition = imageCompositionText(size);
        return StreamParts.of(
                "请生成一张具象、可识别的图片",
                "画面主题：" + enrichedPrompt,
                styleText,
                composition,
                "明确主体和真实场景，主体、空间、物件之间关系自然",
                "主体清晰，构图完整，视觉焦点明确，细节丰富但不杂乱",
                "高质量画面，光影层次自然，色彩协调，材质和空间关系可信",
                "避免抽象图案、乱码纹理、无意义色块、畸形肢体、重复主体、低清晰度、过曝、模糊、噪点、变形",
                "避免文字、字幕、水印、Logo 和边框；节庆装饰可以出现，但避免生成错误可读文字"
        );
    }

    static String buildZhipuImagePrompt(String prompt, String style, String size, String model) {
        if (isFlashImageModel(model)) {
            return buildFastImagePrompt(prompt, style);
        }
        return buildImagePrompt(prompt, style, size);
    }

    private static boolean isFlashImageModel(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).contains("flash");
    }

    private static String buildFastImagePrompt(String prompt, String style) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        String subject = enrichImagePrompt(safePrompt);
        return limitPromptLength(StreamParts.of(
                subject,
                imageStyleText(style),
                "主体清晰，真实场景，暖色自然光，色彩鲜艳，高质量细节，构图完整，无文字，无水印，无 Logo"
        ), 220);
    }

    private static String limitPromptLength(String prompt, int maxLength) {
        if (prompt == null || prompt.length() <= maxLength) {
            return prompt == null ? "" : prompt;
        }
        return prompt.substring(0, maxLength);
    }

    static String buildVisionSystemPrompt(String language) {
        if ("en".equals(language)) {
            return "You are a careful image analysis assistant. Describe only visible evidence. Do not identify real people or infer intentions, private attributes, or off-image context. If the image is anime, game art, a film poster, illustration, or another fictional-character work, you may identify a specific fictional character when strong visual evidence supports it; prefer \"possibly character name from work title\" and explain the evidence. Only fall back to the work title or category when evidence is insufficient, and say why it is uncertain.";
        }
        return "你是严谨的图片分析助手。只描述图片中可见内容，不要识别真实人物身份，不推断动机、隐私属性或图片外背景。若图片是动漫、游戏、影视海报、插画或其他虚构角色内容，可以在有明显视觉证据时识别到具体角色名；优先输出“可能是《作品名》的角色名”，并说明依据；证据不足时才降级为作品或类别，并说明不确定原因。";
    }

    static String buildVisionPrompt(String type, String language) {
        boolean english = "en".equals(language);
        String normalizedType = type == null ? "" : type;
        if (english) {
            List<String> outputStructure = switch (normalizedType) {
                case "concise" -> List.of(
                        "Create a concise but polished image description with 2 sections: Main description and Reusable prompt.",
                        "Main description: write one natural 60-90 word paragraph that can be used directly for asset labeling or a caption.",
                        "Reusable prompt: write one short prompt line suitable for text-to-image generation or image search."
                );
                case "creative" -> List.of(
                        "Create a vivid, polished image description with 3 sections: Main description, Detail notes, and Reusable prompt.",
                        "Main description: write one natural 120-180 word paragraph with visual rhythm, while keeping every claim grounded in visible evidence.",
                        "Detail notes: write 3-5 high-information bullets about pose, clothing or objects, colors, lighting, composition, style, spatial relationships, and distinctive details.",
                        "Reusable prompt: write one short prompt line suitable for text-to-image generation or image search."
                );
                default -> List.of(
                        "Create a high-quality image description with 3 sections: Main description, Detail notes, and Reusable prompt.",
                        "Main description: write one natural 120-180 word paragraph that can be used directly for asset labeling, a caption, or a text-to-image brief.",
                        "Detail notes: write 3-5 high-information bullets about image type, main subject, action or pose, clothing or objects, environment, colors, lighting, composition, style, spatial relationships, and distinctive details.",
                        "Reusable prompt: write one short prompt line suitable for text-to-image generation or image search."
                );
            };
            List<String> constraints = List.of(
                    "Do not dump fields like \"image type: subject: action/pose:\"; if image type matters, weave it naturally into the paragraph or detail notes.",
                    "Describe only visible content; do not speculate or add off-image context.",
                    "First classify the image type, such as photo, anime/illustration, poster, UI, product, or document.",
                    "If it shows a fictional character or anime/game/film figure, first infer the specific character name and work title when visible evidence supports it; write \"possibly character name from work title\" and list evidence. Do not merely write \"a character from a work\".",
                    "Describe the main subject, action or pose, clothing or objects, environment, colors, lighting, composition, style, spatial relationships, and the most distinctive details.",
                    "Avoid vague low-information phrases such as blurry, some objects, or some effect unless the image is genuinely unreadable; replace them with concrete visible elements.",
                    "Mention visible text, logos, symbols, or unreadable marks when present.",
                    "If something is uncertain, say it is uncertain instead of presenting a guess as fact."
            );
            List<String> lines = new ArrayList<>(outputStructure);
            lines.addAll(constraints);
            return String.join("\n", lines);
        }

        List<String> outputStructure = switch (normalizedType) {
            case "concise" -> List.of(
                    "请生成简洁但自然成文的中文图像描述，输出 2 个部分：主描述、可复用提示词。",
                    "主描述：用 60-90 字写成一段自然描述，可直接用于素材标注、图文说明或文生图参考。",
                    "可复用提示词：用 1 行短语提炼画面主体、风格、色彩和构图，适合文生图或图片检索。"
            );
            case "creative" -> List.of(
                    "请生成更有画面感的高质量中文图像描述，输出 3 个部分：主描述、细节补充、可复用提示词。",
                    "主描述：用 120-180 字写成一段自然成文的描述，语言可以更有镜头感，但所有内容都必须基于可见证据。",
                    "细节补充：用 3-5 条高信息密度要点补充动作/姿态、服饰/物件、颜色、光影、构图、风格、空间关系和最突出的细节。",
                    "可复用提示词：用 1 行短语提炼画面主体、风格、色彩和构图，适合文生图或图片检索。"
            );
            default -> List.of(
                    "请生成可直接使用的高质量中文图像描述，输出 3 个部分：主描述、细节补充、可复用提示词。",
                    "主描述：用 120-180 字写成一段自然成文的描述，可直接用于素材标注、图文说明或文生图参考。",
                    "细节补充：用 3-5 条高信息密度要点补充图片类型、主体、动作/姿态、服饰/物件、环境、颜色、光影、构图、风格、空间关系和最突出的细节。",
                    "可复用提示词：用 1 行短语提炼画面主体、风格、色彩和构图，适合文生图或图片检索。"
            );
        };
        List<String> constraints = List.of(
                "不要用“图片类型：主体：动作/姿态：”这类字段清单式堆砌；如果需要说明图片类型，请自然写进主描述或细节补充。",
                "只描述图片中可见内容，不要臆测或补充图片外背景。",
                "先判断图片类型，例如照片、动漫/插画、海报、界面、商品或文档。",
                "如果是虚构角色或动漫/游戏/影视形象，优先判断具体角色名和作品名，按“可能是《作品名》的角色名”表达，并列出可见视觉证据；不要只写“某作品中的角色”。",
                "描述主体、动作/姿态、服饰/物件、环境、颜色、光影、构图、风格、空间关系和最突出的细节。",
                "避免泛泛使用“模糊、某些、可能是某种效果”等低信息词；除非图像确实看不清，否则用具体可见元素替代。",
                "如有文字、标识或符号，请说明可见内容；读不清就说明无法辨认。",
                "不确定的内容请明确说明不确定，不要把猜测写成事实。"
        );
        List<String> lines = new ArrayList<>(outputStructure);
        lines.addAll(constraints);
        return String.join("\n", lines);
    }

    static Map<String, Object> buildOllamaVisionRequest(String base64Image, String type, String language, String model) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", visionTemperature(type));
        options.put("num_predict", visionMaxTokens(type));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("prompt", buildVisionSystemPrompt(language) + "\n\n" + buildVisionPrompt(type, language));
        request.put("images", List.of(base64Image));
        request.put("stream", false);
        request.put("options", options);
        return request;
    }

    static String buildPublicImagePrompt(String prompt, String style) {
        return StreamParts.of(
                enrichImagePrompt(prompt),
                imageStyleText(style),
                "主体清晰，真实场景，高质量，避免文字、水印和 Logo"
        );
    }

    static Optional<String> normalizeVisionImageBase64(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return Optional.empty();
            }
            int width = Math.max(MIN_VISION_IMAGE_SIDE, image.getWidth());
            int height = Math.max(MIN_VISION_IMAGE_SIDE, image.getHeight());
            BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            copyImageToRgbCanvas(image, normalized);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(normalized, "png", output) || output.size() == 0) {
                return Optional.empty();
            }
            return Optional.of(Base64.getEncoder().encodeToString(output.toByteArray()));
        } catch (IOException e) {
            log.warn("图片重编码为视觉模型输入失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void copyImageToRgbCanvas(BufferedImage source, BufferedImage target) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int targetWidth = target.getWidth();
        int targetHeight = target.getHeight();
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = Math.min(sourceHeight - 1, y * sourceHeight / targetHeight);
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(sourceWidth - 1, x * sourceWidth / targetWidth);
                target.setRGB(x, y, flattenArgbOnWhite(source.getRGB(sourceX, sourceY)));
            }
        }
    }

    private static int flattenArgbOnWhite(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        if (alpha >= 255) {
            return (red << 16) | (green << 8) | blue;
        }
        int inverseAlpha = 255 - alpha;
        int flattenedRed = ((red * alpha) + (255 * inverseAlpha)) / 255;
        int flattenedGreen = ((green * alpha) + (255 * inverseAlpha)) / 255;
        int flattenedBlue = ((blue * alpha) + (255 * inverseAlpha)) / 255;
        return (flattenedRed << 16) | (flattenedGreen << 8) | flattenedBlue;
    }

    static Map<String, Object> buildFreeImageRequest(String prompt, String size, int imageCount, String model) {
        ImageSize imageSize = parseImageSize(size);
        Map<String, Object> overrideSettings = new LinkedHashMap<>();
        overrideSettings.put("sd_model_checkpoint", model);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("prompt", prompt);
        request.put("negative_prompt", "文字, 水印, logo, 低清晰度, 模糊, 噪点, 畸形, 变形, 重复主体");
        request.put("width", imageSize.width());
        request.put("height", imageSize.height());
        request.put("batch_size", Math.max(1, imageCount));
        request.put("steps", 4);
        request.put("cfg_scale", 1.0D);
        request.put("sampler_name", "Euler");
        request.put("override_settings", overrideSettings);
        return request;
    }

    static boolean isVisionModelUnavailableError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        return error.contains("messages.content.type 参数非法")
                || error.contains("取值范围 ['text']")
                || error.contains("not support image")
                || error.contains("does not support image")
                || error.contains("image_url");
    }

    static boolean isVisionQuotaError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        return error.contains("余额不足")
                || error.contains("无可用资源包")
                || error.contains("insufficient balance")
                || error.contains("quota");
    }

    static boolean isImageQuotaError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        return error.contains("余额不足")
                || error.contains("无可用资源包")
                || error.contains("Insufficient balance")
                || error.contains("no resource package")
                || error.contains("quota");
    }

    static String imageQuotaDescription() {
        return "当前账号缺少高质量图片模型资源包或余额不足，无法生成可靠图片。为避免把低质量备用模型结果误判为成功，系统已停止自动降级。";
    }

    static String imageQuotaActionSuggestion() {
        return "请为高质量图片模型充值或开通资源包，或通过 ZHIPU_IMAGE_MODELS 显式配置有额度的图片模型；如临时演示可配置 cogview-3-flash，但生成质量可能不稳定。";
    }

    static String visionUnavailableDescription(String language) {
        if ("en".equals(language)) {
            return "No available image understanding model is configured, so a reliable description cannot be generated from the uploaded image. Start Ollama with a vision model such as Qwen2.5-VL, or explicitly switch to another provider.";
        }
        return "当前没有可用的图片理解模型，无法基于上传图片生成可信描述。请启动 Ollama 并加载 Qwen2.5-VL 等本地视觉模型，或显式切换到其他可用提供方。";
    }

    static String visionQuotaDescription(String language) {
        if ("en".equals(language)) {
            return "The image understanding request failed because the current account has insufficient balance or no available resource package. A reliable description cannot be generated from this image until the account or API key has vision quota.";
        }
        return "图片理解请求失败：当前账号余额不足或无可用资源包，无法基于上传图片生成可信描述。请补充视觉模型资源包、充值，或切换到有视觉额度的 API Key 后重试。";
    }

    static Optional<String> buildLocalImageSummary(byte[] imageBytes, String type, String language) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return Optional.empty();
            }
            LocalImageStats stats = analyzeLocalImage(image);
            if ("en".equals(language)) {
                return Optional.of(buildEnglishLocalImageSummary(stats, type));
            }
            return Optional.of(buildChineseLocalImageSummary(stats, type));
        } catch (IOException e) {
            log.warn("本地图片基础分析失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static String configuredZhipuApiKey(Map<String, String> env) {
        return Optional.ofNullable(env.get("ZHIPU_API_KEY"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_ZHIPU_API_KEY);
    }

    static List<String> configuredZhipuImageModels(Map<String, String> env) {
        String configuredModels = Optional.ofNullable(env.get("ZHIPU_IMAGE_MODELS"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_IMAGE_MODELS);
        return Arrays.stream(configuredModels.split(","))
                .map(String::trim)
                .filter(model -> !model.isBlank())
                .distinct()
                .toList();
    }

    static String configuredImageProvider(Map<String, String> env) {
        return configuredValue(env, "MEDIA_IMAGE_PROVIDER", PROVIDER_HYBRID).toLowerCase(Locale.ROOT);
    }

    static String configuredVisionProvider(Map<String, String> env) {
        return configuredValue(env, "MEDIA_VISION_PROVIDER", PROVIDER_HYBRID).toLowerCase(Locale.ROOT);
    }

    static String configuredFreeImageApiUrl(Map<String, String> env) {
        return configuredValue(env, "FREE_IMAGE_API_URL", DEFAULT_FREE_IMAGE_API_URL);
    }

    static String configuredFreeImageModel(Map<String, String> env) {
        return configuredValue(env, "FREE_IMAGE_MODEL", DEFAULT_FREE_IMAGE_MODEL);
    }

    static String configuredPublicImageModel(Map<String, String> env) {
        return configuredValue(env, "PUBLIC_IMAGE_MODEL", DEFAULT_PUBLIC_IMAGE_MODEL);
    }

    static String configuredOllamaBaseUrl(Map<String, String> env) {
        return configuredValue(env, "OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL);
    }

    static String configuredOllamaVisionModel(Map<String, String> env) {
        return configuredValue(env, "OLLAMA_VISION_MODEL", DEFAULT_OLLAMA_VISION_MODEL);
    }

    static List<String> configuredOllamaVisionModels(Map<String, String> env) {
        String configuredModels = configuredValue(env, "OLLAMA_VISION_MODELS", "");
        if (!configuredModels.isBlank()) {
            return Arrays.stream(configuredModels.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }

        String configuredModel = Optional.ofNullable(env.get("OLLAMA_VISION_MODEL"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse("");
        if (!configuredModel.isBlank()) {
            return List.of(configuredModel);
        }

        return Arrays.stream(DEFAULT_OLLAMA_VISION_MODELS.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    static String freeImageUnavailableDescription() {
        return "当前默认使用本地免费图片生成模型，但本地 Stable Diffusion/Forge 兼容接口没有返回可用图片。";
    }

    static String freeImageActionSuggestion() {
        return "请启动本地图片生成服务并打开 API，例如 Stable Diffusion WebUI/Forge 的 /sdapi/v1/txt2img；推荐加载 FLUX.1-schnell 或 SDXL，并通过 FREE_IMAGE_API_URL、FREE_IMAGE_MODEL 调整地址和模型。";
    }

    static String publicImageUnavailableDescription() {
        return "本地图片模型和高质量云端图片模型都不可用，公网免费图片模型也没有返回可下载图片。";
    }

    static String publicImageActionSuggestion() {
        return "请检查网络是否可访问公网免费图片生成服务；正式演示建议启动本地 Stable Diffusion/Forge，或配置有额度的 ZHIPU_IMAGE_MODELS。";
    }

    private static void clearImageFailureFields(Map<String, Object> response) {
        response.remove("description");
        response.remove("actionSuggestion");
        response.remove("error");
    }

    static String ollamaVisionUnavailableDescription(String language) {
        if ("en".equals(language)) {
            return "The local free vision model is unavailable. Start Ollama, pull a vision model such as qwen2.5vl:7b or qwen2.5vl:3b, or configure OLLAMA_BASE_URL, OLLAMA_VISION_MODEL, or OLLAMA_VISION_MODELS.";
        }
        return "本地免费图片理解模型不可用。请启动 Ollama，并拉取 Qwen2.5-VL 模型，例如 qwen2.5vl:7b 或 qwen2.5vl:3b；也可以通过 OLLAMA_BASE_URL、OLLAMA_VISION_MODEL 或 OLLAMA_VISION_MODELS 调整地址和模型。";
    }

    static String visionActionSuggestion() {
        return "推荐执行 ollama pull qwen2.5vl:7b 提升图生文质量；机器资源不足时执行 ollama pull qwen2.5vl:3b。也可通过 OLLAMA_VISION_MODELS 配置候选顺序。";
    }

    private static String configuredValue(Map<String, String> env, String key, String defaultValue) {
        return Optional.ofNullable(env.get(key))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    private String zhipuApiKey() {
        return configuredZhipuApiKey(environment);
    }

    private static double visionTemperature(String type) {
        if ("creative".equals(type)) {
            return 0.75D;
        }
        return 0.2D;
    }

    private static int visionMaxTokens(String type) {
        return "concise".equals(type) ? 500 : 1024;
    }

    static String normalizeImageSize(String size) {
        if (size == null || size.isBlank()) {
            return "1024x1024";
        }
        String normalized = size.trim();
        if ("512x512".equals(normalized)) {
            return "1024x1024";
        }
        return normalized;
    }

    private static ImageSize parseImageSize(String size) {
        String normalized = normalizeImageSize(size);
        String[] parts = normalized.split("x");
        if (parts.length != 2) {
            return new ImageSize(1024, 1024);
        }
        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            return new ImageSize(Math.max(256, width), Math.max(256, height));
        } catch (NumberFormatException ignored) {
            return new ImageSize(1024, 1024);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_OLLAMA_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static boolean usesLocalFreeProvider(String provider) {
        return PROVIDER_LOCAL_FREE.equals(provider) || PROVIDER_HYBRID.equals(provider);
    }

    private static boolean usesOllamaProvider(String provider) {
        return "ollama".equals(provider) || PROVIDER_HYBRID.equals(provider);
    }

    private static boolean usesZhipuProvider(String provider) {
        return PROVIDER_ZHIPU.equals(provider) || PROVIDER_HYBRID.equals(provider);
    }

    private static boolean usesPublicFreeProvider(String provider) {
        return PROVIDER_PUBLIC_FREE.equals(provider) || PROVIDER_HYBRID.equals(provider);
    }

    static String buildPublicImageUrl(String prompt, String imageSize, String model, long seed) {
        ImageSize size = parseImageSize(imageSize);
        String safePrompt = prompt == null ? "" : prompt.trim();
        String safeModel = model == null || model.isBlank() ? DEFAULT_PUBLIC_IMAGE_MODEL : model.trim();
        String encodedPrompt = URLEncoder.encode(safePrompt, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedModel = URLEncoder.encode(safeModel, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://image.pollinations.ai/prompt/" + encodedPrompt
                + "?width=" + size.width()
                + "&height=" + size.height()
                + "&model=" + encodedModel
                + "&nologo=true"
                + "&private=true"
                + "&safe=true"
                + "&seed=" + Math.abs(seed);
    }

    private static String imageStyleText(String style) {
        return switch (style == null ? "" : style) {
            case "anime" -> "动漫风格，清晰线条，干净色块，角色比例自然";
            case "cartoon" -> "卡通风格，造型简洁，色彩明快，边缘清晰";
            case "painting" -> "油画风格，笔触自然，层次丰富，具有艺术质感";
            case "sketch" -> "素描风格，线条准确，明暗关系清楚，纸面质感";
            default -> "写实风格，真实摄影质感，自然光线，高清细节";
        };
    }

    private static String imageCompositionText(String size) {
        if (size != null && size.startsWith("1536x")) {
            return "横向构图，画面层次从前景到背景自然展开";
        }
        if (size != null && size.endsWith("1536")) {
            return "纵向构图，主体占据视觉中心，留白适度";
        }
        return "方形构图，主体居中或三分法布局，画面稳定";
    }

    private static String enrichImagePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        String trimmed = prompt.trim();
        if (trimmed.length() <= 8 && trimmed.contains("春节")) {
            return trimmed + "，中国春节年夜饭场景，室内圆桌上摆放饺子、鱼、年菜和红包，背景有红灯笼、春联、窗花，窗外烟花，暖色灯光，喜庆但真实";
        }
        if (trimmed.length() <= 8) {
            return trimmed + "，选择一个清晰主体和具体环境，画面可识别，避免抽象化表达";
        }
        return trimmed;
    }

    private static LocalImageStats analyzeLocalImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int stepX = Math.max(1, width / 80);
        int stepY = Math.max(1, height / 80);
        long red = 0;
        long green = 0;
        long blue = 0;
        long luminance = 0;
        long saturation = 0;
        long edgeDiff = 0;
        int edgeCount = 0;
        int sampleCount = 0;
        Map<String, Integer> colorBuckets = new HashMap<>();

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                Color color = new Color(image.getRGB(x, y), true);
                red += color.getRed();
                green += color.getGreen();
                blue += color.getBlue();
                int lum = luminance(color);
                luminance += lum;
                float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                saturation += Math.round(hsb[1] * 100);
                colorBuckets.merge(colorBucket(color), 1, Integer::sum);
                sampleCount++;

                int nextX = Math.min(width - 1, x + stepX);
                int nextY = Math.min(height - 1, y + stepY);
                edgeDiff += Math.abs(lum - luminance(new Color(image.getRGB(nextX, y), true)));
                edgeDiff += Math.abs(lum - luminance(new Color(image.getRGB(x, nextY), true)));
                edgeCount += 2;
            }
        }

        int safeSamples = Math.max(1, sampleCount);
        List<String> dominantColors = colorBuckets.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        return new LocalImageStats(
                width,
                height,
                dominantColors,
                (int) (red / safeSamples),
                (int) (green / safeSamples),
                (int) (blue / safeSamples),
                (int) (luminance / safeSamples),
                (int) (saturation / safeSamples),
                edgeCount == 0 ? 0 : (int) (edgeDiff / edgeCount)
        );
    }

    private static String buildChineseLocalImageSummary(LocalImageStats stats, String type) {
        String colors = String.join("、", stats.dominantColors());
        String base = "本地基础描述（非大模型识图）：这是一张" + stats.orientationZh()
                + "图片，尺寸约为 " + stats.width() + "×" + stats.height()
                + "，整体" + stats.brightnessZh()
                + "，饱和度" + stats.saturationZh()
                + "，主要色彩倾向为" + colors
                + "，画面细节复杂度" + stats.complexityZh() + "。";
        String limitation = "当前视觉模型资源不可用，因此本地摘要只能描述尺寸、方向、颜色、亮暗和复杂度，无法可靠识别人物身份、具体物体名称或场景语义。";
        if ("concise".equals(type)) {
            return base + "\n" + limitation;
        }
        if ("creative".equals(type)) {
            return base + "\n可作为占位文案参考，但正式描述建议接入可用视觉大模型后重新生成。\n" + limitation;
        }
        return base + "\n" + limitation + "\n处理建议：启动 Ollama 并加载 qwen2.5vl:3b 后，可生成真正的语义级图片描述。";
    }

    private static String buildEnglishLocalImageSummary(LocalImageStats stats, String type) {
        String colors = String.join(", ", stats.dominantColorsEn());
        String base = "Local basic summary (not a vision-model caption): this is a "
                + stats.orientationEn() + " image, about " + stats.width() + "x" + stats.height()
                + " pixels. It looks " + stats.brightnessEn()
                + ", has " + stats.saturationEn() + " saturation, dominant colors around "
                + colors + ", and " + stats.complexityEn() + " visual detail.";
        String limitation = "The vision model is currently unavailable, so this local summary only describes measurable image properties and cannot reliably identify people, objects, or scene semantics.";
        if ("concise".equals(type)) {
            return base + "\n" + limitation;
        }
        return base + "\n" + limitation + "\nSuggestion: start Ollama with qwen2.5vl:3b to generate a true semantic image description.";
    }

    private static int luminance(Color color) {
        return Math.round((0.2126F * color.getRed()) + (0.7152F * color.getGreen()) + (0.0722F * color.getBlue()));
    }

    private static String colorBucket(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        int brightness = Math.round(hsb[2] * 100);
        int saturation = Math.round(hsb[1] * 100);
        if (brightness < 18) {
            return "黑色或深灰";
        }
        if (saturation < 12) {
            return brightness > 78 ? "白色或浅灰" : "灰色";
        }
        float hue = hsb[0] * 360;
        if (hue < 18 || hue >= 345) {
            return "红色";
        }
        if (hue < 45) {
            return "橙色";
        }
        if (hue < 70) {
            return "黄色";
        }
        if (hue < 160) {
            return "绿色";
        }
        if (hue < 200) {
            return "青色";
        }
        if (hue < 255) {
            return "蓝色";
        }
        if (hue < 300) {
            return "紫色";
        }
        return "粉紫色";
    }

    private record LocalImageStats(
            int width,
            int height,
            List<String> dominantColors,
            int averageRed,
            int averageGreen,
            int averageBlue,
            int averageLuminance,
            int averageSaturation,
            int averageEdgeDiff
    ) {
        String orientationZh() {
            if (width > height * 1.15) {
                return "横向";
            }
            if (height > width * 1.15) {
                return "纵向";
            }
            return "近似方形";
        }

        String orientationEn() {
            if (width > height * 1.15) {
                return "landscape";
            }
            if (height > width * 1.15) {
                return "portrait";
            }
            return "nearly square";
        }

        String brightnessZh() {
            if (averageLuminance < 75) {
                return "偏暗";
            }
            if (averageLuminance > 185) {
                return "偏亮";
            }
            return "明暗适中";
        }

        String brightnessEn() {
            if (averageLuminance < 75) {
                return "dark";
            }
            if (averageLuminance > 185) {
                return "bright";
            }
            return "moderately lit";
        }

        String saturationZh() {
            if (averageSaturation < 18) {
                return "较低";
            }
            if (averageSaturation > 58) {
                return "较高";
            }
            return "中等";
        }

        String saturationEn() {
            if (averageSaturation < 18) {
                return "low";
            }
            if (averageSaturation > 58) {
                return "high";
            }
            return "medium";
        }

        String complexityZh() {
            if (averageEdgeDiff > 38) {
                return "较高";
            }
            if (averageEdgeDiff < 14) {
                return "较低";
            }
            return "中等";
        }

        String complexityEn() {
            if (averageEdgeDiff > 38) {
                return "high";
            }
            if (averageEdgeDiff < 14) {
                return "low";
            }
            return "medium";
        }

        List<String> dominantColorsEn() {
            return dominantColors.stream()
                    .map(color -> switch (color) {
                        case "黑色或深灰" -> "black or dark gray";
                        case "白色或浅灰" -> "white or light gray";
                        case "灰色" -> "gray";
                        case "红色" -> "red";
                        case "橙色" -> "orange";
                        case "黄色" -> "yellow";
                        case "绿色" -> "green";
                        case "青色" -> "cyan";
                        case "蓝色" -> "blue";
                        case "紫色" -> "purple";
                        case "粉紫色" -> "magenta";
                        default -> color;
                    })
                    .toList();
        }
    }

    private List<String> visionModelCandidates() {
        String configuredModels = Optional.ofNullable(environment.get("ZHIPU_VISION_MODELS"))
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_VISION_MODELS);
        return Arrays.stream(configuredModels.split(","))
                .map(String::trim)
                .filter(model -> !model.isBlank())
                .distinct()
                .toList();
    }

    private List<String> imageModelCandidates() {
        return configuredZhipuImageModels(environment);
    }

    private static boolean isPublicQueueFull(String error) {
        return error != null && (error.contains("Queue full") || error.contains("HTTP 402"));
    }

    private static void sleepBeforePublicRetry() {
        try {
            Thread.sleep(4000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record VisionApiResult(
            boolean success,
            String message,
            String description,
            String error,
            String model,
            java.util.List<String> modelsTried,
            String actionSuggestion,
            String provider,
            List<Map<String, Object>> modelAvailability
    ) {
        static VisionApiResult success(String description, String model, String provider) {
            return new VisionApiResult(
                    true,
                    "图片描述生成成功",
                    description,
                    "",
                    model,
                    List.of(model),
                    "",
                    provider,
                    List.of(buildModelAvailability(model, provider, true, ""))
            );
        }

        static VisionApiResult failure(String message, String description, String error, String model, String actionSuggestion, String provider) {
            return new VisionApiResult(
                    false,
                    message,
                    description,
                    error == null ? "" : error,
                    model,
                    List.of(),
                    actionSuggestion,
                    provider,
                    List.of(buildModelAvailability(model, provider, false, error))
            );
        }

        static VisionApiResult success(String description, String model, String provider, List<String> modelsTried) {
            return new VisionApiResult(
                    true,
                    "图片描述生成成功",
                    description,
                    "",
                    model,
                    normalizeModelsTried(modelsTried),
                    "",
                    provider,
                    modelsTried.isEmpty()
                            ? List.of(buildModelAvailability(model, provider, true, ""))
                            : normalizeModelsTried(modelsTried).stream()
                            .map(availableModel -> buildModelAvailability(availableModel, provider, true, ""))
                            .toList()
            );
        }

        static VisionApiResult success(
                String description,
                String model,
                String provider,
                List<String> modelsTried,
                List<Map<String, Object>> modelAvailability
        ) {
            return new VisionApiResult(
                    true,
                    "图片描述生成成功",
                    description,
                    "",
                    model,
                    normalizeModelsTried(modelsTried),
                    "",
                    provider,
                    normalizeModelAvailability(modelAvailability)
            );
        }

        static VisionApiResult failure(
                String message,
                String description,
                String error,
                String model,
                List<String> modelsTried,
                List<Map<String, Object>> modelAvailability,
                String actionSuggestion,
                String provider
        ) {
            return new VisionApiResult(
                    false,
                    message,
                    description,
                    error == null ? "" : error,
                    model,
                    normalizeModelsTried(modelsTried),
                    actionSuggestion,
                    provider,
                    normalizeModelAvailability(modelAvailability)
            );
        }
    }

    private static Map<String, Object> buildModelAvailability(String model, String provider, boolean available, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("model", model == null ? "" : model);
        entry.put("provider", provider == null ? "" : provider);
        entry.put("available", available);
        if (reason != null && !reason.isBlank()) {
            entry.put("reason", reason);
        }
        return entry;
    }

    private static List<Map<String, Object>> normalizeModelAvailability(List<Map<String, Object>> modelAvailability) {
        if (modelAvailability == null || modelAvailability.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> deduplicated = new LinkedHashMap<>();
        for (Map<String, Object> item : modelAvailability) {
            if (item == null) {
                continue;
            }
            String model = String.valueOf(item.getOrDefault("model", "")).trim();
            if (model.isBlank()) {
                continue;
            }
            deduplicated.put(model, item);
        }
        return List.copyOf(deduplicated.values());
    }

    private static List<Map<String, Object>> markModelAvailability(
            List<Map<String, Object>> modelAvailability,
            String model,
            boolean available,
            String reason
    ) {
        List<Map<String, Object>> result = new ArrayList<>(Optional.ofNullable(modelAvailability).orElseGet(ArrayList::new));
        String normalizedModel = model == null ? "" : model.trim();
        if (normalizedModel.isBlank()) {
            return result;
        }
        for (Map<String, Object> item : result) {
            if (normalizedModel.equals(String.valueOf(item.getOrDefault("model", "")).trim())) {
                item.put("available", available);
                if (reason != null && !reason.isBlank()) {
                    item.put("reason", reason);
                }
                return result;
            }
        }
        result.add(buildModelAvailability(normalizedModel, PROVIDER_ZHIPU, available, reason));
        return result;
    }

    private static List<String> normalizeModelsTried(List<String> modelsTried) {
        if (modelsTried == null || modelsTried.isEmpty()) {
            return List.of();
        }
        return modelsTried.stream()
                .filter(model -> model != null && !model.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        }

    private record ImageGenerationResult(
            boolean success,
            List<String> images,
            String model,
            String message,
            String description,
            String error,
            String actionSuggestion
    ) {
        static ImageGenerationResult success(List<String> images, String model) {
            return new ImageGenerationResult(true, images, model, "图片生成成功", "", "", "");
        }

        static ImageGenerationResult failure(
                String message,
                String description,
                String error,
                String model,
                String actionSuggestion
        ) {
            return new ImageGenerationResult(false, List.of(), model, message, description, error == null ? "" : error, actionSuggestion);
        }
    }

    private record ImageSize(int width, int height) {
    }

    private record ImageDownloadResult(boolean success, String localPath, String error) {
        static ImageDownloadResult success(String localPath) {
            return new ImageDownloadResult(true, localPath, "");
        }

        static ImageDownloadResult failure(String error) {
            return new ImageDownloadResult(false, "", error == null ? "" : error);
        }
    }

    private static final class StreamParts {
        private StreamParts() {
        }

        static String of(String... parts) {
            return Arrays.stream(parts)
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining("，"));
        }
    }


    /**
     * 下载图片并保存到本地
     * @param imageUrl 图片URL
     * @return 本地图片路径
     */
    private String downloadImageFromUrl(String imageUrl) {
        try {
            // 生成唯一的文件名
            String fileName = UUID.randomUUID().toString() + ".png";
            Path filePath = Paths.get(UPLOAD_DIR, fileName);

            log.info("开始下载图片，URL: {}", imageUrl);
            log.info("保存路径: {}", filePath.toAbsolutePath());

            // 确保上传目录存在
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("创建上传目录: {}", uploadPath.toAbsolutePath());
            }

            // 下载内容
            URI uri = new URI(imageUrl);
            try (InputStream inputStream = uri.toURL().openStream()) {
                // 读取内容
                byte[] content = inputStream.readAllBytes();
                log.info("下载内容大小: {} bytes", content.length);

                // 检查是否是HTML页面（Pollinations AI返回的是HTML）
                // 首先检查内容是否是有效的图片数据（PNG、JPG、WebP等）
                boolean isImage = false;
                if (content.length > 8) {
                    // 检查图片文件头
                    String magic = Integer.toHexString((content[0] & 0xFF) << 24 | (content[1] & 0xFF) << 16 | (content[2] & 0xFF) << 8 | (content[3] & 0xFF)).toUpperCase();
                    isImage = magic.startsWith("89504E47") || // PNG
                            magic.startsWith("FFD8FF") || // JPEG
                            magic.startsWith("52494646") || // WebP
                            magic.startsWith("47494638"); // GIF
                }

                // 只有当不是图片且包含HTML标签时才视为HTML
                String contentStr = new String(content, StandardCharsets.UTF_8);
                boolean isHtml = !isImage && (contentStr.contains("<!DOCTYPE html") || contentStr.contains("<html"));
                log.info("下载内容是否为图片: {}", isImage);
                log.info("下载内容是否为HTML: {}", isHtml);

                if (isHtml) {
                    // 尝试从HTML中提取图片URL
                    log.info("尝试从HTML中提取图片URL");

                    // 尝试多种方法提取图片URL
                    String actualImageUrl = null;

                    // 方法1: 查找<img>标签中的src属性
                    int imgTagStart = contentStr.indexOf("<img");
                    log.info("找到<img>标签的位置: {}", imgTagStart);

                    if (imgTagStart != -1) {
                        int srcStart = contentStr.indexOf("src=", imgTagStart);
                        log.info("找到src属性的位置: {}", srcStart);

                        if (srcStart != -1) {
                            // 处理不同的引号情况
                            char quoteChar = contentStr.charAt(srcStart + 5);
                            int srcEnd = contentStr.indexOf(quoteChar, srcStart + 6);
                            log.info("找到src属性结束位置: {}", srcEnd);

                            if (srcEnd != -1) {
                                actualImageUrl = contentStr.substring(srcStart + 6, srcEnd);
                                log.info("提取到的图片URL: {}", actualImageUrl);
                            }
                        }
                    }

                    // 方法2: 查找meta标签中的图片URL
                    if (actualImageUrl == null) {
                        int metaTagStart = contentStr.indexOf("<meta property=\"og:image\"");
                        log.info("找到og:image meta标签的位置: {}", metaTagStart);

                        if (metaTagStart != -1) {
                            int contentStart = contentStr.indexOf("content=", metaTagStart);
                            log.info("找到content属性的位置: {}", contentStart);

                            if (contentStart != -1) {
                                // 处理不同的引号情况
                                char quoteChar = contentStr.charAt(contentStart + 8);
                                int contentEnd = contentStr.indexOf(quoteChar, contentStart + 9);
                                log.info("找到content属性结束位置: {}", contentEnd);

                                if (contentEnd != -1) {
                                    actualImageUrl = contentStr.substring(contentStart + 9, contentEnd);
                                    log.info("从meta标签提取到的图片URL: {}", actualImageUrl);
                                }
                            }
                        }
                    }

                    // 方法3: 查找JSON-LD中的图片URL
                    if (actualImageUrl == null) {
                        int jsonLdStart = contentStr.indexOf("<script type=\"application/ld+json\">");
                        log.info("找到JSON-LD脚本的位置: {}", jsonLdStart);

                        if (jsonLdStart != -1) {
                            int jsonLdEnd = contentStr.indexOf("</script>", jsonLdStart);
                            log.info("找到JSON-LD脚本结束位置: {}", jsonLdEnd);

                            if (jsonLdEnd != -1) {
                                String jsonLdContent = contentStr.substring(jsonLdStart + 32, jsonLdEnd);
                                log.info("提取到JSON-LD内容");

                                // 尝试从JSON中提取图片URL
                                int imageStart = jsonLdContent.indexOf("\"image\":");
                                if (imageStart != -1) {
                                    int urlStart = jsonLdContent.indexOf("\"", imageStart + 8);
                                    int urlEnd = jsonLdContent.indexOf("\"", urlStart + 1);
                                    if (urlStart != -1 && urlEnd != -1) {
                                        actualImageUrl = jsonLdContent.substring(urlStart + 1, urlEnd);
                                        log.info("从JSON-LD提取到的图片URL: {}", actualImageUrl);
                                    }
                                }
                            }
                        }
                    }

                    // 如果找到图片URL，下载并保存
                    if (actualImageUrl != null) {
                        // 如果是相对路径，转换为绝对路径
                        if (!actualImageUrl.startsWith("http")) {
                            // 构建绝对URL
                            URI baseUri = new URI(imageUrl);
                            URI absoluteUri = baseUri.resolve(actualImageUrl);
                            actualImageUrl = absoluteUri.toString();
                            log.info("转换为绝对URL: {}", actualImageUrl);
                        }

                        // 下载实际的图片
                        log.info("开始下载实际图片: {}", actualImageUrl);
                        try (InputStream imageInputStream = new URI(actualImageUrl).toURL().openStream()) {
                            byte[] imageContent = imageInputStream.readAllBytes();
                            log.info("实际图片大小: {} bytes", imageContent.length);
                            Files.write(filePath, imageContent);
                            log.info("图片保存成功: {}", filePath.toAbsolutePath());
                        }
                    } else {
                        log.error("未找到图片URL");
                    }
                } else {
                    // 直接是图片文件
                    log.info("直接保存图片文件");
                    Files.write(filePath, content);
                    log.info("图片保存成功: {}", filePath.toAbsolutePath());
                }
            }

            // 检查文件是否存在
            if (Files.exists(filePath)) {
                log.info("文件存在，大小: {} bytes", Files.size(filePath));
                // 返回相对路径
                String localImagePath = "/api/image/download/" + fileName;
                log.info("返回本地图片路径: {}", localImagePath);
                return localImagePath;
            } else {
                log.error("文件不存在: {}", filePath.toAbsolutePath());
                return null;
            }
        } catch (Exception e) {
            log.error("下载图片失败: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private ImageDownloadResult downloadPublicImageFromUrl(String imageUrl) {
        String fileName = UUID.randomUUID() + ".png";
        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            HttpURLConnection connection = (HttpURLConnection) new URI(imageUrl).toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/*,*/*");

            int status = connection.getResponseCode();
            byte[] content;
            try (InputStream inputStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                content = inputStream == null ? new byte[0] : inputStream.readAllBytes();
            } finally {
                connection.disconnect();
            }
            if (status >= 400) {
                return ImageDownloadResult.failure("HTTP " + status + ": " + responseSnippet(content));
            }
            if (!looksLikeImage(content)) {
                return ImageDownloadResult.failure("公网免费图片模型返回非图片内容: " + responseSnippet(content));
            }
            Files.write(filePath, content);
            return ImageDownloadResult.success("/api/image/download/" + fileName);
        } catch (Exception e) {
            return ImageDownloadResult.failure(e.getMessage());
        }
    }

    private static boolean looksLikeImage(byte[] content) {
        if (content == null || content.length < 4) {
            return false;
        }
        int magic = (content[0] & 0xFF) << 24
                | (content[1] & 0xFF) << 16
                | (content[2] & 0xFF) << 8
                | (content[3] & 0xFF);
        String header = Integer.toHexString(magic).toUpperCase(Locale.ROOT);
        return header.startsWith("89504E47")
                || header.startsWith("FFD8FF")
                || header.startsWith("52494646")
                || header.startsWith("47494638");
    }

    private static String responseSnippet(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        String text = new String(content, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

}
