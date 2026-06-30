package com.example.demo.service;

import java.util.List;

public record SkillMonitorOverview(
        String checkedAt,
        int totalSkills,
        int usedSkills,
        int totalEvents,
        double successRate,
        int attentionSkills,
        List<SkillUsageSummary> skills,
        List<SkillUsageEvent> recentEvents,
        List<String> recommendations
) {
}
