package com.example.demo.service;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.DTO.KnowledgeCaptureResult;

public interface KnowledgeCaptureService {

    KnowledgeCaptureResult capture(KnowledgeCaptureRequest request);
}
