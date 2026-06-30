package com.example.demo.controller;

import com.example.demo.DTO.KnowledgeCaptureRequest;
import com.example.demo.service.DocumentQualityExtractionResult;
import com.example.demo.service.DocumentQualityExportRequest;
import com.example.demo.service.DocumentQualityExportResult;
import com.example.demo.service.DocumentQualityFileService;
import com.example.demo.service.DocumentQualityHistoryPage;
import com.example.demo.service.DocumentQualityHistoryRecord;
import com.example.demo.service.DocumentQualityHistoryService;
import com.example.demo.service.DocumentQualityRequest;
import com.example.demo.service.DocumentQualityResult;
import com.example.demo.service.DocumentQualityReportExportService;
import com.example.demo.service.DocumentQualityService;
import com.example.demo.service.DocumentQualityTrendResult;
import com.example.demo.service.KnowledgeCaptureService;
import com.example.demo.service.MemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/document-quality")
public class DocumentQualityController {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityController.class);

    private final DocumentQualityService documentQualityService;
    private final DocumentQualityFileService documentQualityFileService;
    private final DocumentQualityReportExportService reportExportService;
    private final DocumentQualityHistoryService historyService;
    private final KnowledgeCaptureService knowledgeCaptureService;
    private final MemoryService memoryService;

    public DocumentQualityController(
            DocumentQualityService documentQualityService,
            DocumentQualityFileService documentQualityFileService) {
        this(
                documentQualityService,
                documentQualityFileService,
                new DocumentQualityReportExportService(),
                DocumentQualityHistoryService.disabled(),
                null,
                null
        );
    }

    public DocumentQualityController(
            DocumentQualityService documentQualityService,
            DocumentQualityFileService documentQualityFileService,
            DocumentQualityReportExportService reportExportService,
            DocumentQualityHistoryService historyService) {
        this(
                documentQualityService,
                documentQualityFileService,
                reportExportService,
                historyService,
                null,
                null
        );
    }

    @Autowired
    public DocumentQualityController(
            DocumentQualityService documentQualityService,
            DocumentQualityFileService documentQualityFileService,
            DocumentQualityReportExportService reportExportService,
            DocumentQualityHistoryService historyService,
            @Autowired(required = false) KnowledgeCaptureService knowledgeCaptureService,
            @Autowired(required = false) MemoryService memoryService) {
        this.documentQualityService = documentQualityService;
        this.documentQualityFileService = documentQualityFileService;
        this.reportExportService = reportExportService;
        this.historyService = historyService;
        this.knowledgeCaptureService = knowledgeCaptureService;
        this.memoryService = memoryService;
    }

    public DocumentQualityResult evaluate(DocumentQualityRequest request) {
        return evaluate(request, false, "draft", "default_user");
    }

    @PostMapping("/evaluate")
    public DocumentQualityResult evaluate(
            @RequestBody DocumentQualityRequest request,
            @RequestParam(defaultValue = "false") boolean captureKnowledge,
            @RequestParam(defaultValue = "draft") String knowledgeStatus,
            @RequestParam(defaultValue = "default_user") String userId) {
        log.info("收到文档质量评测请求: documentName={}, documentType={}",
                request == null ? "" : request.documentName(),
                request == null ? "" : request.documentType());
        DocumentQualityResult result = documentQualityService.evaluate(request);
        saveDocumentQualityMemory(request, result, userId);
        if (captureKnowledge && result != null && result.success()) {
            captureDocumentQualityResult(request, result, knowledgeStatus);
        }
        return result;
    }

    private void saveDocumentQualityMemory(DocumentQualityRequest request, DocumentQualityResult result, String userId) {
        if (memoryService == null || request == null || result == null) {
            return;
        }
        String safeUserId = normalizeUserId(userId);
        String prompt = String.format(
                "文档名: %s\n文档类型: %s\n内容摘要: %s",
                request.documentName() == null ? "" : request.documentName().trim(),
                request.documentType() == null ? "" : request.documentType().trim(),
                normalizeContentForMemory(request.content())
        );
        memoryService.saveMemory(safeUserId, prompt, "document-quality_prompt", null);
        memoryService.saveMemory(safeUserId, buildDocumentQualityResultMemory(result), "document-quality_result", null);
    }

    private static String normalizeContentForMemory(String content) {
        if (content == null) {
            return "";
        }
        String safeContent = content.trim();
        if (safeContent.length() <= 500) {
            return safeContent;
        }
        return safeContent.substring(0, 500) + "…（截断）";
    }

    private static String buildDocumentQualityResultMemory(DocumentQualityResult result) {
        return String.format(
                "评测状态: %s\n综合得分: %s\n评分等级: %s\n规则得分: %s\nLLM得分: %s\n量化得分: %s\n问题数: %s\n建议: %s",
                result.success(),
                result.overallScore(),
                result.grade(),
                result.ruleScore(),
                result.llmJudgeScore(),
                result.metricScore(),
                result.issues() == null ? 0 : result.issues().size(),
                result.recommendations() == null ? "" : String.join("；", result.recommendations())
        );
    }

    private static String normalizeUserId(String userId) {
        if (userId == null) {
            return "default_user";
        }
        String safeUserId = userId.trim();
        return safeUserId.isBlank() ? "default_user" : safeUserId;
    }

    private void captureDocumentQualityResult(
            DocumentQualityRequest request,
            DocumentQualityResult result,
            String knowledgeStatus) {
        if (knowledgeCaptureService == null) {
            return;
        }
        String documentName = request == null ? null : request.documentName();
        String documentType = request == null ? null : request.documentType();
        String content = request == null ? "" : request.content();

        KnowledgeCaptureRequest captureRequest = new KnowledgeCaptureRequest();
        captureRequest.setTitle(buildDocumentQualityCaptureTitle(documentName, documentType, result.overallScore()));
        captureRequest.setContent(buildDocumentQualityCaptureContent(documentName, documentType, content, result));
        captureRequest.setSourceType("document-quality");
        captureRequest.setSourceId(documentName);
        captureRequest.setSourceScene("document-quality-evaluate");
        captureRequest.setSourceReference("/api/document-quality/evaluate");
        captureRequest.setCreatedBy("system");
        captureRequest.setStatus(knowledgeStatus);
        captureRequest.setPublish("published".equalsIgnoreCase(knowledgeStatus));
        knowledgeCaptureService.capture(captureRequest);
    }

    private static String buildDocumentQualityCaptureTitle(String documentName, String documentType, int overallScore) {
        String safeName = documentName == null || documentName.isBlank() ? "未命名文档" : documentName.trim();
        String safeType = documentType == null || documentType.isBlank() ? "通用文档" : documentType.trim();
        return safeName + "（" + safeType + "）- 评测结果：" + overallScore + "分";
    }

    private static String buildDocumentQualityCaptureContent(
            String documentName,
            String documentType,
            String originalContent,
            DocumentQualityResult result) {
        String safeName = documentName == null || documentName.isBlank() ? "未命名文档" : documentName.trim();
        String safeType = documentType == null || documentType.isBlank() ? "通用文档" : documentType.trim();
        String safeContent = originalContent == null ? "" : originalContent.trim();
        String summary = buildQualitySummary(result);
        return "文档名: " + safeName + "\n文档类型: " + safeType
                + "\n\n" + summary + "\n\n原始内容（前1000字）:\n"
                + (safeContent.length() <= 1000 ? safeContent : safeContent.substring(0, 1000));
    }

    private static String buildQualitySummary(DocumentQualityResult result) {
        if (result == null) {
            return "评测结果为空。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("评测状态: ").append(result.success() ? "成功" : "失败").append("\n");
        builder.append("综合得分: ").append(result.overallScore()).append("\n");
        builder.append("评分等级: ").append(result.grade()).append("\n");
        builder.append("规则评分: ").append(result.ruleScore())
                .append(", LLM评分: ").append(result.llmJudgeScore())
                .append(", 量化评分: ").append(result.metricScore()).append("\n");
        builder.append("问题数量: ").append(result.issues().size()).append("\n");
        if (!result.recommendations().isEmpty()) {
            builder.append("关键建议: ").append(String.join("；", result.recommendations())).append("\n");
        }
        return builder.toString();
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentQualityExtractionResult extract(@RequestPart("file") MultipartFile file) {
        log.info("收到文档质量文件解析请求: filename={}", file == null ? "" : file.getOriginalFilename());
        return documentQualityFileService.extract(file);
    }

    @PostMapping("/export")
    public DocumentQualityExportResult export(@RequestBody DocumentQualityExportRequest request) {
        log.info("收到文档质量报告导出请求: format={}", request == null ? "" : request.format());
        return reportExportService.export(request);
    }

    @GetMapping("/history")
    public List<DocumentQualityHistoryRecord> history(@RequestParam(defaultValue = "20") int limit) {
        log.info("收到文档质量历史查询请求: limit={}", limit);
        return historyService.recent(limit);
    }

    @GetMapping("/history/page")
    public DocumentQualityHistoryPage historyPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        log.info("收到文档质量历史分页查询请求: page={}, size={}", page, size);
        return historyService.page(page, size);
    }

    @GetMapping("/trend")
    public DocumentQualityTrendResult trend(
            @RequestParam(required = false) String documentName,
            @RequestParam(required = false) String documentType) {
        log.info("收到文档质量趋势查询请求: documentName={}, documentType={}", documentName, documentType);
        return historyService.trend(documentName, documentType);
    }
}
