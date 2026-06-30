package com.example.demo.controller;

import com.example.demo.service.DigitalTwinOverview;
import com.example.demo.service.DigitalTwinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/digital-twin")
public class DigitalTwinController {

    private static final Logger log = LoggerFactory.getLogger(DigitalTwinController.class);

    private final DigitalTwinService digitalTwinService;

    public DigitalTwinController(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;
    }

    @GetMapping("/overview")
    public DigitalTwinOverview overview() {
        log.info("收到 AI 能力数字孪生概览请求");
        return digitalTwinService.overview();
    }
}
