-- MySQL 8: backup first, pause imports, select the intended database explicitly.
-- No USE statement: the operator must provide the target schema with mysql --database.
-- ALTER TABLE auto-commits; rerunning after partial completion is supported.
-- Lost historical fractional digits are NOT reconstructed and RA is NOT backfilled.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_score_precision$$
CREATE PROCEDURE migrate_score_precision()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND data_type='decimal' AND numeric_scale <= 4
          AND ((table_name='score_record' AND column_name='achievement_rate')
            OR (table_name='recommendation_item' AND column_name IN ('current_achievement','target_achievement')))) <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected three DECIMAL achievement columns with scale <= 4';
    END IF;
    IF EXISTS (SELECT 1 FROM score_record WHERE achievement_rate < 0 OR achievement_rate > 101)
       OR EXISTS (SELECT 1 FROM recommendation_item WHERE current_achievement < 0 OR current_achievement > 101
                    OR target_achievement < 0 OR target_achievement > 101) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid legacy achievements: audit manually before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
               AND table_name='score_record' AND column_name='achievement_rate'
               AND (numeric_precision<>7 OR numeric_scale<>4)) THEN
        ALTER TABLE score_record MODIFY achievement_rate DECIMAL(7,4) DEFAULT NULL COMMENT '达成率(%)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
               AND table_name='recommendation_item' AND column_name IN ('current_achievement','target_achievement')
               AND (numeric_precision<>7 OR numeric_scale<>4)) THEN
        ALTER TABLE recommendation_item
            MODIFY current_achievement DECIMAL(7,4) DEFAULT NULL COMMENT '当前达成率',
            MODIFY target_achievement DECIMAL(7,4) NOT NULL COMMENT '目标达成率';
    END IF;
END$$
CALL migrate_score_precision()$$
DROP PROCEDURE IF EXISTS migrate_score_precision$$
DELIMITER ;
