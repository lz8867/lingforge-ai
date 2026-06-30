package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Service
public class SkillMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SkillMonitorService.class);
    private static final int MAX_SKILL_FILES = 300;
    private static final int MAX_RECENT_EVENTS = 40;

    private final List<Path> skillRoots;
    private final Clock clock;
    private final CopyOnWriteArrayList<SkillUsageEvent> usageEvents = new CopyOnWriteArrayList<>();

    public SkillMonitorService() {
        this(defaultSkillRoots(), Clock.systemDefaultZone());
    }

    public SkillMonitorService(List<Path> skillRoots, Clock clock) {
        this.skillRoots = List.copyOf(skillRoots == null ? List.of() : skillRoots);
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public SkillMonitorOverview overview() {
        log.info("开始生成 Skill 使用监控概览");
        Map<String, SkillDefinition> definitions = discoverSkillDefinitions();
        List<SkillUsageEvent> events = List.copyOf(usageEvents);
        addAdHocDefinitions(definitions, events);

        List<SkillUsageSummary> summaries = definitions.values().stream()
                .map(definition -> summarize(definition, events))
                .sorted(Comparator
                        .comparingInt(SkillUsageSummary::totalCount).reversed()
                        .thenComparing(SkillUsageSummary::skillName))
                .toList();
        List<SkillUsageEvent> recentEvents = recentEvents(events);
        int totalEvents = events.size();
        int usedSkills = (int) summaries.stream().filter(summary -> summary.totalCount() > 0).count();
        int attentionSkills = (int) summaries.stream().filter(summary -> "ATTENTION".equals(summary.usageStatus())).count();
        double successRate = round1(totalEvents == 0 ? 0 : countStatus(events, "SUCCESS") * 100.0 / totalEvents);

        SkillMonitorOverview overview = new SkillMonitorOverview(
                OffsetDateTime.now(clock).toString(),
                summaries.size(),
                usedSkills,
                totalEvents,
                successRate,
                attentionSkills,
                summaries,
                recentEvents,
                recommendations(summaries, totalEvents, attentionSkills)
        );
        log.info("Skill 使用监控概览生成完成: totalSkills={}, totalEvents={}, attentionSkills={}",
                overview.totalSkills(), overview.totalEvents(), overview.attentionSkills());
        return overview;
    }

    public SkillUsageEvent recordUsage(SkillUsageReportRequest request) {
        String skillId = normalizeId(request == null ? "" : request.skillId());
        String skillName = safeText(request == null ? "" : request.skillName(), skillId);
        String status = normalizeStatus(request == null ? "" : request.status());
        String source = safeText(request == null ? "" : request.source(), "manual");
        long durationMs = Math.max(0, request == null ? 0 : request.durationMs());
        String note = safeText(request == null ? "" : request.note(), "");
        log.info("收到 Skill 使用事件上报: skillId={}, status={}, source={}, durationMs={}",
                skillId, status, source, durationMs);

        if (skillId.isBlank()) {
            log.error("Skill 使用事件上报失败: skillId 为空");
            throw new IllegalArgumentException("skillId 不能为空");
        }

        SkillUsageEvent event = new SkillUsageEvent(
                UUID.randomUUID().toString(),
                skillId,
                skillName,
                status,
                source,
                durationMs,
                note,
                OffsetDateTime.now(clock).toString()
        );
        usageEvents.add(event);
        return event;
    }

    private Map<String, SkillDefinition> discoverSkillDefinitions() {
        Map<String, SkillDefinition> definitions = new LinkedHashMap<>();
        int readCount = 0;
        for (Path root : skillRoots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            log.info("开始扫描 Skill 根目录: root={}", root);
            try (Stream<Path> files = Files.walk(root, 4)) {
                List<Path> skillFiles = files
                        .filter(Files::isRegularFile)
                        .filter(path -> "SKILL.md".equals(path.getFileName().toString()))
                        .filter(path -> isLocalCustomSkillFile(root, path))
                        .sorted()
                        .limit(MAX_SKILL_FILES)
                        .toList();
                for (Path file : skillFiles) {
                    if (readCount >= MAX_SKILL_FILES) {
                        return definitions;
                    }
                    readCount++;
                    SkillDefinition definition = readSkillDefinition(root, file);
                    definitions.putIfAbsent(definition.skillId(), definition);
                }
            } catch (IOException e) {
                log.warn("扫描 Skill 根目录失败: root={}, message={}", root, e.getMessage());
            }
        }
        return definitions;
    }

    private SkillDefinition readSkillDefinition(Path root, Path file) {
        String directoryName = file.getParent() == null ? "unknown-skill" : file.getParent().getFileName().toString();
        String name = directoryName;
        String description = "";
        try {
            String content = Files.readString(file);
            name = safeText(frontMatterValue(content, "name"), directoryName);
            description = safeText(frontMatterValue(content, "description"), firstHeading(content));
        } catch (IOException e) {
            log.warn("读取 Skill 文件失败: file={}, message={}", file, e.getMessage());
        }
        String id = normalizeId(name);
        if (id.isBlank()) {
            id = normalizeId(directoryName);
        }
        return new SkillDefinition(id, name, description, root.relativize(file).toString());
    }

    private void addAdHocDefinitions(Map<String, SkillDefinition> definitions, List<SkillUsageEvent> events) {
        for (SkillUsageEvent event : events) {
            definitions.putIfAbsent(event.skillId(), new SkillDefinition(
                    event.skillId(),
                    safeText(event.skillName(), event.skillId()),
                    "通过使用事件上报登记的本地 skill。",
                    "usage-event"
            ));
        }
    }

    private SkillUsageSummary summarize(SkillDefinition definition, List<SkillUsageEvent> events) {
        List<SkillUsageEvent> matched = events.stream()
                .filter(event -> definition.skillId().equals(event.skillId()))
                .toList();
        int total = matched.size();
        int success = countStatus(matched, "SUCCESS");
        int failure = countStatus(matched, "FAILURE");
        int cancelled = countStatus(matched, "CANCELLED");
        String status = total == 0 ? "UNUSED" : failure > 0 ? "ATTENTION" : "ACTIVE";
        long averageDuration = total == 0
                ? 0
                : Math.round(matched.stream().mapToLong(SkillUsageEvent::durationMs).average().orElse(0));
        String lastUsedAt = total == 0 ? "" : matched.get(total - 1).occurredAt();
        double successRate = round1(total == 0 ? 0 : success * 100.0 / total);
        return new SkillUsageSummary(
                definition.skillId(),
                definition.skillName(),
                definition.description(),
                definition.sourcePath(),
                status,
                total,
                success,
                failure,
                cancelled,
                successRate,
                averageDuration,
                lastUsedAt
        );
    }

    private List<SkillUsageEvent> recentEvents(List<SkillUsageEvent> events) {
        List<SkillUsageEvent> copy = new ArrayList<>(events);
        java.util.Collections.reverse(copy);
        return copy.stream().limit(MAX_RECENT_EVENTS).toList();
    }

    private List<String> recommendations(List<SkillUsageSummary> summaries, int totalEvents, int attentionSkills) {
        List<String> recommendations = new ArrayList<>();
        if (summaries.isEmpty()) {
            recommendations.add("未发现本地自定义 SKILL.md，可检查 .codex/skills、.agents/skills 或项目内 skills 目录。");
            return recommendations;
        }
        if (totalEvents == 0) {
            recommendations.add("已发现 " + summaries.size() + " 个本地自定义 skill，下一步接入使用事件上报后才能形成真实使用趋势。");
            recommendations.add("可通过 POST /api/skill-monitor/usage 从脚本、页面或 Codex 包装流程写入使用记录。");
            return recommendations;
        }
        long unused = summaries.stream().filter(summary -> summary.totalCount() == 0).count();
        if (unused > 0) {
            recommendations.add("还有 " + unused + " 个 skill 没有使用记录，可结合近期任务判断是否需要归档或补充说明。");
        }
        if (attentionSkills > 0) {
            recommendations.add("存在 " + attentionSkills + " 个 skill 有失败记录，优先检查依赖路径、权限和触发说明。");
        }
        recommendations.add("使用记录当前保存在内存中，如需长期统计可扩展为 JPA 持久化。");
        return recommendations;
    }

    private boolean isLocalCustomSkillFile(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            String name = segment.toString();
            if (name.startsWith(".")) {
                return false;
            }
            if ("plugins".equals(name) || "cache".equals(name) || "node_modules".equals(name)) {
                return false;
            }
        }
        return true;
    }

    private static List<Path> defaultSkillRoots() {
        String userHome = System.getProperty("user.home", "");
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of(".codex", "skills"));
        roots.add(Path.of(".agents", "skills"));
        roots.add(Path.of("skills"));
        if (!userHome.isBlank()) {
            roots.add(Path.of(userHome, ".codex", "skills"));
            roots.add(Path.of(userHome, ".agents", "skills"));
        }
        return roots;
    }

    private String frontMatterValue(String content, String key) {
        if (content == null || !content.startsWith("---")) {
            return "";
        }
        String[] lines = content.split("\\R");
        String prefix = key + ":";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return unquote(trimmed.substring(prefix.length()).trim());
            }
        }
        return "";
    }

    private String firstHeading(String content) {
        if (content == null) {
            return "";
        }
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return "";
    }

    private String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String normalized = safeText(status, "UNKNOWN").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUCCESS", "FAILURE", "CANCELLED" -> normalized;
            default -> "UNKNOWN";
        };
    }

    private static int countStatus(List<SkillUsageEvent> events, String status) {
        return (int) events.stream()
                .filter(event -> status.equals(event.status()))
                .count();
    }

    private String normalizeId(String value) {
        return safeText(value, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._:-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record SkillDefinition(
            String skillId,
            String skillName,
            String description,
            String sourcePath
    ) {
    }
}
