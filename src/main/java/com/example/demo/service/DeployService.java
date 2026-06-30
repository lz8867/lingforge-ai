package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    private static final int DEFAULT_TAIL_LINES = 500;
    private static final int MAX_TAIL_LINES = 5000;
    private static final int STATUS_LOG_TAIL_LINES = 80;
    private static final Set<String> ALLOWED_LOG_CONTAINERS = Set.of("chart-demo", "mysql");
    private static final List<String> STATUS_CONTAINERS = List.of("chart-demo", "mysql");
    private static final String APP_CONTAINER = "chart-demo";
    private static final String APP_IMAGE = "chart-demo:latest";
    private static final String APP_HOST_PORT = "8081";
    private static final String APP_CONTAINER_PORT = "8080";
    private static final String APP_SERVICE_URL = "http://localhost:" + APP_HOST_PORT;
    private static final List<ModelServiceProbe> MODEL_SERVICE_PROBES = List.of(
            new ModelServiceProbe(
                    "ollama",
                    "Ollama 本地模型",
                    "Ollama",
                    "文本生成 / 视觉理解 / Embedding",
                    "http://localhost:11434/api/tags",
                    11434,
                    List.of("ollama", "serve"),
                    "",
                    "启动 ollama serve，并拉取 qwen2.5、qwen2.5vl:3b；文档 embedding 如需外部模型请拉取 nomic-embed-text。"
            ),
            new ModelServiceProbe(
                    "stable-diffusion",
                    "Stable Diffusion / Forge",
                    "local-free",
                    "文生图",
                    "http://localhost:7860/sdapi/v1/sd-models",
                    7860,
                    List.of("bash", "scripts/start-stable-diffusion.sh"),
                    "scripts/start-stable-diffusion.sh",
                    "启动 scripts/start-stable-diffusion.sh，拉起 Stable Diffusion WebUI/Forge API，推荐加载 FLUX.1-schnell 或 SDXL。"
            ),
            new ModelServiceProbe(
                    "wan-video",
                    "Wan2.1 视频桥",
                    "local-free",
                    "文生视频",
                    "http://localhost:7861/api/text-to-video/health",
                    7861,
                    List.of("bash", "scripts/start-wan-video-server.sh"),
                    "scripts/start-wan-video-server.sh",
                    "准备 Wan2.1 仓库和 Wan2.1-T2V-1.3B 权重后启动 scripts/start-wan-video-server.sh。"
            ),
            new ModelServiceProbe(
                    "triposplat",
                    "TripoSplat 3D 建模",
                    "local-gpu",
                    "单图到 3D Gaussian",
                    "http://localhost:7862/health",
                    7862,
                    List.of("bash", "scripts/start-triposplat-service.sh"),
                    "scripts/start-triposplat-service.sh",
                    "配置 VAST-AI-Research/TripoSplat 仓库和权重后启动 scripts/start-triposplat-service.sh。"
            )
    );
    private static final Pattern JSON_STRING_FIELD_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");

    private final CommandExecutor commandExecutor;
    private final File workingDirectory;
    private final Executor deploymentExecutor;
    private final Map<String, String> environment;
    private final AtomicBoolean deploymentRunning = new AtomicBoolean(false);

    @Autowired
    public DeployService(CommandExecutor commandExecutor, @Value("${deploy.project-dir:.}") String projectDirectory) {
        this(commandExecutor, new File(projectDirectory));
    }

    DeployService(CommandExecutor commandExecutor, File workingDirectory) {
        this(commandExecutor, workingDirectory, defaultDeploymentExecutor());
    }

    DeployService(CommandExecutor commandExecutor, File workingDirectory, Executor deploymentExecutor) {
        this(commandExecutor, workingDirectory, deploymentExecutor, System.getenv());
    }

    DeployService(CommandExecutor commandExecutor, File workingDirectory, Executor deploymentExecutor, Map<String, String> environment) {
        this.commandExecutor = commandExecutor;
        this.workingDirectory = workingDirectory;
        this.deploymentExecutor = deploymentExecutor;
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
    }

    private static Executor defaultDeploymentExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "deploy-cicd-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public DeployResult buildProject() {
        log.info("开始构建项目");
        if (shouldDelegateDeploymentActionsToHost()) {
            return delegateToHostDeployer("构建项目", "/api/deploy/build");
        }

        StringBuilder output = new StringBuilder("开始构建项目...\n");

        try {
            buildCurrentProject(output);

            log.info("构建项目成功");
            return DeployResult.success("构建成功", output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("构建项目被中断: {}", e.getMessage());
            return DeployResult.failure("构建过程中出现错误", output.toString(), e.getMessage());
        } catch (Exception e) {
            log.error("构建项目出现错误: {}", e.getMessage());
            return DeployResult.failure("构建过程中出现错误", output.toString(), e.getMessage());
        }
    }

    public DeployResult deployProject() {
        log.info("开始部署项目");
        if (shouldDelegateDeploymentActionsToHost()) {
            return delegateToHostDeployer("部署应用", "/api/deploy/deploy");
        }

        StringBuilder output = new StringBuilder();
        output.append("开始部署项目...\n");
        output.append("启动后台CI/CD部署流程...\n");

        try {
            DeployResult validationResult = validateProjectForDeployment();
            if (!validationResult.success()) {
                return validationResult;
            }

            if (!deploymentRunning.compareAndSet(false, true)) {
                output.append("已有部署流程正在执行，请稍后查看 deploy.log\n");
                log.info("部署请求已忽略: 已有部署流程执行中");
                return DeployResult.success("部署流程已在执行中", output.toString());
            }

            resetDeployLog();
            deploymentExecutor.execute(this::runDeploymentPipeline);

            output.append("CI/CD部署流程将在后台执行，请查看部署日志和 Docker 日志页面\n");
            log.info("后台CI/CD部署流程已启动");
            return DeployResult.success("后台CI/CD部署流程已启动", output.toString());
        } catch (Exception e) {
            log.error("部署项目出现错误: {}", e.getMessage());
            return DeployResult.failure("部署过程中出现错误", output.toString(), e.getMessage());
        }
    }

    public DeployResult restartProject() {
        log.info("开始重启项目");
        if (shouldDelegateDeploymentActionsToHost()) {
            String output = """
                    当前请求来自 Docker 容器版应用，不能在容器内重启自身。
                    请从宿主机部署器执行重启: http://localhost:8080/index.html#deploy
                    页面从 8081 打开时会自动把重启请求发送到宿主机 8080。
                    """;
            log.warn("拒绝容器内自重启，请使用宿主机部署器");
            return DeployResult.failure("不能在容器内重启自身", output);
        }

        StringBuilder output = new StringBuilder("开始重启项目...\n");

        try {
            CommandExecutionResult restartResult = commandExecutor.execute(
                    List.of("docker", "restart", "chart-demo", "mysql"),
                    workingDirectory
            );
            output.append(restartResult.output());

            if (restartResult.exitCode() != 0) {
                log.error("重启项目失败: exitCode={}", restartResult.exitCode());
                return DeployResult.failure("重启项目失败", output.toString());
            }

            output.append("重启操作已完成\n");
            log.info("重启项目成功");
            return DeployResult.success("重启操作已完成", output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("重启项目被中断: {}", e.getMessage());
            return DeployResult.failure("重启过程中出现错误", output.toString(), e.getMessage());
        } catch (Exception e) {
            log.error("重启项目出现错误: {}", e.getMessage());
            return DeployResult.failure("重启过程中出现错误", output.toString(), e.getMessage());
        }
    }

    private DeployResult delegateToHostDeployer(String actionLabel, String endpoint) {
        String hostBaseUrl = hostDeployerBaseUrl();
        String targetUrl = hostBaseUrl + endpoint;
        StringBuilder output = new StringBuilder();
        output.append(actionLabel).append("已转交宿主机部署器\n");
        output.append("目标地址: ").append(targetUrl).append('\n');

        log.info("容器运行态部署动作转交宿主机部署器: action={}, targetUrl={}", actionLabel, targetUrl);
        try {
            CommandExecutionResult result = commandExecutor.execute(
                    List.of("curl", "-fsS", "--max-time", "8", "-X", "POST", targetUrl),
                    workingDirectory
            );
            output.append(result.output());

            if (result.exitCode() != 0) {
                log.warn("宿主机部署器不可用: action={}, exitCode={}, output={}", actionLabel, result.exitCode(), firstLine(result.output()));
                output.append("\n请先在宿主机启动本地服务: mvn spring-boot:run，然后打开 http://localhost:8080/index.html#deploy。\n");
                return DeployResult.failure("宿主机部署器不可用", output.toString(), result.output());
            }

            if (result.output() != null && result.output().contains("\"success\":false")) {
                log.warn("宿主机部署器返回失败: action={}, output={}", actionLabel, firstLine(result.output()));
                return DeployResult.failure("宿主机部署器返回失败", output.toString(), result.output());
            }

            return DeployResult.success(actionLabel + "已交给宿主机部署器", output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("宿主机部署器请求被中断: action={}, message={}", actionLabel, e.getMessage());
            return DeployResult.failure("宿主机部署器请求被中断", output.toString(), e.getMessage());
        } catch (Exception e) {
            log.error("宿主机部署器请求异常: action={}, message={}", actionLabel, e.getMessage());
            output.append("请求宿主机部署器失败: ").append(e.getMessage()).append('\n');
            return DeployResult.failure("宿主机部署器不可用", output.toString(), e.getMessage());
        }
    }

    private boolean shouldDelegateDeploymentActionsToHost() {
        String forced = environment.getOrDefault("DEPLOY_DELEGATE_TO_HOST", "");
        if ("false".equalsIgnoreCase(forced)) {
            return false;
        }
        if ("true".equalsIgnoreCase(forced)) {
            return true;
        }
        if ("true".equalsIgnoreCase(environment.getOrDefault("DEPLOY_CONTAINER_MODE", ""))) {
            return true;
        }
        if (Files.isRegularFile(Path.of("/.dockerenv"))) {
            return true;
        }
        try {
            return "/workspace".equals(workingDirectory.getCanonicalPath());
        } catch (IOException e) {
            log.warn("判断部署工作目录失败: {}", e.getMessage());
            return false;
        }
    }

    private String hostDeployerBaseUrl() {
        String configuredUrl = environment.getOrDefault("DEPLOY_HOST_DEPLOYER_URL", "http://host.docker.internal:8080").trim();
        if (configuredUrl.isBlank()) {
            configuredUrl = "http://host.docker.internal:8080";
        }
        while (configuredUrl.endsWith("/")) {
            configuredUrl = configuredUrl.substring(0, configuredUrl.length() - 1);
        }
        return configuredUrl;
    }

    public DeployResult startModelService(String serviceId) {
        log.info("开始启动模型服务: serviceId={}", serviceId);
        ModelServiceProbe probe = findModelServiceProbe(serviceId);
        if (probe == null) {
            log.error("启动模型服务失败: 不支持的服务ID");
            return DeployResult.failure("不支持的模型服务", "serviceId: " + String.valueOf(serviceId) + "\n");
        }

        if (probe.startCommand().isEmpty()) {
            String output = probe.name() + " 未配置一键启动命令。\n建议: " + probe.actionSuggestion() + "\n";
            log.warn("启动模型服务失败: id={}, reason=未配置启动命令", probe.id());
            return DeployResult.failure("未配置 " + probe.name() + " 一键启动命令", output);
        }
        if (!probe.requiredStartPath().isBlank()) {
            Path requiredPath = workingDirectory.toPath().resolve(probe.requiredStartPath()).normalize();
            if (!Files.isRegularFile(requiredPath)) {
                String output = probe.name() + " 启动入口不存在: " + requiredPath + "\n建议: " + probe.actionSuggestion() + "\n";
                log.warn("启动模型服务失败: id={}, reason=启动入口不存在, path={}", probe.id(), requiredPath);
                return DeployResult.failure(probe.name() + " 启动入口不存在", output);
            }
        }

        File logFile = workingDirectory.toPath()
                .resolve("logs")
                .resolve("model-service-" + probe.id() + ".log")
                .toFile();
        StringBuilder output = new StringBuilder();
        output.append("模型服务: ").append(probe.name()).append('\n');
        output.append("启动命令: ").append(displayCommand(probe.startCommand())).append('\n');
        output.append("日志文件: ").append(logFile.getPath()).append('\n');

        try {
            commandExecutor.start(probe.startCommand(), workingDirectory, logFile);
            log.info("模型服务启动命令已提交: id={}, logFile={}", probe.id(), logFile.getPath());
            output.append("启动请求已提交，请稍后刷新运行状态。\n");
            return DeployResult.success(probe.name() + " 启动请求已提交", output.toString());
        } catch (IOException e) {
            log.error("启动模型服务异常: id={}, message={}", probe.id(), e.getMessage());
            output.append("启动失败: ").append(e.getMessage()).append('\n');
            return DeployResult.failure(probe.name() + " 启动失败", output.toString(), e.getMessage());
        }
    }

    public ServiceStatusSnapshot currentStatus() {
        log.info("开始查询服务运行状态");
        String deployLogTail = readDeployLogTail(STATUS_LOG_TAIL_LINES);
        DeploymentSummary deploymentSummary = summarizeDeployment(deployLogTail);
        HealthCheckStatus healthCheckStatus = checkServiceReachable();
        DockerStatus dockerStatus = loadDockerStatus();
        RuntimeStatus runtimeStatus = loadRuntimeStatus();
        ServiceStatusSnapshot.ServiceRuntimeMetrics serviceMetrics = loadServiceRuntimeMetrics(healthCheckStatus.latencyMs());
        List<ServiceStatusSnapshot.ModelServiceStatus> modelServices = loadModelServiceStatuses();

        log.info("服务运行状态查询完成: deploymentState={}, serviceReachable={}, dockerAvailable={}, modelServiceCount={}",
                deploymentSummary.state(), healthCheckStatus.reachable(), dockerStatus.available(), modelServices.size());

        return new ServiceStatusSnapshot(
                OffsetDateTime.now().toString(),
                deploymentSummary.state(),
                deploymentSummary.message(),
                deploymentRunning.get(),
                healthCheckStatus.reachable() ? "UP" : "DOWN",
                healthCheckStatus.reachable(),
                APP_SERVICE_URL,
                dockerStatus.available(),
                dockerStatus.error(),
                dockerStatus.containers(),
                runtimeStatus.uptimeSeconds(),
                runtimeStatus.memoryUsedMb(),
                runtimeStatus.memoryMaxMb(),
                runtimeStatus.memoryUsagePercent(),
                runtimeStatus.availableProcessors(),
                serviceMetrics,
                modelServices,
                deployLogTail
        );
    }

    public DeployResult getDockerLogs(String container, boolean follow, Integer tailLines) {
        log.info("开始获取Docker日志: container={}, follow={}, tailLines={}", container, follow, tailLines);
        String normalizedContainer = normalizeContainer(container);
        if (normalizedContainer == null) {
            log.error("获取Docker日志失败: 不支持的容器名称");
            return DeployResult.failure("不支持的Docker容器", "");
        }

        int normalizedTailLines = normalizeTailLines(tailLines);
        StringBuilder output = new StringBuilder("开始获取Docker容器日志...\n");

        try {
            CommandExecutionResult logsResult = commandExecutor.execute(
                    List.of("docker", "logs", "--tail=" + normalizedTailLines, normalizedContainer),
                    workingDirectory
            );
            output.append(logsResult.output());

            if (logsResult.exitCode() != 0) {
                log.error("获取Docker日志失败: container={}, exitCode={}", normalizedContainer, logsResult.exitCode());
                return DeployResult.failure("获取Docker容器日志失败", output.toString());
            }

            log.info("获取Docker日志成功: container={}", normalizedContainer);
            return DeployResult.success("获取Docker容器日志成功", output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取Docker日志被中断: container={}, message={}", normalizedContainer, e.getMessage());
            return DeployResult.failure("获取Docker容器日志过程中出现错误", output.toString(), e.getMessage());
        } catch (Exception e) {
            log.error("获取Docker日志出现错误: container={}, message={}", normalizedContainer, e.getMessage());
            return DeployResult.failure("获取Docker容器日志过程中出现错误", output.toString(), e.getMessage());
        }
    }

    public DeployResult validateDockerLogsRequest(String container, Integer tailLines) {
        String normalizedContainer = normalizeContainer(container);
        if (normalizedContainer == null) {
            log.error("Docker日志请求校验失败: 不支持的容器名称");
            return DeployResult.failure("不支持的Docker容器", "");
        }
        normalizeTailLines(tailLines);
        return DeployResult.success("Docker日志请求有效", "");
    }

    public CommandLogStream streamDockerLogs(String container, Integer tailLines) throws IOException {
        log.info("开始流式获取Docker日志: container={}, tailLines={}", container, tailLines);
        String normalizedContainer = normalizeContainer(container);
        if (normalizedContainer == null) {
            log.error("流式获取Docker日志失败: 不支持的容器名称");
            throw new IllegalArgumentException("不支持的Docker容器");
        }

        int normalizedTailLines = normalizeTailLines(tailLines);
        return commandExecutor.stream(
                List.of("docker", "logs", "--follow", "--tail=" + normalizedTailLines, normalizedContainer),
                workingDirectory
        );
    }

    private DeployResult validateProjectForDeployment() {
        Path projectPath = workingDirectory.toPath();
        if (!Files.isRegularFile(projectPath.resolve("pom.xml"))) {
            log.error("部署项目校验失败: pom.xml不存在");
            return DeployResult.failure("当前项目目录缺少 pom.xml", "当前项目目录: " + projectPath + "\n");
        }
        if (!Files.isRegularFile(projectPath.resolve("Dockerfile"))) {
            log.error("部署项目校验失败: Dockerfile不存在");
            return DeployResult.failure("当前项目目录缺少 Dockerfile", "当前项目目录: " + projectPath + "\n");
        }
        return DeployResult.success("部署项目校验通过", "");
    }

    private DeploymentSummary summarizeDeployment(String deployLogTail) {
        if (deploymentRunning.get()) {
            return new DeploymentSummary("DEPLOYING", "部署执行中");
        }
        if (deployLogTail.contains("CI/CD部署流程执行成功")) {
            return new DeploymentSummary("SUCCEEDED", "服务已正常运行");
        }
        if (deployLogTail.contains("CI/CD部署流程执行失败") || deployLogTail.contains("CI/CD部署流程被中断")) {
            return new DeploymentSummary("FAILED", "最近一次部署失败");
        }
        if (deployLogTail.contains("暂无部署日志")) {
            return new DeploymentSummary("UNKNOWN", "暂无部署记录");
        }
        return new DeploymentSummary("UNKNOWN", "等待部署结果");
    }

    private HealthCheckStatus checkServiceReachable() {
        log.info("开始检查部署服务可达性: url={}", APP_SERVICE_URL);
        List<String> healthUrls = List.of(
                APP_SERVICE_URL + "/index.html",
                "http://host.docker.internal:" + APP_HOST_PORT + "/index.html"
        );

        for (String healthUrl : healthUrls) {
            long startedAt = System.nanoTime();
            try {
                CommandExecutionResult result = commandExecutor.execute(
                        List.of("curl", "-fsS", "--max-time", "2", "-o", "/dev/null", healthUrl),
                        workingDirectory
                );
                long latencyMs = elapsedMillis(startedAt);
                if (result.exitCode() == 0) {
                    log.info("部署服务健康检查通过: url={}, latencyMs={}", healthUrl, latencyMs);
                    return new HealthCheckStatus(true, latencyMs);
                }
                log.warn("部署服务健康检查未通过: url={}, exitCode={}, output={}",
                        healthUrl, result.exitCode(), result.output());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("部署服务健康检查被中断: url={}, message={}", healthUrl, e.getMessage());
                return new HealthCheckStatus(false, elapsedMillis(startedAt));
            } catch (Exception e) {
                log.error("部署服务健康检查失败: url={}, message={}", healthUrl, e.getMessage());
            }
        }
        return new HealthCheckStatus(false, -1);
    }

    private DockerStatus loadDockerStatus() {
        log.info("开始读取Docker容器状态");
        try {
            CommandExecutionResult result = commandExecutor.execute(
                    List.of("docker", "ps", "--format", "{{.Names}}\t{{.Status}}\t{{.Ports}}"),
                    workingDirectory
            );
            if (result.exitCode() != 0) {
                log.warn("读取Docker容器状态失败: exitCode={}, output={}", result.exitCode(), result.output());
                return new DockerStatus(false, result.output(), List.of());
            }
            return new DockerStatus(true, null, parseContainerStatuses(result.output()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("读取Docker容器状态被中断: {}", e.getMessage());
            return new DockerStatus(false, e.getMessage(), List.of());
        } catch (Exception e) {
            log.error("读取Docker容器状态失败: {}", e.getMessage());
            return new DockerStatus(false, e.getMessage(), List.of());
        }
    }

    private List<ServiceStatusSnapshot.ContainerStatus> parseContainerStatuses(String output) {
        Map<String, ServiceStatusSnapshot.ContainerStatus> byName = output.lines()
                .map(this::parseContainerStatus)
                .filter(status -> status != null && STATUS_CONTAINERS.contains(status.name()))
                .collect(Collectors.toMap(
                        ServiceStatusSnapshot.ContainerStatus::name,
                        status -> status,
                        (left, ignored) -> left
                ));

        List<ServiceStatusSnapshot.ContainerStatus> containers = new ArrayList<>();
        for (String containerName : STATUS_CONTAINERS) {
            containers.add(byName.getOrDefault(containerName,
                    new ServiceStatusSnapshot.ContainerStatus(containerName, "未运行", "", true)));
        }
        return containers;
    }

    private List<ServiceStatusSnapshot.ModelServiceStatus> loadModelServiceStatuses() {
        log.info("开始检查模型服务运行状态");
        List<ServiceStatusSnapshot.ModelServiceStatus> statuses = new ArrayList<>();
        for (ModelServiceProbe probe : MODEL_SERVICE_PROBES) {
            statuses.add(checkModelService(probe));
        }
        return statuses;
    }

    private ServiceStatusSnapshot.ModelServiceStatus checkModelService(ModelServiceProbe probe) {
        long startedAt = System.nanoTime();
        String lastMessage = "端口未响应或服务未启动";
        long lastLatencyMs = -1;
        for (String endpoint : modelServiceHealthEndpoints(probe)) {
            try {
                CommandExecutionResult result = commandExecutor.execute(
                        List.of("curl", "-fsS", "--max-time", "2", endpoint),
                        workingDirectory
                );
                long latencyMs = elapsedMillis(startedAt);
                lastLatencyMs = latencyMs;
                if (result.exitCode() == 0) {
                    return reachableModelServiceStatus(probe, result.output(), latencyMs);
                }
                String message = result.output() == null || result.output().isBlank()
                        ? "端口未响应或服务未启动"
                        : firstLine(result.output());
                lastMessage = message;
                log.warn("模型服务检查失败: id={}, endpoint={}, exitCode={}, message={}",
                        probe.id(), endpoint, result.exitCode(), message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("模型服务检查被中断: id={}, endpoint={}", probe.id(), endpoint);
                return modelServiceStatus(probe, "DOWN", false, elapsedMillis(startedAt), "--", "检查被中断");
            } catch (Exception e) {
                lastLatencyMs = elapsedMillis(startedAt);
                lastMessage = e.getMessage();
                log.error("模型服务检查异常: id={}, endpoint={}, message={}", probe.id(), endpoint, e.getMessage());
            }
        }
        return modelServiceStatus(probe, "DOWN", false, lastLatencyMs, "--", lastMessage);
    }

    private List<String> modelServiceHealthEndpoints(ModelServiceProbe probe) {
        if (!probe.endpoint().contains("://localhost:")) {
            return List.of(probe.endpoint());
        }
        return List.of(
                probe.endpoint(),
                probe.endpoint().replace("://localhost:", "://host.docker.internal:")
        );
    }

    private ServiceStatusSnapshot.ModelServiceStatus reachableModelServiceStatus(
            ModelServiceProbe probe,
            String output,
            long latencyMs
    ) {
        if ("wan-video".equals(probe.id())) {
            boolean configured = jsonBoolean(output, "configured") || jsonBoolean(output, "ready") || jsonBoolean(output, "canGenerate");
            String model = firstJsonString(output, "model", "modelName").orElse("Wan2.1-T2V-1.3B");
            String message = firstJsonString(output, "message", "reason").orElse(configured ? "Wan2.1 视频桥可用" : "Wan2.1 视频桥未完成配置");
            return modelServiceStatus(probe, configured ? "UP" : "DEGRADED", true, latencyMs, model, message);
        }

        if ("triposplat".equals(probe.id())) {
            boolean configured = jsonBoolean(output, "configured") || jsonBoolean(output, "ready") || jsonBoolean(output, "success");
            String message = firstJsonString(output, "message", "reason").orElse(configured ? "TripoSplat 适配服务可用" : "TripoSplat 适配服务未完成配置");
            return modelServiceStatus(probe, configured ? "UP" : "DEGRADED", true, latencyMs, "TripoSplat", message);
        }

        if ("ollama".equals(probe.id())) {
            List<String> models = jsonStringValues(output, "name");
            String summary = models.isEmpty() ? "Ollama 可达，未读取到模型列表" : String.join("、", models.stream().limit(4).toList());
            String message = models.stream().anyMatch(model -> model.startsWith("qwen2.5"))
                    ? "Ollama 服务可达，文本/视觉模型已检测到"
                    : "Ollama 服务可达，但未检测到 qwen2.5 系列模型";
            return modelServiceStatus(probe, "UP", true, latencyMs, summary, message);
        }

        List<String> models = jsonStringValues(output, "model_name");
        if (models.isEmpty()) {
            models = jsonStringValues(output, "title");
        }
        String summary = models.isEmpty() ? "服务可达，未读取到模型名称" : String.join("、", models.stream().limit(3).toList());
        return modelServiceStatus(probe, "UP", true, latencyMs, summary, "本地图片生成服务可达");
    }

    private ServiceStatusSnapshot.ModelServiceStatus modelServiceStatus(
            ModelServiceProbe probe,
            String state,
            boolean reachable,
            long latencyMs,
            String modelSummary,
            String message
    ) {
        return new ServiceStatusSnapshot.ModelServiceStatus(
                probe.id(),
                probe.name(),
                probe.provider(),
                probe.capability(),
                probe.endpoint(),
                probe.port(),
                state,
                reachable,
                latencyMs,
                modelSummary == null || modelSummary.isBlank() ? "--" : modelSummary,
                message == null || message.isBlank() ? "--" : message,
                probe.actionSuggestion()
        );
    }

    private ServiceStatusSnapshot.ContainerStatus parseContainerStatus(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        String name = parts.length > 0 ? parts[0].trim() : "";
        String status = parts.length > 1 ? parts[1].trim() : "";
        String ports = parts.length > 2 ? parts[2].trim() : "";
        if (name.isBlank()) {
            return null;
        }
        return new ServiceStatusSnapshot.ContainerStatus(name, status, ports, STATUS_CONTAINERS.contains(name));
    }

    private static List<String> jsonStringValues(String json, String field) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Pattern pattern = Pattern.compile(String.format(JSON_STRING_FIELD_PATTERN_TEMPLATE.pattern(), Pattern.quote(field)));
        Matcher matcher = pattern.matcher(json);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static java.util.Optional<String> firstJsonString(String json, String... fields) {
        for (String field : fields) {
            List<String> values = jsonStringValues(json, field);
            if (!values.isEmpty()) {
                return java.util.Optional.of(values.get(0));
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean jsonBoolean(String json, String field) {
        if (json == null || json.isBlank()) {
            return false;
        }
        return Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*true").matcher(json).find();
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.lines().findFirst().orElse(text).trim();
    }

    private RuntimeStatus loadRuntimeStatus() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMb = bytesToMb(heapUsage.getUsed());
        long maxMb = bytesToMb(heapUsage.getMax());
        double usagePercent = maxMb <= 0 ? 0 : round(usedMb * 100.0 / maxMb);

        return new RuntimeStatus(
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                usedMb,
                maxMb,
                usagePercent,
                Runtime.getRuntime().availableProcessors()
        );
    }

    private ServiceStatusSnapshot.ServiceRuntimeMetrics loadServiceRuntimeMetrics(long healthLatencyMs) {
        log.info("开始读取服务运行指标");
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

        double processCpuLoadPercent = -1;
        double systemCpuLoadPercent = -1;
        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean sunOperatingSystemMXBean) {
            processCpuLoadPercent = percentFromLoad(sunOperatingSystemMXBean.getProcessCpuLoad());
            systemCpuLoadPercent = percentFromLoad(sunOperatingSystemMXBean.getSystemCpuLoad());
        }

        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(garbageCollectorMXBean.getCollectionCount(), 0);
            gcTimeMs += Math.max(garbageCollectorMXBean.getCollectionTime(), 0);
        }

        long heapUsedMb = bytesToMb(heapUsage.getUsed());
        long heapMaxMb = bytesToMb(heapUsage.getMax());
        double heapUsagePercent = heapMaxMb <= 0 ? 0 : round(heapUsedMb * 100.0 / heapMaxMb);
        long diskTotalGb = bytesToGb(workingDirectory.getTotalSpace());
        long diskUsableGb = bytesToGb(workingDirectory.getUsableSpace());
        double diskUsagePercent = diskTotalGb <= 0 ? 0 : round((diskTotalGb - diskUsableGb) * 100.0 / diskTotalGb);

        return new ServiceStatusSnapshot.ServiceRuntimeMetrics(
                processCpuLoadPercent,
                systemCpuLoadPercent,
                threadMXBean.getThreadCount(),
                threadMXBean.getDaemonThreadCount(),
                threadMXBean.getPeakThreadCount(),
                heapUsedMb,
                heapMaxMb,
                heapUsagePercent,
                bytesToMb(nonHeapUsage.getUsed()),
                classLoadingMXBean.getLoadedClassCount(),
                gcCount,
                gcTimeMs,
                diskUsableGb,
                diskTotalGb,
                diskUsagePercent,
                healthLatencyMs
        );
    }

    private String readDeployLogTail(int maxLines) {
        Path path = deployLogPath();
        if (!Files.isRegularFile(path)) {
            log.info("部署日志不存在: path={}", path);
            return "暂无部署日志\n";
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int fromIndex = Math.max(0, lines.size() - maxLines);
            return String.join("\n", lines.subList(fromIndex, lines.size()));
        } catch (IOException e) {
            log.error("读取部署日志失败: {}", e.getMessage());
            return "读取部署日志失败: " + e.getMessage() + "\n";
        }
    }

    private void runDeploymentPipeline() {
        log.info("CI/CD部署流程开始执行");
        StringBuilder output = new StringBuilder();

        try {
            appendDeployLog("开始CI/CD部署流程...\n");

            buildCurrentProject(output);
            appendDeployLog(output.toString());

            runRequiredCommand(List.of("docker", "info"));
            runRequiredCommand(List.of("docker", "build", "-t", APP_IMAGE, "."));
            runOptionalCommand(List.of("docker", "rm", "-f", APP_CONTAINER));
            runRequiredCommand(buildDockerRunCommand());
            waitForApplicationReady();

            appendDeployLog("CI/CD部署流程执行成功\n");
            log.info("CI/CD部署流程执行成功");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeDeploymentFailure("CI/CD部署流程被中断", e);
        } catch (Exception e) {
            writeDeploymentFailure("CI/CD部署流程执行失败", e);
        } finally {
            deploymentRunning.set(false);
        }
    }

    private void buildCurrentProject(StringBuilder output) throws IOException, InterruptedException {
        CommandExecutionResult buildResult = commandExecutor.execute(
                List.of("mvn", "clean", "package", "-Dmaven.test.skip=true"),
                workingDirectory
        );
        output.append(buildResult.output());

        if (buildResult.exitCode() != 0) {
            log.error("构建项目失败: exitCode={}", buildResult.exitCode());
            throw new IOException("构建失败，退出码: " + buildResult.exitCode());
        }

        output.append("构建成功！\n");
        output.append("复制构建产物...\n");
        Path jarPath = findLatestJar();
        Files.copy(jarPath, workingDirectory.toPath().resolve("app.jar"), StandardCopyOption.REPLACE_EXISTING);
        output.append("构建完成，app.jar已更新\n");

        log.info("构建项目成功: jar={}", jarPath.getFileName());
    }

    private List<String> buildDockerRunCommand() throws IOException {
        String projectMount = workingDirectory.getCanonicalPath() + ":/workspace";
        return List.of(
                "docker", "run", "-d",
                "--restart=always",
                "--name", APP_CONTAINER,
                "-p", APP_HOST_PORT + ":" + APP_CONTAINER_PORT,
                "--add-host", "host.docker.internal:host-gateway",
                "-v", "/var/run/docker.sock:/var/run/docker.sock",
                "-v", projectMount,
                "-e", "MYSQL_PASSWORD=activiti",
                "-e", "SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/activiti?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                "-e", "OLLAMA_BASE_URL=http://host.docker.internal:11434",
                "-e", "DEPLOY_PROJECT_DIR=/workspace",
                APP_IMAGE
        );
    }

    private void waitForApplicationReady() throws IOException, InterruptedException {
        for (int i = 1; i <= 20; i++) {
            CommandExecutionResult healthCheck = runOptionalCommand(List.of(
                    "curl", "-fsS", "-o", "/dev/null", "http://localhost:" + APP_HOST_PORT + "/index.html"
            ));
            if (healthCheck.exitCode() == 0) {
                appendDeployLog("应用容器已就绪: http://localhost:" + APP_HOST_PORT + "\n");
                return;
            }
            appendDeployLog("等待应用容器就绪... (" + i + "/20)\n");
            Thread.sleep(3000);
        }
        throw new IOException("应用容器未在预期时间内就绪");
    }

    private CommandExecutionResult runRequiredCommand(List<String> command) throws IOException, InterruptedException {
        CommandExecutionResult result = runCommand(command);
        if (result.exitCode() != 0) {
            throw new IOException("命令执行失败: " + command.get(0) + "，退出码: " + result.exitCode());
        }
        return result;
    }

    private CommandExecutionResult runOptionalCommand(List<String> command) throws IOException, InterruptedException {
        return runCommand(command);
    }

    private CommandExecutionResult runCommand(List<String> command) throws IOException, InterruptedException {
        appendDeployLog("$ " + displayCommand(command) + "\n");
        CommandExecutionResult result = commandExecutor.execute(command, workingDirectory);
        appendDeployLog(result.output());
        if (!result.output().endsWith("\n")) {
            appendDeployLog("\n");
        }
        appendDeployLog("退出码: " + result.exitCode() + "\n");
        return result;
    }

    private void resetDeployLog() throws IOException {
        Files.writeString(
                deployLogPath(),
                "CI/CD部署日志\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private void appendDeployLog(String content) throws IOException {
        Files.writeString(
                deployLogPath(),
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private Path deployLogPath() {
        return workingDirectory.toPath().resolve("deploy.log");
    }

    private void writeDeploymentFailure(String message, Exception e) {
        log.error("{}: {}", message, e.getMessage());
        try {
            appendDeployLog(message + ": " + e.getMessage() + "\n");
        } catch (IOException ioException) {
            log.error("写入部署失败日志失败: {}", ioException.getMessage());
        }
    }

    private Path findLatestJar() throws IOException {
        Path targetDirectory = workingDirectory.toPath().resolve("target");
        try (Stream<Path> jars = Files.list(targetDirectory)) {
            return jars
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .max(Comparator.comparing(this::lastModifiedTime))
                    .orElseThrow(() -> new IOException("target目录中没有找到jar构建产物"));
        }
    }

    private long lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private String normalizeContainer(String container) {
        if (container == null) {
            return null;
        }
        String normalizedContainer = container.trim();
        if (ALLOWED_LOG_CONTAINERS.contains(normalizedContainer)) {
            return normalizedContainer;
        }
        return null;
    }

    private ModelServiceProbe findModelServiceProbe(String serviceId) {
        if (serviceId == null) {
            return null;
        }
        String normalizedServiceId = serviceId.trim();
        for (ModelServiceProbe probe : MODEL_SERVICE_PROBES) {
            if (probe.id().equals(normalizedServiceId)) {
                return probe;
            }
        }
        return null;
    }

    private int normalizeTailLines(Integer tailLines) {
        if (tailLines == null || tailLines < 1) {
            return DEFAULT_TAIL_LINES;
        }
        return Math.min(tailLines, MAX_TAIL_LINES);
    }

    private String displayCommand(List<String> command) {
        return String.join(" ", command.stream()
                .map(this::redactIfSensitive)
                .toList());
    }

    private String redactIfSensitive(String value) {
        int separatorIndex = value.indexOf('=');
        if (separatorIndex < 0) {
            return value;
        }

        String key = value.substring(0, separatorIndex).toUpperCase();
        if (key.contains("PASSWORD") || key.contains("TOKEN") || key.contains("SECRET") || key.contains("API_KEY")) {
            return value.substring(0, separatorIndex + 1) + "******";
        }
        return value;
    }

    private long bytesToMb(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return bytes / 1024 / 1024;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double percentFromLoad(double load) {
        if (load < 0) {
            return -1;
        }
        return round(load * 100.0);
    }

    private long bytesToGb(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return bytes / 1024 / 1024 / 1024;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private record DeploymentSummary(String state, String message) {
    }

    private record DockerStatus(
            boolean available,
            String error,
            List<ServiceStatusSnapshot.ContainerStatus> containers) {
    }

    private record RuntimeStatus(
            long uptimeSeconds,
            long memoryUsedMb,
            long memoryMaxMb,
            double memoryUsagePercent,
            int availableProcessors) {
    }

    private record HealthCheckStatus(boolean reachable, long latencyMs) {
    }

    private record ModelServiceProbe(
            String id,
            String name,
            String provider,
            String capability,
            String endpoint,
            int port,
            List<String> startCommand,
            String requiredStartPath,
            String actionSuggestion) {
    }
}
