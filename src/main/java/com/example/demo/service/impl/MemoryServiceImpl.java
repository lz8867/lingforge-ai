package com.example.demo.service.impl;

import com.example.demo.DTO.MemorySearchResult;
import com.example.demo.model.UserMemory;
import com.example.demo.repository.UserMemoryRepository;
import com.example.demo.service.MemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class MemoryServiceImpl implements MemoryService {

    private final UserMemoryRepository memoryRepository;

    @Autowired
    public MemoryServiceImpl(UserMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Override
    public UserMemory saveMemory(String userId, String content, String type, String metadata) {
        UserMemory memory = new UserMemory(userId, content, type, metadata);
        return memoryRepository.save(memory);
    }

    @Override
    public List<UserMemory> getUserMemories(String userId) {
        return memoryRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()).getContent();
    }

    @Override
    public List<UserMemory> getUserMemoriesByType(String userId, String type) {
        return memoryRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, Pageable.unpaged()).getContent();
    }

    @Override
    public MemorySearchResult searchUserMemories(String userId, String type, String keyword, int page, int size) {
        String normalizedType = normalizeType(type);
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);

        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserMemory> pageResult;

        if (normalizedType == null && normalizedKeyword == null) {
            pageResult = memoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else if (normalizedType != null && normalizedKeyword == null) {
            pageResult = memoryRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, normalizedType, pageable);
        } else {
            pageResult = memoryRepository.searchByUserIdAndTypeAndKeyword(userId, normalizedType, normalizedKeyword, pageable);
        }

        MemorySearchResult result = new MemorySearchResult();
        result.setPage(pageResult.getNumber());
        result.setSize(pageResult.getSize());
        result.setTotalElements(pageResult.getTotalElements());
        result.setTotalPages(pageResult.getTotalPages());
        result.setHasMore(pageResult.hasNext());
        result.setMemories(pageResult.getContent());
        result.setSummary(buildSummaryCounts(userId));
        return result;
    }

    @Override
    public void deleteMemory(Long memoryId) {
        memoryRepository.deleteById(memoryId);
    }

    @Override
    public void deleteUserMemories(String userId) {
        memoryRepository.deleteByUserId(userId);
    }

    @Override
    public String buildMemoryContext(String userId) {
        List<UserMemory> memories = memoryRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);

        if (memories.isEmpty()) {
            return "";
        }

        return memories.stream()
                .limit(10)
                .map(memory -> String.format("[%s] %s", memory.getType(), memory.getContent()))
                .collect(Collectors.joining("\n"));
    }

    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.trim();
        if (normalized.isEmpty() || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizePage(int page) {
        return Math.max(0, page);
    }

    private int normalizeSize(int size) {
        return Math.min(100, Math.max(10, size));
    }

    private Map<String, Long> buildSummaryCounts(String userId) {
        Map<String, Long> summary = new HashMap<>();
        summary.put("total", memoryRepository.countByUserId(userId));
        summary.put("user_input", memoryRepository.countByUserIdAndType(userId, "user_input"));
        summary.put("ai_response", memoryRepository.countByUserIdAndType(userId, "ai_response"));
        summary.put("prompt_template", memoryRepository.countByUserIdAndType(userId, "prompt_template"));
        summary.put("preference", memoryRepository.countByUserIdAndType(userId, "preference"));
        summary.put("context", memoryRepository.countByUserIdAndType(userId, "context"));
        return summary;
    }
}
