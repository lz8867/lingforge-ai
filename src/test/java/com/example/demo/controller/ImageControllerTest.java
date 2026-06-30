package com.example.demo.controller;

import com.example.demo.service.ImageService;
import com.example.demo.service.MemoryService;
import com.example.demo.service.VideoService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImageControllerTest {

    @Test
    void generateImageShouldCapturePromptAndResultToMemory() {
        ImageService imageService = mock(ImageService.class);
        VideoService videoService = mock(VideoService.class);
        MemoryService memoryService = mock(MemoryService.class);

        Map<String, Object> request = new HashMap<>();
        request.put("prompt", "一只在月球上奔跑的猫");

        Map<String, Object> response = new HashMap<>();
        response.put("images", List.of("img-cat-1.png", "img-cat-2.png"));

        when(imageService.generateImage(request)).thenReturn(response);

        ImageController controller = new ImageController(imageService, videoService, memoryService);
        Map<String, Object> actual = controller.generateImage(request, "user-image", "published");

        assertEquals(response, actual);
        verify(imageService).generateImage(request);
        verify(memoryService).saveMemory("user-image", "一只在月球上奔跑的猫", "image_prompt", "published");
        verify(memoryService).saveMemory("user-image", "images=[img-cat-1.png, img-cat-2.png]", "image_result", "published");
    }

    @Test
    void generateImageShouldSkipMemoryWhenPromptMissing() {
        ImageService imageService = mock(ImageService.class);
        VideoService videoService = mock(VideoService.class);
        MemoryService memoryService = mock(MemoryService.class);

        Map<String, Object> request = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "生成失败");

        when(imageService.generateImage(request)).thenReturn(response);

        ImageController controller = new ImageController(imageService, videoService, memoryService);
        Map<String, Object> actual = controller.generateImage(request, "user-image", "draft");

        assertEquals(response, actual);
        assertEquals("生成失败", String.valueOf(actual.get("message")));
        verifyNoInteractions(memoryService);
    }

    @Test
    void generateVideoShouldCapturePromptAndResultToMemory() {
        ImageService imageService = mock(ImageService.class);
        VideoService videoService = mock(VideoService.class);
        MemoryService memoryService = mock(MemoryService.class);

        Map<String, Object> request = new HashMap<>();
        request.put("prompt", "制作一个 5 秒的广告短片");
        request.put("duration", 5);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", "task-123");

        when(videoService.submitVideoTask(request)).thenReturn(response);

        ImageController controller = new ImageController(imageService, videoService, memoryService);
        Map<String, Object> actual = controller.generateVideo(request, "user-video", "draft");

        assertEquals(response, actual);
        verify(videoService).submitVideoTask(request);
        assertEquals("task-123", actual.get("taskId"));
        verify(memoryService).saveMemory("user-video", "制作一个 5 秒的广告短片", "video_prompt", "draft");
        verify(memoryService).saveMemory("user-video", "taskId=task-123", "video_result", "draft");
    }

    @Test
    void generateVideoShouldSkipMemoryWhenPromptMissing() {
        ImageService imageService = mock(ImageService.class);
        VideoService videoService = mock(VideoService.class);
        MemoryService memoryService = mock(MemoryService.class);

        Map<String, Object> request = new HashMap<>();
        request.put("duration", 5);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", "task-empty");

        when(videoService.submitVideoTask(request)).thenReturn(response);

        ImageController controller = new ImageController(imageService, videoService, memoryService);
        Map<String, Object> actual = controller.generateVideo(request, "user-video", "");

        assertEquals(response, actual);
        assertEquals("task-empty", String.valueOf(actual.get("taskId")));
        verifyNoInteractions(memoryService);
    }
}
