package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ProcessCommandExecutor implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessCommandExecutor.class);

    @Override
    public CommandExecutionResult execute(List<String> command, File directory) throws IOException, InterruptedException {
        log.info("开始执行外部命令: command={}, directory={}", displayCommand(command), directory.getPath());

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(directory);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            String output = readOutput(process.getInputStream());
            int exitCode = process.waitFor();

            log.info("外部命令执行完成: command={}, exitCode={}", command.get(0), exitCode);
            return new CommandExecutionResult(exitCode, output);
        } catch (IOException | InterruptedException e) {
            log.error("外部命令执行失败: command={}, message={}", command.get(0), e.getMessage());
            throw e;
        }
    }

    @Override
    public CommandExecutionResult execute(List<String> command, File directory, Map<String, String> environment)
            throws IOException, InterruptedException {
        log.info("开始执行外部命令: command={}, directory={}, envKeys={}",
                displayCommand(command), directory.getPath(), environment.keySet());

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(directory);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().putAll(environment);

            Process process = processBuilder.start();
            String output = readOutput(process.getInputStream());
            int exitCode = process.waitFor();

            log.info("外部命令执行完成: command={}, exitCode={}", command.get(0), exitCode);
            return new CommandExecutionResult(exitCode, output);
        } catch (IOException | InterruptedException e) {
            log.error("外部命令执行失败: command={}, message={}", command.get(0), e.getMessage());
            throw e;
        }
    }

    @Override
    public void start(List<String> command, File directory, File logFile) throws IOException {
        log.info("启动后台外部命令: command={}, directory={}, logFile={}",
                displayCommand(command), directory.getPath(), logFile.getPath());

        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(directory);
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            processBuilder.start();
        } catch (IOException e) {
            log.error("后台外部命令启动失败: command={}, message={}", command.get(0), e.getMessage());
            throw e;
        }
    }

    @Override
    public CommandLogStream stream(List<String> command, File directory) throws IOException {
        log.info("开始流式执行外部命令: command={}, directory={}", displayCommand(command), directory.getPath());

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(directory);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            return new CommandLogStream(process.getInputStream(), () -> {
                process.destroy();
                log.info("流式外部命令已关闭: command={}", command.get(0));
            });
        } catch (IOException e) {
            log.error("流式外部命令启动失败: command={}, message={}", command.get(0), e.getMessage());
            throw e;
        }
    }

    private String readOutput(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private String displayCommand(List<String> command) {
        List<String> safeParts = new ArrayList<>(command.size());
        for (String part : command) {
            safeParts.add(redactIfSensitive(part));
        }
        return String.join(" ", safeParts);
    }

    private String redactIfSensitive(String value) {
        String upperCaseValue = value.toUpperCase(Locale.ROOT);
        if (upperCaseValue.contains("PASSWORD=")
                || upperCaseValue.contains("TOKEN=")
                || upperCaseValue.contains("API_KEY=")
                || upperCaseValue.contains("SECRET=")) {
            int separatorIndex = value.indexOf('=');
            if (separatorIndex >= 0) {
                return value.substring(0, separatorIndex + 1) + "******";
            }
            return "******";
        }
        return value;
    }
}
