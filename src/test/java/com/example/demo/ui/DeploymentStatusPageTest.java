package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentStatusPageTest {

    @Test
    void deployWorkbenchShouldMergeDeploymentResultStatus() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/index.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));
        String controller = Files.readString(Path.of("src/main/java/com/example/demo/controller/DeployController.java"));

        assertTrue(page.contains("deploy-detail-tabs"));
        assertTrue(page.contains("deploy-result-panel"));
        assertTrue(page.contains("data-deploy-panel=\"result\""));
        assertTrue(page.contains("data-deploy-panel=\"containers\""));
        assertTrue(page.contains("data-deploy-panel=\"log\""));
        assertTrue(page.contains("data-deploy-panel=\"models\""));
        assertTrue(page.contains("id=\"deploy-detail-state\""));
        assertTrue(page.contains("id=\"deploy-container-list\""));
        assertTrue(page.contains("id=\"deploy-log-tail\""));
        assertTrue(page.contains("id=\"deploy-model-services\""));
        assertTrue(page.contains("openDeployPanel('result'"));
        assertTrue(page.contains("renderDeployDetail"));
        assertTrue(page.contains("renderDeployContainers"));
        assertTrue(page.contains("renderDeployModelServices"));
        assertTrue(page.contains("resolveDeployActionBaseUrl"));
        assertTrue(page.contains("window.location.port === '8081'"));
        assertTrue(page.contains("fetchDeployAction('/api/deploy/deploy'"));
        assertTrue(page.contains("宿主机部署器"));
        assertTrue(page.contains("fetch('/api/deploy/status'"));
        assertFalse(page.contains("href=\"/cicd-deploy.html\""));

        assertTrue(stylesheet.contains(".deploy-detail-tabs"));
        assertTrue(stylesheet.contains(".deploy-result-grid"));
        assertTrue(stylesheet.contains(".deploy-container-list"));
        assertTrue(stylesheet.contains(".deploy-model-service-list"));

        assertTrue(controller.contains("@CrossOrigin"));
        assertTrue(controller.contains("http://localhost:8081"));
        assertTrue(controller.contains("http://127.0.0.1:8081"));
    }

    @Test
    void cicdDeployPageShouldRedirectToMergedDeployWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/cicd-deploy.html"));

        assertTrue(page.contains("window.location.replace('/index.html#deploy-result')"));
        assertTrue(page.contains("部署工作台"));
        assertTrue(page.contains("/index.html#deploy-result"));
        assertFalse(page.contains("loadDeployStatus"));
        assertFalse(page.contains("id=\"container-list\""));
    }

    @Test
    void serviceDashboardShouldRenderRealtimeRuntimeScreen() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/service-dashboard.html"));

        assertTrue(page.contains("服务运行大屏"));
        assertTrue(page.contains("refreshRuntimeDashboard"));
        assertTrue(page.contains("fetch('/api/deploy/status'"));
        assertTrue(page.contains("setInterval(refreshRuntimeDashboard"));
        assertTrue(page.contains("服务运行状态"));
        assertTrue(page.contains("CPU 负载"));
        assertTrue(page.contains("线程数"));
        assertTrue(page.contains("GC 次数"));
        assertTrue(page.contains("磁盘可用"));
        assertTrue(page.contains("健康延迟"));
        assertTrue(page.contains("CPU / 线程 / 内存 / GC / 磁盘 / 延迟"));
        assertFalse(page.contains("CPU 负载、线程数、堆内存、GC 次数、磁盘可用和健康延迟"));
        assertTrue(page.contains("renderServiceRuntimeMetrics"));
        assertTrue(page.contains("模型服务"));
        assertTrue(page.contains("runtime-model-strip"));
        assertTrue(page.contains("runtime-model-chip-row"));
        assertTrue(page.contains("renderModelServices"));
        assertTrue(page.contains("screen-model-services"));
        assertTrue(page.contains("Ollama 本地模型"));
        assertTrue(page.contains("Stable Diffusion / Forge"));
        assertTrue(page.contains("Wan2.1 视频桥"));
        assertFalse(page.contains("运行摘要"));
        assertFalse(page.contains("screen-summary"));
        assertFalse(page.contains("renderSummary"));
        assertFalse(page.contains("runtime-bottom-grid"));
        assertFalse(page.contains("screen-containers"));
        assertFalse(page.contains("screen-deploy-log"));
        assertFalse(page.contains("runtime-head-panel"));
        assertFalse(page.contains("runtime-status-cards"));
        assertFalse(page.contains("screen-deploy-state"));
        assertFalse(page.contains("screen-service-state"));
        assertFalse(page.contains("screen-uptime"));
        assertFalse(page.contains("screen-memory"));
    }

    @Test
    void serviceDashboardShouldIncludeAnimatedCanvasVisualization() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/service-dashboard.html"));

        assertTrue(page.contains("<canvas id=\"runtime-visual-canvas\""));
        assertTrue(page.contains("<canvas id=\"runtime-metrics-canvas\""));
        assertTrue(page.contains("startRuntimeCanvas"));
        assertTrue(page.contains("startRuntimeMetricsCanvas"));
        assertTrue(page.contains("drawRuntimeVisualization"));
        assertTrue(page.contains("drawRuntimeMetricsCanvas"));
        assertTrue(page.contains("requestAnimationFrame"));
        assertTrue(page.contains("runtimeVisualState"));
        assertTrue(page.contains("runtimeMetricCanvasState"));
        assertTrue(page.contains("renderRuntimeTopology"));
        assertTrue(page.contains("renderModelServiceVisualBadges"));
        assertTrue(page.contains("modelServiceNodes"));
        assertTrue(page.contains("visual-model-summary-badge"));
        assertFalse(page.contains("visual-docker-badge"));
        assertTrue(page.contains("renderMetricGauge"));
        assertTrue(page.contains("resolveMetricBoardColumns"));
        assertTrue(page.contains("renderCompactMetricGauge"));
        assertTrue(page.contains("height < 210"));
        assertTrue(page.contains("drawMetricText"));
        assertTrue(page.contains("measureText"));
        assertTrue(page.contains("valueColumnWidth"));
        assertTrue(page.contains("body[data-app-page=\"service-dashboard\"] .runtime-visual-stage"));
    }

    @Test
    void serviceDashboardShouldRenderLayeredDigitalTwinFlowScene() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/service-dashboard.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(page.contains("runtime-twin-facility"));
        assertTrue(page.contains("runtime-twin-layer-legend"));
        assertTrue(page.contains("数字孪生脉冲"));
        assertTrue(page.contains("接入 → 内核 → 模型 → 回流"));
        assertTrue(page.contains("接入"));
        assertTrue(page.contains("内核"));
        assertTrue(page.contains("模型"));
        assertTrue(page.contains("回流"));
        assertFalse(page.contains("按上游接入、数字孪生内核、模型执行、JVM 与容器回流分层呈现服务流向"));

        assertTrue(page.contains("renderRuntimeDigitalTwinScene"));
        assertTrue(page.contains("drawTwinBasePlate"));
        assertTrue(page.contains("drawTwinCoreFacility"));
        assertTrue(page.contains("drawTwinFlowLane"));
        assertTrue(page.contains("drawTwinMiniServer"));
        assertTrue(page.contains("drawTwinLayerLabel"));

        assertTrue(stylesheet.contains(".runtime-twin-layer-legend"));
        assertTrue(stylesheet.contains(".runtime-twin-facility"));
    }

    @Test
    void serviceDashboardTwinSceneShouldUseCrispDomLabelsAndStrongerLayering() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/service-dashboard.html"));

        assertTrue(page.contains("id=\"runtime-node-label-layer\""));
        assertTrue(page.contains("runtime-node-label"));
        assertTrue(page.contains("syncRuntimeNodeLabels"));
        assertTrue(page.contains("renderRuntimeNodeLabels"));
        assertTrue(page.contains("drawTwinLayerDecks"));
        assertTrue(page.contains("resizeRuntimeCanvas(canvas, 3"));
        assertTrue(page.contains("translate3d"));
        assertTrue(page.contains("data-layer"));
        assertFalse(page.contains("drawTwinTextBlock(context, node.label"));
    }

    @Test
    void serviceDashboardTwinSceneShouldStartFailedModelServicesFromNodeLabels() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/service-dashboard.html"));

        assertTrue(page.contains("runtime-node-start-button"));
        assertTrue(page.contains("data-model-service-start"));
        assertTrue(page.contains("handleModelServiceStartClick"));
        assertTrue(page.contains("fetch('/api/deploy/model-service/start'"));
        assertTrue(page.contains("serviceId"));
        assertTrue(page.contains("启动"));
        assertTrue(page.contains("启动中"));
        assertFalse(page.contains("aria-hidden=\"true\"></div>"));
    }

    @Test
    void serviceDashboardShouldPreferSingleScreenCanvasWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/service-dashboard.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(page.contains("runtime-dashboard-workbench"));
        assertTrue(page.contains("runtime-metrics-canvas-wrap"));
        assertTrue(page.contains("runtime-model-strip"));
        assertTrue(page.contains("aria-label=\"运行指标 Canvas\""));
        assertTrue(page.contains("CPU 负载"));
        assertTrue(page.contains("线程数"));
        assertTrue(page.contains("堆内存"));
        assertTrue(page.contains("GC 次数"));
        assertTrue(page.contains("磁盘可用"));
        assertTrue(page.contains("健康延迟"));

        assertTrue(stylesheet.contains("body[data-app-page=\"service-dashboard\"]"));
        assertTrue(stylesheet.contains(".runtime-dashboard-workbench"));
        assertTrue(stylesheet.contains("height: calc(100vh - var(--app-header-height) - 24px)"));
        assertTrue(stylesheet.contains("overflow: hidden"));
        assertTrue(stylesheet.contains("grid-template-rows: auto minmax(0, 1fr)"));
        assertTrue(stylesheet.contains(".runtime-model-strip"));
        assertTrue(stylesheet.contains(".runtime-model-chip"));
        assertTrue(stylesheet.contains(".runtime-dashboard-main"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(0, 1.62fr) minmax(360px, 0.38fr)"));
        assertTrue(stylesheet.contains(".runtime-metrics-canvas-wrap"));
        assertTrue(stylesheet.contains("#runtime-metrics-canvas"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] .app-sidebar::-webkit-scrollbar"));
        assertTrue(stylesheet.contains("body[data-app-page=\"service-dashboard\"] .runtime-panel-head > span"));
        assertTrue(stylesheet.contains(".runtime-visual-badge.is-warning"));
        assertTrue(stylesheet.contains("body[data-app-page=\"service-dashboard\"] .runtime-canvas-panel {\n        min-height: 220px !important;\n        display: grid !important;"));
        assertFalse(stylesheet.contains("#screen-summary"));
        assertFalse(stylesheet.contains(".runtime-bottom-grid"));
        assertFalse(stylesheet.contains(".runtime-log"));
        assertFalse(stylesheet.contains(".runtime-head-panel"));
        assertFalse(stylesheet.contains(".runtime-status-cards"));
        assertFalse(stylesheet.contains(".runtime-model-card"));
        assertFalse(stylesheet.contains("body[data-app-page=\"service-dashboard\"] .runtime-canvas-panel {\n        display: none !important;"));
    }

    @Test
    void runtimeDashboardShouldBeReachableFromOpsNavigation() throws Exception {
        String home = Files.readString(Path.of("src/main/resources/static/index.html"));
        String layout = Files.readString(Path.of("src/main/resources/static/js/app-layout.js"));

        assertTrue(home.contains("/service-dashboard.html"));
        assertTrue(home.contains("运行大屏"));
        assertTrue(layout.contains("运行大屏"));
        assertTrue(layout.contains("/service-dashboard.html"));
    }
}
