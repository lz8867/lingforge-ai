package com.example.demo.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ImageService {
    Map<String, Object> generateImage(Map<String, Object> request);

    ResponseEntity<Resource> downloadImage(String fileName);

    Map<String, Object> describeImage(MultipartFile image, String type, String language);
}
