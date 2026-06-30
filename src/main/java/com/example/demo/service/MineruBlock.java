package com.example.demo.service;

public record MineruBlock(
        String id,
        String type,
        String title,
        String content,
        int page,
        int order,
        double confidence
) {
    public MineruBlock {
        id = safeText(id, "B000");
        type = safeText(type, "paragraph");
        title = safeText(title, "未命名片段");
        content = safeText(content, "");
        page = Math.max(1, page);
        order = Math.max(1, order);
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
