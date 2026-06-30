package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactDashboardFrontendTest {

    @Test
    void packageShouldExposeReactViteAntdDashboardScripts() throws Exception {
        String packageJson = Files.readString(Path.of("package.json"));

        assertTrue(packageJson.contains("\"dev\": \"vite"));
        assertTrue(packageJson.contains("\"build\": \"vite build\""));
        assertTrue(packageJson.contains("\"react\""));
        assertTrue(packageJson.contains("\"vite\""));
        assertTrue(packageJson.contains("\"antd\""));
    }

    @Test
    void viteShouldBuildDashboardIntoSpringStaticResources() throws Exception {
        String viteConfig = Files.readString(Path.of("vite.config.ts"));
        String packageJson = Files.readString(Path.of("package.json"));

        assertTrue(viteConfig.contains("root: 'frontend'"));
        assertTrue(viteConfig.contains("outDir: '../src/main/resources/static/react-dashboard'"));
        assertTrue(viteConfig.contains("base: '/react-dashboard/'"));
        assertTrue(viteConfig.contains("host: '127.0.0.1'"));
        assertTrue(viteConfig.contains("publicDir: false"));
        assertTrue(packageJson.contains("\"dev\": \"vite --host 127.0.0.1\""));
        assertTrue(packageJson.contains("\"preview\": \"vite preview --host 127.0.0.1\""));
    }

    @Test
    void reactDashboardShouldUseCustomerReadyWorkbenchSections() throws Exception {
        String html = Files.readString(Path.of("frontend/index.html"));
        String app = Files.readString(Path.of("frontend/src/App.tsx"));
        String stylesheet = Files.readString(Path.of("frontend/src/App.css"));

        assertTrue(html.contains("rel=\"icon\""));
        assertTrue(app.contains("from 'antd'"));
        assertFalse(app.contains("/api/" + "master/overview"));
        assertFalse(app.contains("/api/" + "master/collect"));
        assertFalse(app.contains("/api/" + "master/demo"));
        assertTrue(app.contains("/api/deploy/status"));
        assertTrue(app.contains("智能中枢"));
        assertTrue(app.contains("Prompt 评测"));
        assertTrue(app.contains("内容生成"));
        assertTrue(app.contains("治理分析"));
        assertTrue(app.contains("governance-form"));
        assertTrue(app.contains("sourceData"));
        assertTrue(app.contains("报告草稿"));
        assertTrue(app.contains("生成草稿"));
        assertTrue(app.contains("打开来源"));
        assertTrue(app.contains("通用导出数据"));
        assertTrue(app.contains("页面输入"));
        assertTrue(app.contains("粘贴通用导出数据"));
        assertTrue(app.contains("buildGovernanceReport"));
        assertTrue(app.contains("实时态势"));
        assertTrue(stylesheet.contains("dashboard-shell"));
        assertTrue(stylesheet.contains("tech-grid"));
        assertTrue(stylesheet.contains("signal-pulse"));
        assertTrue(stylesheet.contains("governance-actions"));
        assertTrue(stylesheet.contains("source-data-input"));
    }

    @Test
    void reactDashboardShouldKeepDarkWorkbenchTheme() throws Exception {
        String app = Files.readString(Path.of("frontend/src/App.tsx"));
        String stylesheet = Files.readString(Path.of("frontend/src/App.css"));

        assertTrue(app.contains("theme.darkAlgorithm"));
        assertTrue(app.contains("colorBgBase: '#050914'"));
        assertTrue(app.contains("colorTextBase: '#eaf7ff'"));
        assertTrue(stylesheet.contains("background: #050914;"));
        assertTrue(stylesheet.contains("color: #eaf7ff;"));
        assertFalse(app.contains("theme.defaultAlgorithm"));
        assertFalse(stylesheet.contains("/* React 白色工作台主题 */"));
    }
}
