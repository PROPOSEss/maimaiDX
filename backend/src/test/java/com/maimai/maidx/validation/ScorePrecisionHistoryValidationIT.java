package com.maimai.maidx.validation;

import com.maimai.maidx.dto.MvpDtos;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.AopTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ScorePrecisionHistoryValidationIT extends ValidationTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    com.maimai.maidx.assistant.service.AssistantBusinessService assistantBusiness;
    @org.springframework.beans.factory.annotation.Autowired
    com.maimai.maidx.service.ScoreRecordService legacyScores;
    @org.springframework.beans.factory.annotation.Autowired
    com.maimai.maidx.service.ScoreAnalysisService legacyAnalysis;
    @ParameterizedTest
    @CsvSource({"99.9990,274", "99.9999,274", "100.0000,280", "100.4999,282",
            "100.5000,292", "100.5001,292", "101.0000,292", "99.99,274", "99.999,274"})
    void storesFourDecimalsAndApiAndRecalculationAgree(String raw, int ra) throws Exception {
        long id = mvp.importScores(1L, request(item(1, raw))).getSnapshotId();
        BigDecimal stored = jdbc.queryForObject("SELECT achievement_rate FROM score_record WHERE snapshot_id=?", BigDecimal.class, id);
        assertThat(stored).isEqualByComparingTo(raw);
        assertThat(stored.scale()).isEqualTo(4);
        var score = mvp.getB50(1L, id).getRecords().get(0);
        assertThat(score.getRa()).isEqualTo(ra);
        assertThat(score.getAchievement()).isEqualByComparingTo(raw);
        var target = AopTestUtils.getUltimateTargetObject(mvp);
        var method = target.getClass().getDeclaredMethod("calculateRa", BigDecimal.class, BigDecimal.class);
        method.setAccessible(true);
        assertThat(method.invoke(target, new BigDecimal("13.0"), stored)).isEqualTo(ra);
        JsonNode api = http.getForObject("/player/1/b50?snapshotId=" + id, JsonNode.class);
        assertThat(api.path("data").path("records").get(0).path("achievement").decimalValue()).isEqualByComparingTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.0001", "101.0001", "99.99999", "100.00000"})
    void rejectsIllegalRangeOrPrecisionBeforeAnyCommittedRows(String raw) {
        assertThatThrownBy(() -> mvp.importScores(1L, request(item(1, raw))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("score_record")).isZero();
        assertThat(count("score_snapshot")).isZero();
        JsonNode response = http.postForObject("/player/import?userId=1", request(item(1, raw)), JsonNode.class);
        assertThat(response.path("code").asInt()).isNotEqualTo(200);
        assertThat(count("score_record")).isZero();
    }

    @Test
    void keepsEverySubmittedRowButSelectsOneBestPerChart() {
        long id = mvp.importScores(1L, request(item(1, "98.5000"), item(1, "99.9999"),
                item(1, "100.0000"), item(2, "99.5000"))).getSnapshotId();
        assertThat(count("score_record")).isEqualTo(4);
        assertThat(mvp.getB50(1L, id).getRecords()).hasSize(2);
        assertThat(mvp.getB50(1L, id).getRecords()).filteredOn(r -> r.getSongId().equals(code(1)))
                .singleElement().satisfies(r -> assertThat(r.getAchievement()).isEqualByComparingTo("100.0000"));
    }

    @Test
    void identicalRecordsRemainInHistoryAndStableIdBreaksTies() {
        long id = mvp.importScores(1L, request(item(1, "99.9999"), item(1, "99.9999"))).getSnapshotId();
        assertThat(count("score_record")).isEqualTo(2);
        assertThat(mvp.getB50(1L, id).getRecords()).hasSize(1);
        long winner = jdbc.queryForObject("SELECT MAX(id) FROM score_record", Long.class);
        assertThat(jdbc.queryForObject("SELECT id FROM score_record WHERE is_b50=1", Long.class)).isEqualTo(winner);
    }

    @Test
    void differentDifficultiesAndDistinctChartIdsAreNotMerged() {
        jdbc.update("INSERT INTO song_difficulty(id,song_id,difficulty,level,level_decimal) VALUES (501,1,2,12,12.0)");
        var expert = item(1, "99.5000"); expert.setDifficulty(2);
        long id = mvp.importScores(1L, request(item(1, "99.5000"), expert, item(2, "99.5000"))).getSnapshotId();
        assertThat(mvp.getB50(1L, id).getRecords()).hasSize(3);
    }

    @Test
    void deduplicatesBeforeLimitAndGrowthAndRecommendationUseBest() {
        var records = new java.util.ArrayList<MvpDtos.ScoreImportItem>();
        IntStream.rangeClosed(1, 51).forEach(i -> records.add(item(i, i == 51 ? "97.0000" : "99.0000")));
        records.add(item(1, "100.5000"));
        records.add(item(1, "100.5000"));
        long first = mvp.importScores(1L, request(records.toArray(MvpDtos.ScoreImportItem[]::new))).getSnapshotId();
        assertThat(count("score_record")).isEqualTo(53);
        assertThat(mvp.getB50(1L, first).getRecords()).hasSize(50);
        assertThat(mvp.getB50(1L, first).getRecords().stream().map(MvpDtos.ScoreItem::getSongId).distinct().count()).isEqualTo(50);
        mvp.generateRecommendations(1L, first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_item WHERE snapshot_id=? AND chart_id=1", Integer.class, first)).isZero();
        long second = mvp.importScores(1L, request(item(1, "101.0000"))).getSnapshotId();
        assertThat(mvp.getGrowth(1L, first, second).getImprovedScores()).isEmpty(); // RA capped at 100.5
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_record WHERE snapshot_id=?", Integer.class, first)).isEqualTo(53);
    }

    @Test
    void noActualPlayTimeIsReturnedAsNullRatherThanUploadTime() {
        long id = mvp.importScores(1L, request(item(1, "99.5000"))).getSnapshotId();
        assertThat(jdbc.queryForObject("SELECT last_play_time FROM score_record", java.sql.Timestamp.class)).isNull();
        JsonNode api = http.getForObject("/player/1/history?snapshotId=" + id, JsonNode.class);
        assertThat(api.path("code").asInt()).isEqualTo(200);
        assertThat(api.path("data").size()).isEqualTo(1);
        assertThat(api.path("data").get(0).path("playedAt").isNull() || api.path("data").get(0).path("playedAt").isMissingNode()).isTrue();
        assertThat(api.path("data").get(0).path("uploadedAt").asText()).isNotBlank();
    }

    @Test
    void actualTimeAndDxScoreBreakTiesWithoutDeletingHistory() throws Exception {
        String body = "{\"records\":[{\"songId\":\"v001\",\"difficulty\":3,\"achievement\":99.9999,\"dxScore\":100,\"playedAt\":\"2026-01-01T10:00:00\"},"
                + "{\"songId\":\"v001\",\"difficulty\":3,\"achievement\":99.9999,\"dxScore\":200,\"playedAt\":\"2026-01-01T09:00:00\"},"
                + "{\"songId\":\"v001\",\"difficulty\":3,\"achievement\":99.9999,\"dxScore\":200,\"playedAt\":\"2026-01-01T11:00:00\"}]}";
        long id = mvp.importScores(1L, mapper.readValue(body, MvpDtos.ScoreImportRequest.class)).getSnapshotId();
        assertThat(count("score_record")).isEqualTo(3);
        assertThat(mvp.getB50(1L, id).getRecords()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT last_play_time FROM score_record WHERE is_b50=1", java.sql.Timestamp.class))
                .isEqualTo(java.sql.Timestamp.valueOf("2026-01-01 11:00:00"));
    }

    @Test
    void snapshotHistoryAndCrossSnapshotTopUseDifferentSemantics() {
        long first = mvp.importScores(1L, request(item(1, "100.0000"), item(1, "100.0000"))).getSnapshotId();
        long second = mvp.importScores(1L, request(item(1, "98.0000"), item(2, "99.9999"))).getSnapshotId();
        assertThat(mvp.getSnapshotHistory(1L, first, 1, 20)).hasSize(2);
        assertThat(mvp.getSnapshotHistory(1L, second, 1, 20)).hasSize(2);
        assertThat(mvp.getSnapshotHistory(1L, first, 2, 1)).hasSize(1);
        assertThatThrownBy(() -> mvp.getSnapshotHistory(2L, first, 1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThat(assistantBusiness.getRecentScores(1L, 20).records()).hasSize(4);
        assertThat(assistantBusiness.getTopScores(1L, 50).records()).hasSize(2)
                .anySatisfy(record -> {
                    assertThat(record.getDifficultyId()).isEqualTo(1L);
                    assertThat(record.getAchievementRate()).isEqualByComparingTo("100.0000");
                });
    }

    @Test
    void legacyPlayerAggregateUsesDecimalRaButIsNotPlayHistory() {
        jdbc.update("INSERT INTO player_bind(id,user_id,ma_id) VALUES (11,1,'validation-player')");
        for (String achievement : List.of("99.9990", "100.0000", "98.0000")) {
            var record = new com.maimai.maidx.entity.ScoreRecord();
            record.setPlayerId(11L); record.setDifficultyId(1L); record.setSongId(1L);
            record.setAchievementRate(new BigDecimal(achievement)); record.setScore(1);
            legacyScores.saveOrUpdateScore(record);
        }
        assertThat(count("score_record")).isEqualTo(1); // explicitly legacy aggregate, not a snapshot
        assertThat(legacyScores.getPlayerB50(11L)).singleElement().satisfies(record -> {
            assertThat(record.getAchievementRate()).isEqualByComparingTo("100.0000");
            assertThat(record.getRa()).isEqualTo(280);
            assertThat(record.getLastPlayTime()).isNull();
        });
        assertThat(legacyAnalysis.getB50(1L)).singleElement()
                .satisfies(record -> assertThat(record.getDxRating()).isEqualTo(280.0));
        assertThat(legacyAnalysis.estimateDxRating(1L)).isEqualTo(280.0);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "49.9999,45", "50.0000,52", "59.9999,62", "60.0000,74",
            "69.9999,87", "70.0000,101", "74.9999,109", "75.0000,117", "79.9999,124",
            "80.0000,141", "89.9999,159", "90.0000,177", "93.9999,185", "94.0000,205",
            "96.9999,211", "97.0000,252", "97.9999,254", "98.0000,258", "98.9999,261",
            "99.0000,267", "99.4999,269", "99.5000,272"})
    void ratingIntervalsDoNotRoundAcrossBoundaries(String raw, int expected) {
        assertThat(com.maimai.maidx.utils.ScoreRules.calculateRa(new BigDecimal("13.0"), new BigDecimal(raw)))
                .isEqualTo(expected);
    }

    @Test
    void legacySchemaMigrationIsRepeatableAndDoesNotInventLostDigits() throws Exception {
        jdbc.execute("ALTER TABLE score_record MODIFY achievement_rate DECIMAL(6,2) DEFAULT NULL");
        jdbc.execute("ALTER TABLE recommendation_item MODIFY current_achievement DECIMAL(6,3) DEFAULT NULL, MODIFY target_achievement DECIMAL(6,3) NOT NULL");
        try {
            jdbc.update("INSERT INTO score_record(user_id,song_id,difficulty_id,achievement_rate,score) VALUES (1,1,1,99.99,0)");
            migratePrecision();
            migratePrecision();
            assertThat(jdbc.queryForObject("SELECT achievement_rate FROM score_record", BigDecimal.class))
                    .isEqualByComparingTo("99.9900");
            assertThat(jdbc.queryForObject("SELECT numeric_scale FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='score_record' AND column_name='achievement_rate'", Integer.class)).isEqualTo(4);
        } finally {
            migratePrecision();
        }
    }

    @Test
    void migrationRejectsInvalidLegacyValuesBeforeAlteringColumns() throws Exception {
        jdbc.execute("ALTER TABLE score_record MODIFY achievement_rate DECIMAL(6,2) DEFAULT NULL");
        try {
            jdbc.update("INSERT INTO score_record(user_id,song_id,difficulty_id,achievement_rate,score) VALUES (1,1,1,101.01,0)");
            assertThatThrownBy(this::migratePrecision).isInstanceOf(java.sql.SQLException.class);
            assertThat(jdbc.queryForObject("SELECT numeric_scale FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='score_record' AND column_name='achievement_rate'", Integer.class)).isEqualTo(2);
        } finally {
            jdbc.update("DELETE FROM score_record"); // guarded, disposable validation schema only
            migratePrecision();
        }
    }

    private void migratePrecision() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("../database/score_precision_migration.sql"));
        sql = sql.replace("DELIMITER $$", "").replace("DELIMITER ;", "");
        try (var connection = java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection();
             var statement = connection.createStatement()) {
            for (String part : sql.split("\\$\\$")) if (!part.isBlank()) statement.execute(part);
        }
    }
}
