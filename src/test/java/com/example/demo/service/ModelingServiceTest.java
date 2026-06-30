package com.example.demo.service;

import com.example.demo.controller.ModelingController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelingServiceTest {

    @TempDir
    Path workspace;

    @Test
    void submitJobShouldReturnUnavailableWhenTripoSplatServiceIsNotConfigured() throws Exception {
        ModelingService service = new ModelingService(
                new FakeTripoSplatGateway(false, TripoSplatGenerationResult.unavailable("TripoSplat 服务未配置")),
                workspace,
                directExecutor()
        );

        ModelingJobSnapshot job = service.submit(uploadedImage(), request());

        assertEquals("UNAVAILABLE", job.status());
        assertTrue(job.localFallbackAllowed());
        assertFalse(job.remoteServiceConfigured());
        assertTrue(job.message().contains("TripoSplat"));
        assertTrue(job.logs().stream().anyMatch(log -> log.contains("本地预览")));
        assertFalse(job.logs().stream().anyMatch(log -> log.contains("兜底")));
        assertTrue(Files.exists(workspace.resolve(job.jobId()).resolve("input.png")));
    }

    @Test
    void capabilityShouldExplainLocalPreviewWithoutFallbackWording() {
        ModelingService service = new ModelingService(
                new FakeTripoSplatGateway(false, TripoSplatGenerationResult.unavailable("TripoSplat 服务未配置")),
                workspace,
                directExecutor()
        );

        Map<String, Object> capability = service.capability();

        String message = String.valueOf(capability.get("message"));
        assertEquals(false, capability.get("configured"));
        assertTrue(message.contains("本地联调资产"));
        assertFalse(message.contains("兜底"));
    }

    @Test
    void submitJobShouldExposeGeneratedPlySplatAndManifestArtifacts() throws Exception {
        ModelingService service = new ModelingService(
                new FakeTripoSplatGateway(true, TripoSplatGenerationResult.success("TripoSplat 生成完成", List.of(), Map.of("gaussians", 32768))),
                workspace,
                directExecutor()
        );

        ModelingJobSnapshot job = service.submit(uploadedImage(), request());

        assertEquals("SUCCEEDED", job.status());
        assertTrue(job.remoteServiceConfigured());
        assertEquals(List.of("preprocessed.webp", "splat.ply", "splat.splat", "manifest.json"), job.outputs().stream().map(ModelingArtifact::name).toList());
        assertTrue(job.outputs().stream().allMatch(output -> output.downloadUrl().startsWith("/api/modeling/jobs/" + job.jobId() + "/files/")));
        Optional<Resource> splat = service.resolveArtifact(job.jobId(), "splat.splat");
        assertTrue(splat.isPresent());
        assertTrue(splat.get().contentLength() > 0);
    }

    @Test
    void controllerShouldCreateJobAndExposeCapability() throws Exception {
        ModelingService service = new ModelingService(
                new FakeTripoSplatGateway(true, TripoSplatGenerationResult.success("ok", List.of(), Map.of())),
                workspace,
                directExecutor()
        );
        ModelingController controller = new ModelingController(service);

        Map<String, Object> response = controller.createJob(
                uploadedImage(),
                "角色建模",
                "character",
                "balanced",
                8,
                42,
                42,
                20,
                3.0,
                32768
        );
        Map<String, Object> capability = controller.capability();

        assertEquals(true, response.get("success"));
        assertNotNull(response.get("data"));
        assertEquals(true, capability.get("success"));
        assertTrue(String.valueOf(capability.get("message")).contains("TripoSplat"));
    }

    @Test
    void tripoSplatPythonAdapterShouldKeepOfficialPipelineStagesVisible() throws Exception {
        String adapter = Files.readString(Path.of("scripts/triposplat_service.py"));
        String pipeline = Files.readString(Path.of(".external/TripoSplat/triposplat.py"));

        assertTrue(adapter.contains("TripoSplatPipeline"));
        assertTrue(adapter.contains("preprocess_image"));
        assertTrue(adapter.contains("encode_image"));
        assertTrue(adapter.contains("sample_latent"));
        assertTrue(adapter.contains("decode_latent"));
        assertTrue(adapter.contains("save_ply"));
        assertTrue(adapter.contains("save_splat"));

        String launcher = Files.readString(Path.of("scripts/start-triposplat-service.sh"));
        assertTrue(launcher.contains("TRIPOSPLAT_PYTHON"));
        assertTrue(launcher.contains(".venv-triposplat/bin/python"));
        assertTrue(adapter.contains("TRIPOSPLAT_DEVICE"));
        assertTrue(pipeline.contains("compute_dtype = torch.float32 if self._device.type == \"cpu\""));
        assertTrue(pipeline.contains("rmbg_dtype = torch.float32 if self._device.type == \"cpu\""));
        assertTrue(pipeline.contains("_NEUTRAL_BACKGROUND = (246, 248, 251)"));
        assertTrue(pipeline.contains("Image.new(\"RGB\", (size, size), _NEUTRAL_BACKGROUND)"));
        assertFalse(pipeline.contains("Image.new(\"RGB\", (size, size), (0, 0, 0))"));
    }

    @Test
    void modelingControllerShouldBeCreatedBySpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(HttpTripoSplatGateway.class, ModelingService.class, ModelingController.class);

            context.refresh();

            assertNotNull(context.getBean(ModelingController.class));
        }
    }

    private static ModelingJobRequest request() {
        return new ModelingJobRequest("角色建模", "character", "balanced", 8, 42, 42, 20, 3.0, 32768);
    }

    private static MockMultipartFile uploadedImage() {
        return new MockMultipartFile(
                "image",
                "input.png",
                "image/png",
                "fake-png".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }

    private static final class FakeTripoSplatGateway implements TripoSplatGateway {
        private final boolean configured;
        private final TripoSplatGenerationResult result;

        private FakeTripoSplatGateway(boolean configured, TripoSplatGenerationResult result) {
            this.configured = configured;
            this.result = result;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String serviceUrl() {
            return configured ? "http://localhost:7862" : "";
        }

        @Override
        public TripoSplatGenerationResult generate(String jobId, Path inputImage, Path outputDir, ModelingJobRequest request) throws Exception {
            if (result.success()) {
                Files.writeString(outputDir.resolve("preprocessed.webp"), "prepared");
                Files.writeString(outputDir.resolve("splat.ply"), "ply");
                Files.writeString(outputDir.resolve("splat.splat"), "splat");
                Files.writeString(outputDir.resolve("manifest.json"), "{}");
            }
            return result;
        }
    }
}
