USE `maimai_dx`;

-- Purpose:
-- Optimize the RECENT_SCORES assistant query:
--   WHERE user_id = ?
--   ORDER BY last_play_time DESC, best_play_time DESC, created_at DESC
-- The leading user_id column serves the equality filter, and the following
-- time columns match the descending order used by the business query.
CREATE INDEX `idx_score_user_recent_time`
ON `score_record` (
    `user_id`,
    `last_play_time` DESC,
    `best_play_time` DESC,
    `created_at` DESC
);

-- Rollback:
-- DROP INDEX `idx_score_user_recent_time` ON `score_record`;
