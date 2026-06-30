package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEvaluationPageTest {

    @Test
    void modelEvaluationPageShouldExplainEmptyDataAndRenderBackendMetricKeys() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/model-evaluation.html"));

        assertTrue(page.contains("先在 Prompt 优化器、RAG 或聊天页面触发一次运行"));
        assertTrue(page.contains("检查 Ollama / 模型服务是否在线"));
        assertTrue(page.contains("getModelEvaluationMetric"));
        assertTrue(page.contains("['Recall', 'R']"));
        assertTrue(page.contains("['Precision', 'P']"));
        assertTrue(page.contains("['F1', 'F1']"));
        assertTrue(page.contains("['Top1', 'T1']"));
        assertTrue(page.contains("['Top3', 'T3']"));
        assertTrue(page.contains("['Top5', 'T5']"));
        assertFalse(page.contains("['recall', 'R']"));
    }

    @Test
    void modelEvaluationPageShouldExplainAllFailedTrendAndExposeRunErrors() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/model-evaluation.html"));

        assertTrue(page.contains("hasOnlyZeroModelEvaluationTrend"));
        assertTrue(page.contains("drawTrendMutedState"));
        assertTrue(page.contains("当前筛选下评测均失败，暂无有效质量曲线"));
        assertTrue(page.contains("formatModelEvaluationError"));
        assertTrue(page.contains("run.errorMessage"));
        assertTrue(page.contains("const errorText = formatModelEvaluationError(run.errorMessage);"));
        assertTrue(page.contains("title=\"${escapeHtml(errorText)}\""));
        assertTrue(page.contains("model-evaluation-run-error"));
    }

    @Test
    void modelEvaluationPageShouldShowActionableFailureDiagnosis() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/model-evaluation.html"));

        assertTrue(page.contains("id=\"modelEvaluationFailureDiagnosis\""));
        assertTrue(page.contains("model-evaluation-failure-diagnosis"));
        assertTrue(page.contains("renderModelEvaluationFailureDiagnosis"));
        assertTrue(page.contains("diagnoseModelEvaluationFailure"));
        assertTrue(page.contains("const latestRun = list[0];"));
        assertTrue(page.contains("if (!latestRun || latestRun.success)"));
        assertTrue(page.contains("最近失败原因"));
        assertTrue(page.contains("Ollama 响应超时"));
        assertTrue(page.contains("OLLAMA_TIMEOUT_MS"));
        assertTrue(page.contains("OLLAMA_NUM_PREDICT"));
        assertTrue(page.contains("60000"));
    }

    @Test
    void modelEvaluationPageShouldDefaultCompareLatestModelAcrossScenes() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/model-evaluation.html"));

        assertTrue(page.contains("initializeModelEvaluationDefaultScope"));
        assertTrue(page.contains("modelEvaluationScopeInitialized"));
        assertTrue(page.contains("fetch('/api/model-evaluation/runs?limit=1')"));
        assertTrue(page.contains("id=\"modelEvaluationGroupByFilter\""));
        assertTrue(page.contains("setModelEvaluationFilterValue('modelEvaluationModelFilter', latestRun.model)"));
        assertFalse(page.contains("setModelEvaluationFilterValue('modelEvaluationSceneFilter', latestRun.scene)"));
        assertFalse(page.contains("setModelEvaluationFilterValue('modelEvaluationProviderFilter', latestRun.provider)"));
    }

    @Test
    void modelEvaluationPageShouldExposeModelFacetsAndTrendGranularity() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/model-evaluation.html"));

        assertTrue(page.contains("loadModelEvaluationFacets"));
        assertTrue(page.contains("fetch('/api/model-evaluation/facets')"));
        assertTrue(page.contains("list=\"modelEvaluationModelOptions\""));
        assertTrue(page.contains("id=\"modelEvaluationModelOptions\""));
        assertTrue(page.contains("id=\"modelEvaluationGranularityFilter\""));
        assertTrue(page.contains("id=\"modelEvaluationGroupByFilter\""));
        assertTrue(page.contains("value=\"scene\">按场景"));
        assertTrue(page.contains("value=\"model\">按模型"));
        assertTrue(page.contains("value=\"metric\">按指标"));
        assertTrue(page.contains("id=\"modelEvaluationTrendMetricFilter\""));
        assertTrue(page.contains("value=\"day\">按天"));
        assertTrue(page.contains("value=\"hour\">按小时"));
        assertTrue(page.contains("trendParams.set('granularity'"));
        assertTrue(page.contains("trendParams.set('groupBy'"));
        assertTrue(page.contains("trendParams.set('metric'"));
        assertTrue(page.contains("getModelEvaluationTrendSeries"));
        assertTrue(page.contains("renderModelEvaluationLegend"));
        assertTrue(page.contains("drawTrendSeriesLine"));
        assertTrue(page.contains("formatModelEvaluationTrendLabel"));
        assertTrue(page.contains("hasNoModelEvaluationTrendRuns"));
        assertTrue(page.contains("Number(point.totalRuns || 0) <= 0"));
    }

    @Test
    void modelEvaluationPageShouldOverrideAppShellHeightsForCompleteDesktopViewport() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/model-evaluation.html"));

        assertTrue(page.contains("body[data-app-page=\"model-evaluation\"] main.model-evaluation-workbench.app-workbench"));
        assertTrue(page.contains("main.model-evaluation-workbench > .app-content-shell"));
        assertTrue(page.contains("grid-template-rows: auto auto minmax(0, 1fr) !important"));
        assertTrue(page.contains(".model-evaluation-command.app-content-shell"));
        assertTrue(page.contains(".model-evaluation-summary-strip.app-content-shell"));
        assertTrue(page.contains(".model-evaluation-main-grid.app-content-shell"));
        assertTrue(page.contains("grid-template-columns: repeat(2, minmax(0, 1fr))"));
    }
}
