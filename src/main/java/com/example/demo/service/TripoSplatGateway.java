package com.example.demo.service;

import java.nio.file.Path;

public interface TripoSplatGateway {

    boolean isConfigured();

    String serviceUrl();

    TripoSplatGenerationResult generate(String jobId, Path inputImage, Path outputDir, ModelingJobRequest request) throws Exception;
}
