package com.example.demo.service;

import java.util.List;

public record DocumentQualityDimensionScore(
        String id,
        String name,
        int score,
        int weight,
        String level,
        String evidence,
        List<String> suggestions
) {
}
