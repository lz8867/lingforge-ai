package com.example.demo.controller;

import com.example.demo.service.MineruDocumentService;
import com.example.demo.service.MineruParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineruControllerTest {

    @Test
    void parseShouldDelegateMultipartFileToDocumentService() {
        MineruController controller = new MineruController(new MineruDocumentService());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.md",
                "text/markdown",
                "# Demo\n\n- upload\n- extract".getBytes(StandardCharsets.UTF_8)
        );

        MineruParseResult result = controller.parse(file);

        assertTrue(result.success());
        assertEquals("demo", result.documentName());
        assertTrue(result.markdown().contains("# Demo"));
        assertTrue(result.blocks().stream().anyMatch(block -> "list".equals(block.type())));
    }
}
