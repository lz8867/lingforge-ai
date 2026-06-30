package com.example.demo.service;

import java.util.List;

public record ModelEvaluationFacets(
        List<String> scenes,
        List<String> models,
        List<String> providers,
        List<String> requestSources
) {
}
