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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineruDocumentServiceTest {

    @Test
    void parseMarkdownShouldReturnMineruLikeStructuredBlocksAndMarkdown() {
        MineruDocumentService service = new MineruDocumentService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "AI能力运营手册.md",
                "text/markdown",
                """
                        # AI能力运营手册

                        目标：把 AI 操作沉淀成可检索知识。

                        | 模块 | 价值 |
                        | --- | --- |
                        | 文档解析 | 生成 Markdown |

                        - 上传业务文档
                        - 解析结构化片段

                        score = hit / total
                        """.getBytes(StandardCharsets.UTF_8)
        );

        MineruParseResult result = service.parse(file);

        assertTrue(result.success());
        assertEquals("AI能力运营手册", result.documentName());
        assertEquals("md", result.fileType());
        assertEquals("local-heuristic", result.sourceMode());
        assertTrue(result.wordCount() > 0);
        assertTrue(result.lineCount() >= 8);
        assertTrue(result.blockCount() >= 5);
        assertTrue(result.markdown().contains("# AI能力运营手册"));
        assertTrue(result.markdown().contains("| 模块 | 价值 |"));
        assertTrue(result.markdown().contains("- 上传业务文档"));
        assertTrue(hasBlockType(result.blocks(), "heading"));
        assertTrue(hasBlockType(result.blocks(), "table"));
        assertTrue(hasBlockType(result.blocks(), "list"));
        assertTrue(hasBlockType(result.blocks(), "formula"));
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void parseDocxShouldExtractParagraphsAsMarkdownBlocks() throws Exception {
        MineruDocumentService service = new MineruDocumentService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "知识库导入规范.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("""
                        知识库导入规范
                        一级标题应该保留为结构块
                        - 每条知识必须有来源
                        |字段|说明|
                        |title|知识标题|
                        """)
        );

        MineruParseResult result = service.parse(file);

        assertTrue(result.success());
        assertEquals("知识库导入规范", result.documentName());
        assertEquals("docx", result.fileType());
        assertTrue(result.markdown().contains("知识库导入规范"));
        assertTrue(result.markdown().contains("- 每条知识必须有来源"));
        assertTrue(result.blocks().stream().anyMatch(block -> block.content().contains("知识标题")));
    }

    @Test
    void parsePdfShouldReportPageCountAndContentBlocks() throws Exception {
        MineruDocumentService service = new MineruDocumentService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "mineru-sample.pdf",
                "application/pdf",
                pdfBytes("MinerU sample document with API table and knowledge extraction")
        );

        MineruParseResult result = service.parse(file);

        assertTrue(result.success());
        assertEquals("mineru-sample", result.documentName());
        assertEquals("pdf", result.fileType());
        assertEquals(1, result.pageCount());
        assertTrue(result.markdown().contains("MinerU sample document"));
        assertTrue(result.blocks().stream().allMatch(block -> block.page() >= 1));
    }

    @Test
    void parseEmptyFileShouldReturnFailureWithoutThrowing() {
        MineruDocumentService service = new MineruDocumentService();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.md",
                "text/markdown",
                new byte[0]
        );

        MineruParseResult result = service.parse(file);

        assertFalse(result.success());
        assertEquals("empty", result.documentName());
        assertEquals(0, result.blockCount());
        assertTrue(result.message().contains("请选择"));
    }

    private boolean hasBlockType(List<MineruBlock> blocks, String type) {
        return blocks.stream().anyMatch(block -> type.equals(block.type()));
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
