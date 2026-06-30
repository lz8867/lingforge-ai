package com.example.demo.service;

import com.example.demo.DTO.MemorySearchResult;
import com.example.demo.model.UserMemory;

import java.util.List;

public interface MemoryService {

    UserMemory saveMemory(String userId, String content, String type, String metadata);

    List<UserMemory> getUserMemories(String userId);

    List<UserMemory> getUserMemoriesByType(String userId, String type);

    MemorySearchResult searchUserMemories(String userId, String type, String keyword, int page, int size);

    void deleteMemory(Long memoryId);

    void deleteUserMemories(String userId);

    String buildMemoryContext(String userId);
}
