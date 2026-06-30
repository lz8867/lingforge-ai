package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQualityFileServiceTest {

    @Test
    void extractShouldReadDocxFileContent() throws Exception {
        DocumentQualityFileService service = new DocumentQualityFileService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "文档质量智能评测系统设计文档.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("""
                        文档质量智能评测系统设计文档
                        规则引擎 + LLM-Judge + 量化指标 + 可视化报告
                        数据模型、API 接口、测试验收
                        """)
        );

        DocumentQualityExtractionResult result = service.extract(file);

        assertTrue(result.success());
        assertEquals("文档质量智能评测系统设计文档", result.documentName());
        assertEquals("技术设计文档", result.documentType());
        assertTrue(result.content().contains("规则引擎"));
        assertTrue(result.content().contains("测试验收"));
    }

    @Test
    void extractShouldReadMarkdownAndInferDocumentType() {
        DocumentQualityFileService service = new DocumentQualityFileService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "操作手册.md",
                "text/markdown",
                "# 概述\n## 操作步骤\n第一步进入系统。".getBytes(StandardCharsets.UTF_8)
        );

        DocumentQualityExtractionResult result = service.extract(file);

        assertTrue(result.success());
        assertEquals("操作手册", result.documentName());
        assertEquals("SOP操作文档", result.documentType());
        assertTrue(result.content().contains("操作步骤"));
    }

    @Test
    void extractShouldReadPdfFileContent() throws Exception {
        DocumentQualityFileService service = new DocumentQualityFileService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "方案.pdf",
                "application/pdf",
                pdfBytes("PDF quality report with API interface and acceptance test")
        );

        DocumentQualityExtractionResult result = service.extract(file);

        assertTrue(result.success());
        assertEquals("方案", result.documentName());
        assertTrue(result.content().contains("PDF quality report"));
        assertTrue(result.content().contains("acceptance test"));
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
}
