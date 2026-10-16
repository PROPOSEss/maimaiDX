package com.maimai.maidx.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.assistant.enums.AdviceSource;
import com.maimai.maidx.assistant.enums.TrainingFocusType;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.service.SongService;
import com.maimai.maidx.utils.ScoreRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssistantBusinessServiceImpl implements AssistantBusinessService {

    private static final int TRAINING_SAMPLE_SIZE = 20;
    private static final BigDecimal IMPROVEMENT_MIN = new BigDecimal("97.000");
    private static final BigDecimal IMPROVEMENT_MAX = new BigDecimal("100.500");

    private final ScoreRecordRepository scoreRecordRepository;
    private final SongDifficultyRepository songDifficultyRepository;
    private final SongService songService;

    @Override
    public ScoreBundle getRecentScores(Long userId, int limit) {
        List<ScoreRecord> records = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .orderByDesc(ScoreRecord::getLastPlayTime)
                .orderByDesc(ScoreRecord::getBestPlayTime)
                .orderByDesc(ScoreRecord::getCreatedAt)
                .orderByDesc(ScoreRecord::getId)
                .last("LIMIT " + limit));
        return scoreBundle(records);
    }

    @Override
    public ScoreBundle getTopScores(Long userId, int limit) {
        List<ScoreRecord> records = scoreRecordRepository.selectList(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getUserId, userId)
                .isNotNull(ScoreRecord::getDifficultyId));

        List<ScoreRecord> topRecords = ScoreRules.bestPerChart(records, limit);
        return scoreBundle(topRecords);
    }

    @Override
    public RecommendationBundle getRandomRecommendations(BigDecimal minConstant,
                                                         BigDecimal maxConstant,
                                                         int count) {
        List<SongDifficulty> candidates = songDifficultyRepository.selectList(
                new LambdaQueryWrapper<SongDifficulty>()
                        .ge(minConstant != null, SongDifficulty::getLevelDecimal, minConstant)
                        .le(maxConstant != null, SongDifficulty::getLevelDecimal, maxConstant)
                        .isNotNull(SongDifficulty::getLevelDecimal));
        List<SongDifficulty> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);

        Map<Long, SongDifficulty> distinctBySong = new LinkedHashMap<>();
        for (SongDifficulty chart : shuffled) {
            distinctBySong.putIfAbsent(chart.getSongId(), chart);
            if (distinctBySong.size() >= count) {
                break;
            }
        }
        List<SongDifficulty> selected = new ArrayList<>(distinctBySong.values());
        return new RecommendationBundle(selected, loadSongsByCharts(selected));
    }

    @Override
    public TrainingAdviceBundle getTrainingAdvice(Long userId, int adviceCount) {
        ScoreBundle recent = getRecentScores(userId, TRAINING_SAMPLE_SIZE);
        if (recent.records().isEmpty()) {
            return new TrainingAdviceBundle(
                    AdviceSource.RULE,
                    List.of(new TrainingSuggestion(
                            TrainingFocusType.IMPROVEMENT_CANDIDATE,
                            "先导入成绩",
                            "训练建议需要基于真实成绩记录生成。",
                            "先使用成绩导入接口导入一份成绩 JSON。")),
                    recent);
        }

        BigDecimal average = averageAchievement(recent.records());
        List<BigDecimal> constants = recent.records().stream()
                .map(ScoreRecord::getDifficultyId)
                .map(recent.charts()::get)
                .filter(Objects::nonNull)
                .map(SongDifficulty::getLevelDecimal)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        long improvementCount = recent.records().stream()
                .map(ScoreRecord::getAchievementRate)
                .filter(Objects::nonNull)
                .filter(value -> value.compareTo(IMPROVEMENT_MIN) >= 0 && value.compareTo(IMPROVEMENT_MAX) < 0)
                .count();
        ScoreBundle top = getTopScores(userId, 50);
        Integer b50EdgeRa = top.records().stream()
                .map(ScoreRecord::getRa)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);

        List<TrainingSuggestion> candidates = List.of(
                new TrainingSuggestion(
                        TrainingFocusType.IMPROVEMENT_CANDIDATE,
                        "优先复练接近提升线的谱面",
                        improvementCount == 0
                                ? "最近成绩中暂未发现处于可提升区间的谱面。"
                                : "最近成绩中有 " + improvementCount + " 张谱面处于可提升区间。",
                        "优先减少重复失误，再逐步提高达成率。"),
                new TrainingSuggestion(
                        TrainingFocusType.CONSTANT_STABILITY,
                        "围绕常打定数稳定练习",
                        constants.isEmpty()
                                ? "最近记录暂时缺少谱面定数。"
                                : "最近主要游玩定数覆盖 " + constants.get(0) + " 到 " + constants.get(constants.size() - 1) + "。",
                        "先在常打区间内稳定发挥，再逐步增加挑战难度。"),
                new TrainingSuggestion(
                        TrainingFocusType.RECENT_CONSISTENCY,
                        "关注近期发挥稳定性",
                        "最近成绩平均达成率为 " + valueOrUnknown(average) + "。",
                        "复盘低于近期平均水平的成绩，优先修正重复出现的失误。"),
                new TrainingSuggestion(
                        TrainingFocusType.B50_EDGE,
                        "关注B50边缘成绩",
                        b50EdgeRa == null ? "当前没有可用的B50边缘RA。" : "当前高分样本边缘RA为 " + b50EdgeRa + "。",
                        "优先选择预期提升能超过边缘成绩的谱面。"),
                new TrainingSuggestion(
                        TrainingFocusType.TOP_SCORE_STABILITY,
                        "保持高分谱面的稳定性",
                        "后端选取了 " + top.records().size() + " 张高分谱面作为分析样本。",
                        "先保持已有高分谱面的稳定发挥，再安排新的冲分目标。"));

        List<ScoreRecord> evidenceRecords = recent.records().stream().limit(8).toList();
        ScoreBundle evidence = new ScoreBundle(evidenceRecords, recent.songs(), recent.charts());
        return new TrainingAdviceBundle(
                AdviceSource.RULE,
                candidates.subList(0, Math.min(adviceCount, candidates.size())),
                evidence);
    }

    @Override
    public List<Song> searchSongs(String keyword, int limit) {
        return songService.searchSongs(keyword, 1, limit).getRecords();
    }

    private ScoreBundle scoreBundle(List<ScoreRecord> records) {
        Map<Long, SongDifficulty> charts = loadCharts(records);
        return new ScoreBundle(records, loadSongs(records, charts), charts);
    }

    private Map<Long, SongDifficulty> loadCharts(List<ScoreRecord> records) {
        List<Long> ids = records.stream()
                .map(ScoreRecord::getDifficultyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return songDifficultyRepository.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SongDifficulty::getId, Function.identity()));
    }

    private Map<Long, Song> loadSongs(List<ScoreRecord> records, Map<Long, SongDifficulty> charts) {
        List<Long> ids = records.stream()
                .map(record -> record.getSongId() != null
                        ? record.getSongId()
                        : charts.get(record.getDifficultyId()) == null
                        ? null
                        : charts.get(record.getDifficultyId()).getSongId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return loadSongs(ids);
    }

    private Map<Long, Song> loadSongsByCharts(List<SongDifficulty> charts) {
        return loadSongs(charts.stream().map(SongDifficulty::getSongId).distinct().toList());
    }

    private Map<Long, Song> loadSongs(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return songService.listByIds(ids).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
    }

    private Comparator<ScoreRecord> topScoreComparator() {
        return ScoreRules.bestComparator();
    }

    private LocalDateTime playedAt(ScoreRecord record) {
        return ScoreRules.playedAt(record);
    }

    private BigDecimal averageAchievement(List<ScoreRecord> records) {
        List<BigDecimal> values = records.stream()
                .map(ScoreRecord::getAchievementRate)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 3, RoundingMode.HALF_UP);
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "未知" : value.toString();
    }
}
