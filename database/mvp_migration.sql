-- ============================================================
-- MaiDX Insight MVP migration
-- Purpose: support score snapshots, B50 analysis and rule recommendations.
-- Note: execute once on the existing maimai_dx database before using MVP APIs.
-- ============================================================

USE maimai_dx;

ALTER TABLE `song`
    ADD COLUMN `is_new` TINYINT NOT NULL DEFAULT 0 COMMENT '是否新曲: 0=旧曲, 1=新曲' AFTER `genre`;

ALTER TABLE `song_difficulty`
    ADD COLUMN `fit_diff` DECIMAL(4,2) DEFAULT NULL COMMENT '拟合难度/体感难度' AFTER `level_decimal`,
    ADD COLUMN `charter` VARCHAR(128) DEFAULT NULL COMMENT '谱师' AFTER `break_count`,
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `created_at`;

CREATE TABLE `score_snapshot` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '成绩快照ID',
    `user_id`      BIGINT      NOT NULL COMMENT '用户ID',
    `source`       VARCHAR(32) NOT NULL DEFAULT 'manual_json' COMMENT '导入来源',
    `rating`       INT         DEFAULT 0 COMMENT '导入时Rating',
    `record_count` INT         DEFAULT 0 COMMENT '本次导入成绩条数',
    `imported_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_snapshot_user_time` (`user_id`, `imported_at` DESC),
    CONSTRAINT `fk_snapshot_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩导入快照表';

ALTER TABLE `score_record`
    MODIFY COLUMN `player_id` BIGINT DEFAULT NULL COMMENT '兼容旧版MAID绑定玩家ID，MVP导入可为空',
    ADD COLUMN `snapshot_id` BIGINT DEFAULT NULL COMMENT '成绩快照ID' AFTER `id`,
    ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT '用户ID' AFTER `snapshot_id`,
    ADD COLUMN `song_id` BIGINT DEFAULT NULL COMMENT '歌曲ID' AFTER `player_id`,
    ADD COLUMN `ra` INT DEFAULT NULL COMMENT '单谱面Rating贡献' AFTER `dx_score`,
    ADD COLUMN `is_b50` TINYINT NOT NULL DEFAULT 0 COMMENT '是否进入B50' AFTER `ra`,
    ADD COLUMN `b50_type` VARCHAR(16) DEFAULT NULL COMMENT 'B50类型: new/old/all' AFTER `is_b50`,
    ADD INDEX `idx_score_snapshot` (`snapshot_id`),
    ADD INDEX `idx_score_user_b50` (`user_id`, `is_b50`, `ra` DESC),
    ADD CONSTRAINT `fk_score_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `score_snapshot` (`id`),
    ADD CONSTRAINT `fk_score_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    ADD CONSTRAINT `fk_score_song` FOREIGN KEY (`song_id`) REFERENCES `song` (`id`);

CREATE TABLE `recommendation_item` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '推荐ID',
    `user_id`             BIGINT        NOT NULL COMMENT '用户ID',
    `snapshot_id`         BIGINT        NOT NULL COMMENT '基于哪个快照生成',
    `song_id`             BIGINT        NOT NULL COMMENT '歌曲ID',
    `chart_id`            BIGINT        NOT NULL COMMENT '谱面ID',
    `current_achievement` DECIMAL(6,3)  DEFAULT NULL COMMENT '当前达成率',
    `target_achievement`  DECIMAL(6,3)  NOT NULL COMMENT '目标达成率',
    `expected_gain`       INT           DEFAULT 0 COMMENT '预计Rating提升',
    `difficulty_level`    VARCHAR(16)   DEFAULT NULL COMMENT '推荐难度等级说明',
    `recommend_score`     DECIMAL(8,2)  NOT NULL DEFAULT 0 COMMENT '推荐分',
    `reason`              VARCHAR(512)  NOT NULL COMMENT '推荐理由',
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_recommend_user_snapshot` (`user_id`, `snapshot_id`),
    KEY `idx_recommend_score` (`recommend_score` DESC),
    CONSTRAINT `fk_recommend_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_recommend_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `score_snapshot` (`id`),
    CONSTRAINT `fk_recommend_song` FOREIGN KEY (`song_id`) REFERENCES `song` (`id`),
    CONSTRAINT `fk_recommend_chart` FOREIGN KEY (`chart_id`) REFERENCES `song_difficulty` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则推歌推荐结果表';