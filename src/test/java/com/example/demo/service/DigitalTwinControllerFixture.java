package com.example.demo.service;

import com.example.demo.model.Knowledge;
import com.example.demo.model.KnowledgeCategory;
import com.example.demo.model.KnowledgeTag;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DigitalTwinControllerFixture {

    private DigitalTwinControllerFixture() {
    }

    public static DigitalTwinService service() {
        return new DigitalTwinService(
                new StubDeployService(status()),
                new StubKnowledgeService(),
                new StubDocumentQualityHistoryService()
        );
    }

    private static ServiceStatusSnapshot status() {
        return new ServiceStatusSnapshot(
                "2026-06-12T10:00:00+08:00",
                "SUCCEEDED",
                "服务已正常运行",
                false,
                "UP",
                true,
                "http://localhost:8081",
                true,
                "",
                List.of(new ServiceStatusSnapshot.ContainerStatus("chart-demo", "Up 5 minutes", "8081->8080", true)),
                3600,
                512,
                2048,
                35.0,
                8,
                new ServiceStatusSnapshot.ServiceRuntimeMetrics(
                        32.0,
                        32.0,
                        42,
                        18,
                        56,
                        480,
                        2048,
                        35.0,
                        128,
                        4200,
                        8,
                        120,
                        160,
                        512,
                        24.0,
                        18
                ),
                List.of(),
                "CI/CD部署流程执行成功"
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

    private static final class StubKnowledgeService implements KnowledgeService {

        @Override
        public Map<String, Object> getKnowledgeStatistics() {
            return Map.of("totalKnowledge", 6L, "publishedCount", 5L, "draftCount", 1L);
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

    private static final class StubDocumentQualityHistoryService extends DocumentQualityHistoryService {

        @Override
        public List<DocumentQualityHistoryRecord> recent(int limit) {
            return List.of(new DocumentQualityHistoryRecord(
                    LocalDateTime.now(),
                    "数字孪生方案",
                    "方案文档",
                    88,
                    "良好",
                    1,
                    92.0,
                    "heuristic"
            ));
        }
    }
}
