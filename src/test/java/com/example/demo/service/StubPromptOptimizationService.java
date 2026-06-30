package com.example.demo.service;

import java.util.Map;

public class StubPromptOptimizationService extends PromptOptimizationService {

    private final Map<String, Object> evaluateResponse;
    private final Map<String, Object> optimizeResponse;

    public StubPromptOptimizationService(Map<String, Object> evaluateResponse, Map<String, Object> optimizeResponse) {
        super(new StubOllamaChatClient("stub-response"));
        this.evaluateResponse = evaluateResponse;
        this.optimizeResponse = optimizeResponse;
    }

    @Override
    public Map<String, Object> evaluate(Map<String, Object> request) {
        return evaluateResponse;
    }

    @Override
    public Map<String, Object> optimize(Map<String, Object> request) {
        return optimizeResponse;
    }
}
