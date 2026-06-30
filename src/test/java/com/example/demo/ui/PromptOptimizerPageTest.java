package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptOptimizerPageTest {

    @Test
    void promptOptimizerPageShouldCoverCorePromptEngineeringSkills() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));

        assertTrue(page.contains("客户 Prompt 输入"));
        assertTrue(page.contains("评测 Prompt"));
        assertTrue(page.contains("评分卡"));
        assertTrue(page.contains("基于短板优化"));
        assertTrue(page.contains("Few-shot"));
        assertTrue(page.contains("推理策略安全"));
        assertTrue(page.contains("输出格式契约"));
        assertTrue(page.contains("Prompt 版本管理"));
    }

    @Test
    void promptOptimizerPageShouldHideInternalAdvancedFormsFromCustomerWorkflow() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));

        assertFalse(page.contains("2. 可选评测上下文"));
        assertFalse(page.contains("3. 高级结构参考"));
        assertFalse(page.contains("Prompt 名称"));
        assertFalse(page.contains("角色设定"));
        assertFalse(page.contains("Chain of Thought 策略"));
        assertFalse(page.contains("JSON Schema / 字段约定"));
        assertFalse(page.contains("同一评估集评分"));
        assertFalse(page.contains("评测目标"));
        assertFalse(page.contains("期望输出形态"));
        assertFalse(page.contains("优化流程"));
        assertFalse(page.contains("客户手动输入完整 Prompt"));
    }

    @Test
    void promptOptimizerPageShouldSupportIterativeOptimizationWorkflow() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));

        assertTrue(page.contains("generatePrompt"));
        assertTrue(page.contains("evaluatePrompt"));
        assertTrue(page.contains("renderEvaluation"));
        assertTrue(page.contains("optimizePrompt"));
        assertTrue(page.contains("fetch('/prompt/evaluate'"));
        assertTrue(page.contains("fetch('/prompt/optimize'"));
        assertTrue(page.contains("大模型评分"));
        assertTrue(page.contains("模型返回兜底"));
        assertTrue(page.contains("本地规则评分"));
        assertTrue(page.contains("大模型优化失败，已生成本地优化版本"));
        assertTrue(page.contains("runLocalOptimization"));
        assertTrue(page.contains("优化后 Prompt / 差异"));
        assertTrue(page.contains("promptDiff"));
        assertTrue(page.contains("copyOptimizedPrompt"));
        assertTrue(page.contains("saveVersion"));
        assertTrue(page.contains("restoreVersion"));
        assertTrue(page.contains("addFewShotExample"));
        assertTrue(page.contains("prompt-optimizer-versions"));
        assertTrue(page.contains("ai-optimization"));
        assertTrue(page.contains("大模型优化"));
        assertTrue(page.contains("目标清晰度"));
        assertTrue(page.contains("输出格式契约"));
        assertTrue(page.contains("不输出隐藏思维链"));
    }

    @Test
    void promptOptimizerPageShouldExposeRetrievedRagContext() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));

        assertTrue(page.contains("ragContextPanel"));
        assertTrue(page.contains("renderRagContextPanel"));
        assertTrue(page.contains("RAG 检索上下文"));
        assertTrue(page.contains("ragContext"));
        assertTrue(page.contains("RAG Gate"));
        assertTrue(page.contains("gateReason"));
        assertTrue(page.contains("matchedKeywords"));
    }

    @Test
    void promptOptimizerShouldSanitizeOptimizationPayloadAndLocalFallback() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));
        int localFallbackStart = page.indexOf("function runLocalOptimization");
        int localFallbackEnd = page.indexOf("function renderOptimizedPrompt");
        String localFallback = page.substring(localFallbackStart, localFallbackEnd);

        assertTrue(page.contains("collectOptimizationData"));
        assertTrue(page.contains("sanitizeEvaluationForOptimization"));
        assertTrue(page.contains("data: optimizationData"));
        assertTrue(page.contains("buildLocalReinforcementRules"));
        assertTrue(page.contains("selectBestLocalPromptCandidate"));
        assertTrue(page.contains("buildContractSummaryOptimizedPrompt"));
        assertTrue(page.contains("stripLocalOptimizationArtifacts"));
        assertTrue(localFallback.contains("basePrompt"));
        assertFalse(localFallback.contains("\"missing_info\""));
        assertFalse(localFallback.contains("\"summary\": \"string\""));
        assertFalse(localFallback.contains("检索元数据"));
        assertFalse(localFallback.contains("'你可直接复用的优化后 Prompt"));
        assertFalse(localFallback.contains("'非目标:'"));
        assertFalse(localFallback.contains("'优化规则:'"));
        assertTrue(localFallback.contains("buildIntegratedOptimizedPrompt"));
        assertTrue(localFallback.contains("isOptimizationProcessNote"));
        assertFalse(localFallback.contains("sections.push([\n                '### 强化后的执行约束'"));
        assertTrue(localFallback.contains("执行规则"));
    }

    @Test
    void promptOptimizerShouldNormalizeEvaluationScoreBeforeRendering() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));

        assertTrue(page.contains("function normalizeCriterionScore"));
        assertTrue(page.contains("function normalizeOverallScore"));
        assertTrue(page.contains("function normalizeCriteria"));
        assertTrue(page.contains("Math.min(100"));
        assertTrue(page.contains("Math.min(5"));
        assertTrue(page.contains("const criteria = normalizeCriteria(rawCriteria);"));
        assertTrue(page.contains("const overallScore = normalizeOverallScore(rawOverallScore);"));
        assertTrue(page.contains("normalizeCriterionScore(item.score"));
        assertTrue(page.contains("llm_calibrated"));
        assertTrue(page.contains("大模型评分 · 已规则校准"));
    }

    @Test
    void promptOptimizerShouldBeReachableFromPromptPages() throws Exception {
        String index = Files.readString(Path.of("src/main/resources/static/index.html"));
        String promptDemo = Files.readString(Path.of("src/main/resources/static/prompt-demo.html"));

        assertTrue(index.contains("/prompt-optimizer.html"));
        assertTrue(promptDemo.contains("/prompt-optimizer.html"));
        assertTrue(index.contains("Prompt 优化器"));
    }

    @Test
    void promptOptimizerLayoutShouldPrioritizeInputEvaluationAndOutputColumns() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));

        assertTrue(page.contains("prompt-input-column"));
        assertTrue(page.contains("evaluation-column"));
        assertTrue(page.contains("prompt-output-column"));

        int inputColumn = page.indexOf("prompt-input-column");
        int evaluationColumn = page.indexOf("evaluation-column");
        int outputColumn = page.indexOf("prompt-output-column");

        assertTrue(inputColumn >= 0);
        assertTrue(evaluationColumn > inputColumn);
        assertTrue(outputColumn > evaluationColumn);
        assertTrue(page.indexOf("客户 Prompt 输入", inputColumn) < evaluationColumn);
        assertTrue(page.indexOf("评分卡与短板", evaluationColumn) < outputColumn);
        assertTrue(page.indexOf("优化后 Prompt / 差异", outputColumn) > outputColumn);
    }

    @Test
    void promptOptimizerShouldExposeClosedLoopRetestWorkflow() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));

        assertTrue(page.contains("prompt-workflow-strip"));
        assertTrue(page.contains("评测"));
        assertTrue(page.contains("诊断"));
        assertTrue(page.contains("优化"));
        assertTrue(page.contains("再测"));
        assertTrue(page.contains("retestOptimizedPrompt"));
        assertTrue(page.contains("setWorkflowStage"));
        assertTrue(page.contains("再测优化稿"));
    }

    @Test
    void promptOptimizerShouldUseDarkWorkbenchAndDockedWorkflow() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(page.contains("prompt-optimizer-shell"));
        assertTrue(page.contains("prompt-optimizer-object-bar"));
        assertTrue(page.contains("prompt-optimizer-command-strip"));
        assertTrue(page.contains("prompt-optimizer-stage-rail"));
        assertTrue(page.contains("prompt-optimizer-workspace"));
        assertTrue(page.contains("prompt-optimizer-pane"));
        assertTrue(page.contains("prompt-editor-panel"));
        assertTrue(page.contains("prompt-diagnostic-panel"));
        assertTrue(page.contains("prompt-console-panel"));
        assertTrue(page.contains("prompt-version-dock"));
        assertTrue(page.contains("prompt-optimizer-score-matrix"));
        assertTrue(page.contains("prompt-optimizer-output-console"));
        assertTrue(page.contains("prompt-optimizer-version-drawer"));
        assertTrue(page.contains("Prompt 优化运行台"));
        assertTrue(page.contains("评测态势"));
        assertTrue(page.contains("优化控制台"));

        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-workflow-strip"));
        assertTrue(stylesheet.contains("background: rgba(2, 8, 18, 0.78);"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"][data-app-page=\"prompt-optimizer\"] main.app-workbench"));
        assertTrue(stylesheet.contains("grid-template-rows: auto auto minmax(0, 1fr) !important;"));
        assertTrue(stylesheet.contains("gap: var(--prompt-optimizer-row-gap) !important;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-optimizer-workspace"));
        assertTrue(stylesheet.contains("height: calc(100vh - 210px) !important;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-optimizer-pane"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-optimizer-pane > .panel"));
        assertTrue(stylesheet.contains("display: flex !important;"));
        assertTrue(stylesheet.contains("flex-direction: column;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-optimizer-pane > .panel > .panel-header"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-optimizer-pane > .panel > .panel-body"));
        assertTrue(stylesheet.contains("position: relative;"));
        assertTrue(stylesheet.contains("top: auto;"));
        assertTrue(stylesheet.contains("grid-template-columns: minmax(320px, 0.72fr) minmax(420px, 1fr) minmax(360px, 0.82fr);"));
        assertTrue(stylesheet.contains("overflow-wrap: anywhere;"));
        assertFalse(page.contains("tool-grid mt-5 prompt-optimizer-workbench"));
        assertFalse(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .toolbar {\n    display: none;"));
    }

    @Test
    void promptOptimizerShouldRemoveDuplicatedTopHeroAndReserveHeaderSpace() throws Exception {
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .app-workbench-hero {\n    display: none !important;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] main.app-shell {"));
        assertTrue(stylesheet.contains("--prompt-optimizer-row-gap: 8px;"));
        assertTrue(stylesheet.contains("padding-top: 8px !important;"));
        assertTrue(stylesheet.contains("scroll-padding-top: calc(var(--app-header-height) + var(--prompt-optimizer-row-gap));"));
        assertTrue(stylesheet.contains("body[data-app-layout=\"active\"][data-app-page=\"prompt-optimizer\"] main.app-workbench > .prompt-workflow-strip.app-content-shell"));
    }

    @Test
    void promptOptimizerMobileLayoutShouldForceSingleColumnWorkbench() throws Exception {
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(stylesheet.contains("@media (max-width: 760px)"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] main.app-shell {\n        width: calc(100vw - 24px) !important;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .tool-grid {\n        grid-template-columns: 1fr !important;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .tool-grid > .stack"));
    }

    @Test
    void promptOptimizerOutputConsoleShouldStayReadableInLightWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));
        int inlinePreviewStart = page.indexOf(".prompt-preview {");
        int inlinePreviewEnd = page.indexOf(".version-item", inlinePreviewStart);
        String inlinePreviewStyle = page.substring(inlinePreviewStart, inlinePreviewEnd);

        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-optimizer-output-console"));
        assertTrue(stylesheet.contains("body[data-tech-theme=\"active\"][data-app-page=\"prompt-optimizer\"] #promptPreview.prompt-preview.prompt-optimizer-output-console"));
        assertTrue(stylesheet.contains("color: #102033;"));
        assertTrue(stylesheet.contains("color: #102033 !important;"));
        assertTrue(stylesheet.contains("background: #f8fcff;"));
        assertTrue(stylesheet.contains("background: #f8fcff !important;"));
        assertTrue(page.contains("/css/app-layout.css?v=20260624-prompt-actions-visible2"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-diff"));
        assertTrue(stylesheet.contains("color: #1f3448;"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-diff strong"));
        assertTrue(stylesheet.contains("color: #0f2238;"));
        assertTrue(inlinePreviewStyle.contains("color: #102033;"));
        assertTrue(inlinePreviewStyle.contains("background: #f8fcff;"));
        assertFalse(inlinePreviewStyle.contains("color: #e2f5ff;"));
    }

    @Test
    void promptOptimizerInputEditorShouldKeepLabelCompactAndTextAtTop() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/prompt-optimizer.html"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));
        String fieldSelector = "body[data-app-page=\"prompt-optimizer\"] .prompt-editor-panel .field {";
        int fieldStart = stylesheet.indexOf(fieldSelector);
        int fieldEnd = stylesheet.indexOf("\n}", fieldStart);
        String fieldBlock = stylesheet.substring(fieldStart, fieldEnd);

        String inputSelector = "body[data-app-page=\"prompt-optimizer\"] .prompt-input {";
        int inputStart = stylesheet.indexOf(inputSelector);
        int inputEnd = stylesheet.indexOf("\n}", inputStart);
        String inputBlock = stylesheet.substring(inputStart, inputEnd);

        assertTrue(fieldStart >= 0);
        assertTrue(fieldBlock.contains("grid-template-rows: auto minmax(0, 1fr);"));
        assertTrue(inputStart >= 0);
        assertTrue(inputBlock.contains("padding: 12px;"));
        assertTrue(inputBlock.contains("line-height: 1.6;"));
        assertTrue(page.contains("prompt-editor-actions"));
        assertTrue(stylesheet.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-editor-actions"));
        assertTrue(stylesheet.contains("position: sticky;"));
        assertTrue(stylesheet.contains("bottom: 0;"));
        assertTrue(stylesheet.contains("height: calc(100vh - 210px) !important;"));

        String midDesktopMedia = stylesheet.substring(
            stylesheet.indexOf("@media (min-width: 1181px) and (max-width: 1440px)"),
            stylesheet.indexOf("body[data-app-page=\"memory-manager\"]")
        );
        assertFalse(midDesktopMedia.contains("body[data-app-page=\"prompt-optimizer\"] .prompt-optimizer-workspace {\n        height: auto !important;"));
        assertFalse(stylesheet.contains("main.app-workbench > .prompt-workflow-strip.app-content-shell,\nbody[data-app-layout=\"active\"][data-app-page=\"prompt-optimizer\"] main.app-workbench > .prompt-optimizer-workbench.app-content-shell"));
    }
}
