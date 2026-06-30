package com.example.demo.repository;

import com.example.demo.model.ModelEvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelEvaluationRunRepository extends JpaRepository<ModelEvaluationRun, Long> {

    List<ModelEvaluationRun> findAllByOrderByCreatedAtDesc();

    List<ModelEvaluationRun> findBySceneOrderByCreatedAtDesc(String scene);

    List<ModelEvaluationRun> findByModelOrderByCreatedAtDesc(String model);

    List<ModelEvaluationRun> findBySceneAndModelOrderByCreatedAtDesc(String scene, String model);

    List<ModelEvaluationRun> findByProviderOrderByCreatedAtDesc(String provider);

    List<ModelEvaluationRun> findBySceneAndProviderOrderByCreatedAtDesc(String scene, String provider);

    List<ModelEvaluationRun> findByModelAndProviderOrderByCreatedAtDesc(String model, String provider);

    List<ModelEvaluationRun> findBySceneAndModelAndProviderOrderByCreatedAtDesc(
            String scene,
            String model,
            String provider);
}
