package com.example.demo.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class MineruDocumentService {

    private static final Logger log = LoggerFactory.getLogger(MineruDocumentService.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+|[•·]\\s+).+");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+.+");
    private static final Pattern FORMULA_PATTERN = Pattern.compile(".*[A-Za-z0-9\\p{IsHan}]\\s*=\\s*.+");

    public MineruParseResult parse(MultipartFile file) {
        String originalFilename = file == null ? "" : defaultText(file.getOriginalFilename(), "");
        log.info("MinerU文档解析开始: filename={}, size={}",
                originalFilename,
                file == null ? 0 : file.getSize());

        String extension = normalizeFileType(extensionOf(originalFilename));
        if (file == null || file.isEmpty()) {
            log.warn("MinerU文档解析失败: 文件为空, filename={}", originalFilename);
            return failure("请选择需要解析的文档文件。", originalFilename, extension);
        }

        if (!isSupported(extension)) {
            log.warn("MinerU文档解析失败: 不支持的文件类型, filename={}, extension={}", originalFilename, extension);
            return failure("暂不支持该文件格式，请上传 PDF、DOCX、TXT、Markdown 或 HTML。", originalFilename, extension);
        }

        try {
            ExtractedDocument extracted = extract(file.getBytes(), extension);
            if (extracted.content().isBlank()) {
                log.warn("MinerU文档解析失败: 未解析到正文, filename={}, extension={}", originalFilename, extension);
                return failure("未从文件中解析到有效正文，请确认文件不是扫描图片或空文档。", originalFilename, extension);
            }

            List<MineruBlock> blocks = parseBlocks(extracted.content(), extracted.pageCount());
            String markdown = toMarkdown(blocks);
            List<String> warnings = buildWarnings(blocks, extension);
            MineruParseResult result = new MineruParseResult(
                    true,
                    "解析完成，已生成 Markdown、结构块和 JSON 结果。",
                    documentNameOf(originalFilename),
                    extension,
                    "local-heuristic",
                    extracted.pageCount(),
                    countWords(extracted.content()),
                    countLines(extracted.content()),
                    blocks.size(),
                    markdown,
                    blocks,
                    warnings
            );
            log.info("MinerU文档解析完成: filename={}, fileType={}, pages={}, blocks={}",
                    originalFilename,
                    extension,
                    result.pageCount(),
                    result.blockCount());
            return result;
        } catch (Exception e) {
            log.error("MinerU文档解析异常: filename={}, message={}", originalFilename, e.getMessage());
            return failure("文件解析失败：" + defaultText(e.getMessage(), "未知错误"), originalFilename, extension);
        }
    }

    private ExtractedDocument extract(byte[] bytes, String extension) throws Exception {
        return switch (extension) {
            case "docx" -> new ExtractedDocument(extractDocx(bytes), 1);
            case "txt", "md" -> new ExtractedDocument(normalizeText(new String(bytes, StandardCharsets.UTF_8)), 1);
            case "html" -> new ExtractedDocument(extractHtml(bytes), 1);
            case "pdf" -> extractPdf(bytes);
            default -> new ExtractedDocument("", 0);
        };
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
        appendWordNodeText(paragraph.getChildNodes(), text);
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

    private ExtractedDocument extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return new ExtractedDocument(normalizeText(stripper.getText(document)), document.getNumberOfPages());
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

    private List<MineruBlock> parseBlocks(String content, int pageCount) {
        List<String> lines = content.lines().toList();
        List<MineruBlock> blocks = new ArrayList<>();
        int order = 1;
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index).trim();
            if (line.isBlank()) {
                index++;
                continue;
            }

            String type = typeOf(line);
            StringBuilder blockContent = new StringBuilder(line);
            index++;
            if ("table".equals(type) || "list".equals(type)) {
                while (index < lines.size() && type.equals(typeOf(lines.get(index).trim()))) {
                    blockContent.append('\n').append(lines.get(index).trim());
                    index++;
                }
            } else if ("paragraph".equals(type)) {
                while (index < lines.size()) {
                    String nextLine = lines.get(index).trim();
                    if (nextLine.isBlank() || !"paragraph".equals(typeOf(nextLine))) {
                        break;
                    }
                    blockContent.append('\n').append(nextLine);
                    index++;
                }
            }

            String safeContent = blockContent.toString().trim();
            blocks.add(new MineruBlock(
                    "B" + String.format(Locale.ROOT, "%03d", order),
                    type,
                    titleOf(type, safeContent),
                    safeContent,
                    pageFor(order, Math.max(1, pageCount), lines.size()),
                    order,
                    confidenceOf(type)
            ));
            order++;
        }
        return blocks;
    }

    private String typeOf(String line) {
        if (line.isBlank()) {
            return "blank";
        }
        if (HEADING_PATTERN.matcher(line).matches() || looksLikeTitle(line)) {
            return "heading";
        }
        if (isTableLine(line)) {
            return "table";
        }
        if (LIST_PATTERN.matcher(line).matches()) {
            return "list";
        }
        if (isFormulaLine(line)) {
            return "formula";
        }
        return "paragraph";
    }

    private boolean looksLikeTitle(String line) {
        return line.length() <= 32
                && !line.endsWith("。")
                && !line.endsWith(".")
                && !line.endsWith("：")
                && !line.endsWith(":")
                && !line.contains("|")
                && !LIST_PATTERN.matcher(line).matches()
                && !isFormulaLine(line);
    }

    private boolean isTableLine(String line) {
        return line.contains("|") && line.chars().filter(character -> character == '|').count() >= 2;
    }

    private boolean isFormulaLine(String line) {
        return FORMULA_PATTERN.matcher(line).matches()
                || line.contains("\\(")
                || line.contains("\\[")
                || line.contains("∑")
                || line.contains("√");
    }

    private String titleOf(String type, String content) {
        String firstLine = content.lines().findFirst().orElse(content).trim();
        if ("heading".equals(type)) {
            firstLine = firstLine.replaceFirst("^#{1,6}\\s+", "");
        }
        return firstLine.length() <= 28 ? firstLine : firstLine.substring(0, 28) + "...";
    }

    private int pageFor(int order, int pageCount, int lineCount) {
        if (pageCount <= 1 || lineCount <= 0) {
            return 1;
        }
        int roughPage = (int) Math.ceil((double) order / Math.max(1, lineCount) * pageCount);
        return Math.max(1, Math.min(pageCount, roughPage));
    }

    private double confidenceOf(String type) {
        return switch (type) {
            case "heading" -> 0.96d;
            case "table" -> 0.9d;
            case "list" -> 0.88d;
            case "formula" -> 0.84d;
            default -> 0.78d;
        };
    }

    private String toMarkdown(List<MineruBlock> blocks) {
        StringBuilder markdown = new StringBuilder();
        for (MineruBlock block : blocks) {
            if (!markdown.isEmpty()) {
                markdown.append("\n\n");
            }
            if ("heading".equals(block.type()) && !block.content().startsWith("#")) {
                markdown.append("## ").append(block.content());
            } else {
                markdown.append(block.content());
            }
        }
        return markdown.toString().trim();
    }

    private List<String> buildWarnings(List<MineruBlock> blocks, String extension) {
        List<String> warnings = new ArrayList<>();
        warnings.add("当前版本使用本地启发式解析，未调用 GPU 版 MinerU 模型。");
        warnings.add("图片、公式截图和复杂版面会保留为文本近似结果。");
        if ("pdf".equals(extension) && blocks.stream().noneMatch(block -> "table".equals(block.type()))) {
            warnings.add("PDF 中未识别到表格结构，可能是扫描件或版面文本不足。");
        }
        return warnings;
    }

    private int countWords(String content) {
        int cjkCount = 0;
        int latinCount = 0;
        boolean inLatin = false;
        for (int i = 0; i < content.length(); i++) {
            char character = content.charAt(i);
            if (Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN) {
                cjkCount++;
                inLatin = false;
            } else if (Character.isLetterOrDigit(character)) {
                if (!inLatin) {
                    latinCount++;
                    inLatin = true;
                }
            } else {
                inLatin = false;
            }
        }
        return cjkCount + latinCount;
    }

    private int countLines(String content) {
        return (int) content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .count();
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

    private boolean isSupported(String extension) {
        return switch (extension) {
            case "pdf", "docx", "txt", "md", "html" -> true;
            default -> false;
        };
    }

    private String normalizeFileType(String extension) {
        return switch (extension) {
            case "markdown" -> "md";
            case "htm" -> "html";
            default -> extension;
        };
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

    private MineruParseResult failure(String message, String filename, String extension) {
        return new MineruParseResult(
                false,
                message,
                documentNameOf(filename),
                defaultText(extension, "unknown"),
                "local-heuristic",
                0,
                0,
                0,
                0,
                "",
                List.of(),
                List.of(message)
        );
    }

    private String defaultText(String value, String fallback) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .orElse(fallback);
    }

    private record ExtractedDocument(String content, int pageCount) {
        private ExtractedDocument {
            content = content == null ? "" : content;
            pageCount = Math.max(1, pageCount);
        }
    }
}
