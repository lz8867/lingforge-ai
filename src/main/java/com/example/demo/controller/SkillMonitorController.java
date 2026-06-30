package com.example.demo.controller;

import com.example.demo.service.SkillMonitorOverview;
import com.example.demo.service.SkillMonitorService;
import com.example.demo.service.SkillUsageEvent;
import com.example.demo.service.SkillUsageReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skill-monitor")
public class SkillMonitorController {

    private static final Logger log = LoggerFactory.getLogger(SkillMonitorController.class);

    private final SkillMonitorService skillMonitorService;

    public SkillMonitorController(SkillMonitorService skillMonitorService) {
        this.skillMonitorService = skillMonitorService;
    }

    @GetMapping("/overview")
    public SkillMonitorOverview overview() {
        log.info("收到 Skill 使用监控概览请求");
        return skillMonitorService.overview();
    }

    @PostMapping("/usage")
    public SkillUsageEvent reportUsage(@RequestBody SkillUsageReportRequest request) {
        log.info("收到 Skill 使用事件上报请求: skillId={}, status={}",
                request == null ? "" : request.skillId(),
                request == null ? "" : request.status());
        return skillMonitorService.recordUsage(request);
    }
}
