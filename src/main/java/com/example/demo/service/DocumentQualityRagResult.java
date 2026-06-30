package com.example.demo.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DocumentQualityRagResult {

    private static final int DEFAULT_TOP_K = 3;

    private final List<DocumentQualityRagChunk> chunks;
    private final Map<String, DocumentQualityRagEvidence> evidenceByRequirement;
    private final double coverageScore;
    private final DocumentQualityEmbeddingClient embeddingClient;

    DocumentQualityRagResult(
            List<DocumentQualityRagChunk> chunks,
            Map<String, DocumentQualityRagEvidence> evidenceByRequirement,
            double coverageScore) {
        this(chunks, evidenceByRequirement, coverageScore, DocumentQualityEmbeddingClient.localOnly());
    }

    DocumentQualityRagResult(
            List<DocumentQualityRagChunk> chunks,
            Map<String, DocumentQualityRagEvidence> evidenceByRequirement,
            double coverageScore,
            DocumentQualityEmbeddingClient embeddingClient) {
        this.chunks = List.copyOf(chunks);
        this.evidenceByRequirement = Map.copyOf(evidenceByRequirement);
        this.coverageScore = coverageScore;
        this.embeddingClient = embeddingClient;
    }

    public int chunkCount() {
        return chunks.size();
    }

    public double coverageScore() {
        return coverageScore;
    }

    public Map<String, DocumentQualityRagEvidence> evidenceByRequirement() {
        Map<String, DocumentQualityRagEvidence> ordered = new LinkedHashMap<>();
        evidenceByRequirement.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    public List<DocumentQualityRagHit> search(String query, int topK) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank() || chunks.isEmpty()) {
            return List.of();
        }

        int boundedTopK = topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, chunks.size());
        double[] queryVector = embeddingClient.embed(safeQuery).vector();
        return chunks.stream()
                .map(chunk -> toHit(chunk, cosineSimilarity(queryVector, chunk.vector())))
                .sorted(Comparator.comparingDouble(DocumentQualityRagHit::score).reversed())
                .limit(boundedTopK)
                .toList();
    }

    public List<Map<String, Object>> visualizationRows() {
        return evidenceByRequirement.values().stream()
                .map(evidence -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("requirementId", evidence.requirementId());
                    row.put("label", evidence.label());
                    row.put("matched", evidence.matched());
                    row.put("topScore", evidence.topScore());
                    row.put("topHits", evidence.topHits().stream()
                            .map(hit -> {
                                Map<String, Object> hitRow = new LinkedHashMap<>();
                                hitRow.put("chunkId", hit.chunkId());
                                hitRow.put("title", hit.title());
                                hitRow.put("score", hit.score());
                                hitRow.put("location", "L" + hit.startLine() + "-L" + hit.endLine());
                                hitRow.put("content", hit.content());
                                return hitRow;
                            })
                            .toList());
                    return row;
                })
                .toList();
    }

    private static DocumentQualityRagHit toHit(DocumentQualityRagChunk chunk, double score) {
        return new DocumentQualityRagHit(
                chunk.chunkId(),
                chunk.title(),
                chunk.content(),
                round4(score),
                chunk.startLine(),
                chunk.endLine()
        );
    }

    private static double cosineSimilarity(double[] left, double[] right) {
        if (left.length != right.length || left.length == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < left.length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    record DocumentQualityRagChunk(
            String chunkId,
            String title,
            String content,
            int startLine,
            int endLine,
            double[] vector
    ) {
    }
}
