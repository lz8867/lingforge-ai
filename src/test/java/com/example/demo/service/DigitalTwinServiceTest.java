package com.example.demo.service;

import com.example.demo.model.Knowledge;
import com.example.demo.model.KnowledgeCategory;
import com.example.demo.model.KnowledgeTag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalTwinServiceTest {

    @Test
    void overviewShouldBuildCapabilityTwinWithServiceKnowledgeAndQualitySignals() {
        ServiceStatusSnapshot status = serviceStatus(
                "SUCCEEDED",
                "服务已正常运行",
                true,
                true,
                "",
                42.0,
                38.0,
                31.0
        );
        DigitalTwinService service = new DigitalTwinService(
                new StubDeployService(status),
                new StubKnowledgeService(Map.of("totalKnowledge", 8L, "publishedCount", 6L, "draftCount", 2L)),
                new StubDocumentQualityHistoryService(List.of(
                        new DocumentQualityHistoryRecord(
                                LocalDateTime.now(),
                                "数字孪生方案",
                                "方案文档",
                                88,
                                "良好",
                                1,
                                92.0,
                                "heuristic"
                        )
                ))
        );

        DigitalTwinOverview overview = service.overview();

        assertTrue(overview.healthScore() >= 80);
        assertTrue(overview.totalCapabilities() >= 7);
        assertTrue(overview.onlineCapabilities() >= 5);
        assertTrue(overview.nodes().stream().anyMatch(node -> "service-runtime".equals(node.id())));
        assertTrue(overview.nodes().stream().anyMatch(node -> "knowledge-base".equals(node.id())));
        assertTrue(overview.nodes().stream().anyMatch(node -> "document-quality".equals(node.id())));
        assertTrue(overview.relations().stream().anyMatch(relation ->
                "rag-engine".equals(relation.source()) && "knowledge-base".equals(relation.target())));
        assertTrue(overview.recommendations().stream().anyMatch(recommendation -> recommendation.contains("知识")));
    }

    @Test
    void overviewShouldDegradeAndEmitCriticalEventWhenRuntimeIsUnavailable() {
        ServiceStatusSnapshot status = serviceStatus(
                "FAILED",
                "最近一次部署失败",
                false,
                false,
                "docker unavailable",
                91.0,
                87.0,
                93.0
        );
        DigitalTwinService service = new DigitalTwinService(
                new StubDeployService(status),
                new StubKnowledgeService(Map.of("totalKnowledge", 0L, "publishedCount", 0L, "draftCount", 0L)),
                new StubDocumentQualityHistoryService(List.of())
        );

        DigitalTwinOverview overview = service.overview();

        assertTrue(overview.healthScore() < 75);
        assertTrue(overview.criticalCapabilities() > 0);
        assertTrue(overview.events().stream().anyMatch(event -> "CRITICAL".equals(event.severity())));
        assertTrue(overview.recommendations().stream().anyMatch(recommendation -> recommendation.contains("服务不可达")));
        assertTrue(overview.nodes().stream().anyMatch(node ->
                "service-runtime".equals(node.id()) && "OFFLINE".equals(node.status())));
    }

    @Test
    void overviewShouldKeepReturningWhenOptionalKnowledgeStatisticsFail() {
        DigitalTwinService service = new DigitalTwinService(
                new StubDeployService(serviceStatus(
                        "SUCCEEDED",
                        "服务已正常运行",
                        true,
                        true,
                        "",
                        20.0,
                        18.0,
                        22.0
                )),
                new FailingKnowledgeService(),
                new StubDocumentQualityHistoryService(List.of())
        );

        DigitalTwinOverview overview = service.overview();

        assertFalse(overview.nodes().isEmpty());
        assertTrue(overview.events().stream().anyMatch(event ->
                "WARNING".equals(event.severity()) && event.title().contains("知识库统计")));
    }

    @Test
    void overviewShouldIncludeSkillMonitorNodeWhenUsageEventsExist() {
        SkillMonitorService skillMonitorService = new SkillMonitorService(List.of(), Clock.systemDefaultZone());
        skillMonitorService.recordUsage(new SkillUsageReportRequest(
                "code-review",
                "代码审阅",
                "SUCCESS",
                "manual",
                320,
                "本地调用完成"
        ));
        skillMonitorService.recordUsage(new SkillUsageReportRequest(
                "rag-index",
                "知识索引",
                "SUCCESS",
                "manual",
                280,
                "索引完成"
        ));

        DigitalTwinService service = new DigitalTwinService(
                new StubDeployService(serviceStatus(
                        "SUCCEEDED",
                        "服务已正常运行",
                        true,
                        true,
                        "",
                        42.0,
                        38.0,
                        31.0
                )),
                new StubKnowledgeService(Map.of("totalKnowledge", 8L, "publishedCount", 6L, "draftCount", 2L)),
                new StubDocumentQualityHistoryService(List.of()),
                skillMonitorService
        );

        DigitalTwinOverview overview = service.overview();

        assertTrue(overview.nodes().stream().anyMatch(node -> "skill-monitor".equals(node.id())));
        assertTrue(overview.events().stream().anyMatch(event -> "Skill 孪生".equals(event.source())));
        assertTrue(overview.recommendations().stream().anyMatch(recommendation -> recommendation.contains("Skill")));
    }

    private static ServiceStatusSnapshot serviceStatus(
            String deploymentState,
            String deploymentMessage,
            boolean serviceReachable,
            boolean dockerAvailable,
            String dockerError,
            double memoryUsage,
            double cpuUsage,
            double diskUsage) {
        return new ServiceStatusSnapshot(
                "2026-06-12T10:00:00+08:00",
                deploymentState,
                deploymentMessage,
                false,
                serviceReachable ? "UP" : "DOWN",
                serviceReachable,
                "http://localhost:8081",
                dockerAvailable,
                dockerError,
                List.of(new ServiceStatusSnapshot.ContainerStatus("chart-demo", serviceReachable ? "Up 5 minutes" : "Exited", "8081->8080", true)),
                3600,
                512,
                2048,
                memoryUsage,
                8,
                new ServiceStatusSnapshot.ServiceRuntimeMetrics(
                        cpuUsage,
                        cpuUsage,
                        42,
                        18,
                        56,
                        480,
                        2048,
                        memoryUsage,
                        128,
                        4200,
                        8,
                        120,
                        160,
                        512,
                        diskUsage,
                        18
                ),
                List.of(),
                deploymentMessage
        );
    }

    private static final class StubDeployService extends DeployService {

        private final ServiceStatusSnapshot status;

        private StubDeployService(ServiceStatusSnapshot status) {
            super(new NoopCommandExecutor(), new File("."));
            this.status = status;
        }

        @Override
        public ServiceStatusSnapshot currentStatus() {
            return status;
        }
    }

    private static final class NoopCommandExecutor implements CommandExecutor {

        @Override
        public CommandExecutionResult execute(List<String> command, File directory) {
            return new CommandExecutionResult(0, "");
        }

        @Override
        public void start(List<String> command, File directory, File logFile) {
        }

        @Override
        public CommandLogStream stream(List<String> command, File directory) throws IOException {
            return new CommandLogStream(java.io.InputStream.nullInputStream(), () -> {
            });
        }
    }

    private static class StubKnowledgeService implements KnowledgeService {

        private final Map<String, Object> statistics;

        private StubKnowledgeService(Map<String, Object> statistics) {
            this.statistics = statistics;
        }

        @Override
        public Map<String, Object> getKnowledgeStatistics() {
            return statistics;
        }

        @Override
        public List<Knowledge> getAllKnowledge() {
            return List.of();
        }

        @Override
        public List<Knowledge> getKnowledgeByCategory(Long categoryId) {
            return List.of();
        }

        @Override
        public List<Knowledge> getKnowledgeByStatus(String status) {
            return List.of();
        }

        @Override
        public List<Knowledge> searchKnowledge(String keyword) {
            return List.of();
        }

        @Override
        public List<Knowledge> getKnowledgeByTag(String tagName) {
            return List.of();
        }

        @Override
        public Knowledge getKnowledgeById(Long id) {
            return null;
        }

        @Override
        public Knowledge createKnowledge(Knowledge knowledge) {
            return knowledge;
        }

        @Override
        public Knowledge updateKnowledge(Knowledge knowledge) {
            return knowledge;
        }

        @Override
        public void deleteKnowledge(Long id) {
        }

        @Override
        public List<KnowledgeCategory> getAllCategories() {
            return List.of();
        }

        @Override
        public List<KnowledgeCategory> getCategoriesByParent(Long parentId) {
            return List.of();
        }

        @Override
        public KnowledgeCategory getCategoryById(Long id) {
            return null;
        }

        @Override
        public KnowledgeCategory createCategory(KnowledgeCategory category) {
            return category;
        }

        @Override
        public KnowledgeCategory updateCategory(KnowledgeCategory category) {
            return category;
        }

        @Override
        public void deleteCategory(Long id) {
        }

        @Override
        public List<KnowledgeTag> getAllTags() {
            return List.of();
        }

        @Override
        public KnowledgeTag getTagById(Long id) {
            return null;
        }

        @Override
        public KnowledgeTag createTag(KnowledgeTag tag) {
            return tag;
        }

        @Override
        public KnowledgeTag updateTag(KnowledgeTag tag) {
            return tag;
        }

        @Override
        public void deleteTag(Long id) {
        }

        @Override
        public List<KnowledgeTag> searchTags(String keyword) {
            return List.of();
        }

        @Override
        public String aiGenerateKnowledge(String prompt) {
            return "";
        }

        @Override
        public String aiSummarizeKnowledge(Long knowledgeId) {
            return "";
        }

        @Override
        public String aiAnswerQuestion(String question, List<Knowledge> contextKnowledge) {
            return "";
        }
    }

    private static final class FailingKnowledgeService extends StubKnowledgeService {

        private FailingKnowledgeService() {
            super(Map.of());
        }

        @Override
        public Map<String, Object> getKnowledgeStatistics() {
            throw new IllegalStateException("knowledge db unavailable");
        }
    }

    private static final class StubDocumentQualityHistoryService extends DocumentQualityHistoryService {

        private final List<DocumentQualityHistoryRecord> records;

        private StubDocumentQualityHistoryService(List<DocumentQualityHistoryRecord> records) {
            this.records = records;
        }

        @Override
        public List<DocumentQualityHistoryRecord> recent(int limit) {
            return records.stream().limit(limit).toList();
        }
    }
}
