package com.example.demo.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ModelingJobSnapshot(
        String jobId,
        String status,
        String message,
        boolean remoteServiceConfigured,
        boolean localFallbackAllowed,
        String sourceFileName,
        ModelingJobRequest request,
        List<ModelingArtifact> outputs,
        Map<String, Object> metrics,
        List<String> logs,
        Instant createdAt,
        Instant updatedAt
) {

    public ModelingJobSnapshot {
        outputs = List.copyOf(outputs == null ? List.of() : outputs);
        metrics = Map.copyOf(metrics == null ? Map.of() : metrics);
        logs = List.copyOf(logs == null ? List.of() : logs);
    }
}
