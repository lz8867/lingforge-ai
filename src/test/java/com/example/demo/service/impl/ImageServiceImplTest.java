package com.example.demo.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ImageServiceImplTest {

    private static final String ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=";

    @Test
    void textToImagePromptShouldAddStyleCompositionAndQualityConstraints() {
        String prompt = ImageServiceImpl.buildImagePrompt("一只橘猫坐在窗台上", "painting", "1536x1024");

        assertTrue(prompt.contains("一只橘猫坐在窗台上"));
        assertTrue(prompt.contains("油画风格"));
        assertTrue(prompt.contains("横向构图"));
        assertTrue(prompt.contains("主体清晰"));
        assertTrue(prompt.contains("光影层次"));
        assertTrue(prompt.contains("避免文字"));
        assertTrue(prompt.contains("水印"));
    }

    @Test
    void shortFestivalPromptShouldBeExpandedIntoConcreteRecognizableScene() {
        String prompt = ImageServiceImpl.buildImagePrompt("春节", "realistic", "512x512");

        assertTrue(prompt.contains("中国春节年夜饭场景"));
        assertTrue(prompt.contains("室内圆桌"));
        assertTrue(prompt.contains("红灯笼"));
        assertTrue(prompt.contains("春联"));
        assertTrue(prompt.contains("窗外烟花"));
        assertTrue(prompt.contains("红包"));
        assertTrue(prompt.contains("具象、可识别"));
        assertTrue(prompt.contains("避免抽象图案"));
        assertFalse(prompt.contains("无人物"));
    }

    @Test
    void smallImageSizeShouldUseModelNativeQualitySize() {
        assertEquals("1024x1024", ImageServiceImpl.normalizeImageSize("512x512"));
    }

    @Test
    void zhipuApiKeyShouldPreferEnvironmentOverride() {
        assertEquals("replacement-key", ImageServiceImpl.configuredZhipuApiKey(Map.of("ZHIPU_API_KEY", "replacement-key")));
        assertFalse(ImageServiceImpl.configuredZhipuApiKey(Map.of("ZHIPU_API_KEY", " ")).isBlank());
    }

    @Test
    void imageModelCandidatesShouldPreferQualityModelAndAllowEnvironmentOverride() {
        assertEquals(
                List.of("cogview-4-250304", "cogview-3-flash"),
                ImageServiceImpl.configuredZhipuImageModels(Map.of())
        );
        assertEquals(
                List.of("custom-image-a", "custom-image-b"),
                ImageServiceImpl.configuredZhipuImageModels(Map.of("ZHIPU_IMAGE_MODELS", " custom-image-a, custom-image-b, custom-image-a "))
        );
    }

    @Test
    void mediaImageAndVisionProvidersShouldDefaultToHybridFallback() {
        assertEquals("hybrid", ImageServiceImpl.configuredImageProvider(Map.of()));
        assertEquals("hybrid", ImageServiceImpl.configuredVisionProvider(Map.of()));
    }

    @Test
    void localVisionModelShouldDefaultToSmallerRunnableQwenVisionModel() {
        assertEquals("qwen2.5vl:3b", ImageServiceImpl.configuredOllamaVisionModel(Map.of()));
        assertEquals(
                "qwen2.5vl:7b",
                ImageServiceImpl.configuredOllamaVisionModel(Map.of("OLLAMA_VISION_MODEL", " qwen2.5vl:7b "))
        );
    }

    @Test
    void localVisionModelsShouldPreferHigherQualityCandidateWithSmallerFallback() {
        assertEquals(
                List.of("qwen2.5vl:7b", "qwen2.5vl:3b"),
                ImageServiceImpl.configuredOllamaVisionModels(Map.of())
        );
        assertEquals(
                List.of("qwen2.5vl:3b"),
                ImageServiceImpl.configuredOllamaVisionModels(Map.of("OLLAMA_VISION_MODEL", " qwen2.5vl:3b "))
        );
        assertEquals(
                List.of("vision-a", "vision-b"),
                ImageServiceImpl.configuredOllamaVisionModels(Map.of("OLLAMA_VISION_MODELS", " vision-a, vision-b, vision-a "))
        );
    }

    @Test
    void publicFreeImageUrlShouldEncodePromptSizeModelAndSeed() {
        String imageUrl = ImageServiceImpl.buildPublicImageUrl(
                "春节 年夜饭",
                "512x512",
                "flux",
                1234L
        );

        assertTrue(imageUrl.startsWith("https://image.pollinations.ai/prompt/"));
        assertTrue(imageUrl.contains("春节".replace("春", "%E6%98%A5").replace("节", "%E8%8A%82")) || imageUrl.contains("%E6%98%A5%E8%8A%82"));
        assertTrue(imageUrl.contains("width=1024"));
        assertTrue(imageUrl.contains("height=1024"));
        assertTrue(imageUrl.contains("model=flux"));
        assertTrue(imageUrl.contains("seed=1234"));
    }

    @Test
    void publicImagePromptShouldStayShortForAnonymousQueue() {
        String publicPrompt = ImageServiceImpl.buildPublicImagePrompt("春节", "realistic");
        String detailedPrompt = ImageServiceImpl.buildImagePrompt("春节", "realistic", "512x512");

        assertTrue(publicPrompt.contains("春节"));
        assertTrue(publicPrompt.contains("年夜饭"));
        assertTrue(publicPrompt.contains("写实"));
        assertTrue(publicPrompt.length() < detailedPrompt.length());
        assertTrue(publicPrompt.length() <= 220);
    }

    @Test
    void flashImageModelShouldUseShortConcretePromptInsteadOfDetailedConstraintPrompt() {
        String flashPrompt = ImageServiceImpl.buildZhipuImagePrompt("春节", "realistic", "512x512", "cogview-3-flash");
        String qualityPrompt = ImageServiceImpl.buildZhipuImagePrompt("春节", "realistic", "512x512", "cogview-4-250304");

        assertTrue(flashPrompt.contains("春节"));
        assertTrue(flashPrompt.contains("年夜饭"));
        assertTrue(flashPrompt.contains("红灯笼"));
        assertTrue(flashPrompt.contains("写实"));
        assertTrue(flashPrompt.length() < qualityPrompt.length());
        assertTrue(flashPrompt.length() <= 220);
        assertFalse(flashPrompt.contains("避免抽象图案"));
        assertFalse(flashPrompt.contains("错误可读文字"));
    }

    @Test
    void imageQuotaErrorShouldExplainHighQualityModelResourceIssue() {
        String error = "Insufficient balance or no resource package. Please recharge.";

        assertTrue(ImageServiceImpl.isImageQuotaError(error));
        assertTrue(ImageServiceImpl.imageQuotaDescription().contains("高质量图片模型资源"));
        assertTrue(ImageServiceImpl.imageQuotaActionSuggestion().contains("ZHIPU_IMAGE_MODELS"));
    }

    @Test
    void textToImageShouldUseLocalFreeImageProviderByDefault() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ImageServiceImpl imageService = new ImageServiceImpl(restTemplate, Map.of(
                "FREE_IMAGE_API_URL", "http://localhost:7860/sdapi/v1/txt2img",
                "FREE_IMAGE_MODEL", "flux.1-schnell"
        ));

        server.expect(requestTo("http://localhost:7860/sdapi/v1/txt2img"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model": "flux.1-schnell",
                          "prompt": "请生成一张具象、可识别的图片，画面主题：一只橘猫，选择一个清晰主体和具体环境，画面可识别，避免抽象化表达，写实风格，真实摄影质感，自然光线，高清细节，方形构图，主体居中或三分法布局，画面稳定，明确主体和真实场景，主体、空间、物件之间关系自然，主体清晰，构图完整，视觉焦点明确，细节丰富但不杂乱，高质量画面，光影层次自然，色彩协调，材质和空间关系可信，避免抽象图案、乱码纹理、无意义色块、畸形肢体、重复主体、低清晰度、过曝、模糊、噪点、变形，避免文字、字幕、水印、Logo 和边框；节庆装饰可以出现，但避免生成错误可读文字",
                          "negative_prompt": "文字, 水印, logo, 低清晰度, 模糊, 噪点, 畸形, 变形, 重复主体",
                          "width": 1024,
                          "height": 1024,
                          "batch_size": 1,
                          "steps": 4,
                          "cfg_scale": 1.0,
                          "sampler_name": "Euler"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "images": ["%s"]
                        }
                        """.formatted(ONE_PIXEL_PNG_BASE64), MediaType.APPLICATION_JSON));

        Map<String, Object> response = imageService.generateImage(Map.of(
                "prompt", "一只橘猫",
                "size", "512x512",
                "count", "1",
                "style", "realistic"
        ));

        assertEquals(true, response.get("success"));
        assertEquals("local-free", response.get("provider"));
        assertEquals("flux.1-schnell", response.get("imageModel"));
        assertTrue(String.valueOf(response.get("image")).startsWith("/api/image/download/"));
        deleteGeneratedImage(response);
        server.verify();
    }

    @Test
    void zhipuImageItemWithBase64ShouldReturnDownloadPath() throws Exception {
        ImageServiceImpl imageService = new ImageServiceImpl(new RestTemplate(), Map.of());

        ai.z.openapi.service.image.Image imageResult = new ai.z.openapi.service.image.Image();
        imageResult.setB64Json(ONE_PIXEL_PNG_BASE64);

        Method extractMethod = ImageServiceImpl.class.getDeclaredMethod("extractGeneratedImageReference", ai.z.openapi.service.image.Image.class);
        extractMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Optional<String> savedPath = (Optional<String>) extractMethod.invoke(imageService, imageResult);

        assertTrue(savedPath.isPresent());
        assertTrue(savedPath.get().startsWith("/api/image/download/"));
        deleteGeneratedImage(savedPath.get());
    }

    @Test
    void zhipuImageItemFailureReasonShouldDifferentiateBase64AndUrl() throws Exception {
        Method reasonMethod = ImageServiceImpl.class.getDeclaredMethod("imageResultDownloadFailureReason", ai.z.openapi.service.image.Image.class);
        reasonMethod.setAccessible(true);

        String nullReason = (String) reasonMethod.invoke(null, new Object[]{null});
        assertEquals("文生图 API 响应中的图片项为空", nullReason);

        ai.z.openapi.service.image.Image urlOnly = new ai.z.openapi.service.image.Image();
        urlOnly.setUrl("http://example.com/image.png");
        String urlReason = (String) reasonMethod.invoke(null, urlOnly);
        assertEquals("文生图 API 响应中的图片链接无法解析或下载", urlReason);

        ai.z.openapi.service.image.Image base64Only = new ai.z.openapi.service.image.Image();
        base64Only.setB64Json(ONE_PIXEL_PNG_BASE64);
        String base64Reason = (String) reasonMethod.invoke(null, base64Only);
        assertEquals("文生图 API 返回 base64 内容但未能生成图片文件", base64Reason);
    }

    @Test
    void imageDescriptionPromptShouldRequireGroundedStructuredDescription() {
        String prompt = ImageServiceImpl.buildVisionPrompt("detailed", "zh");

        assertTrue(prompt.contains("只描述图片中可见内容"));
        assertTrue(prompt.contains("不要臆测"));
        assertTrue(prompt.contains("图片类型"));
        assertTrue(prompt.contains("主体"));
        assertTrue(prompt.contains("可能是"));
        assertTrue(prompt.contains("虚构角色"));
        assertTrue(prompt.contains("具体角色名"));
        assertTrue(prompt.contains("作品名"));
        assertTrue(prompt.contains("不要只写"));
        assertTrue(prompt.contains("避免泛泛使用"));
        assertTrue(prompt.contains("模糊"));
        assertTrue(prompt.contains("视觉证据"));
        assertTrue(prompt.contains("构图"));
        assertTrue(prompt.contains("风格"));
        assertTrue(prompt.contains("环境"));
        assertTrue(prompt.contains("颜色"));
        assertTrue(prompt.contains("空间关系"));
        assertTrue(prompt.contains("文字"));
        assertTrue(prompt.contains("不确定"));
    }

    @Test
    void detailedVisionPromptShouldPreferPolishedReusableDescriptionOverFieldDump() {
        String prompt = ImageServiceImpl.buildVisionPrompt("detailed", "zh");

        assertTrue(prompt.contains("自然成文"));
        assertTrue(prompt.contains("主描述"));
        assertTrue(prompt.contains("细节补充"));
        assertTrue(prompt.contains("可复用提示词"));
        assertTrue(prompt.contains("文生图"));
        assertTrue(prompt.contains("字段清单式"));
        assertTrue(prompt.contains("图片类型：主体：动作/姿态"));
    }

    @Test
    void visionSystemPromptShouldAllowFictionalCharacterRecognitionButForbidRealPersonIdentification() {
        String systemPrompt = ImageServiceImpl.buildVisionSystemPrompt("zh");

        assertTrue(systemPrompt.contains("不要识别真实人物身份"));
        assertTrue(systemPrompt.contains("虚构角色"));
        assertTrue(systemPrompt.contains("可能是"));
        assertTrue(systemPrompt.contains("具体角色名"));
        assertTrue(systemPrompt.contains("作品名"));
        assertTrue(systemPrompt.contains("证据"));
    }

    @Test
    void detailedVisionRequestShouldUseLowerTemperatureForLessVagueDescription() {
        Map<String, Object> request = ImageServiceImpl.buildOllamaVisionRequest(
                ONE_PIXEL_PNG_BASE64,
                "detailed",
                "zh",
                "qwen2.5vl:3b"
        );

        Map<String, Object> options = (Map<String, Object>) request.get("options");
        assertEquals(0.2D, options.get("temperature"));
    }

    @Test
    void visionUnavailableErrorShouldBeDetectedFromZhipuContentTypeError() {
        String errorBody = "{\"error\":{\"code\":\"1210\",\"message\":\"messages.content.type 参数非法，取值范围 ['text']\"}}";

        assertTrue(ImageServiceImpl.isVisionModelUnavailableError(errorBody));
    }

    @Test
    void visionQuotaErrorShouldExplainAccountResourceIssue() {
        String errorBody = "{\"error\":{\"code\":\"1113\",\"message\":\"余额不足或无可用资源包,请充值。\"}}";

        assertTrue(ImageServiceImpl.isVisionQuotaError(errorBody));
        assertTrue(ImageServiceImpl.visionQuotaDescription("zh").contains("余额不足或无可用资源包"));
    }

    @Test
    void visionUnavailableMessageShouldExplainConfigurationInsteadOfPretendingToDescribeImage() {
        String fallback = ImageServiceImpl.visionUnavailableDescription("zh");

        assertTrue(fallback.contains("当前没有可用的图片理解模型"));
        assertTrue(fallback.contains("Ollama"));
        assertTrue(fallback.contains("Qwen2.5-VL"));
        assertFalse(fallback.contains("测试图片"));
    }

    @Test
    void detailedVisionRequestShouldStayWithinFlashModelTokenLimit() {
        Map<String, Object> request = ImageServiceImpl.buildOllamaVisionRequest(
                ONE_PIXEL_PNG_BASE64,
                "detailed",
                "zh",
                "qwen2.5vl:7b"
        );

        Map<String, Object> options = (Map<String, Object>) request.get("options");
        assertEquals(1024, options.get("num_predict"));
    }

    @Test
    void readableUploadedImageShouldBeReencodedBeforeVisionModelCall() throws Exception {
        byte[] uploadedBytes = Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64);

        Optional<String> normalized = ImageServiceImpl.normalizeVisionImageBase64(uploadedBytes);

        assertTrue(normalized.isPresent());
        assertFalse(normalized.get().isBlank());
        assertFalse(normalized.get().equals(ONE_PIXEL_PNG_BASE64));
        BufferedImage normalizedImage = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(normalized.get())));
        assertTrue(normalizedImage != null);
        assertFalse(normalizedImage.getColorModel().hasAlpha());
        assertTrue(normalizedImage.getWidth() >= 32);
        assertTrue(normalizedImage.getHeight() >= 32);
    }

    @Test
    void imageDescriptionShouldUseOllamaVisionModelByDefault() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ImageServiceImpl imageService = new ImageServiceImpl(restTemplate, Map.of(
                "OLLAMA_BASE_URL", "http://localhost:11434",
                "OLLAMA_VISION_MODEL", "qwen2.5vl:7b"
        ));
        byte[] imageBytes = pngBytes(1, 1, Color.WHITE, Color.WHITE);
        String normalizedImage = ImageServiceImpl.normalizeVisionImageBase64(imageBytes).orElseThrow();

        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("qwen2.5vl:7b"))
                .andExpect(jsonPath("$.prompt").value(
                        ImageServiceImpl.buildVisionSystemPrompt("zh")
                                + "\n\n"
                                + ImageServiceImpl.buildVisionPrompt("concise", "zh")
                ))
                .andExpect(jsonPath("$.images[0]").value(normalizedImage))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.options.temperature").value(0.2D))
                .andExpect(jsonPath("$.options.num_predict").value(500))
                .andRespond(withSuccess("""
                        {
                          "response": "图片中是一块明亮的白色区域。"
                        }
                        """, MediaType.APPLICATION_JSON));

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "white.png",
                "image/png",
                imageBytes
        );

        Map<String, Object> response = imageService.describeImage(image, "concise", "zh");

        assertEquals(true, response.get("success"));
        assertEquals("ollama", response.get("provider"));
        assertEquals("qwen2.5vl:7b", response.get("model"));
        assertEquals("图片中是一块明亮的白色区域。", response.get("description"));
        server.verify();
    }

    @Test
    void imageDescriptionShouldTryHigherQualityLocalVisionModelBeforeSmallerFallback() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ImageServiceImpl imageService = new ImageServiceImpl(restTemplate, Map.of(
                "OLLAMA_BASE_URL", "http://localhost:11434",
                "MEDIA_VISION_PROVIDER", "ollama"
        ));

        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("qwen2.5vl:7b"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"model qwen2.5vl:7b not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("qwen2.5vl:3b"))
                .andRespond(withSuccess("""
                        {
                          "response": "图片中是一名黑发红眼的动漫角色，红黑圆形纹样和黑色长袍很醒目。"
                        }
                        """, MediaType.APPLICATION_JSON));

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "anime.png",
                "image/png",
                pngBytes(8, 8, Color.RED, Color.BLACK)
        );

        Map<String, Object> response = imageService.describeImage(image, "detailed", "zh");

        assertEquals(true, response.get("success"));
        assertEquals("qwen2.5vl:3b", response.get("model"));
        assertEquals(List.of("qwen2.5vl:7b", "qwen2.5vl:3b"), response.get("visionModelsTried"));
        List<Map<String, Object>> availability = (List<Map<String, Object>>) response.get("modelAvailability");
        assertEquals(false, availability.get(0).get("available"));
        assertEquals(true, availability.get(1).get("available"));
        server.verify();
    }

    @Test
    void hybridVisionFailureShouldReportOllamaAndZhipuAttempts() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ImageServiceImpl imageService = new ImageServiceImpl(restTemplate, Map.of(
                "OLLAMA_BASE_URL", "http://localhost:11434",
                "OLLAMA_VISION_MODEL", "qwen2.5vl:3b",
                "ZHIPU_VISION_MODELS", "glm-4v-flash"
        ));

        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError()
                        .body("{\"error\":\"Failed to create new sequence\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.bigmodel.cn/api/paas/v4/chat/completions"))
                .andExpect(method(POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests()
                        .body("{\"error\":{\"code\":\"1113\",\"message\":\"余额不足或无可用资源包,请充值。\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "white.png",
                "image/png",
                pngBytes(1, 1, Color.WHITE, Color.WHITE)
        );

        Map<String, Object> response = imageService.describeImage(image, "concise", "zh");

        assertEquals(false, response.get("success"));
        assertEquals("hybrid", response.get("provider"));
        assertEquals(List.of("qwen2.5vl:3b", "glm-4v-flash"), response.get("visionModelsTried"));
        List<Map<String, Object>> availability = (List<Map<String, Object>>) response.get("modelAvailability");
        assertEquals("ollama", availability.get(0).get("provider"));
        assertEquals("zhipu", availability.get(1).get("provider"));
        assertTrue(String.valueOf(response.get("error")).contains("余额不足"));
        server.verify();
    }

    @Test
    void imageDescriptionShouldMarkLocalSummaryAsFallbackInsteadOfModelSuccess() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ImageServiceImpl imageService = new ImageServiceImpl(restTemplate, Map.of(
                "MEDIA_VISION_PROVIDER", "ollama",
                "OLLAMA_BASE_URL", "http://localhost:11434",
                "OLLAMA_VISION_MODEL", "qwen2.5vl:7b"
        ));

        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "fallback.png",
                "image/png",
                pngBytes(4, 2, Color.WHITE, Color.BLACK)
        );

        Map<String, Object> response = imageService.describeImage(image, "detailed", "zh");

        assertEquals(false, response.get("success"));
        assertEquals(true, response.get("fallback"));
        assertEquals("local-basic-image-summary", response.get("model"));
        assertEquals(true, response.get("modelUnavailable"));
        assertTrue(String.valueOf(response.get("description")).contains("本地基础描述"));
        assertTrue(String.valueOf(response.get("modelMessage")).contains("本地免费图片理解模型不可用"));
        server.verify();
    }

    @Test
    void localImageSummaryShouldDescribeObservableImageProperties() throws Exception {
        byte[] imageBytes = pngBytes(4, 2, new Color(180, 20, 30), new Color(20, 30, 160));

        Optional<String> summary = ImageServiceImpl.buildLocalImageSummary(imageBytes, "detailed", "zh");

        assertTrue(summary.isPresent());
        assertTrue(summary.get().contains("本地基础描述"));
        assertTrue(summary.get().contains("4×2"));
        assertTrue(summary.get().contains("横向"));
        assertTrue(summary.get().contains("非大模型识图"));
        assertTrue(summary.get().contains("无法可靠识别"));
    }

    private static byte[] pngBytes(int width, int height, Color leftColor, Color rightColor) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, x < width / 2 ? leftColor.getRGB() : rightColor.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static void deleteGeneratedImage(Map<String, Object> response) throws IOException {
        String imagePath = String.valueOf(response.get("image"));
        String prefix = "/api/image/download/";
        if (imagePath.startsWith(prefix)) {
            Files.deleteIfExists(Path.of("images", imagePath.substring(prefix.length())));
        }
    }

    private static void deleteGeneratedImage(String imagePath) throws IOException {
        String prefix = "/api/image/download/";
        if (imagePath != null && imagePath.startsWith(prefix)) {
            Files.deleteIfExists(Path.of("images", imagePath.substring(prefix.length())));
        }
    }
}
