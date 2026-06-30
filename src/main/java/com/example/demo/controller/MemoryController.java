package com.example.demo.controller;

import com.example.demo.DTO.MemoryRequest;
import com.example.demo.DTO.MemorySearchResult;
import com.example.demo.model.UserMemory;
import com.example.demo.service.MemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    @Autowired
    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/save")
    public ResponseEntity<UserMemory> saveMemory(@RequestBody MemoryRequest request) {
        UserMemory memory = memoryService.saveMemory(
            request.getUserId(),
            request.getContent(),
            request.getType(),
            request.getMetadata()
        );
        return ResponseEntity.ok(memory);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserMemory>> getUserMemories(@PathVariable String userId) {
        List<UserMemory> memories = memoryService.getUserMemories(userId);
        return ResponseEntity.ok(memories);
    }

    @GetMapping("/user/{userId}/search")
    public ResponseEntity<Map<String, Object>> searchUserMemories(
            @PathVariable String userId,
            @RequestParam(name = "type", defaultValue = "all") String type,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "24") int size
    ) {
        MemorySearchResult searchResult = memoryService.searchUserMemories(userId, type, keyword, page, size);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", searchResult.getPage());
        response.put("size", searchResult.getSize());
        response.put("total", searchResult.getTotalElements());
        response.put("totalPages", searchResult.getTotalPages());
        response.put("hasMore", searchResult.isHasMore());
        response.put("memories", searchResult.getMemories());
        response.put("summary", searchResult.getSummary());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/type/{type}")
    public ResponseEntity<List<UserMemory>> getUserMemoriesByType(
            @PathVariable String userId,
            @PathVariable String type) {
        List<UserMemory> memories = memoryService.getUserMemoriesByType(userId, type);
        return ResponseEntity.ok(memories);
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> deleteMemory(@PathVariable Long memoryId) {
        memoryService.deleteMemory(memoryId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteUserMemories(@PathVariable String userId) {
        memoryService.deleteUserMemories(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/context/{userId}")
    public ResponseEntity<Map<String, String>> getMemoryContext(@PathVariable String userId) {
        String context = memoryService.buildMemoryContext(userId);
        Map<String, String> response = new HashMap<>();
        response.put("context", context);
        return ResponseEntity.ok(response);
    }
}
