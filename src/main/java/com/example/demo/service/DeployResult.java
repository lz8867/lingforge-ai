package com.example.demo.service;

import java.util.HashMap;
import java.util.Map;

public record DeployResult(boolean success, String message, String output, String error) {

    public static DeployResult success(String message, String output) {
        return new DeployResult(true, message, output, null);
    }

    public static DeployResult failure(String message, String output) {
        return new DeployResult(false, message, output, null);
    }

    public static DeployResult failure(String message, String output, String error) {
        return new DeployResult(false, message, output, error);
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        if (output != null) {
            response.put("output", output);
        }
        if (error != null) {
            response.put("error", error);
        }
        return response;
    }
}
