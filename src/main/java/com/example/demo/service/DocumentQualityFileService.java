package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentQualityFileService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQualityFileService.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");

    public DocumentQualityExtractionResult extract(MultipartFile file) {
        String originalFilename = file == null ? "" : defaultText(file.getOriginalFilename(), "");
        log.info("文档质量文件解析开始: filename={}, size={}",
                originalFilename,
                file == null ? 0 : file.getSize());

        if (file == null || file.isEmpty()) {
            log.warn("文档质量文件解析失败: 文件为空");
            return failure("请选择需要评测的文档文件。", originalFilename);
        }

        String extension = extensionOf(originalFilename);
        try {
            byte[] bytes = file.getBytes();
            String content = switch (extension) {
                case "docx" -> extractDocx(bytes);
                case "txt", "md", "markdown" -> normalizeText(new String(bytes, StandardCharsets.UTF_8));
                case "html", "htm" -> extractHtml(bytes);
                case "pdf" -> extractPdf(bytes);
                default -> "";
            };

            if (content.isBlank()) {
                log.warn("文档质量文件解析失败: 未解析到正文, filename={}, extension={}", originalFilename, extension);
                return failure("未从文件中解析到有效正文，请确认文件格式或内容。", originalFilename);
            }

            String documentName = documentNameOf(originalFilename);
            DocumentQualityExtractionResult result = new DocumentQualityExtractionResult(
                    true,
                    "已读取文件内容，可直接进行质量评测。",
                    documentName,
                    inferDocumentType(documentName, content),
                    content
            );
            log.info("文档质量文件解析完成: filename={}, contentLength={}", originalFilename, result.content().length());
            return result;
        } catch (Exception e) {
            log.error("文档质量文件解析异常: filename={}, message={}", originalFilename, e.getMessage());
            return failure("文件解析失败：" + defaultText(e.getMessage(), "未知错误"), originalFilename);
        }
    }

    private String extractDocx(byte[] bytes) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return extractWordDocumentXml(readAll(zip));
                }
            }
        }
        return "";
    }

    private String extractWordDocumentXml(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xmlBytes)));
        NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < paragraphs.getLength(); i++) {
            String paragraph = paragraphText(paragraphs.item(i)).trim();
            if (!paragraph.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(paragraph);
            }
        }
        return normalizeText(text.toString());
    }

    private String paragraphText(Node paragraph) {
        StringBuilder text = new StringBuilder();
        NodeList children = paragraph.getChildNodes();
        appendWordNodeText(children, text);
        return text.toString();
    }

    private void appendWordNodeText(NodeList nodes, StringBuilder text) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String localName = node.getLocalName();
            if ("t".equals(localName)) {
                text.append(node.getTextContent());
            } else if ("tab".equals(localName)) {
                text.append('\t');
            } else if ("br".equals(localName) || "cr".equals(localName)) {
                text.append('\n');
            } else if (node.hasChildNodes()) {
                appendWordNodeText(node.getChildNodes(), text);
            }
        }
    }

    private String extractHtml(byte[] bytes) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        String withoutScripts = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll(" ");
        String text = HTML_TAG_PATTERN.matcher(withoutScripts).replaceAll("\n");
        return normalizeText(htmlDecode(text));
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return normalizeText(stripper.getText(document));
        }
    }

    private byte[] readAll(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String normalizeText(String content) {
        String normalized = Normalizer.normalize(defaultText(content, ""), Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        StringBuilder result = new StringBuilder();
        boolean previousBlank = false;
        for (String line : normalized.lines().map(String::trim).toList()) {
            if (line.isBlank()) {
                if (!previousBlank && !result.isEmpty()) {
                    result.append('\n');
                    previousBlank = true;
                }
                continue;
            }
            if (!result.isEmpty() && !previousBlank) {
                result.append('\n');
            }
            result.append(line);
            previousBlank = false;
        }
        return result.toString().trim();
    }

    private String htmlDecode(String text) {
        return defaultText(text, "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private String inferDocumentType(String documentName, String content) {
        String joined = (defaultText(documentName, "") + "\n" + defaultText(content, "")).toLowerCase(Locale.ROOT);
        if (containsAny(joined, "接口", "api", "架构", "数据模型", "设计", "技术方案")) {
            return "技术设计文档";
        }
        if (containsAny(joined, "操作", "步骤", "sop", "手册")) {
            return "SOP操作文档";
        }
        if (containsAny(joined, "知识库", "faq", "问答")) {
            return "知识库文档";
        }
        if (containsAny(joined, "ai生成", "prompt", "llm")) {
            return "AI生成文档";
        }
        return "业务说明文档";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String documentNameOf(String filename) {
        String safeFilename = defaultText(filename, "未命名文档");
        int slashIndex = Math.max(safeFilename.lastIndexOf('/'), safeFilename.lastIndexOf('\\'));
        String name = slashIndex >= 0 ? safeFilename.substring(slashIndex + 1) : safeFilename;
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    private String extensionOf(String filename) {
        String safeFilename = defaultText(filename, "");
        int dotIndex = safeFilename.lastIndexOf('.');
        return dotIndex < 0 ? "" : safeFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private DocumentQualityExtractionResult failure(String message, String filename) {
        return new DocumentQualityExtractionResult(
                false,
                message,
                documentNameOf(filename),
                "通用文档",
                ""
        );
    }

    private String defaultText(String value, String fallback) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .orElse(fallback);
    }
}
