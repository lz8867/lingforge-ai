-- 创建数据库
CREATE DATABASE IF NOT EXISTS spring_ai_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE spring_ai_db;

-- 创建用户记忆表
CREATE TABLE IF NOT EXISTS user_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    type VARCHAR(50) NOT NULL,
    metadata VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    INDEX idx_user_id (user_id),
    INDEX idx_user_type (user_id, type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入测试数据（可选）
INSERT INTO user_memory (user_id, content, type, metadata, created_at, updated_at)
VALUES
('default_user', '你好，我是AI助手', 'ai_response', NULL, NOW(), NOW()),
('default_user', '介绍一下你自己', 'user_input', NULL, NOW(), NOW()),
('default_user', '我是一个基于Spring AI开发的智能助手，可以帮助你进行对话、代码审查、文档生成等任务。', 'ai_response', NULL, NOW(), NOW());

-- 文档质量评测历史表
CREATE TABLE IF NOT EXISTS document_quality_evaluation_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    overall_score INT NOT NULL,
    grade VARCHAR(80),
    rule_score INT NOT NULL,
    llm_judge_score INT NOT NULL,
    metric_score INT NOT NULL,
    issue_count INT NOT NULL,
    rag_coverage_score DOUBLE NOT NULL,
    llm_judge_source VARCHAR(40),
    result_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    created_at DATETIME NOT NULL,
    INDEX idx_dq_document_name_created_at (document_name, created_at),
    INDEX idx_dq_document_type_created_at (document_type, created_at),
    INDEX idx_dq_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模型评测主表（场景、模型、供应商、状态、运行指标）
CREATE TABLE IF NOT EXISTS model_evaluation_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene VARCHAR(60) NOT NULL,
    model VARCHAR(120) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    success BOOLEAN NOT NULL,
    duration_ms BIGINT NOT NULL,
    overall_score INT NOT NULL,
    request_source VARCHAR(80),
    request_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    response_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    metadata_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    error_message VARCHAR(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    created_at DATETIME NOT NULL,
    INDEX idx_mer_scene_created_at (scene, created_at),
    INDEX idx_mer_model_created_at (model, created_at),
    INDEX idx_mer_provider_created_at (provider, created_at),
    INDEX idx_mer_status_created_at (status, created_at),
    INDEX idx_mer_request_source_created_at (request_source, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模型评测指标明细表（指标名-数值）
CREATE TABLE IF NOT EXISTS model_evaluation_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    metric_name VARCHAR(80) NOT NULL,
    metric_value DOUBLE NOT NULL,
    INDEX idx_mem_run_id (run_id),
    CONSTRAINT fk_mem_run_id FOREIGN KEY (run_id) REFERENCES model_evaluation_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
