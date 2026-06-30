package com.example.demo.service;

import org.springframework.web.client.RestTemplate;

public class StubOllamaChatClient extends OllamaChatClient {

    private final String response;

    public StubOllamaChatClient(String response) {
        super(new RestTemplate(), "http://localhost:11434", "qwen2.5");
        this.response = response;
    }

    @Override
    public String generate(String prompt) {
        return response;
    }

    @Override
    public String generateJson(String prompt, int numPredict) {
        return response;
    }
}
