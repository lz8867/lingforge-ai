package com.example.demo.DTO;

import com.example.demo.model.Knowledge;

public class KnowledgeCaptureResult {
    public static final String ACTION_CREATED = "CREATED";
    public static final String ACTION_SKIPPED_DUPLICATE = "SKIPPED_DUPLICATE";
    public static final String ACTION_SKIPPED_INVALID = "SKIPPED_INVALID";
    public static final String ACTION_FAILED = "FAILED";

    private final boolean captured;
    private final String action;
    private final String message;
    private final Knowledge knowledge;

    private KnowledgeCaptureResult(boolean captured, String action, String message, Knowledge knowledge) {
        this.captured = captured;
        this.action = action;
        this.message = message;
        this.knowledge = knowledge;
    }

    public static KnowledgeCaptureResult created(Knowledge knowledge, String message) {
        return new KnowledgeCaptureResult(true, ACTION_CREATED, message, knowledge);
    }

    public static KnowledgeCaptureResult skipped(String action, String message) {
        return new KnowledgeCaptureResult(false, action, message, null);
    }

    public static KnowledgeCaptureResult failed(String message) {
        return new KnowledgeCaptureResult(false, ACTION_FAILED, message, null);
    }

    public boolean isCaptured() {
        return captured;
    }

    public String getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }
}
