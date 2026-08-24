package com.maimai.maidx.assistant.service;

import com.maimai.maidx.assistant.enums.AdviceSource;
import com.maimai.maidx.assistant.enums.TrainingFocusType;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AssistantBusinessService {

    ScoreBundle getRecentScores(Long userId, int limit);

    ScoreBundle getTopScores(Long userId, int limit);

    RecommendationBundle getRandomRecommendations(BigDecimal minConstant,
                                                  BigDecimal maxConstant,
                                                  int count);

    TrainingAdviceBundle getTrainingAdvice(Long userId, int adviceCount);

    List<Song> searchSongs(String keyword, int limit);

    record ScoreBundle(List<ScoreRecord> records,
                       Map<Long, Song> songs,
                       Map<Long, SongDifficulty> charts) {
    }

    record RecommendationBundle(List<SongDifficulty> charts,
                                Map<Long, Song> songs) {
    }

    record TrainingAdviceBundle(AdviceSource source,
                                List<TrainingSuggestion> suggestions,
                                ScoreBundle evidence) {
    }

    record TrainingSuggestion(TrainingFocusType focusType,
                              String title,
                              String reason,
                              String action) {
    }
}
