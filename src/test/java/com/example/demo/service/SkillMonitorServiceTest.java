package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMonitorServiceTest {

    @TempDir
    Path workspace;

    @Test
    void overviewShouldDiscoverLocalSkillMarkdownFilesWithoutUsageEvents() throws Exception {
        Path codexRoot = workspace.resolve("codex-skills");
        Path agentsRoot = workspace.resolve("agent-skills");
        writeSkill(codexRoot.resolve("open-code-review/SKILL.md"), "open-code-review", "代码评审 skill");
        writeSkill(agentsRoot.resolve("playwright/SKILL.md"), "playwright", "浏览器自动化 skill");

        SkillMonitorService service = new SkillMonitorService(
                List.of(codexRoot, agentsRoot),
                fixedClock()
        );

        SkillMonitorOverview overview = service.overview();

        assertEquals(2, overview.totalSkills());
        assertEquals(0, overview.usedSkills());
        assertEquals(0, overview.totalEvents());
        assertEquals(0, overview.successRate());
        assertTrue(overview.skills().stream().anyMatch(skill -> "open-code-review".equals(skill.skillId())));
        assertTrue(overview.skills().stream().allMatch(skill -> "UNUSED".equals(skill.usageStatus())));
        assertTrue(overview.recommendations().stream().anyMatch(item -> item.contains("上报")));
    }

    @Test
    void overviewShouldIgnoreBundledSystemAndTrashSkillFiles() throws Exception {
        Path skillRoot = workspace.resolve("skills");
        writeSkill(skillRoot.resolve("open-code-review/SKILL.md"), "open-code-review", "代码评审 skill");
        writeSkill(skillRoot.resolve(".system/openai-docs/SKILL.md"), "openai-docs", "官方文档 skill");
        writeSkill(skillRoot.resolve(".trash/old-local-skill/SKILL.md"), "old-local-skill", "已归档 skill");

        SkillMonitorService service = new SkillMonitorService(List.of(skillRoot), fixedClock());

        SkillMonitorOverview overview = service.overview();

        assertEquals(1, overview.totalSkills());
        assertTrue(overview.skills().stream().anyMatch(skill -> "open-code-review".equals(skill.skillId())));
        assertFalse(overview.skills().stream().anyMatch(skill -> "openai-docs".equals(skill.skillId())));
        assertFalse(overview.skills().stream().anyMatch(skill -> "old-local-skill".equals(skill.skillId())));
    }

    @Test
    void recordUsageShouldUpdateSummaryRecentEventsAndSuccessRate() throws Exception {
        Path skillRoot = workspace.resolve("skills");
        writeSkill(skillRoot.resolve("open-code-review/SKILL.md"), "open-code-review", "代码评审 skill");
        writeSkill(skillRoot.resolve("playwright/SKILL.md"), "playwright", "浏览器自动化 skill");
        SkillMonitorService service = new SkillMonitorService(List.of(skillRoot), fixedClock());

        service.recordUsage(new SkillUsageReportRequest(
                "open-code-review",
                "",
                "SUCCESS",
                "codex",
                1200,
                "完成 MR 评审"
        ));
        service.recordUsage(new SkillUsageReportRequest(
                "open-code-review",
                "",
                "FAILURE",
                "codex",
                600,
                "规则读取失败"
        ));
        service.recordUsage(new SkillUsageReportRequest(
                "playwright",
                "",
                "SUCCESS",
                "codex",
                3000,
                "完成页面验证"
        ));

        SkillMonitorOverview overview = service.overview();

        assertEquals(2, overview.usedSkills());
        assertEquals(3, overview.totalEvents());
        assertEquals(66.7, overview.successRate());
        assertEquals(3, overview.recentEvents().size());
        assertEquals("playwright", overview.recentEvents().get(0).skillId());
        assertTrue(overview.skills().stream().anyMatch(skill ->
                "open-code-review".equals(skill.skillId())
                        && skill.totalCount() == 2
                        && skill.failureCount() == 1
                        && "ATTENTION".equals(skill.usageStatus())));
    }

    @Test
    void recordUsageShouldKeepUnknownSkillAsAdHocMonitoredObject() {
        SkillMonitorService service = new SkillMonitorService(List.of(workspace.resolve("missing")), fixedClock());

        SkillUsageEvent event = service.recordUsage(new SkillUsageReportRequest(
                "custom-local-skill",
                "自定义本地 skill",
                "SUCCESS",
                "manual",
                80,
                "手动登记"
        ));

        SkillMonitorOverview overview = service.overview();

        assertFalse(event.eventId().isBlank());
        assertEquals(1, overview.totalSkills());
        assertEquals(1, overview.totalEvents());
        assertTrue(overview.skills().stream().anyMatch(skill ->
                "custom-local-skill".equals(skill.skillId())
                        && "自定义本地 skill".equals(skill.skillName())
                        && "ACTIVE".equals(skill.usageStatus())));
    }

    private static void writeSkill(Path path, String name, String description) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                ---
                name: %s
                description: "%s"
                ---

                # %s
                """.formatted(name, description, name));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-12T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }
}
