package com.example.demo.repository;

import com.example.demo.model.DocumentQualityEvaluationRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentQualityEvaluationRecordRepository extends JpaRepository<DocumentQualityEvaluationRecord, Long> {

    List<DocumentQualityEvaluationRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<DocumentQualityEvaluationRecord> findByDocumentNameOrderByCreatedAtAsc(String documentName);

    List<DocumentQualityEvaluationRecord> findByDocumentTypeOrderByCreatedAtAsc(String documentType);
}
