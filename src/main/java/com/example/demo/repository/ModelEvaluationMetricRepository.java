package com.example.demo.repository;

import com.example.demo.model.ModelEvaluationMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelEvaluationMetricRepository extends JpaRepository<ModelEvaluationMetric, Long> {

    List<ModelEvaluationMetric> findByRunIdIn(List<Long> runId);
}
