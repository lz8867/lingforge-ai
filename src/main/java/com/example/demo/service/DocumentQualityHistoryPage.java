package com.example.demo.service;

import java.util.List;

public record DocumentQualityHistoryPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<DocumentQualityHistoryRecord> records
) {
}
