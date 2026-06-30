package com.example.demo.service;

import java.util.List;

public record DocumentSummaryModule(
        String moduleCode,
        String moduleName,
        List<DocumentSummaryPoint> points
) {
}
