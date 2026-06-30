package com.example.demo.service;

public record DigitalTwinEvent(
        String time,
        String severity,
        String source,
        String title,
        String detail
) {
}
