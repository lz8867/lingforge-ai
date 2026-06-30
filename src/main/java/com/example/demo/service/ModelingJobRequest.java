package com.example.demo.service;

public record ModelingJobRequest(
        String prompt,
        String kind,
        String quality,
        Integer viewCount,
        Integer faceBudgetK,
        Integer seed,
        Integer steps,
        Double guidanceScale,
        Integer numGaussians
) {

    private static final int MIN_GAUSSIANS = 32768;
    private static final int MAX_GAUSSIANS = 262144;

    public ModelingJobRequest {
        prompt = normalizeText(prompt, "图像到 3D Gaussian 资产生成");
        kind = normalizeChoice(kind, "character", "product", "character", "space");
        quality = normalizeChoice(quality, "balanced", "speed", "balanced", "quality");
        viewCount = clamp(defaultIfNull(viewCount, 8), 4, 16);
        faceBudgetK = clamp(defaultIfNull(faceBudgetK, 42), 16, 128);
        seed = defaultIfNull(seed, 42);
        steps = clamp(defaultIfNull(steps, "speed".equals(quality) ? 10 : 20), 1, 50);
        guidanceScale = clamp(defaultIfNull(guidanceScale, 3.0), 1.0, 10.0);
        numGaussians = normalizeGaussianCount(defaultIfNull(numGaussians, faceBudgetK * 1024));
    }

    private static String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeChoice(String value, String fallback, String... allowedValues) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(normalized)) {
                return normalized;
            }
        }
        return fallback;
    }

    private static int normalizeGaussianCount(int value) {
        int clamped = clamp(value, MIN_GAUSSIANS, MAX_GAUSSIANS);
        return Math.round(clamped / 32.0f) * 32;
    }

    private static int defaultIfNull(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static double defaultIfNull(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
