package com.example.demo.service;

import java.util.List;

public record MineruParseResult(
        boolean success,
        String message,
        String documentName,
        String fileType,
        String sourceMode,
        int pageCount,
        int wordCount,
        int lineCount,
        int blockCount,
        String markdown,
        List<MineruBlock> blocks,
        List<String> warnings
) {
    public MineruParseResult {
        message = safeText(message, "");
        documentName = safeText(documentName, "未命名文档");
        fileType = safeText(fileType, "unknown");
        sourceMode = safeText(sourceMode, "local-heuristic");
        pageCount = Math.max(0, pageCount);
        wordCount = Math.max(0, wordCount);
        lineCount = Math.max(0, lineCount);
        blockCount = Math.max(0, blockCount);
        markdown = markdown == null ? "" : markdown;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
