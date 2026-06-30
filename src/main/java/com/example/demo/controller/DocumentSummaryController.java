package com.example.demo.controller;

import com.example.demo.service.DocumentSummaryResult;
import com.example.demo.service.DocumentSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document-summary")
public class DocumentSummaryController {

    private static final Logger log = LoggerFactory.getLogger(DocumentSummaryController.class);

    private final DocumentSummaryService documentSummaryService;

    public DocumentSummaryController(DocumentSummaryService documentSummaryService) {
        this.documentSummaryService = documentSummaryService;
    }

    @PostMapping(value = "/summarize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentSummaryResult summarize(@RequestPart("file") MultipartFile file) {
        log.info("收到文档摘要提取请求: filename={}", file == null ? "" : file.getOriginalFilename());
        return documentSummaryService.summarize(file);
    }
}
