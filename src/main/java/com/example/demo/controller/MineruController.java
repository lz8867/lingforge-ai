package com.example.demo.controller;

import com.example.demo.service.MineruDocumentService;
import com.example.demo.service.MineruParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mineru")
public class MineruController {

    private static final Logger log = LoggerFactory.getLogger(MineruController.class);

    private final MineruDocumentService mineruDocumentService;

    public MineruController(MineruDocumentService mineruDocumentService) {
        this.mineruDocumentService = mineruDocumentService;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MineruParseResult parse(@RequestPart("file") MultipartFile file) {
        log.info("收到MinerU文档解析请求: filename={}", file == null ? "" : file.getOriginalFilename());
        return mineruDocumentService.parse(file);
    }
}
