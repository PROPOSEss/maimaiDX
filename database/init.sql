-- ============================================================
-- MaiDX Insight 数据库初始化脚本
-- 版本: V1.1
-- 创建时间: 2026-06-01
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS maimai_dx DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE maimai_dx;

-- ============================================================
-- 一、用户相关表
-- ============================================================

-- 1.1 用户表（微信登录用户）
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `openid`      VARCHAR(64)  NOT NULL                COMMENT '微信openid',
    `union_id`    VARCHAR(64)  DEFAULT NULL            COMMENT '微信unionId',
    `session_key` VARCHAR(64)  DEFAULT NULL            COMMENT '微信会话密钥（不返回前端）',
    `nickname`    VARCHAR(64)  DEFAULT NULL            COMMENT '用户昵称',
    `avatar`      VARCHAR(256) DEFAULT NULL            COMMENT '头像URL',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态: 1=正常, 0=禁用',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_union_id` (`union_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信用户表';

-- 1.2 玩家绑定表（MAID绑定）
CREATE TABLE IF NOT EXISTS `player_bind` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
    `user_id`         BIGINT       NOT NULL                COMMENT '关联用户ID',
    `ma_id`           VARCHAR(32)  NOT NULL                COMMENT '舞萌MAID',
    `player_name`     VARCHAR(64)  DEFAULT NULL            COMMENT '玩家昵称',
    `rating`          INT          DEFAULT 0               COMMENT '当前Rating',
    `max_rating`      INT          DEFAULT 0               COMMENT '最高Rating',
    `class_rank`     VARCHAR(32)  DEFAULT NULL            COMMENT '段位（爱/真/超/舞神等）',
    `last_sync_time`  DATETIME     DEFAULT NULL            COMMENT '最后同步时间',
    `sync_status`     TINYINT      NOT NULL DEFAULT 0     COMMENT '同步状态: 0=未同步, 1=同步中, 2=已同步, -1=同步失败',
    `bind_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ma_id` (`ma_id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    CONSTRAINT `fk_player_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家MAID绑定表';

-- ============================================================
-- 二、歌曲与谱面相关表
-- ============================================================

-- 2.1 歌曲基本信息表
CREATE TABLE IF NOT EXISTS `song` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '歌曲ID',
    `song_id`     VARCHAR(32)  NOT NULL                COMMENT '歌曲唯一标识',
    `title`       VARCHAR(128) NOT NULL                COMMENT '歌曲标题',
    `title_en`    VARCHAR(128) DEFAULT NULL            COMMENT '英文标题',
    `artist`      VARCHAR(128) DEFAULT NULL            COMMENT '艺术家',
    `artist_en`   VARCHAR(128) DEFAULT NULL            COMMENT '艺术家英文名',
    `bpm`         INT          DEFAULT NULL            COMMENT 'BPM',
    `version`     VARCHAR(32)  DEFAULT NULL            COMMENT '版本（maimai/maimai PLUS/niconico等）',
    `genre`       VARCHAR(32)  DEFAULT NULL            COMMENT '曲风分类',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标记: 0=正常, 1=已删除',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_song_id` (`song_id`),
    KEY `idx_title` (`title`),
    KEY `idx_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='歌曲基本信息表';

-- 2.2 谱面难度表（每首歌每个难度一行）
CREATE TABLE IF NOT EXISTS `song_difficulty` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '谱面ID',
    `song_id`       BIGINT       NOT NULL                COMMENT '关联歌曲ID',
    `difficulty`    TINYINT      NOT NULL                COMMENT '难度: 0=BASIC, 1=ADVANCED, 2=EXPERT, 3=MASTER, 4=Re:MASTER',
    `level`         INT          NOT NULL                COMMENT '等级(1-15)',
    `level_decimal` DECIMAL(3,1) DEFAULT NULL            COMMENT '等级小数(如12.4)',
    `note_count`    INT          DEFAULT NULL            COMMENT '音符总数',
    `tap_count`     INT          DEFAULT NULL            COMMENT 'Tap数量',
    `hold_count`    INT          DEFAULT NULL            COMMENT 'Hold数量',
    `slide_count`   INT          DEFAULT NULL            COMMENT 'Slide数量',
    `touch_count`   INT          DEFAULT NULL            COMMENT 'Touch数量',
    `break_count`   INT          DEFAULT NULL            COMMENT 'Break数量',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标记',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_song_difficulty` (`song_id`, `difficulty`),
    CONSTRAINT `fk_difficulty_song` FOREIGN KEY (`song_id`) REFERENCES `song` (`id`),
    KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='谱面难度表';

-- 2.3 谱面标签表（每首谱面可有多个标签）
CREATE TABLE IF NOT EXISTS `song_feature` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `difficulty_id` BIGINT       NOT NULL                COMMENT '关联谱面ID',
    `tag_name`      VARCHAR(32)  NOT NULL                COMMENT '标签名称',
    `weight`        DECIMAL(5,2) NOT NULL DEFAULT 0      COMMENT '标签权重(0-100)',
    `source`        TINYINT      NOT NULL DEFAULT 1      COMMENT '来源: 1=系统预设, 2=社区投票',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_difficulty_tag` (`difficulty_id`, `tag_name`, `source`),
    CONSTRAINT `fk_feature_difficulty` FOREIGN KEY (`difficulty_id`) REFERENCES `song_difficulty` (`id`),
    KEY `idx_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='谱面标签表';

-- ============================================================
-- 三、成绩相关表
-- ============================================================

-- 3.1 成绩记录表
CREATE TABLE IF NOT EXISTS `score_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '成绩ID',
    `player_id`       BIGINT       NOT NULL                COMMENT '关联玩家ID',
    `difficulty_id`   BIGINT       NOT NULL                COMMENT '关联谱面ID',
    `score`           INT          NOT NULL                COMMENT '得分(0-1010000)',
    `achievement_rate` DECIMAL(6,2) DEFAULT NULL           COMMENT '达成率(%)',
    `rank`            VARCHAR(8)   DEFAULT NULL            COMMENT '评级(SSS+/SSS/SS/S/AAA等)',
    `fc`              VARCHAR(16)  DEFAULT NULL            COMMENT 'FC状态(FC/AP)',
    `fs`              VARCHAR(16)  DEFAULT NULL            COMMENT 'FS状态(FS/FS+/FDX/FDX+)',
    `sync_status`     TINYINT      DEFAULT NULL            COMMENT '同步状态(Sync等)',
    `dx_score`        INT          DEFAULT NULL            COMMENT 'DX分数',
    `play_count`      INT          NOT NULL DEFAULT 1      COMMENT '游玩次数',
    `best_play_time`  DATETIME     DEFAULT NULL            COMMENT '最佳成绩时间',
    `last_play_time`  DATETIME     DEFAULT NULL            COMMENT '最后游玩时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_difficulty` (`player_id`, `difficulty_id`),
    CONSTRAINT `fk_score_player` FOREIGN KEY (`player_id`) REFERENCES `player_bind` (`id`),
    CONSTRAINT `fk_score_difficulty` FOREIGN KEY (`difficulty_id`) REFERENCES `song_difficulty` (`id`),
    KEY `idx_score` (`score`),
    KEY `idx_rank` (`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩记录表';

-- 3.2 玩家能力画像表
CREATE TABLE IF NOT EXISTS `player_ability` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '能力ID',
    `player_id`       BIGINT       NOT NULL                COMMENT '关联玩家ID',
    `tag_name`        VARCHAR(32)  NOT NULL                COMMENT '标签名称',
    `avg_score`       DECIMAL(10,2) DEFAULT NULL            COMMENT '该标签下平均分',
    `avg_rating`      DECIMAL(8,2)  DEFAULT NULL            COMMENT '该标签下平均Rating',
    `total_songs`     INT           DEFAULT 0              COMMENT '该标签下总谱面数',
    `sssp_count`      INT           DEFAULT 0              COMMENT 'SSS+数量',
    `weakness_score`  DECIMAL(5,2)  DEFAULT NULL            COMMENT '弱点评分(0-100, 越高越弱)',
    `is_weakness`     TINYINT       NOT NULL DEFAULT 0      COMMENT '是否为弱点: 0=否, 1=是',
    `last_update`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_tag` (`player_id`, `tag_name`),
    CONSTRAINT `fk_ability_player` FOREIGN KEY (`player_id`) REFERENCES `player_bind` (`id`),
    KEY `idx_weakness` (`is_weakness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家能力画像表';

-- ============================================================
-- 四、社区投票相关表
-- ============================================================

-- 4.1 投票记录表
CREATE TABLE IF NOT EXISTS `song_difficulty_vote` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '投票ID',
    `user_id`         BIGINT       NOT NULL                COMMENT '投票用户ID',
    `difficulty_id`   BIGINT       NOT NULL                COMMENT '关联谱面ID',
    `tag_name`        VARCHAR(32)  NOT NULL                COMMENT '投票标签',
    `vote_weight`     DECIMAL(5,2)  NOT NULL                COMMENT '投票权重(Rating/10000)',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_diff_tag` (`user_id`, `difficulty_id`, `tag_name`),
    CONSTRAINT `fk_vote_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_vote_difficulty` FOREIGN KEY (`difficulty_id`) REFERENCES `song_difficulty` (`id`),
    KEY `idx_tag` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='谱面标签投票表';

-- 4.2 训练建议记录表（缓存生成的训练建议）
CREATE TABLE IF NOT EXISTS `training_suggestion` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '建议ID',
    `player_id`       BIGINT       NOT NULL                COMMENT '关联玩家ID',
    `tag_name`        VARCHAR(32)  NOT NULL                COMMENT '目标提升标签',
    `difficulty_id`   BIGINT       NOT NULL                COMMENT '推荐谱面ID',
    `priority`        TINYINT      NOT NULL DEFAULT 1      COMMENT '优先级: 1=高, 2=中, 3=低',
    `reason`          VARCHAR(256) DEFAULT NULL            COMMENT '推荐理由',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_suggestion_player` FOREIGN KEY (`player_id`) REFERENCES `player_bind` (`id`),
    CONSTRAINT `fk_suggestion_difficulty` FOREIGN KEY (`difficulty_id`) REFERENCES `song_difficulty` (`id`),
    KEY `idx_player_tag` (`player_id`, `tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练建议记录表';

-- ============================================================
-- 五、系统配置表
-- ============================================================

-- 5.1 标签定义表（标签字典）
CREATE TABLE IF NOT EXISTS `tag_definition` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '标签定义ID',
    `tag_name`    VARCHAR(32)  NOT NULL                COMMENT '标签名称',
    `tag_code`    VARCHAR(32)  NOT NULL                COMMENT '标签编码(英文)',
    `description` VARCHAR(128) DEFAULT NULL            COMMENT '标签描述',
    `sort_order`  TINYINT      NOT NULL DEFAULT 0      COMMENT '排序',
    `is_active`   TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_code` (`tag_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签定义表';

-- ============================================================
-- 六、初始数据
-- ============================================================

-- 初始化标签定义（V1.1 标签体系）
INSERT INTO `tag_definition` (`tag_name`, `tag_code`, `description`, `sort_order`) VALUES
('反手',         'cross_hand',    '需要频繁左右手交叉处理',     1),
('交互',         'alternate',     '左右手高速交替',           2),
('撞手',         'clash',         '双手轨迹冲突',            3),
('纵连',         'vertical',      '同一区域连续高速敲击',      4),
('体力',         'stamina',       '高密度、持续输出',         5),
('读谱',         'reading',       '谱面结构复杂，需要较强视谱能力', 6),
('节奏难',       'rhythm',        '节奏复杂、多变',          7),
('错位星星',     'star_slide',    '复杂星星轨迹及滑键连续操作',  8),
('Touch圈',      'touch',         'Touch区域参与度较高',      9);

-- 初始难度枚举参考
-- difficulty: 0=BASIC, 1=ADVANCED, 2=EXPERT, 3=MASTER, 4=Re:MASTER

-- ============================================================
-- 七、索引优化
-- ============================================================

-- 成绩查询优化索引（按Rating范围查询时使用）
ALTER TABLE `score_record` ADD INDEX `idx_player_score` (`player_id`, `score` DESC);

-- 能力画像查询优化
ALTER TABLE `player_ability` ADD INDEX `idx_player_weakness` (`player_id`, `is_weakness`, `weakness_score` DESC);

-- 投票统计优化
ALTER TABLE `song_difficulty_vote` ADD INDEX `idx_diff_tag` (`difficulty_id`, `tag_name`);

-- ============================================================
-- 完成
-- ============================================================
-- 共创建 8 张表：
--   user / player_bind / song / song_difficulty / song_feature
--   score_record / player_ability / song_difficulty_vote / training_suggestion / tag_definition
-- ============================================================
