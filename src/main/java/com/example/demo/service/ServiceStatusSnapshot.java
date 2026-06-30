package com.example.demo.service;

import java.util.List;

public record ServiceStatusSnapshot(
        String checkedAt,
        String deploymentState,
        String deploymentMessage,
        boolean deploymentRunning,
        String serviceState,
        boolean serviceReachable,
        String serviceUrl,
        boolean dockerAvailable,
        String dockerError,
        List<ContainerStatus> containers,
        long uptimeSeconds,
        long memoryUsedMb,
        long memoryMaxMb,
        double memoryUsagePercent,
        int availableProcessors,
        ServiceRuntimeMetrics serviceMetrics,
        List<ModelServiceStatus> modelServices,
        String deployLogTail
) {

    public record ContainerStatus(String name, String status, String ports, boolean expected) {
    }

    public record ServiceRuntimeMetrics(
            double processCpuLoadPercent,
            double systemCpuLoadPercent,
            int threadCount,
            int daemonThreadCount,
            int peakThreadCount,
            long heapUsedMb,
            long heapMaxMb,
            double heapUsagePercent,
            long nonHeapUsedMb,
            int loadedClassCount,
            long gcCount,
            long gcTimeMs,
            long diskUsableGb,
        long diskTotalGb,
        double diskUsagePercent,
        long healthLatencyMs) {
    }

    public record ModelServiceStatus(
            String id,
            String name,
            String provider,
            String capability,
            String endpoint,
            int port,
            String state,
            boolean reachable,
            long latencyMs,
            String modelSummary,
            String message,
            String actionSuggestion) {
    }
}
