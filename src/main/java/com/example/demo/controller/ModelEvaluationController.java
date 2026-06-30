package com.example.demo.controller;

import com.example.demo.service.ModelEvaluationFacets;
import com.example.demo.service.ModelEvaluationRunRecord;
import com.example.demo.service.ModelEvaluationService;
import com.example.demo.service.ModelEvaluationSummary;
import com.example.demo.service.ModelEvaluationTrend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/model-evaluation")
public class ModelEvaluationController {

    private static final Logger log = LoggerFactory.getLogger(ModelEvaluationController.class);
    private final ModelEvaluationService modelEvaluationService;

    @Autowired
    public ModelEvaluationController(ModelEvaluationService modelEvaluationService) {
        this.modelEvaluationService = modelEvaluationService;
    }

    @GetMapping("/runs")
    public List<ModelEvaluationRunRecord> listRuns(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String requestSource,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("模型评测列表查询: scene={}, model={}, provider={}, requestSource={}, limit={}", scene, model, provider, requestSource, limit);
        return modelEvaluationService.listRuns(scene, model, provider, requestSource, limit);
    }

    @GetMapping("/facets")
    public ModelEvaluationFacets facets() {
        log.info("模型评测筛选维度查询");
        return modelEvaluationService.facets();
    }

    @GetMapping("/summary")
    public ModelEvaluationSummary summary(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String requestSource) {
        log.info("模型评测汇总查询: scene={}, model={}, provider={}, requestSource={}", scene, model, provider, requestSource);
        return modelEvaluationService.summary(scene, model, provider, requestSource);
    }

    @GetMapping("/trend")
    public ModelEvaluationTrend trend(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String requestSource,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "metric") String groupBy,
            @RequestParam(defaultValue = "avgF1") String metric) {
        log.info("模型评测趋势查询: scene={}, model={}, provider={}, requestSource={}, days={}, granularity={}, groupBy={}, metric={}",
                scene, model, provider, requestSource, days, granularity, groupBy, metric);
        return modelEvaluationService.trend(scene, model, provider, requestSource, days, granularity, groupBy, metric);
    }
}
