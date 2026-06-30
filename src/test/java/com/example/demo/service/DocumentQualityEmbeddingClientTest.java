package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class DocumentQualityEmbeddingClientTest {

    @Test
    void embedShouldCallOllamaEmbeddingEndpointAndReturnModelVector() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DocumentQualityEmbeddingClient client = new DocumentQualityEmbeddingClient(
                restTemplate,
                "ollama",
                "http://localhost:11434",
                "nomic-embed-text",
                "",
                "text-embedding-3-small",
                ""
        );

        server.expect(requestTo("http://localhost:11434/api/embeddings"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model": "nomic-embed-text",
                          "prompt": "接口契约"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "embedding": [0.1, 0.2, 0.3]
                        }
                        """, MediaType.APPLICATION_JSON));

        DocumentQualityEmbeddingClient.EmbeddingResult result = client.embed("接口契约");

        assertFalse(result.fallback());
        assertEquals("ollama:nomic-embed-text", result.source());
        assertEquals(3, result.vector().length);
        assertEquals(0.2, result.vector()[1], 0.0001);
        server.verify();
    }

    @Test
    void embedShouldFallBackToLocalVectorWhenModelEndpointFails() {
        DocumentQualityEmbeddingClient client = DocumentQualityEmbeddingClient.localOnly();

        DocumentQualityEmbeddingClient.EmbeddingResult result = client.embed("接口契约");

        assertTrue(result.fallback());
        assertEquals("local-hash", result.source());
        assertTrue(result.vector().length > 0);
    }

    @Test
    void embedShouldShortCircuitExternalProviderAfterFailure() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DocumentQualityEmbeddingClient client = new DocumentQualityEmbeddingClient(
                restTemplate,
                "ollama",
                "http://localhost:11434",
                "nomic-embed-text",
                "",
                "text-embedding-3-small",
                ""
        );

        server.expect(requestTo("http://localhost:11434/api/embeddings"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        DocumentQualityEmbeddingClient.EmbeddingResult first = client.embed("接口契约");
        DocumentQualityEmbeddingClient.EmbeddingResult second = client.embed("验收标准");

        assertTrue(first.fallback());
        assertTrue(second.fallback());
        assertEquals("local-hash", second.source());
        assertTrue(second.message().contains("暂不可用"));
        server.verify();
    }

    @Test
    void embedShouldCallOpenAiEmbeddingEndpointWhenProviderIsOpenAi() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        DocumentQualityEmbeddingClient client = new DocumentQualityEmbeddingClient(
                restTemplate,
                "openai",
                "http://localhost:11434",
                "nomic-embed-text",
                "https://api.openai.test/v1/",
                "text-embedding-3-small",
                "test-key"
        );

        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model": "text-embedding-3-small",
                          "input": "验收标准"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "embedding": [0.4, 0.5]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DocumentQualityEmbeddingClient.EmbeddingResult result = client.embed("验收标准");

        assertFalse(result.fallback());
        assertEquals("openai:text-embedding-3-small", result.source());
        assertEquals(2, result.vector().length);
        server.verify();
    }
}
