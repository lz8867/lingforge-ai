package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMonitorPageTest {

    @Test
    void skillMonitorPageShouldRenderUsageWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/skill-monitor.html"));

        assertTrue(page.contains("Skill 使用监控"));
        assertTrue(page.contains("本地自定义 Skill"));
        assertTrue(page.contains("skill-monitor-workbench"));
        assertTrue(page.contains("skill-status-strip"));
        assertTrue(page.contains("skill-inventory"));
        assertTrue(page.contains("skill-usage-canvas"));
        assertTrue(page.contains("最近使用事件"));
        assertTrue(page.contains("手动上报"));
        assertTrue(page.contains("fetch('/api/skill-monitor/overview'"));
        assertTrue(page.contains("fetch('/api/skill-monitor/usage'"));
        assertTrue(page.contains("setInterval(loadSkillOverview"));
        assertTrue(page.contains("renderSkillUsageCanvas"));
        assertFalse(page.contains("Skill 使用监控平台介绍"));
    }

    @Test
    void skillMonitorPageShouldUseResponsiveSingleScreenLayout() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/skill-monitor.html"));

        assertTrue(page.contains("grid-template-columns: minmax(300px, 0.8fr) minmax(0, 1.2fr) minmax(310px, 0.72fr)"));
        assertTrue(page.contains("height: calc(100vh - var(--app-header-height) - 24px)"));
        assertTrue(page.contains("@media (max-width: 1180px)"));
        assertTrue(page.contains("grid-template-columns: 1fr"));
        assertTrue(page.contains("overflow: hidden"));
        assertTrue(page.contains("aria-label=\"Skill 使用趋势\""));
        assertTrue(page.contains("aria-label=\"上报 Skill 使用事件\""));
    }

    @Test
    void skillMonitorShouldBeReachableFromHomeAndGlobalNavigation() throws Exception {
        String home = Files.readString(Path.of("src/main/resources/static/index.html"));
        String layout = Files.readString(Path.of("src/main/resources/static/js/app-layout.js"));

        assertTrue(home.contains("/skill-monitor.html"));
        assertTrue(home.contains("Skill 使用监控"));
        assertTrue(layout.contains("skill-monitor"));
        assertTrue(layout.contains("/skill-monitor.html"));
        assertTrue(layout.contains("Skill 监控"));
        assertTrue(layout.contains("跟踪本地自定义 skill 清单、使用事件、成功率和接入状态。"));
    }
}
