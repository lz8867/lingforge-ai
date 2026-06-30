package com.example.demo.service;

import com.example.demo.controller.DeployController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployServiceTest {

    @TempDir
    Path workspace;

    @Test
    void buildProjectRunsMavenAndCopiesNewestJarToAppJar() throws Exception {
        Files.createDirectories(workspace.resolve("target"));
        Path oldJar = workspace.resolve("target/chart-demo-old.jar");
        Path latestJar = workspace.resolve("target/chart-demo-latest.jar");
        Files.writeString(oldJar, "old");
        Files.writeString(latestJar, "latest");
        Files.setLastModifiedTime(oldJar, java.nio.file.attribute.FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        Files.setLastModifiedTime(latestJar, java.nio.file.attribute.FileTime.from(Instant.parse("2026-02-01T00:00:00Z")));

        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, "maven ok\n"));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.buildProject();

        assertTrue(result.success());
        assertEquals(List.of("mvn", "clean", "package", "-Dmaven.test.skip=true"), executor.executedCommands.get(0));
        assertEquals("latest", Files.readString(workspace.resolve("app.jar")));
    }

    @Test
    void deployProjectStartsBackgroundScriptAndRedirectsOutput() throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), "<project />");
        Files.writeString(workspace.resolve("Dockerfile"), "FROM eclipse-temurin:17-jdk");
        Files.createDirectories(workspace.resolve("target"));
        Files.writeString(workspace.resolve("target/chart-demo-latest.jar"), "latest");
        Files.writeString(workspace.resolve("deploy-background.sh"), "#!/bin/bash\n");
        FakeCommandExecutor executor = new FakeCommandExecutor(command -> {
            if (command.equals(List.of("curl", "-fsS", "-o", "/dev/null", "http://localhost:8081/index.html"))) {
                return new CommandExecutionResult(0, "<!DOCTYPE html>\n");
            }
            return new CommandExecutionResult(0, String.join(" ", command) + "\n");
        });
        DeployService service = new DeployService(executor, workspace.toFile(), directExecutor());

        DeployResult result = service.deployProject();

        assertTrue(result.success());
        assertTrue(executor.executedCommands.contains(List.of("mvn", "clean", "package", "-Dmaven.test.skip=true")));
        assertTrue(executor.executedCommands.contains(List.of("docker", "build", "-t", "chart-demo:latest", ".")));
        assertTrue(executor.executedCommands.contains(List.of("docker", "rm", "-f", "chart-demo")));
        assertTrue(executor.executedCommands.stream().anyMatch(command ->
                command.contains("docker") && command.contains("run") && command.contains("chart-demo:latest")
                        && command.contains("SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/activiti?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true")));
        assertFalse(executor.executedCommands.stream().anyMatch(command ->
                command.contains("docker") && command.contains("start") && command.contains("mysql")));
        assertFalse(executor.executedCommands.stream().anyMatch(command ->
                command.contains("docker") && command.contains("run") && command.contains("mysql:8.0")));
        assertTrue(Files.readString(workspace.resolve("deploy.log")).contains("CI/CD部署流程执行成功"));
    }

    @Test
    void deployProjectDelegatesToHostDeployerWhenRunningInsideDocker() throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), "<project />");
        Files.writeString(workspace.resolve("Dockerfile"), "FROM eclipse-temurin:17-jdk");
        FakeCommandExecutor executor = new FakeCommandExecutor(command -> {
            if (command.equals(List.of("curl", "-fsS", "--max-time", "8", "-X", "POST", "http://host.docker.internal:8080/api/deploy/deploy"))) {
                return new CommandExecutionResult(0, "{\"success\":true,\"message\":\"后台CI/CD部署流程已启动\"}");
            }
            return new CommandExecutionResult(1, "unexpected command");
        });
        DeployService service = new DeployService(executor, workspace.toFile(), directExecutor(), Map.of(
                "DEPLOY_CONTAINER_MODE", "true",
                "DEPLOY_HOST_DEPLOYER_URL", "http://host.docker.internal:8080"
        ));

        DeployResult result = service.deployProject();

        assertTrue(result.success());
        assertTrue(result.message().contains("宿主机部署器"));
        assertEquals(List.of("curl", "-fsS", "--max-time", "8", "-X", "POST", "http://host.docker.internal:8080/api/deploy/deploy"), executor.executedCommands.get(0));
        assertFalse(executor.executedCommands.stream().anyMatch(command -> command.contains("mvn")));
        assertFalse(executor.executedCommands.stream().anyMatch(command -> command.contains("docker") && command.contains("build")));
    }

    @Test
    void restartProjectRejectsSelfRestartWhenRunningInsideDocker() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, "restarted\n"));
        DeployService service = new DeployService(executor, workspace.toFile(), directExecutor(), Map.of(
                "DEPLOY_CONTAINER_MODE", "true"
        ));

        DeployResult result = service.restartProject();

        assertFalse(result.success());
        assertTrue(result.message().contains("不能在容器内重启自身"));
        assertTrue(result.output().contains("8080"));
        assertTrue(executor.executedCommands.isEmpty());
    }

    @Test
    void restartProjectUsesDockerRestartArguments() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, "restarted\n"));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.restartProject();

        assertTrue(result.success());
        assertEquals(List.of("docker", "restart", "chart-demo", "mysql"), executor.executedCommands.get(0));
    }

    @Test
    void startModelServiceShouldLaunchWanVideoBridgeWithProjectScript() throws Exception {
        Files.createDirectories(workspace.resolve("scripts"));
        Files.writeString(workspace.resolve("scripts/start-wan-video-server.sh"), "#!/bin/bash\n");
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.startModelService("wan-video");

        assertTrue(result.success());
        assertTrue(result.message().contains("Wan2.1 视频桥"));
        assertEquals(List.of("bash", "scripts/start-wan-video-server.sh"), executor.startedCommands.get(0));
        assertTrue(executor.logFiles.get(0).getPath().endsWith("logs/model-service-wan-video.log"));
    }

    @Test
    void startModelServiceShouldLaunchOllamaServe() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.startModelService("ollama");

        assertTrue(result.success());
        assertTrue(result.message().contains("Ollama 本地模型"));
        assertEquals(List.of("ollama", "serve"), executor.startedCommands.get(0));
        assertTrue(executor.logFiles.get(0).getPath().endsWith("logs/model-service-ollama.log"));
    }

    @Test
    void startModelServiceShouldRejectUnknownServiceWithoutStartingCommand() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.startModelService("stable-diffusion; rm -rf /");

        assertFalse(result.success());
        assertTrue(result.message().contains("不支持"));
        assertTrue(executor.startedCommands.isEmpty());
    }

    @Test
    void startModelServiceShouldLaunchStableDiffusionWithSiblingComposeFile() throws Exception {
        Files.createDirectories(workspace.resolve("scripts"));
        Files.writeString(workspace.resolve("scripts/start-stable-diffusion.sh"), "#!/bin/bash\n");
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.startModelService("stable-diffusion");

        assertTrue(result.success());
        assertTrue(result.message().contains("Stable Diffusion"));
        assertEquals(List.of("bash", "scripts/start-stable-diffusion.sh"), executor.startedCommands.get(0));
        assertTrue(executor.logFiles.get(0).getPath().endsWith("logs/model-service-stable-diffusion.log"));
    }

    @Test
    void startModelServiceShouldLaunchTripoSplatAdapterWithProjectScript() throws Exception {
        Files.createDirectories(workspace.resolve("scripts"));
        Files.writeString(workspace.resolve("scripts/start-triposplat-service.sh"), "#!/bin/bash\n");
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.startModelService("triposplat");

        assertTrue(result.success());
        assertTrue(result.message().contains("TripoSplat"));
        assertEquals(List.of("bash", "scripts/start-triposplat-service.sh"), executor.startedCommands.get(0));
        assertTrue(executor.logFiles.get(0).getPath().endsWith("logs/model-service-triposplat.log"));
    }

    @Test
    void startModelServiceShouldExplainMissingStartEntryWithoutStartingCommand() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.startModelService("wan-video");

        assertFalse(result.success());
        assertTrue(result.message().contains("启动入口不存在"));
        assertTrue(result.output().contains("start-wan-video-server.sh"));
        assertTrue(executor.startedCommands.isEmpty());
    }

    @Test
    void deployControllerShouldExposeModelServiceStartRequest() throws Exception {
        Files.createDirectories(workspace.resolve("scripts"));
        Files.writeString(workspace.resolve("scripts/start-wan-video-server.sh"), "#!/bin/bash\n");
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());
        DeployController controller = new DeployController(service);

        Map<String, Object> response = controller.startModelService(Map.of("serviceId", "wan-video"));

        assertEquals(true, response.get("success"));
        assertEquals(List.of("bash", "scripts/start-wan-video-server.sh"), executor.startedCommands.get(0));
    }

    @Test
    void getDockerLogsRejectsUnsupportedContainer() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.getDockerLogs("chart-demo; rm -rf /", false, 100);

        assertFalse(result.success());
        assertTrue(result.message().contains("不支持"));
        assertTrue(executor.executedCommands.isEmpty());
    }

    @Test
    void getDockerLogsUsesDockerArgumentListWithoutShell() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, "log line\n"));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.getDockerLogs("chart-demo", true, 1000);

        assertTrue(result.success());
        assertEquals(List.of("docker", "logs", "--tail=1000", "chart-demo"), executor.executedCommands.get(0));
    }

    @Test
    void streamDockerLogsUsesFollowArgumentWithoutShell() throws Exception {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        try (CommandLogStream ignored = service.streamDockerLogs("mysql", 200)) {
            assertEquals(List.of("docker", "logs", "--follow", "--tail=200", "mysql"), executor.streamedCommands.get(0));
        }
    }

    @Test
    void streamDockerLogsRejectsUnsupportedContainer() {
        FakeCommandExecutor executor = new FakeCommandExecutor(new CommandExecutionResult(0, ""));
        DeployService service = new DeployService(executor, workspace.toFile());

        DeployResult result = service.validateDockerLogsRequest("mysql;touch /tmp/x", 100);

        assertFalse(result.success());
        assertTrue(executor.streamedCommands.isEmpty());
    }

    @Test
    void deployControllerShouldBeCreatedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ProcessCommandExecutor.class, DeployService.class, DeployController.class);

            context.refresh();

            assertTrue(context.getBean(DeployController.class) != null);
        }
    }

    @Test
    void dockerfileShouldPackageExistingAppJarInsteadOfBuildingAgain() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertTrue(dockerfile.contains("COPY app.jar app.jar"));
        assertFalse(dockerfile.contains("mvn clean package"));
    }

    @Test
    void dockerfileShouldIncludeRuntimeToolsForModelServiceStartButtons() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertTrue(dockerfile.contains("docker.io"));
        assertTrue(dockerfile.contains("docker-compose"));
        assertTrue(dockerfile.contains("python3"));
    }

    @Test
    void currentStatusShouldSummarizeDeploymentHealthContainersAndLogTail() throws Exception {
        Files.writeString(workspace.resolve("deploy.log"), """
                CI/CD部署日志
                $ docker build -t chart-demo:latest .
                退出码: 0
                应用容器已就绪: http://localhost:8081
                CI/CD部署流程执行成功
                """);
        FakeCommandExecutor executor = new FakeCommandExecutor(command -> {
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "-o", "/dev/null", "http://localhost:8081/index.html"))) {
                return new CommandExecutionResult(0, "");
            }
            if (command.equals(List.of("docker", "ps", "--format", "{{.Names}}\t{{.Status}}\t{{.Ports}}"))) {
                return new CommandExecutionResult(0, """
                        chart-demo\tUp 5 minutes\t0.0.0.0:8081->8080/tcp
                        mysql\tUp 2 hours\t3306/tcp
                        """);
            }
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "http://localhost:11434/api/tags"))) {
                return new CommandExecutionResult(0, """
                        {"models":[{"name":"qwen2.5:latest"},{"name":"qwen2.5vl:3b"}]}
                        """);
            }
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "http://localhost:7860/sdapi/v1/sd-models"))) {
                return new CommandExecutionResult(7, "connection refused");
            }
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "http://localhost:7861/api/text-to-video/health"))) {
                return new CommandExecutionResult(0, """
                        {"success":true,"configured":false,"model":"Wan2.1-T2V-1.3B","message":"Wan2.1 本地服务未完成配置"}
                        """);
            }
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "http://localhost:7862/health"))) {
                return new CommandExecutionResult(0, """
                        {"success":false,"configured":false,"message":"TripoSplat adapter is not configured"}
                        """);
            }
            return new CommandExecutionResult(1, "unsupported command");
        });
        DeployService service = new DeployService(executor, workspace.toFile());

        ServiceStatusSnapshot status = service.currentStatus();

        assertEquals("SUCCEEDED", status.deploymentState());
        assertEquals("服务已正常运行", status.deploymentMessage());
        assertTrue(status.serviceReachable());
        assertEquals("http://localhost:8081", status.serviceUrl());
        assertEquals(2, status.containers().size());
        assertEquals("chart-demo", status.containers().get(0).name());
        assertTrue(status.deployLogTail().contains("CI/CD部署流程执行成功"));
        assertTrue(status.memoryUsedMb() >= 0);
        assertTrue(status.uptimeSeconds() >= 0);
        assertTrue(status.serviceMetrics().healthLatencyMs() >= 0);
        assertTrue(status.serviceMetrics().threadCount() > 0);
        assertTrue(status.serviceMetrics().heapUsedMb() >= 0);
        assertTrue(status.serviceMetrics().nonHeapUsedMb() >= 0);
        assertTrue(status.serviceMetrics().diskUsableGb() >= 0);
        assertTrue(status.serviceMetrics().loadedClassCount() > 0);
        assertEquals(4, status.modelServices().size());
        assertEquals("ollama", status.modelServices().get(0).id());
        assertEquals("UP", status.modelServices().get(0).state());
        assertTrue(status.modelServices().get(0).modelSummary().contains("qwen2.5"));
        assertEquals("stable-diffusion", status.modelServices().get(1).id());
        assertEquals("DOWN", status.modelServices().get(1).state());
        assertEquals("wan-video", status.modelServices().get(2).id());
        assertEquals("DEGRADED", status.modelServices().get(2).state());
        assertTrue(status.modelServices().get(2).message().contains("未完成配置"));
        assertEquals("triposplat", status.modelServices().get(3).id());
        assertEquals("DEGRADED", status.modelServices().get(3).state());
    }

    @Test
    void currentStatusShouldReportFailureWhenDeployLogContainsFailure() throws Exception {
        Files.writeString(workspace.resolve("deploy.log"), """
                CI/CD部署日志
                CI/CD部署流程执行失败: 应用容器未在预期时间内就绪
                """);
        FakeCommandExecutor executor = new FakeCommandExecutor(command -> {
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "-o", "/dev/null", "http://localhost:8081/index.html"))) {
                return new CommandExecutionResult(7, "connection refused");
            }
            if (command.equals(List.of("docker", "ps", "--format", "{{.Names}}\t{{.Status}}\t{{.Ports}}"))) {
                return new CommandExecutionResult(1, "docker unavailable");
            }
            return new CommandExecutionResult(1, "unsupported command");
        });
        DeployService service = new DeployService(executor, workspace.toFile());

        ServiceStatusSnapshot status = service.currentStatus();

        assertEquals("FAILED", status.deploymentState());
        assertEquals("最近一次部署失败", status.deploymentMessage());
        assertFalse(status.serviceReachable());
        assertFalse(status.dockerAvailable());
        assertTrue(status.dockerError().contains("docker unavailable"));
    }

    @Test
    void currentStatusShouldFallbackModelProbeToHostDockerInternal() throws Exception {
        Files.writeString(workspace.resolve("deploy.log"), "CI/CD部署流程执行成功\n");
        FakeCommandExecutor executor = new FakeCommandExecutor(command -> {
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "-o", "/dev/null", "http://localhost:8081/index.html"))) {
                return new CommandExecutionResult(0, "");
            }
            if (command.equals(List.of("docker", "ps", "--format", "{{.Names}}\t{{.Status}}\t{{.Ports}}"))) {
                return new CommandExecutionResult(0, "chart-demo\tUp 1 minute\t8081/tcp\nmysql\tUp 1 minute\t3306/tcp\n");
            }
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "http://localhost:11434/api/tags"))) {
                return new CommandExecutionResult(7, "connection refused");
            }
            if (command.equals(List.of("curl", "-fsS", "--max-time", "2", "http://host.docker.internal:11434/api/tags"))) {
                return new CommandExecutionResult(0, "{\"models\":[{\"name\":\"qwen2.5:latest\"}]}");
            }
            return new CommandExecutionResult(7, "connection refused");
        });
        DeployService service = new DeployService(executor, workspace.toFile());

        ServiceStatusSnapshot status = service.currentStatus();

        assertEquals("UP", status.modelServices().get(0).state());
        assertTrue(status.modelServices().get(0).modelSummary().contains("qwen2.5"));
        assertTrue(executor.executedCommands.contains(List.of("curl", "-fsS", "--max-time", "2", "http://localhost:11434/api/tags")));
        assertTrue(executor.executedCommands.contains(List.of("curl", "-fsS", "--max-time", "2", "http://host.docker.internal:11434/api/tags")));
    }

    private static final class FakeCommandExecutor implements CommandExecutor {
        private final Function<List<String>, CommandExecutionResult> resultFactory;
        private final List<List<String>> executedCommands = new ArrayList<>();
        private final List<List<String>> startedCommands = new ArrayList<>();
        private final List<List<String>> streamedCommands = new ArrayList<>();
        private final List<File> logFiles = new ArrayList<>();

        private FakeCommandExecutor(CommandExecutionResult result) {
            this(command -> result);
        }

        private FakeCommandExecutor(Function<List<String>, CommandExecutionResult> resultFactory) {
            this.resultFactory = resultFactory;
        }

        @Override
        public CommandExecutionResult execute(List<String> command, File directory) {
            executedCommands.add(List.copyOf(command));
            return resultFactory.apply(command);
        }

        @Override
        public void start(List<String> command, File directory, File logFile) throws IOException {
            startedCommands.add(List.copyOf(command));
            logFiles.add(logFile);
        }

        @Override
        public CommandLogStream stream(List<String> command, File directory) {
            streamedCommands.add(List.copyOf(command));
            InputStream inputStream = new java.io.ByteArrayInputStream("line\n".getBytes(StandardCharsets.UTF_8));
            return new CommandLogStream(inputStream, () -> {
            });
        }
    }

    private Executor directExecutor() {
        return Runnable::run;
    }
}
