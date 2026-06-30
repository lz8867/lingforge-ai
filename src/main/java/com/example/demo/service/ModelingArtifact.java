package com.example.demo.service;

public record ModelingArtifact(
        String name,
        String type,
        long bytes,
        String downloadUrl,
        String role
) {

    public ModelingArtifact withDownloadUrl(String downloadUrl) {
        return new ModelingArtifact(name, type, bytes, downloadUrl, role);
    }
}
