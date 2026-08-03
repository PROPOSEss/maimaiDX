package com.maimai.maidx.assistant.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.SongService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RecentScoresTool extends AssistantToolSupport implements AssistantTool {

    private final ScoreRecordRepository scoreRecordRepository;
    private final SongDifficultyRepository songDifficultyRepository;
    private final SongService songService;

    @Override
    public IntentType supportIntent() {
        return IntentType.RECENT_SCORES;
    }

    @Override
    public AssistantQueryResponse execute(Long userId, ParsedIntent intent) {
        int limit = intent.getLimit();
        List<ScoreRecord> records = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .orderByDesc(ScoreRecord::getLastPlayTime)
                .orderByDesc(ScoreRecord::getBestPlayTime)
                .orderByDesc(ScoreRecord::getCreatedAt)
                .last("LIMIT " + limit));

        Map<Long, SongDifficulty> chartMap = loadChartMap(records);
        Map<Long, Song> songMap = loadSongMap(records, chartMap);

        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(userId);
        response.setIntent(IntentType.RECENT_SCORES);
        response.setParsedIntent(intent);
        response.setScores(records.stream().map(record -> toScoreItem(record, songMap, chartMap)).toList());
        response.setAnswer("已查询最近" + response.getScores().size() + "条成绩。");
        return response;
    }

    private Map<Long, SongDifficulty> loadChartMap(List<ScoreRecord> records) {
        List<Long> chartIds = records.stream()
                .map(ScoreRecord::getDifficultyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (chartIds.isEmpty()) {
            return Map.of();
        }
        return songDifficultyRepository.selectBatchIds(chartIds).stream()
                .collect(Collectors.toMap(SongDifficulty::getId, Function.identity()));
    }

    private Map<Long, Song> loadSongMap(List<ScoreRecord> records, Map<Long, SongDifficulty> chartMap) {
        List<Long> songIds = records.stream()
                .map(record -> record.getSongId() != null
                        ? record.getSongId()
                        : chartMap.get(record.getDifficultyId()) == null ? null : chartMap.get(record.getDifficultyId()).getSongId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (songIds.isEmpty()) {
            return Map.of();
        }
        return songService.listByIds(songIds).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
    }
}
