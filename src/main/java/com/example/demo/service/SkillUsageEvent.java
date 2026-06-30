package com.example.demo.service;

public record SkillUsageEvent(
        String eventId,
        String skillId,
        String skillName,
        String status,
        String source,
        long durationMs,
        String note,
        String occurredAt
) {
}
