package com.example.demo.open;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSourceSanitizationTest {

    private static final List<String> SCAN_ROOTS = List.of(
            "src/main",
            "src/test",
            "frontend",
            "scripts",
            ".github"
    );

    private static final List<String> SCAN_FILES = List.of(
            "README.md",
            "pom.xml",
            "package.json",
            "vite.config.ts",
            "tsconfig.json",
            "Dockerfile",
            "docker-compose.yml"
    );

    private static final Map<String, List<String>> FORBIDDEN_TERMS = Map.of(
            "internal-company-domain", List.of("q" + "fei", "matrix." + "q" + "fei"),
            "internal-doc-platform", List.of("fei" + "shu", "\u98de" + "\u4e66"),
            "real-company-example", List.of("\u6b63\u6cf0", "\u5b9c\u5bbe", "\u4f01\u98de"),
            "retired-internal-tool", List.of("master-tools", "/api/master", "master \u5df2\u5378\u8f7d"),
            "unsafe-demo-secret", List.of("abc" + "123456", "138" + "00138000", "\u751f\u4ea7" + "\u73af\u5883")
    );

    @Test
    void openSourceCandidateFilesShouldNotContainInternalTraces() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : publicCandidateFiles()) {
            if (!Files.isRegularFile(path) || isBinaryOrGeneratedAsset(path)) {
                continue;
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String lowerContent = content.toLowerCase(Locale.ROOT);
            FORBIDDEN_TERMS.forEach((category, terms) -> {
                for (String term : terms) {
                    if (lowerContent.contains(term.toLowerCase(Locale.ROOT))) {
                        violations.add(path + " contains " + category);
                    }
                }
            });
        }

        assertTrue(violations.isEmpty(), () -> "开源候选文件仍包含内部痕迹：\n" + String.join("\n", violations));
    }

    private List<Path> publicCandidateFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String root : SCAN_ROOTS) {
            Path path = Path.of(root);
            if (!Files.exists(path)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(path)) {
                stream.filter(Files::isRegularFile)
                        .filter(item -> !isIgnoredPath(item))
                        .forEach(files::add);
            }
        }
        for (String file : SCAN_FILES) {
            Path path = Path.of(file);
            if (Files.isRegularFile(path)) {
                files.add(path);
            }
        }
        return files;
    }

    private boolean isIgnoredPath(Path path) {
        String normalized = path.toString();
        return normalized.contains("/vendor/")
                || normalized.contains("/react-dashboard/assets/")
                || normalized.contains("/__pycache__/")
                || normalized.endsWith("OpenSourceSanitizationTest.java");
    }

    private boolean isBinaryOrGeneratedAsset(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith(".")
                || name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".mp4")
                || name.endsWith(".jar")
                || name.endsWith(".class")
                || name.endsWith(".pyc")
                || name.endsWith(".docx")
                || name.endsWith(".pdf");
    }
}
