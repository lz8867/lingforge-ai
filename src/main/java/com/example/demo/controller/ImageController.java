package com.example.demo.controller;

import com.example.demo.service.ImageService;
import com.example.demo.service.MemoryService;
import com.example.demo.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);
    private static final String UPLOAD_DIR = "images";
    private final RestTemplate restTemplate;

    @Autowired
    private ImageService imageService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private MemoryService memoryService;

    public ImageController() {
        this.restTemplate = new RestTemplate();
        initUploadDir();
    }

    public ImageController(ImageService imageService, VideoService videoService, MemoryService memoryService) {
        this.restTemplate = new RestTemplate();
        this.imageService = imageService;
        this.videoService = videoService;
        this.memoryService = memoryService;
        initUploadDir();
    }

    private void initUploadDir() {
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

    /**
     * 提供本地保存的文件（图片或视频）
     * @param fileName 文件名
     * @return 文件资源
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
       return  imageService.downloadImage(fileName);
    }

    /**
     * 文生图接口
     * @param request 请求参数
     * @return 生成结果
     */
    @PostMapping("/generate")
    public Map<String, Object> generateImage(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "default_user") String userId,
            @RequestParam(defaultValue = "draft") String knowledgeStatus) {
        Map<String, Object> response = imageService.generateImage(request);
        captureImageMemory(request, response, userId, knowledgeStatus);
        return response;
    }

    /**
     * 文生视频接口
     * @param request 请求参数
     * @return 生成结果
     */
    @PostMapping("/generate/video")
    public Map<String, Object> generateVideo(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "default_user") String userId,
            @RequestParam(defaultValue = "draft") String knowledgeStatus) {
        Map<String, Object> response = videoService.submitVideoTask(request);
        captureVideoMemory(request, response, userId, knowledgeStatus);
        return response;
    }

    /**
     * 查询文生视频通道能力
     * @return 通道配置状态
     */
    @GetMapping("/generate/video/capability")
    public Map<String, Object> getVideoCapability() {
        log.info("查询文生视频通道能力");
        return videoService.getVideoCapability();
    }

    /**
     * 查询文生视频任务状态
     * @param taskId 任务ID
     * @return 任务状态或生成结果
     */
    @GetMapping("/generate/video/status/{taskId}")
    public Map<String, Object> getVideoTaskStatus(@PathVariable String taskId) {
        return videoService.getVideoTaskStatus(taskId);
    }

    /**
     * 图生文接口
     * @param image 上传的图片
     * @param type 描述类型
     * @param language 语言
     * @return 描述结果
     */
    @PostMapping("/describe")
    public Map<String, Object> describeImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("type") String type,
            @RequestParam("language") String language) {

        return imageService.describeImage(image, type, language);
    }

    private void captureImageMemory(
            Map<String, Object> request,
            Map<String, Object> response,
            String userId,
            String knowledgeStatus) {
        if (memoryService == null || response == null) {
            return;
        }
        String safeUserId = normalizeUserId(userId);
        String prompt = readText(request, "prompt");
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        String output = buildImageResultText(response);
        String status = normalizeKnowledgeStatus(knowledgeStatus);
        memoryService.saveMemory(safeUserId, prompt, "image_prompt", status);
        if (output != null) {
            memoryService.saveMemory(safeUserId, output, "image_result", status);
        }
    }

    private void captureVideoMemory(
            Map<String, Object> request,
            Map<String, Object> response,
            String userId,
            String knowledgeStatus) {
        if (memoryService == null || response == null) {
            return;
        }
        String safeUserId = normalizeUserId(userId);
        String prompt = readText(request, "prompt");
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        String output = buildVideoResultText(response);
        String status = normalizeKnowledgeStatus(knowledgeStatus);
        memoryService.saveMemory(safeUserId, prompt, "video_prompt", status);
        if (output != null) {
            memoryService.saveMemory(safeUserId, output, "video_result", status);
        }
    }

    private static String buildImageResultText(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object images = response.getOrDefault("images", response.get("image"));
        if (images != null) {
            return "images=" + images;
        }
        Object message = response.get("message");
        if (message == null) {
            return Collections.singletonMap("response", response).toString();
        }
        return String.valueOf(message);
    }

    private static String buildVideoResultText(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        if (response.get("taskId") != null) {
            return "taskId=" + response.get("taskId");
        }
        if (response.get("video") != null) {
            return "video=" + response.get("video");
        }
        Object message = response.get("message");
        if (message == null) {
            return Collections.singletonMap("response", response).toString();
        }
        return String.valueOf(message);
    }

    private static String readText(Map<String, Object> request, String key) {
        if (request == null) {
            return null;
        }
        Object value = request.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static String normalizeUserId(String userId) {
        if (userId == null) {
            return "default_user";
        }
        String safeUserId = userId.trim();
        return safeUserId.isBlank() ? "default_user" : safeUserId;
    }

    private static String normalizeKnowledgeStatus(String knowledgeStatus) {
        if (knowledgeStatus == null) {
            return "draft";
        }
        String status = knowledgeStatus.trim();
        return status.isBlank() ? "draft" : status;
    }
}
