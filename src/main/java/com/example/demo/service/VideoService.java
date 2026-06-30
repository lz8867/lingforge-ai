package com.example.demo.service;

import java.util.Map;

public interface VideoService {
    Map<String, Object> getVideoCapability();

    Map<String, Object> submitVideoTask(Map<String, Object> request);

    Map<String, Object> getVideoTaskStatus(String taskId);
}
