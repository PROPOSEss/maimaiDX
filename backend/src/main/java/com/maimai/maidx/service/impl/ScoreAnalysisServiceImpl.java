package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.dto.B50Response;
import com.maimai.maidx.dto.ScoreResponse;
import com.maimai.maidx.dto.ScoreTrendResponse;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.entity.SongFeature;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.repository.PlayerBindRepository;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.repository.SongDifficultyRepository;
import com.maimai.maidx.repository.SongFeatureRepository;
import com.maimai.maidx.service.ScoreAnalysisService;
import com.maimai.maidx.utils.BeanUtil;
import com.maimai.maidx.utils.CommonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 成绩分析服务实现 - B50计算与成绩趋势分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreAnalysisServiceImpl implements ScoreAnalysisService {

    private final ScoreRecordRepository scoreRecordRepository;
    private final SongRepository songRepository;
    private final SongDifficultyRepository songDifficultyRepository;
    private final SongFeatureRepository songFeatureRepository;
    private final PlayerBindRepository playerBindRepository;

    @Override
    public List<B50Response> getB50(Long userId) {
        // 1. 通过 userId 查找 playerId (PlayerBind)
        PlayerBind bind = playerBindRepository.selectOne(
                new LambdaQueryWrapper<PlayerBind>().eq(PlayerBind::getUserId, userId)
        );
        if (bind == null) {
            return Collections.emptyList();
        }
        Long playerId = bind.getId();

        // 2. 获取玩家所有最佳成绩
        List<ScoreRecord> records = scoreRecordRepository.selectList(
                new LambdaQueryWrapper<ScoreRecord>()
                        .eq(ScoreRecord::getPlayerId, playerId)
                        .isNotNull(ScoreRecord::getAchievementRate)
                        .orderByDesc(ScoreRecord::getScore)
        );

        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 计算 DX Rating 并排序取 Top 50
        List<B50Response> b50List = new ArrayList<>();

        for (ScoreRecord record : records) {
            // 查找难度信息
            SongDifficulty difficulty = songDifficultyRepository.selectById(record.getDifficultyId());
            if (difficulty == null || difficulty.getLevel() == null) {
                continue;
            }

            // 查找歌曲信息
            Song song = songRepository.selectById(difficulty.getSongId());

            // DX Rating = min(achievementRate/100, 100.5) × level
            double achievement = record.getAchievementRate() != null ? record.getAchievementRate().doubleValue() : 0;
            double ratingFactor = Math.min(achievement / 100.0, 100.5);
            double dxRating = ratingFactor * difficulty.getLevel();

            B50Response item = new B50Response();
            if (song != null) {
                item.setSongId(song.getSongId());
                item.setSongTitle(song.getTitle());
            }
            item.setDifficulty(BeanUtil.getDifficultyName(difficulty.getDifficulty()));
            item.setLevel(difficulty.getLevelDecimal() != null ? difficulty.getLevelDecimal().doubleValue() : difficulty.getLevel().doubleValue());
            item.setDxRating(dxRating);
            item.setAchievement(achievement);
            item.setDxScore(record.getDxScore());
            item.setAchievementRank(record.getRank());

            b50List.add(item);
        }

        // 按 DX Rating 降序排序取 Top 50
        b50List.sort((a, b) -> Double.compare(b.getDxRating(), a.getDxRating()));

        // 设置排名
        List<B50Response> result = new ArrayList<>();
        for (int i = 0; i < Math.min(50, b50List.size()); i++) {
            B50Response item = b50List.get(i);
            item.setRank(i + 1);
            result.add(item);
        }

        log.info("玩家 {} B50 计算完成，共 {} 条记录", userId, result.size());
        return result;
    }

    @Override
    public List<ScoreTrendResponse> getScoreTrend(Long userId, int months) {
        // 通过 userId 查找 playerId
        PlayerBind bind = playerBindRepository.selectOne(
                new LambdaQueryWrapper<PlayerBind>().eq(PlayerBind::getUserId, userId)
        );
        if (bind == null) {
            return Collections.emptyList();
        }
        Long playerId = bind.getId();

        List<ScoreTrendResponse> trends = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

            String month = monthStart.toString().substring(0, 7);

            // 查询该月所有成绩记录
            List<ScoreRecord> monthRecords = scoreRecordRepository.selectList(
                    new LambdaQueryWrapper<ScoreRecord>()
                            .eq(ScoreRecord::getPlayerId, playerId)
                            .ge(ScoreRecord::getBestPlayTime, monthStart.atStartOfDay())
                            .le(ScoreRecord::getBestPlayTime, monthEnd.atTime(23, 59, 59))
            );

            ScoreTrendResponse trend = new ScoreTrendResponse();
            trend.setMonth(month);
            trend.setPlayCount(monthRecords.size());

            if (!monthRecords.isEmpty()) {
                // 平均成就率
                double avgAchievement = monthRecords.stream()
                        .filter(r -> r.getAchievementRate() != null)
                        .mapToDouble(r -> r.getAchievementRate().doubleValue())
                        .average()
                        .orElse(0.0);
                trend.setAvgAchievement(Math.round(avgAchievement * 100.0) / 100.0);

                // 最佳成就率
                double bestAchievement = monthRecords.stream()
                        .filter(r -> r.getAchievementRate() != null)
                        .mapToDouble(r -> r.getAchievementRate().doubleValue())
                        .max()
                        .orElse(0.0);
                trend.setBestAchievement(bestAchievement);

                // AP数量 (FC=AP)
                int apCount = (int) monthRecords.stream()
                        .filter(r -> "AP".equalsIgnoreCase(r.getFc()) || "FC".equalsIgnoreCase(r.getFc()))
                        .count();
                trend.setNewApCount(apCount);

                // SSS+数量 (rank=SSS+)
                int sssPlusCount = (int) monthRecords.stream()
                        .filter(r -> "SSS+".equals(r.getRank()))
                        .count();
                trend.setNewSssPlusCount(sssPlusCount);
            } else {
                trend.setAvgAchievement(0.0);
                trend.setBestAchievement(0.0);
                trend.setNewApCount(0);
                trend.setNewSssPlusCount(0);
            }

            trends.add(trend);
        }

        // 计算Rating变化
        for (int i = 1; i < trends.size(); i++) {
            double currentRating = estimateMonthlyRating(playerId, trends.get(i).getMonth());
            double prevRating = estimateMonthlyRating(playerId, trends.get(i - 1).getMonth());
            trends.get(i).setRatingChange(Math.round((currentRating - prevRating) * 100.0) / 100.0);
        }
        if (!trends.isEmpty()) {
            trends.get(0).setRatingChange(0.0);
        }

        return trends;
    }

    /**
     * 估算某月的 DX Rating
     */
    private double estimateMonthlyRating(Long playerId, String month) {
        LocalDate monthEnd = LocalDate.parse(month + "-01").plusMonths(1).minusDays(1);

        List<ScoreRecord> records = scoreRecordRepository.selectList(
                new LambdaQueryWrapper<ScoreRecord>()
                        .eq(ScoreRecord::getPlayerId, playerId)
                        .le(ScoreRecord::getBestPlayTime, monthEnd.atTime(23, 59, 59))
        );

        double totalRating = 0;
        for (ScoreRecord record : records) {
            SongDifficulty difficulty = songDifficultyRepository.selectById(record.getDifficultyId());
            if (difficulty != null && difficulty.getLevel() != null) {
                double achievement = record.getAchievementRate() != null ? record.getAchievementRate().doubleValue() : 0;
                double factor = Math.min(achievement / 100.0, 100.5);
                totalRating += factor * difficulty.getLevel();
            }
        }

        return totalRating / 50.0;
    }

    @Override
    public double estimateDxRating(Long userId) {
        List<B50Response> b50 = getB50(userId);
        if (b50.isEmpty()) {
            return 0;
        }

        double totalDxRating = b50.stream()
                .mapToDouble(B50Response::getDxRating)
                .sum();

        return Math.round(totalDxRating / 50.0 * 100.0) / 100.0;
    }

    @Override
    public List<ScoreResponse> getBestScoresByDifficulty(Long userId, String difficulty, int page, int size) {
        // 通过 userId 查找 playerId
        PlayerBind bind = playerBindRepository.selectOne(
                new LambdaQueryWrapper<PlayerBind>().eq(PlayerBind::getUserId, userId)
        );
        if (bind == null) {
            return Collections.emptyList();
        }
        Long playerId = bind.getId();

        // 解析难度名到数值
        Integer difficultyValue = parseDifficultyName(difficulty);

        // 先通过难度数值查找 SongDifficulty
        List<SongDifficulty> diffList = songDifficultyRepository.selectList(
                new LambdaQueryWrapper<SongDifficulty>()
                        .eq(difficultyValue != null, SongDifficulty::getDifficulty, difficultyValue)
        );

        if (diffList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> diffIds = diffList.stream().map(SongDifficulty::getId).collect(Collectors.toList());

        List<ScoreRecord> records = scoreRecordRepository.selectList(
                new LambdaQueryWrapper<ScoreRecord>()
                        .eq(ScoreRecord::getPlayerId, playerId)
                        .in(ScoreRecord::getDifficultyId, diffIds)
                        .orderByDesc(ScoreRecord::getScore)
                        .last("LIMIT " + size + " OFFSET " + (page - 1) * size)
        );

        // 组装带歌曲信息的响应
        Map<Long, SongDifficulty> diffMap = diffList.stream()
                .collect(Collectors.toMap(SongDifficulty::getId, d -> d));

        return records.stream()
                .map(r -> {
                    SongDifficulty diff = diffMap.get(r.getDifficultyId());
                    Song song = null;
                    if (diff != null) {
                        song = songRepository.selectById(diff.getSongId());
                    }
                    return BeanUtil.toScoreResponse(r, song, diff);
                })
                .collect(Collectors.toList());
    }

    /**
     * 解析难度名到数值
     */
    private Integer parseDifficultyName(String difficulty) {
        if (difficulty == null) return null;
        return switch (difficulty.toUpperCase()) {
            case "BASIC" -> 0;
            case "ADVANCED" -> 1;
            case "EXPERT" -> 2;
            case "MASTER" -> 3;
            case "RE:MASTER" -> 4;
            default -> null;
        };
    }

    @Override
    public int refreshScores(Long userId) {
        // TODO: 对接 maimai DX 外部成绩 API
        log.info("开始刷新玩家 {} 的成绩数据...", userId);
        log.warn("成绩同步API尚未对接，返回0");
        return 0;
    }
}
