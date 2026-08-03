package com.maimai.maidx.assistant.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.assistant.dto.AssistantQueryResponse;
import com.maimai.maidx.assistant.dto.ParsedIntent;
import com.maimai.maidx.assistant.enums.IntentType;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.SongService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RandomRecommendationTool extends AssistantToolSupport implements AssistantTool {

    private final SongDifficultyRepository songDifficultyRepository;
    private final SongService songService;

    @Override
    public IntentType supportIntent() {
        return IntentType.RANDOM_RECOMMENDATION;
    }

    @Override
    public AssistantQueryResponse execute(Long userId, ParsedIntent intent) {
        List<SongDifficulty> candidates = songDifficultyRepository.selectList(new LambdaQueryWrapper<SongDifficulty>()
                .ge(intent.getMinConstant() != null, SongDifficulty::getLevelDecimal, intent.getMinConstant())
                .le(intent.getMaxConstant() != null, SongDifficulty::getLevelDecimal, intent.getMaxConstant())
                .isNotNull(SongDifficulty::getLevelDecimal));

        List<SongDifficulty> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);

        Map<Long, SongDifficulty> distinctBySong = new LinkedHashMap<>();
        for (SongDifficulty chart : shuffled) {
            distinctBySong.putIfAbsent(chart.getSongId(), chart);
            if (distinctBySong.size() >= intent.getCount()) {
                break;
            }
        }

        List<SongDifficulty> selected = new ArrayList<>(distinctBySong.values());
        Map<Long, Song> songMap = loadSongMap(selected);

        AssistantQueryResponse response = new AssistantQueryResponse();
        response.setUserId(userId);
        response.setIntent(IntentType.RANDOM_RECOMMENDATION);
        response.setParsedIntent(intent);
        response.setRecommendations(selected.stream()
                .map(chart -> toRecommendationItem(chart, songMap.get(chart.getSongId())))
                .toList());
        response.setAnswer("已随机推荐" + response.getRecommendations().size() + "首不同歌曲。");
        return response;
    }

    private Map<Long, Song> loadSongMap(List<SongDifficulty> charts) {
        List<Long> songIds = charts.stream()
                .map(SongDifficulty::getSongId)
                .distinct()
                .toList();
        if (songIds.isEmpty()) {
            return Map.of();
        }
        return songService.listByIds(songIds).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
    }
}
