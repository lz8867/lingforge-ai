package com.example.demo.service;

public record SkillUsageSummary(
        String skillId,
        String skillName,
        String description,
        String sourcePath,
        String usageStatus,
        int totalCount,
        int successCount,
        int failureCount,
        int cancelledCount,
        double successRate,
        long averageDurationMs,
        String lastUsedAt
) {
}
