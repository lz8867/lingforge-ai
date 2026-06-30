package com.example.demo.controller;

import com.example.demo.service.ModelingJobRequest;
import com.example.demo.service.ModelingJobSnapshot;
import com.example.demo.service.ModelingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/modeling")
public class ModelingController {

    private static final Logger log = LoggerFactory.getLogger(ModelingController.class);

    private final ModelingService modelingService;

    public ModelingController(ModelingService modelingService) {
        this.modelingService = modelingService;
    }

    @PostMapping(value = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createJob(
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String quality,
            @RequestParam(required = false) Integer viewCount,
            @RequestParam(required = false) Integer faceBudgetK,
            @RequestParam(required = false) Integer seed,
            @RequestParam(required = false) Integer steps,
            @RequestParam(required = false) Double guidanceScale,
            @RequestParam(required = false) Integer numGaussians
    ) {
        log.info("收到图像建模任务创建请求: file={}, kind={}, quality={}, numGaussians={}",
                image == null ? null : image.getOriginalFilename(), kind, quality, numGaussians);
        try {
            ModelingJobSnapshot job = modelingService.submit(
                    image,
                    new ModelingJobRequest(prompt, kind, quality, viewCount, faceBudgetK, seed, steps, guidanceScale, numGaussians)
            );
            return Map.of(
                    "success", true,
                    "message", "图像建模任务已创建",
                    "data", job
            );
        } catch (IllegalArgumentException e) {
            log.warn("图像建模任务参数不合法: message={}", e.getMessage());
            return Map.of(
                    "success", false,
                    "message", e.getMessage()
            );
        } catch (Exception e) {
            log.error("图像建模任务创建失败: message={}", e.getMessage());
            return Map.of(
                    "success", false,
                    "message", "图像建模任务创建失败：" + e.getMessage()
            );
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> jobStatus(@PathVariable String jobId) {
        log.info("收到图像建模任务状态查询: jobId={}", jobId);
        return modelingService.findJob(jobId)
                .map(job -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "查询图像建模任务成功",
                        "data", job
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", "图像建模任务不存在"
                )));
    }

    @GetMapping("/capability")
    public Map<String, Object> capability() {
        log.info("收到 TripoSplat 建模能力查询");
        return Map.of(
                "success", true,
                "message", "TripoSplat 建模能力",
                "data", modelingService.capability()
        );
    }

    @GetMapping("/jobs/{jobId}/files/{fileName:.+}")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable String jobId, @PathVariable String fileName) {
        log.info("收到图像建模产物下载请求: jobId={}, fileName={}", jobId, fileName);
        return modelingService.resolveArtifact(jobId, fileName)
                .map(resource -> ResponseEntity.ok()
                        .contentType(mediaTypeFor(fileName))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeHeaderFilename(fileName) + "\"")
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MediaType mediaTypeFor(String fileName) {
        if (fileName != null && fileName.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (fileName != null && fileName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static String sanitizeHeaderFilename(String fileName) {
        return fileName == null ? "artifact.bin" : fileName.replace("\"", "");
    }
}
