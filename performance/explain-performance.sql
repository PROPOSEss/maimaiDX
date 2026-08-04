SELECT DATABASE() AS database_name;

SELECT COUNT(*) AS score_record_count FROM score_record;

EXPLAIN
SELECT *
FROM score_record
WHERE user_id = 999
ORDER BY last_play_time DESC,
         best_play_time DESC,
         created_at DESC
LIMIT 20;

EXPLAIN
SELECT *
FROM song_difficulty
WHERE is_deleted = 0
  AND level_decimal >= 13.0
  AND level_decimal <= 14.5
  AND level_decimal IS NOT NULL;
