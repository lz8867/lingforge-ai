package com.example.demo.service.impl;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.DTO.KnowledgeCaptureResult;
import com.example.demo.model.Knowledge;
import com.example.demo.repository.KnowledgeRepository;
import com.example.demo.service.KnowledgeCaptureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class KnowledgeCaptureServiceImpl implements KnowledgeCaptureService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCaptureServiceImpl.class);
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 12000;
    private static final int MAX_METADATA_LENGTH = 500;

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeCaptureServiceImpl(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    @Override
    public KnowledgeCaptureResult capture(KnowledgeCaptureRequest request) {
        try {
            String normalizedTitle = normalizeText(request == null ? null : request.getTitle(), MAX_TITLE_LENGTH, true);
            String normalizedContent = normalizeText(request == null ? null : request.getContent(), MAX_CONTENT_LENGTH, false);

            if (normalizedTitle.isBlank() || normalizedContent.isBlank()) {
                log.warn("知识库采集被跳过: title or content is blank");
                return KnowledgeCaptureResult.skipped(
                        KnowledgeCaptureResult.ACTION_SKIPPED_INVALID,
                        "标题或内容为空，未入库。"
                );
            }

            String normalizedMetadata = buildMetadata(request, normalizedTitle, normalizedContent);
            String status = resolveStatus(request == null ? null : request.getStatus(), request != null && request.isPublish());

            if (knowledgeRepository != null && knowledgeRepository.existsByTitleAndContentIgnoreCase(normalizedTitle, normalizedContent)) {
                log.info("知识库采集被跳过: duplicate title+content, title={}", preview(normalizedTitle));
                return KnowledgeCaptureResult.skipped(
                        KnowledgeCaptureResult.ACTION_SKIPPED_DUPLICATE,
                        "已有相同标题和内容，不重复入库。"
                );
            }

            Knowledge knowledge = new Knowledge();
            knowledge.setTitle(normalizedTitle);
            knowledge.setContent(buildContentForPersist(normalizedContent, normalizedMetadata));
            knowledge.setCategoryId(request == null ? null : request.getCategoryId());
            knowledge.setStatus(status);
            knowledge.setCreatedBy(request == null ? null : request.getCreatedBy());
            knowledge.setUpdatedBy(request == null ? null : request.getCreatedBy());
            knowledge.setCreatedAt(LocalDateTime.now());
            knowledge.setUpdatedAt(LocalDateTime.now());
            knowledge.setVersion(1);

            if (knowledgeRepository != null) {
                knowledge = knowledgeRepository.save(knowledge);
            }

            log.info("知识库采集完成: title={}, status={}", preview(normalizedTitle), status);
            return KnowledgeCaptureResult.created(knowledge, "知识录入成功。");
        } catch (Exception e) {
            log.error("知识库采集失败: {}", e.getMessage());
            return KnowledgeCaptureResult.failed("知识库采集失败: " + e.getMessage());
        }
    }

    private static String resolveStatus(String status, boolean publish) {
        if (publish) {
            return "published";
        }
        if ("published".equalsIgnoreCase(normalizedText(status))) {
            return "published";
        }
        return "draft";
    }

    private static String normalizeText(String value, int maxLength, boolean collapseWhitespace) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (collapseWhitespace) {
            normalized = normalized.replaceAll("\\s+", " ");
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
    }

    private static String normalizedText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String preview(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 20 ? value : value.substring(0, 20) + "...";
    }

    private static String buildContentForPersist(String content, String metadata) {
        if (metadata.isBlank()) {
            return content;
        }
        String sourceInfo = "【知识来源】" + metadata;
        return sourceInfo + "\n\n" + content;
    }

    private static String buildMetadata(KnowledgeCaptureRequest request, String title, String content) {
        StringBuilder metadataBuilder = new StringBuilder();
        appendMetadata(metadataBuilder, "metadata", request == null ? null : request.getMetadata());
        appendMetadata(metadataBuilder, "sourceType", request == null ? null : request.getSourceType());
        appendMetadata(metadataBuilder, "sourceId", request == null ? null : request.getSourceId());
        appendMetadata(metadataBuilder, "sourceScene", request == null ? null : request.getSourceScene());
        appendMetadata(metadataBuilder, "sourceReference", request == null ? null : request.getSourceReference());
        appendMetadata(metadataBuilder, "createdBy", request == null ? null : request.getCreatedBy());
        appendMetadata(metadataBuilder, "captureTime", LocalDateTime.now().toString());
        appendMetadata(metadataBuilder, "titleHash", String.valueOf(Math.abs((title + "|" + content).hashCode())));

        String metadata = metadataBuilder.toString();
        if (metadata.isBlank()) {
            return "";
        }
        return metadata.length() <= MAX_METADATA_LENGTH ? metadata : metadata.substring(0, MAX_METADATA_LENGTH).trim();
    }

    private static void appendMetadata(StringBuilder builder, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" | ");
        }
        builder.append(key).append("=").append(value.trim());
    }
}
