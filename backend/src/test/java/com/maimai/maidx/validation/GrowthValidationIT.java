package com.maimai.maidx.validation;

import com.maimai.maidx.dto.MvpDtos;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GrowthValidationIT extends ValidationTestSupport {
    @Test
    void matchesChartsAndComputesKnownGrowthFromCommittedSnapshots() {
        var before = request(item(1, "98.00"), item(2, "99.00"), item(3, "97.00"));
        var after = request(item(1, "100.00"), item(2, "99.50"), item(4, "98.00"));
        after.setRating(12100);
        long first = mvp.importScores(1L, before).getSnapshotId();
        long second = mvp.importScores(1L, after).getSnapshotId();
        var growth = mvp.getGrowth(1L, first, second);
        assertThat(growth.getNewB50()).extracting(MvpDtos.GrowthScoreChangeItem::getSongId).containsExactly(code(4));
        assertThat(growth.getDroppedB50()).extracting(MvpDtos.GrowthScoreChangeItem::getSongId).containsExactly(code(3));
        assertThat(growth.getImprovedScores()).extracting(MvpDtos.GrowthScoreChangeItem::getSongId)
                .containsExactlyInAnyOrder(code(1), code(2));
        assertThat(growth.getImprovedScores()).filteredOn(i -> i.getSongId().equals(code(1)))
                .singleElement().satisfies(i -> assertThat(i.getRaDelta()).isEqualTo(22));
        assertThat(growth.getImprovedScores()).filteredOn(i -> i.getSongId().equals(code(2)))
                .singleElement().satisfies(i -> assertThat(i.getRaDelta()).isEqualTo(5));
        assertThat(growth.getRatingDelta()).isEqualTo(100);
        assertThat(growth.getEdgeRaDelta()).isEqualTo(6);
        assertThat(mvp.getB50(1L, first).getRecords()).extracting(MvpDtos.ScoreItem::getRa).containsExactly(267, 258, 252);
        assertThat(mvp.getB50(1L, second).getRecords()).extracting(MvpDtos.ScoreItem::getRa).containsExactly(280, 272, 258);
        assertThat(count("score_snapshot")).isEqualTo(2);
        assertThat(count("score_record")).isEqualTo(6);
    }

    @Test
    void noHistoryAndUnknownSnapshotAreRejected() {
        assertThatThrownBy(() -> mvp.getB50(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mvp.getB50(1L, 999999L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mvp.getGrowth(1L, null, 1L)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void oneSnapshotCanBeQueriedAndComparedToItself() {
        long id = mvp.importScores(1L, request(item(1, "99.00"))).getSnapshotId();
        assertThat(mvp.getB50(1L, null).getSnapshotId()).isEqualTo(id);
        var result = mvp.getGrowth(1L, id, id);
        assertThat(result.getNewB50()).isEmpty();
        assertThat(result.getDroppedB50()).isEmpty();
        assertThat(result.getImprovedScores()).isEmpty();
        assertThat(result.getRatingDelta()).isZero();
    }

    @Test
    void identicalBatchesKeepHistoryWithoutInventingImprovements() {
        var input = request(item(1, "99.00"), item(2, "98.00"));
        long first = mvp.importScores(1L, input).getSnapshotId();
        long second = mvp.importScores(1L, input).getSnapshotId();
        var result = mvp.getGrowth(1L, first, second);
        assertThat(result.getNewB50()).isEmpty();
        assertThat(result.getDroppedB50()).isEmpty();
        assertThat(result.getImprovedScores()).isEmpty();
        assertThat(count("score_record")).isEqualTo(4);
    }

    @Test
    void disjointBatchesHaveOnlyNewAndDroppedCharts() {
        long first = mvp.importScores(1L, request(item(1, "99.00"))).getSnapshotId();
        long second = mvp.importScores(1L, request(item(2, "99.00"))).getSnapshotId();
        var result = mvp.getGrowth(1L, first, second);
        assertThat(result.getNewB50()).extracting(MvpDtos.GrowthScoreChangeItem::getSongId).containsExactly(code(2));
        assertThat(result.getDroppedB50()).extracting(MvpDtos.GrowthScoreChangeItem::getSongId).containsExactly(code(1));
        assertThat(result.getImprovedScores()).isEmpty();
    }

    @Test
    void repeatedChartUsesSameBestSelectionInGrowthAndB50() {
        long first = mvp.importScores(1L, request(item(1, "98.00"), item(1, "99.00"))).getSnapshotId();
        long second = mvp.importScores(1L, request(item(1, "100.00"))).getSnapshotId();
        assertThat(mvp.getB50(1L, first).getRecords()).hasSize(1);
        var result = mvp.getGrowth(1L, first, second);
        assertThat(result.getImprovedScores()).singleElement()
                .satisfies(i -> assertThat(i.getRaDelta()).isEqualTo(13));
        assertThat(result.getNewB50()).isEmpty();
    }

    @Test
    void top50BoundaryMeansLeavingTop50NotDeletionFromImport() {
        var before = request();
        before.setRecords(java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> item(i, i == 51 ? "97.00" : "99.00")).toList());
        var after = request();
        after.setRecords(java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> item(i, i == 1 ? "97.00" : "100.00")).toList());
        long first = mvp.importScores(1L, before).getSnapshotId();
        long second = mvp.importScores(1L, after).getSnapshotId();
        var result = mvp.getGrowth(1L, first, second);
        assertThat(result.getNewB50()).extracting(MvpDtos.GrowthScoreChangeItem::getSongId).containsExactly(code(51));
        assertThat(result.getDroppedB50()).extracting(MvpDtos.GrowthScoreChangeItem::getSongId).containsExactly(code(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_record WHERE snapshot_id=? AND difficulty_id=1",
                Integer.class, second)).isEqualTo(1);
    }
}
