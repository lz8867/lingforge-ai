package com.example.demo.service;

import java.util.List;

public record DigitalTwinOverview(
        String checkedAt,
        int healthScore,
        int totalCapabilities,
        int onlineCapabilities,
        int warningCapabilities,
        int criticalCapabilities,
        int activeAlerts,
        List<DigitalTwinNode> nodes,
        List<DigitalTwinRelation> relations,
        List<DigitalTwinEvent> events,
        List<String> recommendations
) {
}
