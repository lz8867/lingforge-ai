package com.example.demo.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VideoServiceImplTest {

    @Test
    void pollIntervalShouldBeShortEnoughForBrowserRequest() {
        assertTrue(VideoServiceImpl.POLL_INTERVAL_SECONDS <= 5);
    }

    @Test
    void springConstructorShouldBeExplicitlySelected() throws Exception {
        assertTrue(VideoServiceImpl.class
                .getConstructor(String.class)
                .isAnnotationPresent(Autowired.class));
    }

    @Test
    void videoRequestShouldUseConfiguredAvailableModel() throws Exception {
        VideoServiceImpl videoService = new VideoServiceImpl("cogvideox-flash");
        Method buildVideoRequest = VideoServiceImpl.class.getDeclaredMethod("buildVideoRequest", String.class, Map.class);
        buildVideoRequest.setAccessible(true);

        Object videoRequest = buildVideoRequest.invoke(videoService, "测试视频", Map.of("resolution", "1920x1080"));
        Method getModel = videoRequest.getClass().getMethod("getModel");

        assertEquals("cogvideox-flash", getModel.invoke(videoRequest));
    }

    @Test
    void videoProviderShouldDefaultToHybridAndTryLocalFirst() throws Exception {
        assertEquals("hybrid", VideoServiceImpl.configuredVideoProvider(Map.of()));

        Method usesLocalFreeProvider = VideoServiceImpl.class.getDeclaredMethod("usesLocalFreeProvider", String.class);
        usesLocalFreeProvider.setAccessible(true);

        assertEquals(true, usesLocalFreeProvider.invoke(null, "hybrid"));
        assertEquals(true, usesLocalFreeProvider.invoke(null, "local-free"));
    }

    @Test
    void videoCapabilityShouldEnableCloudFallbackWhenDefaultZhipuKeyExists() {
        VideoServiceImpl videoService = new VideoServiceImpl("cogvideox-flash", new RestTemplate(), Map.of());

        Map<String, Object> capability = videoService.getVideoCapability();

        assertEquals(true, capability.get("canGenerate"));
        assertEquals("CONFIGURED", capability.get("status"));
        assertEquals("视频生成通道已配置", capability.get("message"));
        assertEquals("cogvideox-flash", capability.get("model"));
        assertTrue(String.valueOf(capability.get("actionSuggestion")).contains("FREE_VIDEO_API_URL"));
        assertTrue(String.valueOf(capability.get("actionSuggestion")).contains("ZHIPU_API_KEY"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> availability = (List<Map<String, Object>>) capability.get("modelAvailability");
        assertEquals(false, availability.get(0).get("available"));
        assertEquals(true, availability.get(1).get("available"));
        assertTrue(String.valueOf(availability.get(1).get("reason")).contains("云端"));
    }

    @Test
    void videoCapabilityShouldDetectDefaultWanVideoHealthEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VideoServiceImpl videoService = new VideoServiceImpl("cogvideox-flash", restTemplate, Map.of(
                "MEDIA_VIDEO_PROVIDER", "local-free"
        ));

        server.expect(requestTo("http://localhost:7861/api/text-to-video/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "configured": true,
                          "model": "Wan2.1-T2V-1.3B",
                          "message": "Wan2.1 ready"
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> capability = videoService.getVideoCapability();

        assertEquals(true, capability.get("canGenerate"));
        assertEquals("CONFIGURED", capability.get("status"));
        assertEquals("local-free", capability.get("provider"));
        assertEquals("Wan2.1-T2V-1.3B", capability.get("model"));
        assertTrue(String.valueOf(capability.get("actionSuggestion")).contains("scripts/wan_video_server.py"));
        server.verify();
    }

    @Test
    void videoCapabilityShouldBecomeReadyWhenLocalVideoEndpointIsConfigured() {
        VideoServiceImpl videoService = new VideoServiceImpl("cogvideox-flash", new RestTemplate(), Map.of(
                "MEDIA_VIDEO_PROVIDER", "local-free",
                "FREE_VIDEO_API_URL", "http://localhost:7861/api/text-to-video"
        ));

        Map<String, Object> capability = videoService.getVideoCapability();

        assertEquals(true, capability.get("canGenerate"));
        assertEquals("CONFIGURED", capability.get("status"));
        assertEquals("local-free", capability.get("provider"));
        assertTrue(String.valueOf(capability.get("message")).contains("已配置"));
    }

    @Test
    void videoRequestShouldEnhancePromptWithShotMotionAndQualityConstraints() throws Exception {
        VideoServiceImpl videoService = new VideoServiceImpl("cogvideox-flash");
        Method buildVideoRequest = VideoServiceImpl.class.getDeclaredMethod("buildVideoRequest", String.class, Map.class);
        buildVideoRequest.setAccessible(true);

        Object videoRequest = buildVideoRequest.invoke(videoService, "雨夜街头的咖啡店", Map.of(
                "resolution", "1920x1080",
                "duration", "10s",
                "mode", "quality",
                "fps", "60"
        ));
        Method getPrompt = videoRequest.getClass().getMethod("getPrompt");
        Method getFps = videoRequest.getClass().getMethod("getFps");

        String prompt = String.valueOf(getPrompt.invoke(videoRequest));
        assertTrue(prompt.contains("雨夜街头的咖啡店"));
        assertTrue(prompt.contains("10秒"));
        assertTrue(prompt.contains("横向 16:9"));
        assertTrue(prompt.contains("镜头运动"));
        assertTrue(prompt.contains("主体动作"));
        assertTrue(prompt.contains("电影感光影"));
        assertTrue(prompt.contains("不要字幕"));
        assertTrue(prompt.contains("不要水印"));
        assertEquals(60, getFps.invoke(videoRequest));
    }

    @Test
    void textToVideoShouldUseLocalFreeVideoProviderByDefault() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VideoServiceImpl videoService = new VideoServiceImpl("cogvideox-flash", restTemplate, Map.of(
                "FREE_VIDEO_API_URL", "http://localhost:7861/api/text-to-video",
                "FREE_VIDEO_MODEL", "Wan2.1-T2V-1.3B"
        ));

        server.expect(requestTo("http://localhost:7861/api/text-to-video"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model": "Wan2.1-T2V-1.3B",
                          "prompt": "雨夜街头的咖啡店；5秒短视频；横向 16:9构图，主体始终清晰可辨；开场建立场景，中段呈现主体动作，结尾自然收束；镜头运动平滑，可使用轻微推近、横移或跟随镜头，避免突然跳切；主体动作连续，物体比例稳定，人物或动物不要畸形变形；电影感光影，真实景深，色彩协调，细节清楚；质量优先，细节丰富，运动自然；无音频要求，画面表达完整；不要字幕、不要水印、不要Logo、不要文字贴片、不要闪烁和低清晰度",
                          "duration": "5s",
                          "resolution": "832x480",
                          "size": "832*480",
                          "mode": "quality",
                          "fps": 30,
                          "withAudio": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "taskId": "local-video-1",
                          "status": "PROCESSING"
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> response = videoService.submitVideoTask(Map.of(
                "prompt", "雨夜街头的咖啡店",
                "duration", "5s",
                "resolution", "1920x1080",
                "mode", "quality",
                "fps", "30"
        ));

        assertEquals(true, response.get("success"));
        assertEquals("local-free", response.get("provider"));
        assertEquals("Wan2.1-T2V-1.3B", response.get("model"));
        assertEquals("local-video-1", response.get("taskId"));
        server.verify();
    }

    @Test
    void hybridVideoFailureShouldPreserveLocalAndCloudModelAvailability() {
        Map<String, Object> localFailure = Map.of(
                "videoModelsTried", List.of("wan2.1-t2v-1.3b"),
                "modelAvailability", List.of(Map.of(
                        "model", "wan2.1-t2v-1.3b",
                        "provider", "local-free",
                        "available", false,
                        "reason", "Connection refused"
                ))
        );

        Map<String, Object> response = VideoServiceImpl.buildZhipuVideoFailureResponse(
                "cogvideox-flash",
                "视频任务提交失败",
                "The service may be temporarily overloaded, please try again later",
                localFailure
        );

        assertEquals(false, response.get("success"));
        assertEquals("zhipu", response.get("provider"));
        assertEquals(List.of("wan2.1-t2v-1.3b", "cogvideox-flash"), response.get("videoModelsTried"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> availability = (List<Map<String, Object>>) response.get("modelAvailability");
        assertEquals(2, availability.size());
        assertEquals("local-free", availability.get(0).get("provider"));
        assertEquals("zhipu", availability.get(1).get("provider"));
        assertEquals(false, availability.get(0).get("available"));
        assertEquals(false, availability.get(1).get("available"));
        assertTrue(String.valueOf(response.get("description")).contains("本地"));
        assertTrue(String.valueOf(response.get("description")).contains("云端"));
        assertTrue(String.valueOf(response.get("actionSuggestion")).contains("MEDIA_VIDEO_PROVIDER"));
    }

    @Test
    void overloadedVideoSubmitErrorShouldBeClassifiedAsTransient() {
        assertTrue(VideoServiceImpl.isTransientVideoSubmitFailure(
                "The service may be temporarily overloaded, please try again later"
        ));
        assertTrue(VideoServiceImpl.isTransientVideoSubmitFailure("HTTP 429 Too Many Requests"));
        assertFalse(VideoServiceImpl.isTransientVideoSubmitFailure("Insufficient balance or no resource package"));
    }

    @Test
    void textToVideoPageShouldDisplayHybridModelFamily() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/text-to-video.html"));

        assertTrue(page.contains("本地优先 · 云端兜底"));
        assertTrue(page.contains("Wan2.1-T2V-1.3B"));
        assertTrue(page.contains("data.model || DEFAULT_VIDEO_MODEL_LABEL"));
        assertFalse(page.contains("默认使用本地免费模型"));
        assertFalse(page.contains("data.model || '本地免费模型'"));
        assertFalse(page.contains("CogVideo-X-3"));
        assertTrue(page.contains("mode: mode"));
        assertTrue(page.contains("fps: fps"));
        assertTrue(page.contains("withAudio: withAudio"));
    }
}
