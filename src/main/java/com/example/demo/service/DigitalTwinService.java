package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DigitalTwinService {

    private static final Logger log = LoggerFactory.getLogger(DigitalTwinService.class);

    private static final String ONLINE = "ONLINE";
    private static final String WARNING = "WARNING";
    private static final String OFFLINE = "OFFLINE";
    private static final String STANDBY = "STANDBY";
    private static final String CRITICAL = "CRITICAL";
    private static final String INFO = "INFO";

    private final DeployService deployService;
    private final KnowledgeService knowledgeService;
    private final DocumentQualityHistoryService documentQualityHistoryService;
    private final SkillMonitorService skillMonitorService;

    public DigitalTwinService(
            DeployService deployService,
            KnowledgeService knowledgeService,
            DocumentQualityHistoryService documentQualityHistoryService) {
        this(deployService, knowledgeService, documentQualityHistoryService, null);
    }

    @Autowired
    public DigitalTwinService(
            DeployService deployService,
            KnowledgeService knowledgeService,
            DocumentQualityHistoryService documentQualityHistoryService,
            SkillMonitorService skillMonitorService) {
        this.deployService = deployService;
        this.knowledgeService = knowledgeService;
        this.documentQualityHistoryService = documentQualityHistoryService;
        this.skillMonitorService = skillMonitorService;
    }

    public DigitalTwinOverview overview() {
        log.info("开始生成 AI 能力数字孪生概览");
        String checkedAt = OffsetDateTime.now().toString();
        List<DigitalTwinEvent> events = new ArrayList<>();

        ServiceStatusSnapshot runtimeStatus = loadRuntimeStatus(events);
        Map<String, Object> knowledgeStats = loadKnowledgeStats(events);
        List<DocumentQualityHistoryRecord> qualityHistory = loadQualityHistory(events);
        SkillMonitorOverview skillMonitorOverview = loadSkillMonitorOverview(events);

        List<DigitalTwinNode> nodes = buildNodes(runtimeStatus, knowledgeStats, qualityHistory, skillMonitorOverview);
        events.addAll(buildRuntimeEvents(runtimeStatus));
        events.addAll(buildKnowledgeEvents(knowledgeStats));
        events.addAll(buildQualityEvents(qualityHistory));
        events.addAll(buildSkillMonitorEvents(skillMonitorOverview));

        List<DigitalTwinRelation> relations = buildRelations(nodes);
        List<String> recommendations = buildRecommendations(runtimeStatus, knowledgeStats, qualityHistory, skillMonitorOverview);
        int total = nodes.size();
        int online = countStatus(nodes, ONLINE);
        int warning = countStatus(nodes, WARNING) + countStatus(nodes, STANDBY);
        int critical = countStatus(nodes, OFFLINE);
        int alertCount = (int) events.stream()
                .filter(event -> WARNING.equals(event.severity()) || CRITICAL.equals(event.severity()))
                .count();
        int healthScore = averageHealth(nodes);

        log.info("AI 能力数字孪生概览生成完成: healthScore={}, totalCapabilities={}, activeAlerts={}",
                healthScore, total, alertCount);

        return new DigitalTwinOverview(
                checkedAt,
                healthScore,
                total,
                online,
                warning,
                critical,
                alertCount,
                nodes,
                relations,
                events,
                recommendations
        );
    }

    private ServiceStatusSnapshot loadRuntimeStatus(List<DigitalTwinEvent> events) {
        try {
            ServiceStatusSnapshot status = deployService.currentStatus();
            log.info("数字孪生运行态读取完成: reachable={}, deploymentState={}",
                    status.serviceReachable(), status.deploymentState());
            return status;
        } catch (RuntimeException e) {
            log.error("数字孪生运行态读取失败: {}", e.getMessage());
            events.add(event(CRITICAL, "服务运行态", "运行态读取失败", "无法读取 DeployService 状态：" + e.getMessage()));
            return null;
        }
    }

    private Map<String, Object> loadKnowledgeStats(List<DigitalTwinEvent> events) {
        try {
            Map<String, Object> stats = knowledgeService.getKnowledgeStatistics();
            log.info("数字孪生知识库统计读取完成: totalKnowledge={}", number(stats, "totalKnowledge"));
            return stats == null ? Map.of() : stats;
        } catch (RuntimeException e) {
            log.error("数字孪生知识库统计读取失败: {}", e.getMessage());
            events.add(event(WARNING, "知识孪生", "知识库统计读取失败", "知识库统计暂不可用：" + e.getMessage()));
            return Map.of("statisticsError", e.getMessage());
        }
    }

    private List<DocumentQualityHistoryRecord> loadQualityHistory(List<DigitalTwinEvent> events) {
        try {
            List<DocumentQualityHistoryRecord> records = documentQualityHistoryService.recent(8);
            log.info("数字孪生文档质量历史读取完成: recordCount={}", records.size());
            return records;
        } catch (RuntimeException e) {
            log.error("数字孪生文档质量历史读取失败: {}", e.getMessage());
            events.add(event(WARNING, "质量孪生", "文档质量历史读取失败", "文档质量历史暂不可用：" + e.getMessage()));
            return List.of();
        }
    }

    private SkillMonitorOverview loadSkillMonitorOverview(List<DigitalTwinEvent> events) {
        if (skillMonitorService == null) {
            return null;
        }
        try {
            return skillMonitorService.overview();
        } catch (RuntimeException e) {
            log.error("Skill 使用监控读取失败: {}", e.getMessage());
            events.add(event(WARNING, "Skill 孪生", "Skill 监控读取失败", "本地 Skill 使用监控暂不可用：" + e.getMessage()));
            return null;
        }
    }

    private List<DigitalTwinNode> buildNodes(
            ServiceStatusSnapshot runtimeStatus,
            Map<String, Object> knowledgeStats,
            List<DocumentQualityHistoryRecord> qualityHistory,
            SkillMonitorOverview skillMonitorOverview) {
        boolean serviceOnline = controlPlaneAvailable(runtimeStatus);
        long totalKnowledge = number(knowledgeStats, "totalKnowledge");
        long publishedKnowledge = number(knowledgeStats, "publishedCount");
        long draftKnowledge = number(knowledgeStats, "draftCount");
        boolean knowledgeStatsFailed = knowledgeStats.containsKey("statisticsError");
        double qualityAverage = averageQualityScore(qualityHistory);

        List<DigitalTwinNode> nodes = new ArrayList<>();
        nodes.add(serviceRuntimeNode(runtimeStatus));
        nodes.add(node(
                "chat-assistant",
                "AI 对话孪生",
                "AI 交互",
                serviceOnline ? ONLINE : OFFLINE,
                serviceOnline ? "对话入口可用，依赖本地模型服务响应。" : "应用不可达，对话能力不可用。",
                serviceOnline ? 88 : 38,
                List.of("service-runtime", "prompt-engine"),
                Map.of("entry", "/chat.html", "provider", "Ollama / Spring AI"),
                List.of("进入对话", "检查模型服务")
        ));
        nodes.add(node(
                "prompt-engine",
                "Prompt 工程孪生",
                "提示词",
                serviceOnline ? ONLINE : OFFLINE,
                "跟踪模板、评测、优化和版本化调优链路。",
                serviceOnline ? 86 : 40,
                List.of("service-runtime"),
                Map.of("entry", "/prompt-optimizer.html", "evaluationMode", "规则评分 + AI 建议"),
                List.of("评测 Prompt", "沉淀优化版本")
        ));
        nodes.add(node(
                "knowledge-base",
                "知识库孪生",
                "知识资产",
                knowledgeStatus(knowledgeStatsFailed, totalKnowledge),
                knowledgeSummary(knowledgeStatsFailed, totalKnowledge, publishedKnowledge),
                knowledgeHealth(knowledgeStatsFailed, totalKnowledge, publishedKnowledge),
                List.of("service-runtime"),
                Map.of("totalKnowledge", totalKnowledge, "publishedKnowledge", publishedKnowledge, "draftKnowledge", draftKnowledge),
                List.of("维护知识", "补齐分类标签")
        ));
        nodes.add(node(
                "rag-engine",
                "RAG 检索孪生",
                "知识增强",
                ragStatus(serviceOnline, totalKnowledge),
                totalKnowledge > 0 ? "检索链路具备知识上下文，可支撑依据型回答。" : "检索链路可用，但知识上下文仍需补齐。",
                ragHealth(serviceOnline, totalKnowledge),
                List.of("service-runtime", "knowledge-base", "prompt-engine"),
                Map.of("entry", "/rag-demo.html", "topK", 3, "knowledgeReady", totalKnowledge > 0),
                List.of("测试检索命中", "补充高频问答资料")
        ));
        nodes.add(node(
                "document-quality",
                "文档质量孪生",
                "质量评测",
                qualityStatus(qualityHistory, qualityAverage),
                qualitySummary(qualityHistory, qualityAverage),
                qualityHealth(qualityHistory, qualityAverage),
                List.of("service-runtime", "rag-engine"),
                Map.of("recentRecords", qualityHistory.size(), "averageScore", round1(qualityAverage)),
                List.of("发起评测", "查看趋势")
        ));
        nodes.add(node(
                "media-generation",
                "内容生成孪生",
                "多模态",
                serviceOnline ? STANDBY : OFFLINE,
                serviceOnline ? "文生图、图生文、文生视频入口已接入，外部模型可用性需按需检查。" : "应用不可达，内容生成入口不可用。",
                serviceOnline ? 72 : 36,
                List.of("service-runtime"),
                Map.of("imageEntry", "/text-to-image.html", "visionEntry", "/image-to-text.html", "videoEntry", "/text-to-video.html"),
                List.of("检查模型桥", "生成样例资产")
        ));
        nodes.add(node(
                "governance-tools",
                "治理工具孪生",
                "研发治理",
                serviceOnline ? STANDBY : OFFLINE,
                serviceOnline ? "治理分析与文档质量工具可作为系统运营分析补充。" : "应用不可达，治理工具入口不可用。",
                serviceOnline ? 74 : 36,
                List.of("service-runtime", "document-quality"),
                Map.of("entry", "/document-quality.html", "modes", List.of("requirement-review", "effort-summary", "release-review")),
                List.of("生成巡检报告", "沉淀复盘结论")
        ));
        nodes.add(node(
                "deployment-pipeline",
                "部署运维孪生",
                "交付运行",
                deploymentStatus(runtimeStatus),
                runtimeStatus == null ? "部署状态不可读。" : runtimeStatus.deploymentMessage(),
                deploymentHealth(runtimeStatus),
                List.of("service-runtime"),
                Map.of(
                        "deploymentState", runtimeStatus == null ? "UNKNOWN" : runtimeStatus.deploymentState(),
                        "dockerAvailable", runtimeStatus != null && runtimeStatus.dockerAvailable(),
                        "containerCount", runtimeStatus == null ? 0 : runtimeStatus.containers().size()
                ),
                List.of("查看运行大屏", "查看 Docker 日志")
        ));
        nodes.add(node(
                "skill-monitor",
                "技能孪生",
                "本地技能",
                skillMonitorStatus(skillMonitorOverview),
                skillMonitorSummary(skillMonitorOverview),
                skillMonitorHealth(skillMonitorOverview),
                List.of("service-runtime"),
                skillMonitorMetrics(skillMonitorOverview),
                List.of("查看 Skill 监控::/skill-monitor.html", "补充 usage 上报")
        ));
        return nodes;
    }

    private DigitalTwinNode serviceRuntimeNode(ServiceStatusSnapshot runtimeStatus) {
        String status = serviceStatus(runtimeStatus);
        int health = serviceHealth(runtimeStatus);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("serviceState", runtimeStatus == null ? "UNKNOWN" : runtimeStatus.serviceState());
        metrics.put("deploymentState", runtimeStatus == null ? "UNKNOWN" : runtimeStatus.deploymentState());
        metrics.put("memoryUsagePercent", runtimeStatus == null ? 0 : runtimeStatus.memoryUsagePercent());
        metrics.put("dockerAvailable", runtimeStatus != null && runtimeStatus.dockerAvailable());
        if (runtimeStatus != null && runtimeStatus.serviceMetrics() != null) {
            metrics.put("systemCpuLoadPercent", runtimeStatus.serviceMetrics().systemCpuLoadPercent());
            metrics.put("diskUsagePercent", runtimeStatus.serviceMetrics().diskUsagePercent());
            metrics.put("threadCount", runtimeStatus.serviceMetrics().threadCount());
            metrics.put("healthLatencyMs", runtimeStatus.serviceMetrics().healthLatencyMs());
        }

        String summary = runtimeStatus == null
                ? "无法读取应用运行态。"
                : runtimeStatus.serviceReachable()
                ? "应用运行态可达，JVM、Docker 和部署日志可持续观测。"
                : "控制台可响应，但部署验证地址不可达，需要检查部署目标。";
        return node(
                "service-runtime",
                "服务运行孪生",
                "运行态",
                status,
                summary,
                health,
                List.of(),
                metrics,
                List.of("刷新状态", "查看运行大屏", "查看日志")
        );
    }

    private List<DigitalTwinRelation> buildRelations(List<DigitalTwinNode> nodes) {
        Map<String, String> statusById = new LinkedHashMap<>();
        for (DigitalTwinNode node : nodes) {
            statusById.put(node.id(), node.status());
        }
        return List.of(
                relation("chat-assistant", "prompt-engine", "依赖提示词编排", statusById),
                relation("chat-assistant", "service-runtime", "依赖应用运行态", statusById),
                relation("prompt-engine", "rag-engine", "依赖上下文检索", statusById),
                relation("rag-engine", "knowledge-base", "依赖知识上下文", statusById),
                relation("document-quality", "rag-engine", "依赖证据检索", statusById),
                relation("media-generation", "service-runtime", "依赖模型接口", statusById),
                relation("governance-tools", "document-quality", "依赖质量结论", statusById),
                relation("skill-monitor", "service-runtime", "依赖应用运行态", statusById),
                relation("deployment-pipeline", "service-runtime", "依赖运行验证", statusById)
        );
    }

    private DigitalTwinRelation relation(String source, String target, String label, Map<String, String> statusById) {
        String sourceStatus = statusById.getOrDefault(source, WARNING);
        String targetStatus = statusById.getOrDefault(target, WARNING);
        String status = OFFLINE.equals(sourceStatus) || OFFLINE.equals(targetStatus)
                ? OFFLINE
                : WARNING.equals(sourceStatus) || WARNING.equals(targetStatus) || STANDBY.equals(sourceStatus) || STANDBY.equals(targetStatus)
                ? WARNING
                : ONLINE;
        return new DigitalTwinRelation(source, target, label, status);
    }

    private List<DigitalTwinEvent> buildRuntimeEvents(ServiceStatusSnapshot runtimeStatus) {
        if (runtimeStatus == null) {
            return List.of();
        }
        List<DigitalTwinEvent> events = new ArrayList<>();
        if (!runtimeStatus.serviceReachable()) {
            String severity = "FAILED".equals(runtimeStatus.deploymentState()) ? CRITICAL : WARNING;
            events.add(event(severity, "服务运行孪生", "部署验证地址不可达", "服务地址 " + runtimeStatus.serviceUrl() + " 当前不可达。"));
        }
        if ("FAILED".equals(runtimeStatus.deploymentState())) {
            events.add(event(CRITICAL, "部署运维孪生", "最近一次部署失败", runtimeStatus.deploymentMessage()));
        }
        if (!runtimeStatus.dockerAvailable()) {
            events.add(event(WARNING, "部署运维孪生", "Docker 状态不可用", safe(runtimeStatus.dockerError(), "无法读取 Docker 容器状态。")));
        }
        if (runtimeStatus.memoryUsagePercent() >= 85) {
            events.add(event(WARNING, "服务运行孪生", "内存使用率偏高", "当前内存使用率 " + round1(runtimeStatus.memoryUsagePercent()) + "%。"));
        }
        if (runtimeStatus.serviceMetrics() != null && runtimeStatus.serviceMetrics().diskUsagePercent() >= 85) {
            events.add(event(WARNING, "服务运行孪生", "磁盘使用率偏高", "当前磁盘使用率 " + round1(runtimeStatus.serviceMetrics().diskUsagePercent()) + "%。"));
        }
        if (runtimeStatus.serviceMetrics() != null && runtimeStatus.serviceMetrics().systemCpuLoadPercent() >= 85) {
            events.add(event(WARNING, "服务运行孪生", "CPU 负载偏高", "当前系统 CPU 负载 " + round1(runtimeStatus.serviceMetrics().systemCpuLoadPercent()) + "%。"));
        }
        if (events.isEmpty()) {
            events.add(event(INFO, "服务运行孪生", "运行态正常", "服务、部署状态和基础资源指标处于可观测状态。"));
        }
        return events;
    }

    private List<DigitalTwinEvent> buildKnowledgeEvents(Map<String, Object> knowledgeStats) {
        if (knowledgeStats.containsKey("statisticsError")) {
            return List.of();
        }
        long totalKnowledge = number(knowledgeStats, "totalKnowledge");
        if (totalKnowledge == 0) {
            return List.of(event(INFO, "知识库孪生", "知识库待补齐", "暂无知识条目，RAG 回答会缺少可引用上下文。"));
        }
        return List.of(event(INFO, "知识库孪生", "知识资产可用", "当前知识条目 " + totalKnowledge + " 条，可继续补齐高频场景。"));
    }

    private List<DigitalTwinEvent> buildQualityEvents(List<DocumentQualityHistoryRecord> qualityHistory) {
        if (qualityHistory.isEmpty()) {
            return List.of(event(INFO, "文档质量孪生", "暂无评测历史", "运行一次文档质量评测后可形成趋势。"));
        }
        double average = averageQualityScore(qualityHistory);
        if (average < 70) {
            return List.of(event(WARNING, "文档质量孪生", "质量均分偏低", "最近文档质量均分 " + round1(average) + "，建议优先处理高频问题。"));
        }
        return List.of(event(INFO, "文档质量孪生", "质量历史可用", "最近文档质量均分 " + round1(average) + "。"));
    }

    private List<DigitalTwinEvent> buildSkillMonitorEvents(SkillMonitorOverview skillMonitorOverview) {
        if (skillMonitorOverview == null) {
            return List.of(
                    event(WARNING, "Skill 孪生", "未接入 Skill 监控", "当前未发现可用的 Skill 监控服务实例，页面仅展示其他能力对象。")
            );
        }
        if (skillMonitorOverview.totalSkills() == 0) {
            return List.of(
                    event(WARNING, "Skill 孪生", "未发现本地 SKILL.md", "请检查 SKILL.md 是否存在于可识别目录，或先补充本地 skill 清单。")
            );
        }
        if (skillMonitorOverview.totalEvents() == 0) {
            return List.of(
                    event(INFO, "Skill 孪生", "已扫描到 Skill", "已发现 " + skillMonitorOverview.totalSkills() + " 个 skill，但当前暂无使用上报。")
            );
        }
        List<DigitalTwinEvent> events = new ArrayList<>();
        events.add(event(INFO, "Skill 孪生", "使用趋势可读", "最近有 " + skillMonitorOverview.totalEvents() + " 条记录，成功率 " + skillMonitorOverview.successRate() + "%。"));
        if (skillMonitorOverview.attentionSkills() > 0) {
            events.add(event(WARNING, "Skill 孪生", "存在关注 Skill", "有 " + skillMonitorOverview.attentionSkills() + " 个 Skill 存在失败或异常记录。"));
        }
        return events;
    }

    private List<String> buildRecommendations(
            ServiceStatusSnapshot runtimeStatus,
            Map<String, Object> knowledgeStats,
            List<DocumentQualityHistoryRecord> qualityHistory,
            SkillMonitorOverview skillMonitorOverview) {
        List<String> recommendations = new ArrayList<>();
        if (runtimeStatus == null || "FAILED".equals(runtimeStatus.deploymentState())) {
            recommendations.add("服务不可达时先恢复 Spring Boot 应用，再检查 AI 对话、RAG 和内容生成入口。");
        } else if (!runtimeStatus.serviceReachable()) {
            recommendations.add("当前控制台可响应，但部署验证地址不可达，建议确认部署目标端口和容器映射。");
        }
        if (runtimeStatus != null && !runtimeStatus.dockerAvailable()) {
            recommendations.add("Docker 状态不可读会影响部署和日志孪生，建议先确认本机 Docker 服务。");
        }
        long totalKnowledge = number(knowledgeStats, "totalKnowledge");
        if (knowledgeStats.containsKey("statisticsError")) {
            recommendations.add("知识库统计暂不可用，先检查数据库连接，再验证知识库页面。");
        } else if (totalKnowledge == 0) {
            recommendations.add("优先补齐知识库条目，让 RAG 孪生具备可引用上下文。");
        } else {
            recommendations.add("知识库已有 " + totalKnowledge + " 条内容，建议按高频问题继续补齐标签和分类。");
        }
        if (qualityHistory.isEmpty()) {
            recommendations.add("运行一次文档质量评测，形成质量孪生的初始基线。");
        } else if (averageQualityScore(qualityHistory) < 80) {
            recommendations.add("文档质量均分未达到 80，建议优先修复评测报告中的高风险问题。");
        } else {
            recommendations.add("文档质量历史较健康，可把优秀样例沉淀到知识库提升 RAG 回答依据。");
        }
        recommendations.addAll(skillMonitorRecommendations(skillMonitorOverview));
        recommendations.add("内容生成孪生当前以入口和模型桥状态为主，后续可补充生成历史与失败原因。");
        return recommendations;
    }

    private String skillMonitorStatus(SkillMonitorOverview overview) {
        if (overview == null) {
            return STANDBY;
        }
        if (overview.totalSkills() == 0) {
            return WARNING;
        }
        if (overview.attentionSkills() > 0) {
            return WARNING;
        }
        return overview.totalEvents() == 0 ? STANDBY : ONLINE;
    }

    private int skillMonitorHealth(SkillMonitorOverview overview) {
        if (overview == null) {
            return 58;
        }
        if (overview.totalSkills() == 0) {
            return 52;
        }
        if (overview.totalEvents() == 0) {
            return 62;
        }
        if (overview.attentionSkills() > 0) {
            return 66;
        }
        return Math.max(74, Math.min(95, (int) Math.round(80 + overview.successRate() * 0.2)));
    }

    private String skillMonitorSummary(SkillMonitorOverview overview) {
        if (overview == null) {
            return "Skill 监控服务暂不可读。";
        }
        if (overview.totalSkills() == 0) {
            return "未发现可识别的本地 SKILL.md，未形成 Skill 使用视图。";
        }
        if (overview.totalEvents() == 0) {
            return "已发现 " + overview.totalSkills() + " 个 Skill，但未有使用上报。建议先接入上报。";
        }
        if (overview.attentionSkills() > 0) {
            return overview.usedSkills() + " 个已使用，" + overview.attentionSkills() + " 个存在失败记录。";
        }
        return "已识别 " + overview.totalSkills() + " 个 Skill，其中 " + overview.usedSkills() + " 个有使用记录。";
    }

    private Map<String, Object> skillMonitorMetrics(SkillMonitorOverview overview) {
        if (overview == null) {
            return Map.of(
                    "status", "UNAVAILABLE",
                    "skills", 0,
                    "usedSkills", 0,
                    "events", 0,
                    "successRate", 0.0
            );
        }
        return Map.of(
                "status", "CONNECTED",
                "skills", overview.totalSkills(),
                "usedSkills", overview.usedSkills(),
                "events", overview.totalEvents(),
                "successRate", overview.successRate(),
                "attentionSkills", overview.attentionSkills(),
                "lastChecked", safe(overview.checkedAt(), "--")
        );
    }

    private List<String> skillMonitorRecommendations(SkillMonitorOverview overview) {
        if (overview == null) {
            return List.of("Skill 监控服务未就绪，建议检查 /api/skill-monitor/overview 是否可用。");
        }
        if (overview.totalSkills() == 0) {
            return List.of("当前未发现本地 Skill 清单，可检查 .agents/skills 与 .codex/skills 下是否存在 SKILL.md。");
        }
        if (overview.totalEvents() == 0) {
            return List.of("有 " + overview.totalSkills() + " 个 skill，但尚无使用记录，建议从页面或调用链路接入 /api/skill-monitor/usage 上报。");
        }
        if (overview.attentionSkills() > 0) {
            return List.of("存在 " + overview.attentionSkills() + " 个 skill 关注点，请优先检查失败链路与依赖。");
        }
        return List.of("Skill 使用记录已覆盖到位，可继续补充更多失败场景以完善趋势分析。");
    }

    private DigitalTwinNode node(
            String id,
            String name,
            String type,
            String status,
            String summary,
            int healthScore,
            List<String> dependencies,
            Map<String, Object> metrics,
            List<String> actions) {
        return new DigitalTwinNode(
                id,
                name,
                type,
                status,
                summary,
                bound(healthScore),
                List.copyOf(dependencies),
                Map.copyOf(metrics),
                List.copyOf(actions)
        );
    }

    private DigitalTwinEvent event(String severity, String source, String title, String detail) {
        return new DigitalTwinEvent(OffsetDateTime.now().toString(), severity, source, title, detail);
    }

    private String serviceStatus(ServiceStatusSnapshot status) {
        if (status == null || "FAILED".equals(status.deploymentState())) {
            return OFFLINE;
        }
        if (!status.serviceReachable() || !status.dockerAvailable() || status.memoryUsagePercent() >= 85 || hasHighRuntimeMetric(status)) {
            return WARNING;
        }
        return ONLINE;
    }

    private int serviceHealth(ServiceStatusSnapshot status) {
        if (status == null) {
            return 30;
        }
        int score = status.serviceReachable() ? 94 : 76;
        if ("FAILED".equals(status.deploymentState())) {
            score = 42;
        }
        if (!status.serviceReachable() && !"FAILED".equals(status.deploymentState())) {
            score -= 10;
        }
        if (!status.dockerAvailable()) {
            score -= 10;
        }
        score -= usagePenalty(status.memoryUsagePercent());
        if (status.serviceMetrics() != null) {
            score -= usagePenalty(status.serviceMetrics().systemCpuLoadPercent());
            score -= usagePenalty(status.serviceMetrics().diskUsagePercent());
        }
        return bound(score);
    }

    private String deploymentStatus(ServiceStatusSnapshot status) {
        if (status == null) {
            return WARNING;
        }
        if ("FAILED".equals(status.deploymentState())) {
            return OFFLINE;
        }
        if ("DEPLOYING".equals(status.deploymentState()) || !status.dockerAvailable()) {
            return WARNING;
        }
        return ONLINE;
    }

    private int deploymentHealth(ServiceStatusSnapshot status) {
        if (status == null) {
            return 50;
        }
        if ("FAILED".equals(status.deploymentState())) {
            return 38;
        }
        if ("DEPLOYING".equals(status.deploymentState())) {
            return 72;
        }
        return status.dockerAvailable() ? 86 : 66;
    }

    private String knowledgeStatus(boolean failed, long totalKnowledge) {
        if (failed) {
            return WARNING;
        }
        return totalKnowledge > 0 ? ONLINE : WARNING;
    }

    private String knowledgeSummary(boolean failed, long totalKnowledge, long publishedKnowledge) {
        if (failed) {
            return "知识库统计暂不可用，已保留能力入口。";
        }
        if (totalKnowledge == 0) {
            return "知识库暂无条目，RAG 缺少稳定上下文。";
        }
        return "知识库已有 " + totalKnowledge + " 条内容，其中已发布 " + publishedKnowledge + " 条。";
    }

    private int knowledgeHealth(boolean failed, long totalKnowledge, long publishedKnowledge) {
        if (failed) {
            return 58;
        }
        if (totalKnowledge == 0) {
            return 62;
        }
        double publishRatio = publishedKnowledge * 1.0 / Math.max(1, totalKnowledge);
        return bound((int) Math.round(78 + publishRatio * 14));
    }

    private String ragStatus(boolean serviceOnline, long totalKnowledge) {
        if (!serviceOnline) {
            return OFFLINE;
        }
        return totalKnowledge > 0 ? ONLINE : WARNING;
    }

    private int ragHealth(boolean serviceOnline, long totalKnowledge) {
        if (!serviceOnline) {
            return 40;
        }
        return totalKnowledge > 0 ? 84 : 68;
    }

    private String qualityStatus(List<DocumentQualityHistoryRecord> records, double average) {
        if (records.isEmpty()) {
            return WARNING;
        }
        if (average < 60) {
            return OFFLINE;
        }
        return average < 80 ? WARNING : ONLINE;
    }

    private String qualitySummary(List<DocumentQualityHistoryRecord> records, double average) {
        if (records.isEmpty()) {
            return "暂无评测历史，质量孪生等待初始化。";
        }
        return "最近 " + records.size() + " 次文档评测均分 " + round1(average) + "。";
    }

    private int qualityHealth(List<DocumentQualityHistoryRecord> records, double average) {
        if (records.isEmpty()) {
            return 66;
        }
        return bound((int) Math.round(average));
    }

    private boolean hasHighRuntimeMetric(ServiceStatusSnapshot status) {
        return status.serviceMetrics() != null
                && (status.serviceMetrics().systemCpuLoadPercent() >= 85 || status.serviceMetrics().diskUsagePercent() >= 85);
    }

    private boolean controlPlaneAvailable(ServiceStatusSnapshot status) {
        return status != null && !"FAILED".equals(status.deploymentState());
    }

    private int usagePenalty(double usage) {
        if (usage >= 95) {
            return 16;
        }
        if (usage >= 85) {
            return 10;
        }
        if (usage >= 75) {
            return 5;
        }
        return 0;
    }

    private int averageHealth(List<DigitalTwinNode> nodes) {
        return bound((int) Math.round(nodes.stream()
                .mapToInt(DigitalTwinNode::healthScore)
                .average()
                .orElse(0)));
    }

    private int countStatus(List<DigitalTwinNode> nodes, String status) {
        return (int) nodes.stream()
                .filter(node -> status.equals(node.status()))
                .count();
    }

    private double averageQualityScore(List<DocumentQualityHistoryRecord> records) {
        return records.stream()
                .mapToInt(DocumentQualityHistoryRecord::overallScore)
                .average()
                .orElse(0);
    }

    private long number(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private int bound(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
