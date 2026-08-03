package com.maimai.maidx.assistant.tool;

import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.enums.DifficultyEnum;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

abstract class AssistantToolSupport {

    AssistantQueryResponse.ScoreItem toScoreItem(ScoreRecord record,
                                                 Map<Long, Song> songMap,
                                                 Map<Long, SongDifficulty> chartMap) {
        SongDifficulty chart = chartMap.get(record.getDifficultyId());
        Song song = resolveSong(record, chart, songMap);

        AssistantQueryResponse.ScoreItem item = new AssistantQueryResponse.ScoreItem();
        item.setRecordId(record.getId());
        item.setSongId(song == null ? null : song.getSongId());
        item.setChartId(record.getDifficultyId());
        item.setSongName(song == null ? null : song.getTitle());
        item.setArtist(song == null ? null : song.getArtist());
        item.setDifficulty(chart == null ? null : chart.getDifficulty());
        item.setDifficultyName(chart == null ? null : difficultyName(chart.getDifficulty()));
        item.setConstant(chart == null ? null : chart.getLevelDecimal());
        item.setAchievement(record.getAchievementRate());
        item.setDxScore(record.getDxScore());
        item.setRate(record.getRank());
        item.setFc(record.getFc());
        item.setFs(record.getFs());
        item.setRa(record.getRa());
        item.setPlayedAt(playedAt(record));
        return item;
    }

    AssistantQueryResponse.RecommendationItem toRecommendationItem(SongDifficulty chart, Song song) {
        AssistantQueryResponse.RecommendationItem item = new AssistantQueryResponse.RecommendationItem();
        item.setSongId(song == null ? null : song.getSongId());
        item.setChartId(chart.getId());
        item.setSongName(song == null ? null : song.getTitle());
        item.setArtist(song == null ? null : song.getArtist());
        item.setDifficulty(chart.getDifficulty());
        item.setDifficultyName(difficultyName(chart.getDifficulty()));
        item.setConstant(chart.getLevelDecimal());
        item.setVersion(song == null ? null : song.getVersion());
        return item;
    }

    LocalDateTime playedAt(ScoreRecord record) {
        if (record.getLastPlayTime() != null) {
            return record.getLastPlayTime();
        }
        if (record.getBestPlayTime() != null) {
            return record.getBestPlayTime();
        }
        return record.getCreatedAt();
    }

    Comparator<ScoreRecord> topScoreComparator() {
        return Comparator
                .comparing(ScoreRecord::getAchievementRate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(this::playedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private Song resolveSong(ScoreRecord record, SongDifficulty chart, Map<Long, Song> songMap) {
        if (record.getSongId() != null && songMap.containsKey(record.getSongId())) {
            return songMap.get(record.getSongId());
        }
        if (chart != null) {
            return songMap.get(chart.getSongId());
        }
        return null;
    }

    private String difficultyName(Integer difficulty) {
        if (difficulty == null) {
            return "UNKNOWN";
        }
        try {
            return DifficultyEnum.fromCode(difficulty).getName();
        } catch (IllegalArgumentException e) {
            return "UNKNOWN";
        }
    }
}
