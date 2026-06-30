package com.example.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class DocumentQualityRuleConfigService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityRuleConfigService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_RULE_CONFIG_PATH = "document-quality-rules.json";

    private final DocumentQualityRuleConfig config;

    public DocumentQualityRuleConfigService() {
        this(loadDefaultConfig());
    }

    private DocumentQualityRuleConfigService(DocumentQualityRuleConfig config) {
        this.config = normalize(config);
    }

    public static DocumentQualityRuleConfigService fromJson(String json) {
        try {
            return new DocumentQualityRuleConfigService(OBJECT_MAPPER.readValue(json, DocumentQualityRuleConfig.class));
        } catch (IOException e) {
            throw new IllegalArgumentException("文档质量规则配置 JSON 解析失败：" + e.getMessage(), e);
        }
    }

    public DocumentQualityThresholds thresholds() {
        return config.thresholds();
    }

    public List<RequiredEvidenceRule> requiredEvidenceRulesFor(String documentType) {
        String normalizedType = safeText(documentType).toLowerCase(Locale.ROOT);
        return config.documentTypes().stream()
                .filter(typeRules -> typeRules.matches(normalizedType))
                .findFirst()
                .map(DocumentTypeRules::requiredEvidence)
                .orElse(List.of());
    }

    private static DocumentQualityRuleConfig loadDefaultConfig() {
        ClassPathResource resource = new ClassPathResource(DEFAULT_RULE_CONFIG_PATH);
        if (!resource.exists()) {
            log.warn("文档质量默认规则配置不存在，使用内置阈值兜底: {}", DEFAULT_RULE_CONFIG_PATH);
            return DocumentQualityRuleConfig.defaultConfig();
        }
        try (InputStream input = resource.getInputStream()) {
            return OBJECT_MAPPER.readValue(input, DocumentQualityRuleConfig.class);
        } catch (IOException e) {
            log.warn("文档质量默认规则配置读取失败，使用内置阈值兜底: {}", e.getMessage());
            return DocumentQualityRuleConfig.defaultConfig();
        }
    }

    private static DocumentQualityRuleConfig normalize(DocumentQualityRuleConfig raw) {
        DocumentQualityThresholds thresholds = raw == null || raw.thresholds() == null
                ? DocumentQualityThresholds.defaultThresholds()
                : raw.thresholds().normalized();
        List<DocumentTypeRules> documentTypes = raw == null || raw.documentTypes() == null
                ? List.of()
                : raw.documentTypes().stream()
                        .filter(Objects::nonNull)
                        .map(DocumentTypeRules::normalized)
                        .toList();
        return new DocumentQualityRuleConfig(thresholds, documentTypes);
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    public record DocumentQualityRuleConfig(
            DocumentQualityThresholds thresholds,
            List<DocumentTypeRules> documentTypes
    ) {
        static DocumentQualityRuleConfig defaultConfig() {
            return new DocumentQualityRuleConfig(DocumentQualityThresholds.defaultThresholds(), List.of());
        }
    }

    public record DocumentQualityThresholds(
            int minHeadingCount,
            int longParagraphChars,
            double duplicateRate,
            int averageSentenceLengthWarning,
            int averageSentenceLengthRisk
    ) {
        static DocumentQualityThresholds defaultThresholds() {
            return new DocumentQualityThresholds(2, 240, 0.12, 70, 90);
        }

        DocumentQualityThresholds normalized() {
            return new DocumentQualityThresholds(
                    minHeadingCount <= 0 ? 2 : minHeadingCount,
                    longParagraphChars <= 0 ? 240 : longParagraphChars,
                    duplicateRate <= 0 ? 0.12 : duplicateRate,
                    averageSentenceLengthWarning <= 0 ? 70 : averageSentenceLengthWarning,
                    averageSentenceLengthRisk <= 0 ? 90 : averageSentenceLengthRisk
            );
        }
    }

    public record DocumentTypeRules(
            List<String> typeKeywords,
            List<RequiredEvidenceRule> requiredEvidence
    ) {
        boolean matches(String normalizedType) {
            return typeKeywords().stream()
                    .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedType::contains);
        }

        DocumentTypeRules normalized() {
            List<String> safeKeywords = typeKeywords == null ? List.of() : typeKeywords.stream()
                    .map(DocumentQualityRuleConfigService::safeText)
                    .filter(keyword -> !keyword.isBlank())
                    .toList();
            List<RequiredEvidenceRule> safeRules = requiredEvidence == null ? List.of() : requiredEvidence.stream()
                    .filter(Objects::nonNull)
                    .map(RequiredEvidenceRule::normalized)
                    .toList();
            return new DocumentTypeRules(safeKeywords, safeRules);
        }
    }

    public record RequiredEvidenceRule(
            String issueId,
            String severity,
            String category,
            String description,
            String suggestion,
            String evidenceMessage,
            String matcher,
            String ragRequirementId,
            List<List<String>> keywordGroups
    ) {
        RequiredEvidenceRule normalized() {
            List<List<String>> safeGroups = keywordGroups == null ? List.of() : keywordGroups.stream()
                    .filter(Objects::nonNull)
                    .map(group -> group.stream()
                            .map(DocumentQualityRuleConfigService::safeText)
                            .filter(keyword -> !keyword.isBlank())
                            .toList())
                    .filter(group -> !group.isEmpty())
                    .toList();
            return new RequiredEvidenceRule(
                    safeText(issueId),
                    safeText(severity).isBlank() ? "P2" : safeText(severity),
                    safeText(category).isBlank() ? "完整性" : safeText(category),
                    safeText(description),
                    safeText(suggestion),
                    safeText(evidenceMessage),
                    safeText(matcher).isBlank() ? "keyword_or_rag" : safeText(matcher),
                    safeText(ragRequirementId),
                    safeGroups
            );
        }
    }
}
