USE `maimai_dx`;

-- Add requestId-based idempotency for asynchronous score imports.
-- Old synchronous imports may keep request_id as NULL. MySQL allows multiple
-- NULL values in a unique index, so this does not affect the existing endpoint.
ALTER TABLE `score_snapshot`
    ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL COMMENT '异步导入请求幂等ID' AFTER `user_id`,
    ADD UNIQUE KEY `uk_snapshot_user_request` (`user_id`, `request_id`);

CREATE TABLE `import_task` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '导入任务ID',
    `request_id`            VARCHAR(64)   NOT NULL COMMENT '业务幂等请求ID',
    `user_id`               BIGINT        NOT NULL COMMENT '用户ID',
    `status`                VARCHAR(32)   NOT NULL COMMENT '任务状态',
    `request_payload`       JSON          NOT NULL COMMENT '原始导入请求JSON',
    `snapshot_id`           BIGINT        DEFAULT NULL COMMENT '成功导入后的成绩快照ID',
    `attempt_count`         INT           NOT NULL DEFAULT 0 COMMENT '消费尝试次数',
    `error_message`         VARCHAR(1024) DEFAULT NULL COMMENT '清理后的失败原因',
    `processing_started_at` DATETIME      DEFAULT NULL COMMENT '开始处理时间',
    `finished_at`           DATETIME      DEFAULT NULL COMMENT '结束时间',
    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_import_task_user_request` (`user_id`, `request_id`),
    KEY `idx_import_task_user_time` (`user_id`, `created_at` DESC),
    KEY `idx_import_task_status_time` (`status`, `updated_at`),
    CONSTRAINT `fk_import_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_import_task_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `score_snapshot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步成绩导入任务表';

-- Rollback:
-- DROP TABLE `import_task`;
-- ALTER TABLE `score_snapshot` DROP INDEX `uk_snapshot_user_request`;
-- ALTER TABLE `score_snapshot` DROP COLUMN `request_id`;
