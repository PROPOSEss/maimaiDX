package com.maimai.maidx.validation;

import com.maimai.maidx.dto.MvpDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ScoreImportValidationIT extends ValidationTestSupport {
    @ParameterizedTest
    @ValueSource(ints = {20, 50, 100})
    void commitsBatchAndReturnsQueryableSnapshot(int rows) {
        MvpDtos.ScoreImportRequest input = request();
        input.setRecords(IntStream.rangeClosed(1, rows).mapToObj(i -> item(i, "99.50")).toList());
        var result = mvp.importScores(1L, input);
        assertThat(result.getImportedCount()).isEqualTo(rows);
        assertThat(count("score_snapshot")).isEqualTo(1);
        assertThat(count("score_record")).isEqualTo(rows);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_record WHERE snapshot_id=? AND user_id=1",
                Integer.class, result.getSnapshotId())).isEqualTo(rows);
        assertThat(jdbc.queryForObject("SELECT record_count FROM score_snapshot WHERE id=?", Integer.class,
                result.getSnapshotId())).isEqualTo(rows);
        var b50 = mvp.getB50(1L, result.getSnapshotId());
        assertThat(b50.getRecords()).hasSize(Math.min(50, rows));
        assertThat(b50.getRecords()).allSatisfy(record -> assertThat(record.getRa()).isEqualTo(272));
        assertThat(b50.getRecords()).allSatisfy(record ->
                assertThat(record.getAchievement()).isEqualByComparingTo("99.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"song", "chart"})
    void rollsBackSnapshotAndEarlierRowsOnMiddleFailure(String invalid) {
        var bad = item(2, "99.00");
        if (invalid.equals("song")) bad.setSongId("missing-validation-song");
        else bad.setDifficulty(0);
        assertThatThrownBy(() -> mvp.importScores(1L, request(item(1, "98.00"), bad, item(3, "99.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(invalid.equals("song") ? "歌曲不存在" : "谱面不存在");
        assertThat(count("score_snapshot")).isZero();
        assertThat(count("score_record")).isZero();
    }

    @Test
    void synchronousImportIntentionallyCreatesTwoHistoricalBatches() {
        var input = request(item(1, "99.00"));
        var first = mvp.importScores(1L, input);
        var second = mvp.importScores(1L, input);
        assertThat(first.getSnapshotId()).isNotEqualTo(second.getSnapshotId());
        assertThat(count("score_snapshot")).isEqualTo(2);
        assertThat(count("score_record")).isEqualTo(2);
        assertThat(mvp.getB50(1L, first.getSnapshotId()).getRecords()).hasSize(1);
        assertThat(mvp.getB50(1L, second.getSnapshotId()).getRecords()).hasSize(1);
    }

    @Test
    void keyedImportReusesCommittedSnapshotWithoutExtraRows() {
        var first = mvp.importScores(1L, request(item(1, "99.00")), "same-request");
        var second = mvp.importScores(1L, request(item(1, "99.00")), "same-request");
        assertThat(second.getSnapshotId()).isEqualTo(first.getSnapshotId());
        assertThat(count("score_snapshot")).isEqualTo(1);
        assertThat(count("score_record")).isEqualTo(1);
    }

    @Test
    void sameRequestIdIsScopedToUserAndReadsAreIsolated() {
        var first = mvp.importScores(1L, request(item(1, "99.00")), "shared-request");
        var second = mvp.importScores(2L, request(item(1, "99.00")), "shared-request");
        assertThat(second.getSnapshotId()).isNotEqualTo(first.getSnapshotId());
        assertThat(count("score_snapshot")).isEqualTo(2);
        assertThatThrownBy(() -> mvp.getB50(2L, first.getSnapshotId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changedContentUnderSameRequestIdCurrentlyReturnsFirstResult() {
        var first = mvp.importScores(1L, request(item(1, "98.00")), "content-conflict");
        var second = mvp.importScores(1L, request(item(2, "100.00")), "content-conflict");
        assertThat(second.getSnapshotId()).isEqualTo(first.getSnapshotId());
        assertThat(jdbc.queryForList("SELECT difficulty_id FROM score_record", Long.class)).containsExactly(1L);
        // Characterization of the current contract, not a claim of conflict detection.
    }

    @Test
    void duplicateChartRemainsInHistoryButAppearsOnceInB50() {
        var result = mvp.importScores(1L, request(item(1, "98.00"), item(1, "100.00")));
        assertThat(count("score_record")).isEqualTo(2);
        assertThat(mvp.getB50(1L, result.getSnapshotId()).getRecords())
                .extracting(MvpDtos.ScoreItem::getSongId).containsExactly(code(1));
    }

    @Test
    void regressionV02PreservesPrecisionAndMatchingRa() {
        var result = mvp.importScores(1L, request(item(1, "99.999")));
        var stored = mvp.getB50(1L, result.getSnapshotId()).getRecords().get(0);
        assertThat(stored.getAchievement()).isEqualByComparingTo("99.9990");
        assertThat(stored.getRa()).isEqualTo(274);
        assertThat(stored.getRa()).isNotEqualTo(280); // 100% using the same 13.0 chart
    }
}
