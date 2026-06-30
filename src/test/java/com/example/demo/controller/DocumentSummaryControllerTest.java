package com.example.demo.controller;

import com.example.demo.service.DocumentQualityFileService;
import com.example.demo.service.DocumentSummaryResult;
import com.example.demo.service.DocumentSummaryService;
import com.example.demo.service.StubOllamaChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSummaryControllerTest {

    @Test
    void summarizeEndpointShouldReturnUploadedDocumentSummary() throws Exception {
        DocumentQualityFileService fileService = new DocumentQualityFileService();
        DocumentSummaryController controller = new DocumentSummaryController(
                new DocumentSummaryService(
                        fileService,
                        new StubOllamaChatClient("""
                                {
                                  "is_contract": false,
                                  "summary_md": "上传文档说明了 PDF 和 DOCX 摘要提取、核心要点和关键词展示流程。"
                                }
                                """)
                )
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "摘要提取方案.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("""
                        # 概述
                        当前功能需要支持 PDF 和 DOCX 文件的摘要提取。
                        # 处理流程
                        上传文件后解析正文，调用摘要模型，失败时返回本地摘要兜底。
                        """)
        );

        DocumentSummaryResult result = controller.summarize(file);

        assertTrue(result.success());
        assertTrue(result.summary().contains("PDF 和 DOCX"));
        assertTrue(result.summaryFields().isEmpty());
        assertTrue(result.structuredModules().isEmpty());
        assertEquals("ollama", result.source());
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
}
