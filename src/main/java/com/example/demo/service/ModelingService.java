package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class ModelingService {

    private static final Logger log = LoggerFactory.getLogger(ModelingService.class);

    private static final Map<String, ArtifactSpec> KNOWN_ARTIFACTS = Map.of(
            "preprocessed.webp", new ArtifactSpec("image/webp", "preprocessed"),
            "splat.ply", new ArtifactSpec("application/octet-stream", "ply"),
            "splat.splat", new ArtifactSpec("application/octet-stream", "splat"),
            "manifest.json", new ArtifactSpec("application/json", "manifest")
    );

    private final TripoSplatGateway tripoSplatGateway;
    private final Path outputRoot;
    private final Executor executor;
    private final ConcurrentMap<String, MutableModelingJob> jobs = new ConcurrentHashMap<>();

    @Autowired
    public ModelingService(
            TripoSplatGateway tripoSplatGateway,
            @Value("${modeling.output-dir:output/modeling-jobs}") String outputRoot
    ) {
        this(tripoSplatGateway, Path.of(outputRoot), defaultExecutor());
    }

    public ModelingService(TripoSplatGateway tripoSplatGateway, Path outputRoot, Executor executor) {
        this.tripoSplatGateway = tripoSplatGateway;
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.executor = executor;
    }

    public ModelingJobSnapshot submit(MultipartFile image, ModelingJobRequest request) {
        log.info("创建图像建模任务: originalFilename={}, contentType={}, kind={}, numGaussians={}",
                image == null ? null : image.getOriginalFilename(),
                image == null ? null : image.getContentType(),
                request == null ? null : request.kind(),
                request == null ? null : request.numGaussians());
        validateImage(image);
        ModelingJobRequest safeRequest = request == null
                ? new ModelingJobRequest(null, null, null, null, null, null, null, null, null)
                : request;
        String jobId = "modeling-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path jobDir = outputRoot.resolve(jobId).normalize();
        if (!jobDir.startsWith(outputRoot)) {
            throw new IllegalStateException("建模任务目录不合法");
        }

        try {
            Files.createDirectories(jobDir);
            Path inputPath = saveInputImage(image, jobDir);
            MutableModelingJob job = new MutableModelingJob(
                    jobId,
                    "QUEUED",
                    "建模任务已创建，等待 TripoSplat 服务处理。",
                    tripoSplatGateway.isConfigured(),
                    false,
                    image.getOriginalFilename(),
                    safeRequest,
                    jobDir,
                    Instant.now()
            );
            job.logs.add("任务已入队: " + jobId);
            jobs.put(jobId, job);
            executor.execute(() -> runRemoteGeneration(job, inputPath));
            return snapshot(job);
        } catch (IOException e) {
            log.error("保存建模输入图片失败: message={}", e.getMessage());
            throw new IllegalStateException("保存建模输入图片失败", e);
        }
    }

    public Optional<ModelingJobSnapshot> findJob(String jobId) {
        log.info("查询图像建模任务: jobId={}", jobId);
        MutableModelingJob job = jobs.get(jobId);
        if (job == null) {
            log.warn("图像建模任务不存在: jobId={}", jobId);
            return Optional.empty();
        }
        return Optional.of(snapshot(job));
    }

    public Map<String, Object> capability() {
        log.info("查询 TripoSplat 建模服务能力");
        boolean configured = tripoSplatGateway.isConfigured();
        return Map.of(
                "configured", configured,
                "serviceUrl", tripoSplatGateway.serviceUrl(),
                "message", configured
                        ? "TripoSplat 服务已配置，页面会优先提交真实建模任务。"
                        : "TripoSplat 服务未配置，页面会提示服务未就绪，并生成本地联调资产用于预览流程。",
                "expectedOutputs", List.of("preprocessed.webp", "splat.ply", "splat.splat", "manifest.json")
        );
    }

    public Optional<Resource> resolveArtifact(String jobId, String fileName) {
        log.info("解析图像建模产物: jobId={}, fileName={}", jobId, fileName);
        if (!isSafeFileName(fileName)) {
            log.warn("拒绝不安全的建模产物文件名: jobId={}, fileName={}", jobId, fileName);
            return Optional.empty();
        }
        MutableModelingJob job = jobs.get(jobId);
        if (job == null) {
            log.warn("建模产物所属任务不存在: jobId={}, fileName={}", jobId, fileName);
            return Optional.empty();
        }
        Path artifact = job.jobDir.resolve(fileName).normalize();
        if (!artifact.startsWith(job.jobDir) || !Files.exists(artifact)) {
            log.warn("建模产物不存在或越界: jobId={}, fileName={}", jobId, fileName);
            return Optional.empty();
        }
        try {
            return Optional.of(new UrlResource(artifact.toUri()));
        } catch (MalformedURLException e) {
            log.error("建模产物 URL 创建失败: jobId={}, fileName={}, message={}", jobId, fileName, e.getMessage());
            return Optional.empty();
        }
    }

    private void runRemoteGeneration(MutableModelingJob job, Path inputPath) {
        update(job, "RUNNING", "正在调用 TripoSplat 适配服务。", false, List.of(), Map.of(), "开始调用 TripoSplat 服务");
        try {
            TripoSplatGenerationResult result = tripoSplatGateway.generate(job.jobId, inputPath, job.jobDir, job.request);
            if (result.success()) {
                List<ModelingArtifact> artifacts = enrichDownloadUrls(job.jobId, mergeArtifacts(result.outputs(), scanArtifacts(job.jobDir)));
                update(job, "SUCCEEDED", result.message(), false, artifacts, result.metrics(), "TripoSplat 真实产物已生成");
                log.info("图像建模任务完成: jobId={}, outputCount={}", job.jobId, artifacts.size());
                return;
            }
            if (result.unavailable()) {
                update(job, "UNAVAILABLE", result.message(), true, List.of(), result.metrics(), "TripoSplat 服务未就绪，已切换为本地预览流程");
                log.warn("图像建模任务进入未就绪状态: jobId={}, message={}", job.jobId, result.message());
                return;
            }
            update(job, "FAILED", result.message(), true, List.of(), result.metrics(), "TripoSplat 生成失败，已切换为本地预览流程");
            log.error("图像建模任务失败: jobId={}, message={}", job.jobId, result.message());
        } catch (Exception e) {
            update(job, "FAILED", "TripoSplat 生成异常：" + e.getMessage(), true, List.of(), Map.of(), "TripoSplat 生成异常，已切换为本地预览流程");
            log.error("图像建模任务异常: jobId={}, message={}", job.jobId, e.getMessage());
        }
    }

    private void update(
            MutableModelingJob job,
            String status,
            String message,
            boolean localFallbackAllowed,
            List<ModelingArtifact> outputs,
            Map<String, Object> metrics,
            String logLine
    ) {
        synchronized (job) {
            job.status = status;
            job.message = message;
            job.localFallbackAllowed = localFallbackAllowed;
            job.outputs = List.copyOf(outputs);
            job.metrics = Map.copyOf(metrics);
            job.logs.add(logLine);
            job.updatedAt = Instant.now();
        }
    }

    private ModelingJobSnapshot snapshot(MutableModelingJob job) {
        synchronized (job) {
            return new ModelingJobSnapshot(
                    job.jobId,
                    job.status,
                    job.message,
                    job.remoteServiceConfigured,
                    job.localFallbackAllowed,
                    job.sourceFileName,
                    job.request,
                    job.outputs,
                    job.metrics,
                    job.logs,
                    job.createdAt,
                    job.updatedAt
            );
        }
    }

    private Path saveInputImage(MultipartFile image, Path jobDir) throws IOException {
        String extension = extensionOf(image.getOriginalFilename(), image.getContentType());
        Path inputPath = jobDir.resolve("input" + extension);
        try (InputStream inputStream = image.getInputStream()) {
            Files.copy(inputStream, inputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return inputPath;
    }

    private static void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请上传用于建模的图片。");
        }
        String contentType = image.getContentType();
        String filename = image.getOriginalFilename();
        boolean imageContentType = contentType != null && contentType.toLowerCase().startsWith("image/");
        boolean imageExtension = filename != null && filename.toLowerCase().matches(".*\\.(png|jpg|jpeg|webp)$");
        if (!imageContentType && !imageExtension) {
            throw new IllegalArgumentException("仅支持 PNG、JPG、JPEG、WEBP 图片。");
        }
    }

    private static List<ModelingArtifact> mergeArtifacts(List<ModelingArtifact> remoteOutputs, List<ModelingArtifact> scannedOutputs) {
        Map<String, ModelingArtifact> merged = new LinkedHashMap<>();
        scannedOutputs.forEach(output -> merged.put(output.name(), output));
        remoteOutputs.forEach(output -> merged.put(output.name(), output));
        return new ArrayList<>(merged.values());
    }

    private static List<ModelingArtifact> scanArtifacts(Path jobDir) throws IOException {
        List<ModelingArtifact> outputs = new ArrayList<>();
        for (Map.Entry<String, ArtifactSpec> entry : KNOWN_ARTIFACTS.entrySet()) {
            Path path = jobDir.resolve(entry.getKey());
            if (!Files.exists(path)) {
                continue;
            }
            ArtifactSpec spec = entry.getValue();
            outputs.add(new ModelingArtifact(entry.getKey(), spec.type(), Files.size(path), null, spec.role()));
        }
        return outputs.stream()
                .sorted((left, right) -> Integer.compare(artifactOrder(left.name()), artifactOrder(right.name())))
                .toList();
    }

    private static int artifactOrder(String name) {
        return switch (name) {
            case "preprocessed.webp" -> 0;
            case "splat.ply" -> 1;
            case "splat.splat" -> 2;
            case "manifest.json" -> 3;
            default -> 99;
        };
    }

    private static List<ModelingArtifact> enrichDownloadUrls(String jobId, List<ModelingArtifact> outputs) {
        return outputs.stream()
                .map(output -> output.withDownloadUrl("/api/modeling/jobs/" + jobId + "/files/" + output.name()))
                .toList();
    }

    private static String extensionOf(String filename, String contentType) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            int dot = lower.lastIndexOf('.');
            if (dot >= 0) {
                String extension = lower.substring(dot);
                if (extension.matches("\\.(png|jpg|jpeg|webp)")) {
                    return extension;
                }
            }
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return ".webp";
        }
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return ".jpg";
        }
        return ".png";
    }

    private static boolean isSafeFileName(String fileName) {
        return fileName != null
                && !fileName.isBlank()
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && !fileName.contains("..");
    }

    private static Executor defaultExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modeling-triposplat-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    private record ArtifactSpec(String type, String role) {
    }

    private static final class MutableModelingJob {
        private final String jobId;
        private final boolean remoteServiceConfigured;
        private final String sourceFileName;
        private final ModelingJobRequest request;
        private final Path jobDir;
        private final Instant createdAt;
        private final List<String> logs = new ArrayList<>();
        private String status;
        private String message;
        private boolean localFallbackAllowed;
        private List<ModelingArtifact> outputs = List.of();
        private Map<String, Object> metrics = Map.of();
        private Instant updatedAt;

        private MutableModelingJob(
                String jobId,
                String status,
                String message,
                boolean remoteServiceConfigured,
                boolean localFallbackAllowed,
                String sourceFileName,
                ModelingJobRequest request,
                Path jobDir,
                Instant createdAt
        ) {
            this.jobId = jobId;
            this.status = status;
            this.message = message;
            this.remoteServiceConfigured = remoteServiceConfigured;
            this.localFallbackAllowed = localFallbackAllowed;
            this.sourceFileName = sourceFileName;
            this.request = request;
            this.jobDir = jobDir;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }
    }
}
