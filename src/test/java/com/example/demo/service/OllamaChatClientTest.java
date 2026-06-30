package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OllamaChatClientTest {

    @Test
    void generateUsesConfiguredBaseUrlAndModel() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OllamaChatClient client = new OllamaChatClient(restTemplate, "http://localhost:11434/", "qwen2.5");

        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model": "qwen2.5",
                          "prompt": "你是谁",
                          "stream": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "response": "我是本地 Ollama 助手"
                        }
                        """, MediaType.APPLICATION_JSON));

        String response = client.generate("你是谁");

        assertEquals("我是本地 Ollama 助手", response);
        server.verify();
    }

    @Test
    void generateShouldSendConfiguredRuntimeOptions() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OllamaChatClient client = new OllamaChatClient(
                restTemplate,
                "http://localhost:11434",
                "qwen2.5",
                Map.of("num_predict", 512)
        );

        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model": "qwen2.5",
                          "prompt": "评测 Prompt",
                          "stream": false,
                          "options": {
                            "num_predict": 512
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "response": "{\\"overallScore\\":80}"
                        }
                        """, MediaType.APPLICATION_JSON));

        String response = client.generate("评测 Prompt");

        assertEquals("{\"overallScore\":80}", response);
        server.verify();
    }

    @Test
    void generateJsonShouldRequestOllamaJsonModeWithLargerPredictionBudget() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OllamaChatClient client = new OllamaChatClient(
                restTemplate,
                "http://localhost:11434",
                "qwen2.5",
                Map.of("num_predict", 512)
        );

        server.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model": "qwen2.5",
                          "prompt": "评测 Prompt",
                          "stream": false,
                          "format": "json",
                          "options": {
                            "num_predict": 2048
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "response": "{\\"overallScore\\":80}"
                        }
                        """, MediaType.APPLICATION_JSON));

        String response = client.generateJson("评测 Prompt", 2048);

        assertEquals("{\"overallScore\":80}", response);
        server.verify();
    }

    @Test
    void defaultTimeoutShouldAllowColdLocalModelStartupAndPromptEvaluation() throws Exception {
        String clientSource = Files.readString(Path.of("src/main/java/com/example/demo/service/OllamaChatClient.java"));
        String applicationConfig = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(clientSource.contains("spring.ai.ollama.timeout-ms:60000"));
        assertTrue(applicationConfig.contains("timeout-ms: ${OLLAMA_TIMEOUT_MS:60000}"));
        assertTrue(applicationConfig.contains("num-predict: ${OLLAMA_NUM_PREDICT:512}"));
    }
}
