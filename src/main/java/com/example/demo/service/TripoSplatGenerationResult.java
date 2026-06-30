package com.example.demo.service;

import java.util.List;
import java.util.Map;

public record TripoSplatGenerationResult(
        boolean success,
        boolean unavailable,
        String message,
        List<ModelingArtifact> outputs,
        Map<String, Object> metrics
) {

    public TripoSplatGenerationResult {
        message = message == null || message.isBlank() ? "TripoSplat 任务无返回信息" : message;
        outputs = List.copyOf(outputs == null ? List.of() : outputs);
        metrics = Map.copyOf(metrics == null ? Map.of() : metrics);
    }

    public static TripoSplatGenerationResult success(String message, List<ModelingArtifact> outputs, Map<String, Object> metrics) {
        return new TripoSplatGenerationResult(true, false, message, outputs, metrics);
    }

    public static TripoSplatGenerationResult failure(String message) {
        return new TripoSplatGenerationResult(false, false, message, List.of(), Map.of());
    }

    public static TripoSplatGenerationResult unavailable(String message) {
        return new TripoSplatGenerationResult(false, true, message, List.of(), Map.of());
    }
}
