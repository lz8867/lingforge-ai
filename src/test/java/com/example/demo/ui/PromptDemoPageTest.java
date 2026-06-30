package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptDemoPageTest {

    @Test
    void promptDemoShouldKeepTemplateListWithoutQuickStartShortcuts() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-demo.html"));

        assertTrue(page.contains("template-list"));
        assertTrue(page.contains("category-tabs"));
        assertFalse(page.contains("快速开始"));
        assertFalse(page.contains("quick-actions"));
        assertFalse(page.contains("quick-btn"));
        assertFalse(page.contains("quickStart("));
    }
}
