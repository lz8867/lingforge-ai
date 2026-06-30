package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalTwinPageTest {

    @Test
    void digitalTwinPageShouldRenderCapabilityGovernanceWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/digital-twin.html"));

        assertTrue(page.contains("AI 能力治理中心"));
        assertTrue(page.contains("twin-workbench"));
        assertTrue(page.contains("twin-status-strip"));
        assertTrue(page.contains("业务影响面"));
        assertTrue(page.contains("待处置队列"));
        assertTrue(page.contains("调用链路"));
        assertTrue(page.contains("处置建议"));
        assertTrue(page.contains("影响业务"));
        assertTrue(page.contains("下一步动作"));
        assertTrue(page.contains("<canvas id=\"twin-topology-canvas\""));
        assertTrue(page.contains("renderTwinTopology"));
        assertTrue(page.contains("renderTwinImpactBoard"));
        assertTrue(page.contains("renderTwinActionQueue"));
        assertTrue(page.contains("selectTwinNode"));
        assertTrue(page.contains("renderTwinDetail"));
        assertTrue(page.contains("fetch('/api/digital-twin/overview'"));
        assertTrue(page.contains("setInterval(loadDigitalTwinOverview"));
        assertFalse(page.contains("数字孪生平台介绍"));
        assertFalse(page.contains("<span class=\"twin-panel-kicker\">Capabilities</span>"));
        assertFalse(page.contains("<span class=\"twin-panel-kicker\">Topology</span>"));
        assertFalse(page.contains("<span class=\"twin-panel-kicker\">Inspector</span>"));
    }

    @Test
    void digitalTwinPageShouldKeepGovernanceLayoutResponsive() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/digital-twin.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(page.contains("grid-template-columns: minmax(300px, 0.82fr) minmax(0, 1.28fr) minmax(340px, 0.9fr)"));
        assertTrue(page.contains("height: calc(100vh - var(--app-header-height) - 24px)"));
        assertTrue(page.contains("overflow: hidden"));
        assertTrue(page.contains("@media (max-width: 1180px)"));
        assertTrue(page.contains("grid-template-columns: 1fr"));
        assertTrue(page.contains("max-height: none"));
        assertTrue(page.contains("aria-label=\"AI 能力调用链路\""));
        assertTrue(page.contains("aria-label=\"刷新能力治理概览\""));
        assertTrue(page.contains("twin-action-queue"));
        assertTrue(page.contains("twin-impact-board"));
        assertTrue(page.contains("twin-business-tags"));
        assertTrue(stylesheet.contains("body[data-app-page=\"digital-twin\"] .twin-status-strip"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(0, 1fr) !important;"));
        assertFalse(stylesheet.contains("body[data-app-page=\"digital-twin\"] .twin-main-grid > aside:last-child"));
    }

    @Test
    void digitalTwinShouldBeReachableFromHomeAndGlobalNavigation() throws Exception {
        String home = Files.readString(Path.of("src/main/resources/static/index.html"));
        String layout = Files.readString(Path.of("src/main/resources/static/js/app-layout.js"));

        assertTrue(home.contains("/digital-twin.html"));
        assertTrue(home.contains("AI 能力治理中心"));
        assertTrue(layout.contains("digital-twin"));
        assertTrue(layout.contains("/digital-twin.html"));
        assertTrue(layout.contains("能力治理"));
        assertTrue(layout.contains("定位 AI 能力故障、业务影响和下一步处置动作。"));
    }

    @Test
    void staticPagesShouldDisableBrowserCacheForFreshGovernanceDeploys() throws Exception {
        String config = Files.readString(Path.of("src/main/java/com/example/demo/config/StaticResourceCacheConfig.java"));

        assertTrue(config.contains("addResourceHandler(\"/**\")"));
        assertTrue(config.contains("classpath:/static/"));
        assertTrue(config.contains("CacheControl.noStore()"));
    }
}
