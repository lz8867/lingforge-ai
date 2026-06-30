package com.example.demo.service;

import java.util.List;
import java.util.Map;

public record DigitalTwinNode(
        String id,
        String name,
        String type,
        String status,
        String summary,
        int healthScore,
        List<String> dependencies,
        Map<String, Object> metrics,
        List<String> actions
) {
}
