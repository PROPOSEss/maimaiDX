-- Local-only performance seed data.
-- Required client variables:
--   @target_rows: score_record rows to generate, e.g. 10000, 50000, 100000.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @target_rows := IFNULL(@target_rows, 10000);
SET @run_id := DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');

INSERT INTO `user` (`id`, `username`, `openid`, `nickname`, `status`)
VALUES
    (990001, 'perf_user_1', 'perf-openid-990001', 'Performance User 1', 1)
ON DUPLICATE KEY UPDATE
    `username` = VALUES(`username`),
    `nickname` = VALUES(`nickname`),
    `status` = VALUES(`status`);

SET SESSION cte_max_recursion_depth = 1000000;

DROP TEMPORARY TABLE IF EXISTS perf_nums;
CREATE TEMPORARY TABLE perf_nums (`n` INT NOT NULL PRIMARY KEY) AS
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @target_rows
)
SELECT n FROM seq;

DROP TEMPORARY TABLE IF EXISTS perf_charts;
CREATE TEMPORARY TABLE perf_charts AS
SELECT ROW_NUMBER() OVER (ORDER BY sd.id) AS chart_pos,
       sd.id AS difficulty_id,
       sd.song_id,
       sd.level_decimal
FROM song_difficulty sd
WHERE sd.is_deleted = 0;

SET @chart_count := (SELECT COUNT(*) FROM perf_charts);

DROP TEMPORARY TABLE IF EXISTS perf_rows;
CREATE TEMPORARY TABLE perf_rows AS
SELECT n.n,
       990001 AS user_id,
       MOD(n.n - 1, @chart_count) + 1 AS chart_pos
FROM perf_nums n;

INSERT INTO score_snapshot (`user_id`, `request_id`, `source`, `rating`, `record_count`, `imported_at`, `created_at`)
SELECT pr.user_id,
       CONCAT('perf-seed-', @run_id, '-', pr.user_id) AS request_id,
       'perf_seed' AS source,
       12000 + MOD(pr.user_id, 1000) AS rating,
       COUNT(*) AS record_count,
       NOW(),
       NOW()
FROM perf_rows pr
GROUP BY pr.user_id;

DROP TEMPORARY TABLE IF EXISTS perf_snapshots;
CREATE TEMPORARY TABLE perf_snapshots AS
SELECT ss.user_id, ss.id AS snapshot_id
FROM score_snapshot ss
WHERE ss.source = 'perf_seed'
  AND ss.request_id LIKE CONCAT('perf-seed-', @run_id, '-%');

INSERT INTO score_record (
    `snapshot_id`,
    `user_id`,
    `player_id`,
    `song_id`,
    `difficulty_id`,
    `score`,
    `achievement_rate`,
    `rank`,
    `fc`,
    `fs`,
    `sync_status`,
    `dx_score`,
    `ra`,
    `is_b50`,
    `b50_type`,
    `play_count`,
    `best_play_time`,
    `last_play_time`,
    `created_at`,
    `updated_at`
)
SELECT ps.snapshot_id,
       pr.user_id,
       NULL AS player_id,
       pc.song_id,
       pc.difficulty_id,
       900000 + MOD(pr.n * 7919, 110001) AS score,
       CAST(ROUND(90 + MOD(pr.n * 37, 1100) / 100, 2) AS DECIMAL(6, 2)) AS achievement_rate,
       CASE
           WHEN 90 + MOD(pr.n * 37, 1100) / 100 >= 100.5 THEN 'SSS+'
           WHEN 90 + MOD(pr.n * 37, 1100) / 100 >= 100.0 THEN 'SSS'
           WHEN 90 + MOD(pr.n * 37, 1100) / 100 >= 99.0 THEN 'SS'
           WHEN 90 + MOD(pr.n * 37, 1100) / 100 >= 97.0 THEN 'S'
           ELSE 'AAA'
       END AS `rank`,
       CASE WHEN MOD(pr.n, 17) = 0 THEN 'FC' ELSE '' END AS fc,
       CASE WHEN MOD(pr.n, 23) = 0 THEN 'FS' ELSE '' END AS fs,
       NULL AS sync_status,
       850000 + MOD(pr.n * 3571, 160001) AS dx_score,
       CAST(FLOOR(pc.level_decimal * (90 + MOD(pr.n * 37, 1100) / 100) * 2) AS SIGNED) AS ra,
       0 AS is_b50,
       NULL AS b50_type,
       1 + MOD(pr.n, 20) AS play_count,
       DATE_SUB(DATE_SUB(NOW(), INTERVAL MOD(pr.n, 180) DAY), INTERVAL MOD(pr.n * 31, 86400) SECOND) AS best_play_time,
       DATE_SUB(DATE_SUB(NOW(), INTERVAL MOD(pr.n, 180) DAY), INTERVAL MOD(pr.n * 37, 86400) SECOND) AS last_play_time,
       DATE_SUB(DATE_SUB(NOW(), INTERVAL MOD(pr.n, 180) DAY), INTERVAL MOD(pr.n * 37, 86400) SECOND) AS created_at,
       NOW() AS updated_at
FROM perf_rows pr
JOIN perf_charts pc ON pc.chart_pos = pr.chart_pos
JOIN perf_snapshots ps ON ps.user_id = pr.user_id;

SELECT @target_rows AS target_rows,
       COUNT(*) AS inserted_rows
FROM score_record sr
JOIN score_snapshot ss ON ss.id = sr.snapshot_id
WHERE ss.request_id LIKE CONCAT('perf-seed-', @run_id, '-%');
