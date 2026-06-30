package com.example.demo.controller;

import com.example.demo.service.DeployResult;
import com.example.demo.service.DeployService;
import com.example.demo.service.CommandLogStream;
import com.example.demo.service.ServiceStatusSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/deploy")
@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"})
public class DeployController {

    private static final Logger log = LoggerFactory.getLogger(DeployController.class);

    private final DeployService deployService;
    private final ExecutorService logStreamExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "docker-log-stream-worker");
        thread.setDaemon(true);
        return thread;
    });

    public DeployController(DeployService deployService) {
        this.deployService = deployService;
    }

    @PostMapping("/build")
    public Map<String, Object> buildProject() {
        log.info("收到构建项目请求");
        DeployResult result = deployService.buildProject();
        return result.toResponse();
    }

    @PostMapping("/deploy")
    public Map<String, Object> deployProject() {
        log.info("收到部署项目请求");
        DeployResult result = deployService.deployProject();
        return result.toResponse();
    }

    @PostMapping("/restart")
    public Map<String, Object> restartProject() {
        log.info("收到重启项目请求");
        DeployResult result = deployService.restartProject();
        return result.toResponse();
    }

    @PostMapping("/model-service/start")
    public Map<String, Object> startModelService(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String serviceId = getString(safeRequest, "serviceId");

        log.info("收到启动模型服务请求: serviceId={}", serviceId);
        DeployResult result = deployService.startModelService(serviceId);
        return result.toResponse();
    }

    @GetMapping("/status")
    public Map<String, Object> currentStatus() {
        log.info("收到查询服务运行状态请求");
        ServiceStatusSnapshot status = deployService.currentStatus();
        return Map.of(
                "success", true,
                "message", "获取服务运行状态成功",
                "data", status
        );
    }

    @PostMapping("/logs")
    public Map<String, Object> getDockerLogs(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String container = getString(safeRequest, "container");
        boolean follow = Boolean.parseBoolean(String.valueOf(safeRequest.getOrDefault("follow", false)));
        Integer tailLines = getInteger(safeRequest, "tail");

        log.info("收到获取Docker日志请求: container={}, follow={}, tailLines={}", container, follow, tailLines);
        DeployResult result = deployService.getDockerLogs(container, follow, tailLines);
        return result.toResponse();
    }

    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDockerLogs(
            @RequestParam String container,
            @RequestParam(required = false) Integer tail
    ) {
        log.info("收到流式获取Docker日志请求: container={}, tail={}", container, tail);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<CommandLogStream> activeStream = new AtomicReference<>();

        emitter.onCompletion(() -> closeActiveStream(activeStream));
        emitter.onTimeout(() -> closeActiveStream(activeStream));
        emitter.onError(error -> closeActiveStream(activeStream));

        logStreamExecutor.execute(() -> {
            DeployResult validationResult = deployService.validateDockerLogsRequest(container, tail);
            if (!validationResult.success()) {
                sendErrorEvent(emitter, validationResult.message());
                return;
            }

            try (CommandLogStream stream = deployService.streamDockerLogs(container, tail);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream.inputStream(), StandardCharsets.UTF_8))) {
                activeStream.set(stream);
                emitter.send(SseEmitter.event().name("status").data("connected"));

                String line;
                while ((line = reader.readLine()) != null) {
                    emitter.send(SseEmitter.event().name("log").data(line));
                }

                emitter.complete();
            } catch (IOException e) {
                log.error("流式获取Docker日志IO失败: container={}, message={}", container, e.getMessage());
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("流式获取Docker日志失败: container={}, message={}", container, e.getMessage());
                sendErrorEvent(emitter, e.getMessage());
            } finally {
                activeStream.set(null);
            }
        });

        return emitter;
    }

    private String getString(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? null : value.toString();
    }

    private Integer getInteger(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.error("请求参数解析失败: key={}, value={}", key, value);
            return null;
        }
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (IOException e) {
            log.error("发送SSE错误事件失败: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private void closeActiveStream(AtomicReference<CommandLogStream> activeStream) {
        CommandLogStream stream = activeStream.getAndSet(null);
        if (stream == null) {
            return;
        }

        try {
            stream.close();
        } catch (IOException e) {
            log.error("关闭Docker日志流失败: {}", e.getMessage());
        }
    }
}
