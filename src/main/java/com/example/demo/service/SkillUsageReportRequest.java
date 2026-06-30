package com.example.demo.service;

public record SkillUsageReportRequest(
        String skillId,
        String skillName,
        String status,
        String source,
        long durationMs,
        String note
) {
}
