package com.example.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DocumentQualityReportExportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityReportExportService.class);
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ObjectMapper objectMapper;

    public DocumentQualityReportExportService() {
        this(new ObjectMapper());
    }

    public DocumentQualityReportExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DocumentQualityExportResult export(DocumentQualityExportRequest request) {
        DocumentQualityResult result = request == null ? null : request.result();
        if (result == null) {
            return failure("缺少可导出的评测结果。");
        }
        String format = request.format() == null ? "html" : request.format().trim().toLowerCase(Locale.ROOT);
        log.info("文档质量报告导出开始: documentName={}, format={}", result.documentName(), format);
        try {
            return switch (format) {
                case "json" -> success(result, "json", "application/json;charset=UTF-8", jsonBytes(result));
                case "pdf" -> success(result, "pdf", "application/pdf", pdfBytes(result));
                default -> success(result, "html", "text/html;charset=UTF-8", htmlBytes(result));
            };
        } catch (RuntimeException e) {
            log.warn("文档质量报告导出失败: {}", e.getMessage());
            return failure("报告导出失败：" + e.getMessage());
        }
    }

    private DocumentQualityExportResult success(
            DocumentQualityResult result,
            String extension,
            String contentType,
            byte[] bytes) {
        return new DocumentQualityExportResult(
                true,
                "报告导出成功。",
                safeFileName(result.documentName()) + "-quality-report-" + LocalDateTime.now().format(FILE_TIME_FORMATTER) + "." + extension,
                contentType,
                "base64",
                Base64.getEncoder().encodeToString(bytes)
        );
    }

    private DocumentQualityExportResult failure(String message) {
        return new DocumentQualityExportResult(false, message, "", "", "base64", "");
    }

    private byte[] jsonBytes(DocumentQualityResult result) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private byte[] htmlBytes(DocumentQualityResult result) {
        String html = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>文档质量评测报告</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #0f172a; margin: 32px; line-height: 1.7; }
                    h1, h2 { margin: 0 0 12px; }
                    section { margin: 24px 0; }
                    .score { font-size: 42px; font-weight: 800; color: #2563eb; }
                    table { width: 100%%; border-collapse: collapse; margin-top: 8px; font-size: 13px; }
                    th, td { border: 1px solid #d8dee8; padding: 8px; text-align: left; vertical-align: top; }
                    th { background: #f1f5f9; }
                    li { margin: 6px 0; }
                  </style>
                </head>
                <body>
                  <h1>文档质量评测报告</h1>
                  <section>
                    <div>文档名称：%s</div>
                    <div>文档类型：%s</div>
                    <div>质量等级：%s</div>
                    <div class="score">%s</div>
                  </section>
                  <section>
                    <h2>六大维度</h2>
                    %s
                  </section>
                  <section>
                    <h2>问题清单</h2>
                    %s
                  </section>
                  <section>
                    <h2>量化指标</h2>
                    %s
                  </section>
                  <section>
                    <h2>结构化字段抽取</h2>
                    %s
                  </section>
                  <section>
                    <h2>整改建议</h2>
                    <ul>%s</ul>
                  </section>
                </body>
                </html>
                """.formatted(
                escapeHtml(result.documentName()),
                escapeHtml(result.documentType()),
                escapeHtml(result.grade()),
                result.overallScore(),
                dimensionTable(result.dimensionScores()),
                issueTable(result.issues()),
                metricTable(result.metrics()),
                extractedFieldTable(result),
                listItems(result.recommendations())
        );
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] pdfBytes(DocumentQualityResult result) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                content.newLineAtOffset(54, 740);
                content.showText("Document Quality Report");
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(0, -28);
                for (String line : pdfLines(result)) {
                    content.showText(pdfSafe(line));
                    content.newLineAtOffset(0, -16);
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private List<String> pdfLines(DocumentQualityResult result) {
        return List.of(
                "Document: " + result.documentName(),
                "Type: " + result.documentType(),
                "Score: " + result.overallScore(),
                "Grade: " + result.grade(),
                "Rule Score: " + result.ruleScore(),
                "LLM Judge Score: " + result.llmJudgeScore(),
                "Metric Score: " + result.metricScore(),
                "Field Extraction Coverage: " + fieldExtractionCoverage(result) + "%",
                "Issue Count: " + (result.issues() == null ? 0 : result.issues().size()),
                "Recommendations: " + String.join("; ", result.recommendations() == null ? List.of() : result.recommendations())
        );
    }

    private String dimensionTable(List<DocumentQualityDimensionScore> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "<p>暂无维度评分。</p>";
        }
        return "<table><thead><tr><th>维度</th><th>分数</th><th>证据</th></tr></thead><tbody>"
                + dimensions.stream()
                        .map(item -> "<tr><td>" + escapeHtml(item.name()) + "</td><td>" + item.score() + "</td><td>" + escapeHtml(item.evidence()) + "</td></tr>")
                        .reduce("", String::concat)
                + "</tbody></table>";
    }

    private String issueTable(List<DocumentQualityIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "<p>未发现明确扣分项。</p>";
        }
        return "<table><thead><tr><th>级别</th><th>类别</th><th>问题</th><th>建议</th></tr></thead><tbody>"
                + issues.stream()
                        .map(item -> "<tr><td>" + escapeHtml(item.severity()) + "</td><td>" + escapeHtml(item.category()) + "</td><td>" + escapeHtml(item.description()) + "</td><td>" + escapeHtml(item.suggestion()) + "</td></tr>")
                        .reduce("", String::concat)
                + "</tbody></table>";
    }

    private String metricTable(List<DocumentQualityMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "<p>暂无量化指标。</p>";
        }
        return "<table><thead><tr><th>指标</th><th>值</th><th>说明</th></tr></thead><tbody>"
                + metrics.stream()
                        .map(item -> "<tr><td>" + escapeHtml(item.name()) + "</td><td>" + item.value() + escapeHtml(item.unit()) + "</td><td>" + escapeHtml(item.description()) + "</td></tr>")
                        .reduce("", String::concat)
                + "</tbody></table>";
    }

    private String extractedFieldTable(DocumentQualityResult result) {
        Object rawFields = result.visualizationData() == null ? null : result.visualizationData().get("extractedFields");
        if (!(rawFields instanceof List<?> fields) || fields.isEmpty()) {
            return "<p>暂无结构化字段抽取结果。</p>";
        }
        return "<table><thead><tr><th>字段</th><th>状态</th><th>置信度</th><th>抽取值</th><th>建议</th></tr></thead><tbody>"
                + fields.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(item -> "<tr><td>" + escapeHtml(String.valueOf(item.get("name")))
                                + "</td><td>" + escapeHtml(String.valueOf(item.get("status")))
                                + "</td><td>" + escapeHtml(String.valueOf(item.get("confidence")))
                                + "</td><td>" + escapeHtml(String.valueOf(item.get("values")))
                                + "</td><td>" + escapeHtml(String.valueOf(item.get("suggestion")))
                                + "</td></tr>")
                        .reduce("", String::concat)
                + "</tbody></table>";
    }

    private String fieldExtractionCoverage(DocumentQualityResult result) {
        Object value = result.visualizationData() == null ? null : result.visualizationData().get("fieldExtractionCoverage");
        return value == null ? "0" : String.valueOf(value);
    }

    private String listItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "<li>暂无整改建议。</li>";
        }
        return items.stream()
                .map(item -> "<li>" + escapeHtml(item) + "</li>")
                .reduce("", String::concat);
    }

    private String safeFileName(String value) {
        String safe = value == null || value.isBlank() ? "document" : value.trim();
        return safe.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private String pdfSafe(String value) {
        StringBuilder result = new StringBuilder();
        for (char ch : String.valueOf(value).toCharArray()) {
            result.append(ch >= 32 && ch <= 126 ? ch : '?');
        }
        return result.toString();
    }

    private String escapeHtml(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }
}
