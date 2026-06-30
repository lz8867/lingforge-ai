package com.example.demo.controller;

import com.example.demo.service.SkillMonitorOverview;
import com.example.demo.service.SkillMonitorService;
import com.example.demo.service.SkillUsageEvent;
import com.example.demo.service.SkillUsageReportRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMonitorControllerTest {

    @TempDir
    Path workspace;

    @Test
    void overviewEndpointShouldReturnSkillUsageOverview() throws Exception {
        SkillMonitorController controller = new SkillMonitorController(serviceWithOneSkill());

        SkillMonitorOverview overview = controller.overview();

        assertEquals(1, overview.totalSkills());
        assertTrue(overview.skills().stream().anyMatch(skill -> "playwright".equals(skill.skillId())));
    }

    @Test
    void usageEndpointShouldRecordSkillUsageEvent() throws Exception {
        SkillMonitorController controller = new SkillMonitorController(serviceWithOneSkill());

        SkillUsageEvent event = controller.reportUsage(new SkillUsageReportRequest(
                "playwright",
                "",
                "SUCCESS",
                "codex",
                450,
                "验证页面"
        ));

        assertEquals("playwright", event.skillId());
        assertEquals("SUCCESS", event.status());
        assertEquals(1, controller.overview().totalEvents());
    }

    @Test
    void controllerShouldBeCreatedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SkillMonitorService.class, () -> new SkillMonitorService(List.of(workspace), fixedClock()));
            context.register(SkillMonitorController.class);

            context.refresh();

            assertTrue(context.getBean(SkillMonitorController.class).overview().checkedAt() != null);
        }
    }

    private SkillMonitorService serviceWithOneSkill() throws Exception {
        Path skillFile = workspace.resolve("playwright/SKILL.md");
        Files.createDirectories(skillFile.getParent());
        Files.writeString(skillFile, """
                ---
                name: playwright
                description: "浏览器自动化 skill"
                ---
                """);
        return new SkillMonitorService(List.of(workspace), fixedClock());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-12T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }
}
