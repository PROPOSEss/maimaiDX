package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.repository.ScoreRecordRepository;
import com.maimai.maidx.service.ScoreRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成绩记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreRecordServiceImpl extends ServiceImpl<ScoreRecordRepository, ScoreRecord> implements ScoreRecordService {

    @Override
    public Page<ScoreRecord> getPlayerScores(Long playerId, int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<ScoreRecord>()
                        .eq(ScoreRecord::getPlayerId, playerId)
                        .orderByDesc(ScoreRecord::getScore));
    }

    @Override
    public List<ScoreRecord> getPlayerB50(Long playerId) {
        return list(new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getPlayerId, playerId)
                .orderByDesc(ScoreRecord::getScore)
                .last("LIMIT 50"));
    }

    @Override
    @Transactional
    public void saveOrUpdateScore(ScoreRecord score) {
        LambdaQueryWrapper<ScoreRecord> wrapper = new LambdaQueryWrapper<ScoreRecord>()
                .eq(ScoreRecord::getPlayerId, score.getPlayerId())
                .eq(ScoreRecord::getDifficultyId, score.getDifficultyId());

        ScoreRecord existing = getOne(wrapper);
        if (existing != null) {
            // 更新：保留最高分
            if (score.getScore() > existing.getScore()) {
                score.setId(existing.getId());
                score.setPlayCount(existing.getPlayCount() + 1);
                score.setBestPlayTime(LocalDateTime.now());
                score.setCreatedAt(existing.getCreatedAt()); // 保留原始创建时间
                updateById(score);
            } else {
                existing.setPlayCount(existing.getPlayCount() + 1);
                existing.setLastPlayTime(LocalDateTime.now());
                updateById(existing);
            }
        } else {
            score.setPlayCount(1);
            score.setBestPlayTime(LocalDateTime.now());
            score.setLastPlayTime(LocalDateTime.now());
            save(score);
        }
    }

    @Override
    @Transactional
    public void batchImportScores(Long playerId, List<ScoreRecord> scores) {
        log.info("批量导入成绩: playerId={}, count={}", playerId, scores.size());
        for (ScoreRecord score : scores) {
            score.setPlayerId(playerId);
            saveOrUpdateScore(score);
        }
    }
}
