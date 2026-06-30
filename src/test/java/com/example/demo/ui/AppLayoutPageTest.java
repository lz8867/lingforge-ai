package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppLayoutPageTest {

    private static final Path STATIC_DIR = Path.of("src/main/resources/static");
    private static final String FLOWBITE_CSS_LINK = "<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/flowbite@2.5.2/dist/flowbite.min.css\">";
    private static final String FLOWBITE_SCRIPT = "<script src=\"https://cdn.jsdelivr.net/npm/flowbite@2.5.2/dist/flowbite.min.js\" defer></script>";
    private static final String LAYOUT_CSS_LINK = "<link rel=\"stylesheet\" href=\"/css/app-layout.css?v=";
    private static final String LAYOUT_SCRIPT = "<script src=\"/js/app-layout.js?v=";

    @Test
    void allStaticPagesShouldUseSharedAppLayoutAssets() throws Exception {
        List<Path> pages;
        try (Stream<Path> files = Files.list(STATIC_DIR)) {
            pages = files
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .sorted()
                    .toList();
        }

        assertFalse(pages.isEmpty());
        for (Path pagePath : pages) {
            if (pagePath.getFileName().toString().equals("triposplat-viewer.html")) {
                continue;
            }
            String page = Files.readString(pagePath);
            assertTrue(page.contains(FLOWBITE_CSS_LINK), pagePath + " should load the Flowbite component stylesheet");
            assertTrue(page.contains(FLOWBITE_SCRIPT), pagePath + " should load the Flowbite interaction script");
            assertTrue(page.contains(LAYOUT_CSS_LINK), pagePath + " should load the shared app layout stylesheet");
            assertTrue(page.contains(LAYOUT_SCRIPT), pagePath + " should load the shared app layout renderer");
        }
    }

    @Test
    void appLayoutShouldDefineFunctionalTaxonomyAndViewportShell() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));
        String renderer = Files.readString(STATIC_DIR.resolve("js/app-layout.js"));

        assertTrue(stylesheet.contains("--app-header-height"));
        assertTrue(stylesheet.contains("--app-sidebar-width"));
        assertTrue(stylesheet.contains("--app-shell-width"));
        assertTrue(stylesheet.contains("html[data-app-layout=\"active\"]"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"]"));
        assertTrue(stylesheet.contains("overflow: hidden !important"));
        assertTrue(stylesheet.contains("height: calc(100vh - var(--app-header-height)) !important"));
        assertTrue(stylesheet.contains("min-height: calc(100vh - var(--app-header-height))"));
        assertTrue(stylesheet.contains(".app-topbar"));
        assertTrue(stylesheet.contains(".app-sidebar"));
        assertTrue(stylesheet.contains(".app-sidebar-group"));
        assertTrue(stylesheet.contains(".app-sidebar-backdrop"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] > header.app-topbar"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] .app-sidebar"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] .app-sidebar-backdrop"));
        assertTrue(stylesheet.contains(".app-context"));
        assertTrue(stylesheet.contains(".app-page-layout"));
        assertTrue(stylesheet.contains("body.app-sidebar-open"));

        assertTrue(renderer.contains("AI 交互"));
        assertTrue(renderer.contains("Prompt 与知识"));
        assertTrue(renderer.contains("内容生成"));
        assertTrue(renderer.contains("治理工具"));
        assertTrue(renderer.contains("运维管理"));
        assertTrue(renderer.contains("accountActions"));
        assertTrue(renderer.contains("app-topbar-account"));
        assertTrue(renderer.contains("账号操作"));
        assertTrue(renderer.contains("categoryLabel: \"账号\""));
        assertTrue(renderer.contains("data-drawer-target"));
        assertTrue(renderer.contains("app-sidebar"));
        assertTrue(renderer.contains("data-flowbite-component"));
        assertTrue(renderer.contains("sidebar"));
        assertTrue(renderer.contains("打开功能导航"));
        assertTrue(renderer.contains("data-app-layout"));
        assertTrue(renderer.contains("data-app-category"));
        assertTrue(renderer.contains("hashchange"));
        assertTrue(renderer.contains("switchTab"));
        assertTrue(renderer.contains("initFlowbite"));
        assertTrue(renderer.contains("header.classList.add(\"app-header\")"));
        assertFalse(renderer.contains("header.className = \"app-header\";"));
    }

    @Test
    void loginShouldBeAccountActionInsteadOfAiToolEntry() throws Exception {
        String renderer = Files.readString(STATIC_DIR.resolve("js/app-layout.js"));
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        assertFalse(renderer.contains("{ id: \"login\", label: \"登录\", href: \"/login.html\", page: \"login\" }"));
        assertTrue(renderer.contains("const accountActions = ["));
        assertTrue(renderer.contains("renderAccountActions(current)"));
        assertTrue(renderer.contains("app-topbar-account"));
        assertTrue(renderer.contains("workbenchSwitcher(current)"));
        assertTrue(stylesheet.contains(".app-topbar-right"));
        assertTrue(stylesheet.contains(".app-topbar-account"));
    }

    @Test
    void homeFeatureCenterShouldUseGroupedCompactLayout() throws Exception {
        String home = Files.readString(STATIC_DIR.resolve("index.html"));
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));
        String renderer = Files.readString(STATIC_DIR.resolve("js/app-layout.js"));

        assertTrue(home.contains("<!-- 首页工作台 -->"));
        assertTrue(home.contains("<div id=\"ai-features\" class=\"max-w-6xl mx-auto\">"));
        assertTrue(home.contains("<div id=\"login-container\" class=\"hidden max-w-4xl mx-auto\">"));
        assertFalse(home.contains("<div id=\"ai-features\" class=\"hidden"));
        assertTrue(renderer.contains("home: \"按功能域查看 AI 对话、Prompt、内容生成、治理和运维工具入口。\""));
        assertTrue(home.contains("class=\"app-capability-map\""));
        assertTrue(home.contains("class=\"app-capability-flow\""));
        assertFalse(home.contains("class=\"app-capability-index\""));
        assertTrue(home.contains("class=\"app-home-ops-brief\""));
        assertTrue(home.contains("id=\"home-knowledge-total\""));
        assertTrue(home.contains("id=\"home-knowledge-published\""));
        assertTrue(home.contains("id=\"home-knowledge-topics\""));
        assertTrue(home.contains("id=\"home-evaluation-success-rate\""));
        assertTrue(home.contains("id=\"home-evaluation-run-list\""));
        assertTrue(home.contains("id=\"home-action-list\""));
        assertTrue(home.contains("app-runtime-preview"));
        assertTrue(home.contains("app-runtime-hall"));
        assertTrue(home.contains("app-runtime-preview-topology"));
        assertTrue(home.contains("app-runtime-hall-topology"));
        assertTrue(home.contains("app-runtime-preview-metrics"));
        assertTrue(home.contains("app-runtime-service-metrics"));
        assertTrue(home.contains("LIVE TOPOLOGY CANVAS"));
        assertTrue(home.contains("服务拓扑与运行脉冲"));
        assertTrue(home.contains("服务运行状态"));
        assertTrue(home.contains("id=\"home-runtime-state\""));
        assertTrue(home.contains("id=\"home-runtime-gc\""));
        assertTrue(home.contains("id=\"home-runtime-disk\""));
        assertTrue(home.contains("id=\"home-runtime-loaded\""));
        assertTrue(home.contains("refreshHomeRuntimePreview"));
        assertTrue(home.contains("fetch('/api/deploy/status'"));
        assertTrue(home.contains("refreshHomeOpsBrief"));
        assertTrue(home.contains("fetch('/api/knowledge/statistics'"));
        assertTrue(home.contains("fetch('/api/model-evaluation/summary'"));
        assertTrue(home.contains("fetch('/api/model-evaluation/runs?limit=3'"));
        assertTrue(home.contains("aria-label=\"输入与上下文\""));
        assertTrue(home.contains("aria-label=\"Prompt 与检索\""));
        assertTrue(home.contains("aria-label=\"多模态生成\""));
        assertTrue(home.contains("aria-label=\"治理与质量\""));
        assertTrue(home.contains("aria-label=\"运行与交付\""));
        assertFalse(home.contains("lg:col-span-4"), "home entries should not create orphan full-row cards");

        assertTrue(stylesheet.contains("--app-shell-width"));
        assertTrue(stylesheet.contains("--app-shell-wide"));
        assertTrue(stylesheet.contains("body[data-app-section=\"home\"] #ai-features"));
        assertTrue(stylesheet.contains(".app-runtime-preview"));
        assertTrue(stylesheet.contains(".app-runtime-hall"));
        assertTrue(stylesheet.contains(".app-runtime-preview-topology"));
        assertTrue(stylesheet.contains(".app-runtime-hall-topology"));
        assertTrue(stylesheet.contains(".app-runtime-preview-metrics"));
        assertTrue(stylesheet.contains(".app-runtime-service-metrics"));
        assertTrue(stylesheet.contains(".app-home-ops-brief"));
        assertTrue(stylesheet.contains(".app-home-ops-grid"));
        assertTrue(stylesheet.contains(".app-home-knowledge-list"));
        assertTrue(stylesheet.contains(".app-home-run-list"));
        assertTrue(stylesheet.contains(".app-home-action-list"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"][data-app-page=\"index\"] main.app-workbench > .app-content-shell"));
        assertTrue(stylesheet.contains("scrollbar-gutter: stable"));
        assertTrue(stylesheet.contains("grid-template-rows: auto auto auto auto"));
        assertTrue(stylesheet.contains("min-height: 318px"));
        assertFalse(stylesheet.contains("height: min(720px, var(--app-compact-panel));"));
        assertFalse(stylesheet.contains("grid-template-rows: auto minmax(0, 1fr) auto auto auto"));
        assertFalse(stylesheet.contains(".app-capability-index"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(0, 1.62fr) minmax(360px, 0.38fr)"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] > footer"));
        assertTrue(stylesheet.contains("--app-compact-panel"));
    }

    @Test
    void workflowPagesShouldNotClipFunctionalContent() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] main.app-shell"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .tool-grid"));
        assertTrue(stylesheet.contains("overflow: visible"));
        assertFalse(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] main.app-shell {\n    height: calc(100vh - var(--app-header-height));\n    overflow: hidden;"));
        assertFalse(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .tool-grid {\n    grid-template-columns: 270px minmax(0, 1fr) 390px;\n    height: calc(100vh - var(--app-header-height) - 132px);"));
    }

    @Test
    void allPagesShouldReceiveUnifiedWorkbenchContentShell() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));
        String renderer = Files.readString(STATIC_DIR.resolve("js/app-layout.js"));

        assertTrue(renderer.contains("pageSummaries"));
        assertTrue(renderer.contains("decorateWorkbench"));
        assertTrue(renderer.contains("data-app-workbench"));
        assertTrue(renderer.contains("app-workbench-hero"));
        assertTrue(renderer.contains("app-workbench-switcher"));
        assertTrue(renderer.contains("app-content-shell"));
        assertTrue(renderer.contains("app-workbench-panel"));
        assertTrue(renderer.contains("app-legacy-heading"));

        assertTrue(stylesheet.contains(".app-workbench"));
        assertTrue(stylesheet.contains(".app-workbench-hero"));
        assertTrue(stylesheet.contains(".app-workbench-switcher"));
        assertTrue(stylesheet.contains(".app-content-shell"));
        assertTrue(stylesheet.contains(".app-workbench-panel"));
        assertTrue(stylesheet.contains(".app-legacy-heading"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] main.app-workbench"));
    }

    @Test
    void desktopNavigationShouldCollapseByParentCategoryWithoutHidingLabels() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));
        String renderer = Files.readString(STATIC_DIR.resolve("js/app-layout.js"));

        assertTrue(stylesheet.contains("--app-sidebar-width: 244px"));
        assertTrue(stylesheet.contains(".app-sidebar-label"));
        assertTrue(stylesheet.contains("button.app-sidebar-label"));
        assertTrue(stylesheet.contains(".app-sidebar-caret"));
        assertTrue(stylesheet.contains(".app-sidebar-group:not(.is-expanded) .app-sidebar-links"));
        assertTrue(stylesheet.contains("display: none"));
        assertTrue(stylesheet.contains("margin-left: var(--app-sidebar-width) !important"));
        assertTrue(stylesheet.contains("width: calc(100vw - var(--app-sidebar-width)) !important"));
        assertFalse(stylesheet.contains("--app-sidebar-rail-width"));
        assertFalse(stylesheet.contains("app-sidebar-pinned"));
        assertFalse(stylesheet.contains(".app-sidebar:hover"));

        assertTrue(renderer.contains("expandedCategoryIds"));
        assertTrue(renderer.contains("data-app-sidebar-group-toggle"));
        assertTrue(renderer.contains("toggleSidebarGroup"));
        assertTrue(renderer.contains("aria-expanded"));
        assertTrue(renderer.contains("aria-controls"));
        assertTrue(renderer.contains("title=\\\""));
        assertTrue(renderer.contains("aria-label=\\\""));
        assertFalse(renderer.contains("setSidebarPinned"));
        assertFalse(renderer.contains("固定展开功能导航"));
    }

    @Test
    void workbenchChromeShouldStayCompactSoTaskPanelsStartEarlier() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        assertTrue(stylesheet.contains("--app-header-height: 58px"));
        assertTrue(stylesheet.contains("--app-page-gap: 10px"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] .app-context"));
        assertTrue(stylesheet.contains("display: none !important"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] main.app-workbench"));
        assertTrue(stylesheet.contains("gap: 10px"));
        assertTrue(stylesheet.contains(".app-workbench-hero"));
        assertTrue(stylesheet.contains("min-height: 54px"));
        assertTrue(stylesheet.contains("padding: 0 0 8px"));
        assertTrue(stylesheet.contains("-webkit-line-clamp: 1"));
        assertTrue(stylesheet.contains(".app-workbench-chip"));
        assertTrue(stylesheet.contains("min-height: 30px !important"));
        assertTrue(stylesheet.contains("body[data-app-page=\"index\"] #logs-tab .bg-white.rounded-lg.shadow-lg"));
        assertTrue(stylesheet.contains("body[data-app-page=\"index\"] #logs-tab .space-y-6"));
    }

    @Test
    void dockerLogActionsShouldStayAboveScrollableConsole() throws Exception {
        String home = Files.readString(STATIC_DIR.resolve("index.html"));
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        int actionsIndex = home.indexOf("class=\"logs-inline-actions\"");
        int consoleIndex = home.indexOf("id=\"docker-logs\"");

        assertTrue(actionsIndex > -1);
        assertTrue(consoleIndex > -1);
        assertTrue(actionsIndex < consoleIndex);
        assertFalse(home.contains("<!-- 日志操作 -->"));

        assertTrue(stylesheet.contains("body[data-app-page=\"index\"] #logs-tab .logs-panel-shell"));
        assertTrue(stylesheet.contains("body[data-app-page=\"index\"] #logs-tab .logs-inline-actions"));
        assertTrue(stylesheet.contains("body[data-app-page=\"index\"] #docker-logs"));
        assertTrue(stylesheet.contains("body[data-app-page=\"index\"] #logs-tab .bg-white.rounded-lg.shadow-lg {\n    grid-template-rows: minmax(0, 1fr);"));
        assertTrue(stylesheet.contains("height: 100%"));
        assertTrue(stylesheet.contains("overflow: auto"));
    }

    @Test
    void deployToolShouldUseProfessionalOperationsWorkbench() throws Exception {
        String home = Files.readString(STATIC_DIR.resolve("index.html"));
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        assertTrue(home.contains("deploy-workbench"));
        assertTrue(home.contains("deploy-hero"));
        assertTrue(home.contains("deploy-workbench-grid"));
        assertTrue(home.contains("deploy-action-panel"));
        assertTrue(home.contains("deploy-status-panel"));
        assertTrue(home.contains("deploy-action-grid"));
        assertTrue(home.contains("deploy-progress"));
        assertTrue(home.contains("deploy-log-panel"));
        assertTrue(home.contains("deploy-detail-tabs"));
        assertTrue(home.contains("deploy-result-panel"));
        assertTrue(home.contains("deploy-container-list"));
        assertTrue(home.contains("deploy-log-tail"));
        assertTrue(home.contains("deploy-model-services"));
        assertTrue(home.contains("deploy-log-console"));
        assertTrue(home.contains("refreshDeployOverview"));
        assertTrue(home.contains("fetch('/api/deploy/status'"));
        assertFalse(home.contains("href=\"/cicd-deploy.html\""));
        assertFalse(home.contains("<div class=\"text-6xl mb-4\">🚀</div>"));

        assertTrue(stylesheet.contains("body[data-app-page=\"index\"] #deploy-tab .deploy-workbench"));
        assertTrue(stylesheet.contains(".deploy-workbench-grid"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(360px, 0.78fr) minmax(0, 1.22fr)"));
        assertTrue(stylesheet.contains(".deploy-action-grid"));
        assertTrue(stylesheet.contains("grid-template-columns: repeat(2, minmax(0, 1fr))"));
        assertTrue(stylesheet.contains(".deploy-status-metrics"));
        assertTrue(stylesheet.contains(".deploy-detail-tabs"));
        assertTrue(stylesheet.contains(".deploy-result-grid"));
        assertTrue(stylesheet.contains(".deploy-log-console"));

        String renderer = Files.readString(STATIC_DIR.resolve("js/app-layout.js"));
        assertFalse(renderer.contains("label: \"部署结果\""));
        assertFalse(renderer.contains("href: \"/cicd-deploy.html\""));
    }

    @Test
    void memoryManagerShouldUseOperableDashboardLayout() throws Exception {
        String page = Files.readString(STATIC_DIR.resolve("memory-manager.html"));
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        assertTrue(page.contains("memory-workbench"));
        assertTrue(page.contains("memory-compose-panel"));
        assertTrue(page.contains("memory-filter-panel"));
        assertTrue(page.contains("memory-search"));
        assertTrue(page.contains("memory-list-header"));
        assertFalse(page.contains("memory-overview"));
        assertFalse(page.contains("id=\"stats\""));
        assertFalse(page.contains("memory-scale-strip"));
        assertFalse(page.contains("记忆索引控制台"));
        assertFalse(page.contains("服务端筛选"));
        assertFalse(page.contains("分页窗口"));
        assertFalse(page.contains("摘要预览"));
        assertTrue(page.contains("memory-insight-panel"));
        assertTrue(page.contains("memory-distribution-list"));
        assertTrue(page.contains("memory-secondary-compose"));
        assertTrue(page.contains("memory-signal-row"));
        assertTrue(page.contains("memory-result-row"));
        assertTrue(page.contains("memory-snippet"));
        assertTrue(page.contains("memory-delete-button"));
        assertTrue(page.contains("currentSearch"));
        assertTrue(page.contains("toSnippet"));
        assertTrue(page.contains("updateMemoryInsights"));
        assertTrue(page.contains("getTypeShare"));
        assertTrue(page.contains("getTypeSource"));

        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] main .bg-white.rounded-lg.shadow-lg"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(300px, 360px) minmax(0, 1fr)"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-workbench"));
        assertFalse(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-overview"));
        assertFalse(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-scale-strip"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-insight-panel"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-distribution-list"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-secondary-compose"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-signal-row"));
        assertTrue(stylesheet.contains("-webkit-line-clamp: 1"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-compose-panel"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-filter-panel"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-search"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-list-header"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-result-row"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-snippet"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .user-input"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .filter-bar"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-list"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] #memoryContent"));
        assertTrue(stylesheet.contains("height: 72px"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-delete-button"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(0, 1fr)"));
    }

    @Test
    void contentGenerationPagesShouldUseTwoPanelWorkbenchLayout() throws Exception {
        List<String> mediaPages = List.of("text-to-image.html", "image-to-text.html", "text-to-video.html");
        for (String mediaPage : mediaPages) {
            String page = Files.readString(STATIC_DIR.resolve(mediaPage));

            assertTrue(page.contains("media-workbench"), mediaPage + " should use a two-panel generation workbench");
            assertTrue(page.contains("media-control-panel"), mediaPage + " should expose a left control panel");
            assertTrue(page.contains("media-preview-panel"), mediaPage + " should expose a right preview/result panel");
        }

        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));
        assertTrue(stylesheet.contains("body[data-app-category=\"media\"] .media-workbench"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(320px, 420px) minmax(0, 1fr)"));
        assertTrue(stylesheet.contains(".media-control-panel"));
        assertTrue(stylesheet.contains(".media-preview-panel"));
        assertTrue(stylesheet.contains("@media (max-width: 980px)"));
    }

    @Test
    void retiredInternalToolsShouldNotBePublished() throws Exception {
        String home = Files.readString(STATIC_DIR.resolve("index.html"));
        String renderer = Files.readString(STATIC_DIR.resolve("js/app-layout.js"));

        String retiredPage = "master" + "-tools.html";
        String retiredPagePath = "/master" + "-tools.html";
        String retiredPageLink = "master" + "-tools";

        assertFalse(Files.exists(STATIC_DIR.resolve(retiredPage)));
        assertFalse(home.contains("href=\"" + retiredPagePath + "\""));
        assertFalse(home.contains("data-app-link=\"" + retiredPageLink + "\""));
        assertTrue(renderer.contains("id: \"governance\""));
        assertTrue(renderer.contains("label: \"治理工具\""));
        assertFalse(renderer.contains(retiredPagePath));
        assertFalse(renderer.contains("master" + "-retired"));
    }

    @Test
    void appShellShouldUseFluidWideScreenWorkspace() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        assertTrue(stylesheet.contains("--app-shell-width: min(1560px, calc(100vw - 56px))"));
        assertTrue(stylesheet.contains("--app-shell-wide: min(1840px, calc(100vw - 40px))"));
        assertTrue(stylesheet.contains("--app-edge-gutter: clamp(14px, 1.45vw, 28px)"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] *"));
        assertTrue(stylesheet.contains("box-sizing: border-box"));
        assertTrue(stylesheet.contains("padding: 8px var(--app-edge-gutter) 24px !important"));
        assertTrue(stylesheet.contains("margin-left: var(--app-sidebar-width) !important"));
        assertTrue(stylesheet.contains("width: calc(100vw - var(--app-sidebar-width)) !important"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-demo\"] main.container"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-demo\"] .main-content"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(280px, 340px) minmax(0, 1fr)"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] main.app-shell"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(320px, 0.72fr) minmax(420px, 1fr) minmax(360px, 0.82fr)"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"][data-app-page=\"prompt-optimizer\"] main.app-workbench > .prompt-optimizer-command-strip.app-content-shell"));
        assertTrue(stylesheet.contains("body[data-app-page=\"rag-demo\"] main"));
        assertTrue(stylesheet.contains("body[data-app-category=\"media\"] main > .max-w-4xl"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] main > .max-w-6xl"));
    }

    @Test
    void desktopWorkbenchShouldAvoidNonessentialNestedScrollbars() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/app-layout.css"));

        assertTrue(stylesheet.contains("@media (min-width: 1181px)"));
        assertTrue(stylesheet.contains("--app-header-height: 58px"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-demo\"] .template-list"));
        assertTrue(stylesheet.contains("grid-template-columns: repeat(auto-fit, minmax(148px, 1fr))"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-demo\"] #variablesGrid"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-demo\"] #resultContent"));
        assertTrue(stylesheet.contains("max-height: none !important"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-demo\"] .template-item p"));
        assertTrue(stylesheet.contains("-webkit-line-clamp: 2"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-preview"));
        assertTrue(stylesheet.contains("overflow: visible"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-output-column {\n        grid-column: auto;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-output-column {\n        grid-column: 1 / -1;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-list"));
        assertTrue(stylesheet.contains("body[data-app-page=\"memory-manager\"] .memory-result-row"));
        assertTrue(stylesheet.contains("html[data-app-layout=\"active\"],\n    body[data-app-layout=\"active\"] {\n        height: 100vh;"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"] main {\n        min-height: 0 !important;"));
        assertTrue(stylesheet.contains("overflow: hidden !important"));
        assertFalse(stylesheet.contains("body[data-app-page=\"prompt-demo\"] .template-list {\n    max-height: calc(100vh - var(--app-header-height) - 340px);\n    overflow: auto;"));
        assertFalse(stylesheet.contains("body[data-app-page=\"prompt-demo\"] #variablesGrid,\nbody[data-app-page=\"prompt-demo\"] #resultContent {\n    max-height: 170px;\n    overflow: auto;"));
    }
}
