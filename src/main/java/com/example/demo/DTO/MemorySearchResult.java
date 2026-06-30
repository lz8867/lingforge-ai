package com.example.demo.DTO;

import com.example.demo.model.UserMemory;

import java.util.Map;
import java.util.List;

public class MemorySearchResult {

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasMore;
    private List<UserMemory> memories;
    private Map<String, Long> summary;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public List<UserMemory> getMemories() {
        return memories;
    }

    public void setMemories(List<UserMemory> memories) {
        this.memories = memories;
    }

    public Map<String, Long> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Long> summary) {
        this.summary = summary;
    }
}
