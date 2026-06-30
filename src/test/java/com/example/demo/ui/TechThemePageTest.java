package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechThemePageTest {

    private static final Path STATIC_DIR = Path.of("src/main/resources/static");
    private static final String THEME_CSS_LINK = "<link rel=\"stylesheet\" href=\"/css/tech-theme.css?v=20260610-depth\">";
    private static final String THEME_SCRIPT = "<script src=\"/js/tech-theme.js?v=20260610-depth\" defer></script>";

    @Test
    void allStaticPagesShouldUseSharedTechThemeAssets() throws Exception {
        List<Path> pages;
        try (Stream<Path> files = Files.list(STATIC_DIR)) {
            pages = files
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .sorted()
                    .toList();
        }

        assertFalse(pages.isEmpty());
        for (Path pagePath : pages) {
            String page = Files.readString(pagePath);
            assertTrue(page.contains(THEME_CSS_LINK), pagePath + " should load the shared technology theme stylesheet");
            assertTrue(page.contains(THEME_SCRIPT), pagePath + " should load the shared technology theme renderer");
        }
    }

    @Test
    void techThemeShouldProvideAnimatedBackgroundAndAccessibleFallback() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/tech-theme.css"));
        String renderer = Files.readString(STATIC_DIR.resolve("js/tech-theme.js"));

        assertTrue(stylesheet.contains("body[data-tech-theme=\"active\"]"));
        assertTrue(stylesheet.contains("color-scheme: dark;"));
        assertTrue(stylesheet.contains("--tech-bg: #050914;"));
        assertTrue(stylesheet.contains("--tech-text: #e6f4ff;"));
        assertFalse(stylesheet.contains("color-scheme: light;"));
        assertTrue(stylesheet.contains(".tech-grid-canvas"));
        assertTrue(stylesheet.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(stylesheet.contains("--tech-cyan"));
        assertTrue(stylesheet.contains("--tech-amber"));

        assertTrue(renderer.contains("tech-grid-canvas"));
        assertTrue(renderer.contains("requestAnimationFrame"));
        assertTrue(renderer.contains("prefers-reduced-motion: reduce"));
        assertTrue(renderer.contains("data-tech-theme"));
    }

    @Test
    void techThemeShouldCoverCustomPageComponents() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/tech-theme.css"));

        assertTrue(stylesheet.contains(".login-container"));
        assertTrue(stylesheet.contains(".editor-container"));
        assertTrue(stylesheet.contains(".variables-container"));
        assertTrue(stylesheet.contains(".result-container"));
        assertTrue(stylesheet.contains(".user-input"));
        assertTrue(stylesheet.contains(".filter-bar"));
        assertTrue(stylesheet.contains(".memory-card"));
        assertTrue(stylesheet.contains(".stat-card"));
        assertTrue(stylesheet.contains(".category-tab"));
        assertTrue(stylesheet.contains(".quick-btn"));
        assertTrue(stylesheet.contains(".bg-blue-100"));
        assertTrue(stylesheet.contains(".bg-emerald-50"));
        assertTrue(stylesheet.contains(".bg-secondary"));
    }

    @Test
    void techThemeShouldDefineVisualDepthLayers() throws Exception {
        String stylesheet = Files.readString(STATIC_DIR.resolve("css/tech-theme.css"));

        assertTrue(stylesheet.contains("--tech-surface-workbench"));
        assertTrue(stylesheet.contains("--tech-surface-module"));
        assertTrue(stylesheet.contains("--tech-surface-sunken"));
        assertTrue(stylesheet.contains("--tech-depth-workbench"));
        assertTrue(stylesheet.contains("--tech-depth-module"));
        assertTrue(stylesheet.contains("body[data-tech-theme=\"active\"] main >"));
        assertTrue(stylesheet.contains("body[data-tech-theme=\"active\"] .tech-layer-primary"));
        assertTrue(stylesheet.contains("body[data-tech-theme=\"active\"] .tech-layer-secondary"));
        assertTrue(stylesheet.contains("body[data-tech-theme=\"active\"] .tech-layer-sunken"));
        assertTrue(stylesheet.contains("body[data-tech-theme=\"active\"] .tech-layer-accent"));
    }
}
