package com.example.demo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSummaryServiceTest {

    @Test
    void summarizeShouldUseModelForDocxAndKeepParsedMetadata() throws Exception {
        CapturingOllamaChatClient client = new CapturingOllamaChatClient(
                """
                {
                  "is_contract": true,
                  "contract_type": "销售合同",
                  "summary_fields": {
                    "合同主体": "- 甲方：星河智能装备有限公司。\\n- 乙方：云杉制造科技有限公司。",
                    "销售标的": "- 标的为自动缝纫机设备。",
                    "合同金额": "- 合同总金额为 90000 元。",
                    "付款/结算方式": "- 甲方应于验收后 30 日内支付尾款。",
                    "违约责任": "- 逾期付款按日承担违约金。"
                  },
                  "structured_summary": {
                    "summary": "双方签署销售合同，约定自动缝纫机设备销售、价款支付、交付验收和违约责任等事项。",
                    "modules": [
                      {
                        "module_code": "subject_price",
                        "module_name": "标的与价款",
                        "ai_generated": true,
                        "points": [
                          {"name": "销售标的", "detail": "标的为自动缝纫机设备。依据：合同标的条款。", "ai_generated": true}
                        ]
                      },
                      {
                        "module_code": "performance",
                        "module_name": "履行方式",
                        "ai_generated": true,
                        "points": [
                          {"name": "付款节点", "detail": "甲方应于验收后 30 日内支付尾款。依据：付款条款。", "ai_generated": true}
                        ]
                      },
                      {
                        "module_code": "breach_liability",
                        "module_name": "违约责任",
                        "ai_generated": true,
                        "points": []
                      },
                      {
                        "module_code": "dispute_resolution",
                        "module_name": "争议解决",
                        "ai_generated": true,
                        "points": []
                      },
                      {
                        "module_code": "other",
                        "module_name": "其他条款",
                        "ai_generated": true,
                        "points": []
                      }
                    ]
                  }
                }
                """
        );
        DocumentSummaryService service = new DocumentSummaryService(
                new DocumentQualityFileService(),
                client
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "产品销售合同.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("""
                        产品销售合同
                        甲方：星河智能装备有限公司。
                        乙方：云杉制造科技有限公司。
                        销售标的为自动缝纫机设备，合同总金额为 90000 元。
                        甲方应于验收后 30 日内支付尾款。
                        逾期付款按日承担违约金。
                        """)
        );

        DocumentSummaryResult result = service.summarize(file);

        assertTrue(result.success());
        assertEquals("产品销售合同", result.documentName());
        assertEquals("销售合同", result.documentType());
        assertEquals("ollama", result.source());
        assertTrue(result.contract());
        assertEquals("销售合同", result.contractType());
        assertTrue(result.summary().contains("自动缝纫机设备"));
        assertTrue(result.summaryFields().containsKey("合同主体"));
        assertTrue(result.summaryFields().get("合同主体").contains("星河智能"));
        assertTrue(result.summaryFields().get("付款/结算方式").contains("验收后 30 日"));
        assertEquals(5, result.structuredModules().size());
        assertEquals("subject_price", result.structuredModules().get(0).moduleCode());
        assertEquals("标的与价款", result.structuredModules().get(0).moduleName());
        assertEquals("销售标的", result.structuredModules().get(0).points().get(0).name());
        assertFalse(result.keyPoints().isEmpty());
        assertTrue(result.keyPoints().stream().anyMatch(point -> point.contains("销售标的") || point.contains("合同总金额")));
        assertTrue(result.contentLength() > 50);
        assertTrue(client.lastPrompt.contains("你是一名资深合同摘要助手"));
        assertTrue(client.lastPrompt.contains("先判断原文是否属于合同文本"));
        assertTrue(client.lastPrompt.contains("销售合同、采购合同、服务合同、租赁合同"));
        assertTrue(client.lastPrompt.contains("summary_fields"));
        assertTrue(client.lastPrompt.contains("structured_summary"));
        assertTrue(client.lastPrompt.contains("固定五个模块必须始终返回"));
        assertTrue(client.lastPrompt.contains("标的与价款"));
        assertTrue(client.lastPrompt.contains("履行方式"));
        assertTrue(client.lastPrompt.contains("争议解决"));
        assertTrue(client.lastPrompt.contains("不得编造、补全、猜测"));
        assertTrue(client.lastPrompt.contains("不输出隐藏思维链"));
    }

    @Test
    void summarizeShouldKeepPlainSummaryWhenModelReturnsNonContractJson() throws Exception {
        CapturingOllamaChatClient client = new CapturingOllamaChatClient(
                """
                {
                  "is_contract": false,
                  "summary_md": "该文档说明 PDF 和 DOCX 摘要提取流程，包含上传、解析、模型摘要和本地兜底。"
                }
                """
        );
        DocumentSummaryService service = new DocumentSummaryService(
                new DocumentQualityFileService(),
                client
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "摘要提取方案.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("""
                        摘要提取方案
                        当前功能需要支持 PDF 和 DOCX 文件的摘要提取。
                        上传文件后解析正文，调用摘要模型，失败时返回本地摘要兜底。
                        """)
        );

        DocumentSummaryResult result = service.summarize(file);

        assertTrue(result.success());
        assertFalse(result.contract());
        assertEquals("", result.contractType());
        assertEquals(Map.of(), result.summaryFields());
        assertTrue(result.structuredModules().isEmpty());
        assertTrue(result.summary().contains("PDF 和 DOCX"));
    }

    @Test
    void summarizeShouldFallbackToLocalSummaryWhenModelFailsForPdf() throws Exception {
        DocumentSummaryService service = new DocumentSummaryService(
                new DocumentQualityFileService(),
                new FailingOllamaChatClient()
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "api-plan.pdf",
                "application/pdf",
                pdfBytes("Project overview. The API interface supports PDF and DOCX summary extraction. Acceptance test covers upload, fallback summary, and report export.")
        );

        DocumentSummaryResult result = service.summarize(file);

        assertTrue(result.success());
        assertEquals("api-plan", result.documentName());
        assertEquals("local-heuristic", result.source());
        assertFalse(result.contract());
        assertTrue(result.summaryFields().isEmpty());
        assertTrue(result.structuredModules().isEmpty());
        assertTrue(result.message().contains("本地摘要"));
        assertTrue(result.summary().contains("API interface"));
        assertTrue(result.keywords().contains("PDF"));
        assertTrue(result.keywords().contains("DOCX"));
    }

    private byte[] docxBytes(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(wordDocumentXml(text).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private String wordDocumentXml(String text) {
        String paragraphs = text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> "<w:p><w:r><w:t>" + escapeXml(line) + "</w:t></w:r></w:p>")
                .reduce("", String::concat);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        %s
                    </w:body>
                </w:document>
                """.formatted(paragraphs);
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static final class FailingOllamaChatClient extends OllamaChatClient {
        private FailingOllamaChatClient() {
            super("http://localhost:11434", "qwen2.5", 500);
        }

        @Override
        public String generate(String prompt) throws Exception {
            throw new Exception("ollama unavailable");
        }
    }

    private static final class CapturingOllamaChatClient extends OllamaChatClient {
        private final String response;
        private String lastPrompt = "";

        private CapturingOllamaChatClient(String response) {
            super("http://localhost:11434", "qwen2.5", 500);
            this.response = response;
        }

        @Override
        public String generate(String prompt) {
            lastPrompt = prompt;
            return response;
        }
    }
}
