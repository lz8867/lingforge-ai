package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_quality_evaluation_record")
public class DocumentQualityEvaluationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String documentName;

    @Column(nullable = false, length = 80)
    private String documentType;

    private int overallScore;

    @Column(length = 80)
    private String grade;

    private int ruleScore;

    private int llmJudgeScore;

    private int metricScore;

    private int issueCount;

    private double ragCoverageScore;

    @Column(length = 40)
    private String llmJudgeSource;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = overallScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public int getRuleScore() {
        return ruleScore;
    }

    public void setRuleScore(int ruleScore) {
        this.ruleScore = ruleScore;
    }

    public int getLlmJudgeScore() {
        return llmJudgeScore;
    }

    public void setLlmJudgeScore(int llmJudgeScore) {
        this.llmJudgeScore = llmJudgeScore;
    }

    public int getMetricScore() {
        return metricScore;
    }

    public void setMetricScore(int metricScore) {
        this.metricScore = metricScore;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(int issueCount) {
        this.issueCount = issueCount;
    }

    public double getRagCoverageScore() {
        return ragCoverageScore;
    }

    public void setRagCoverageScore(double ragCoverageScore) {
        this.ragCoverageScore = ragCoverageScore;
    }

    public String getLlmJudgeSource() {
        return llmJudgeSource;
    }

    public void setLlmJudgeSource(String llmJudgeSource) {
        this.llmJudgeSource = llmJudgeSource;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
