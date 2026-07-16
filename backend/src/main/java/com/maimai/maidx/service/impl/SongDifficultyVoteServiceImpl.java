package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.entity.SongDifficultyVote;
import com.maimai.maidx.entity.TagDefinition;
import com.maimai.maidx.enums.TagEnum;
import com.maimai.maidx.repository.SongDifficultyVoteRepository;
import com.maimai.maidx.repository.TagDefinitionRepository;
import com.maimai.maidx.repository.PlayerBindRepository;
import com.maimai.maidx.service.SongDifficultyVoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 社区投票服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongDifficultyVoteServiceImpl extends ServiceImpl<SongDifficultyVoteRepository, SongDifficultyVote>
        implements SongDifficultyVoteService {

    private final PlayerBindRepository playerBindRepository;

    @Override
    @Transactional
    public void submitVote(Long userId, Long difficultyId, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            throw new RuntimeException("请至少选择一个标签");
        }
        if (tagNames.size() > 3) {
            throw new RuntimeException("最多选择3个标签");
        }

        // 验证标签合法性
        for (String tagName : tagNames) {
            try {
                TagEnum.fromName(tagName);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("无效标签: " + tagName);
            }
        }

        // 获取玩家Rating计算权重
        PlayerBind bind = playerBindRepository.selectOne(
                new LambdaQueryWrapper<PlayerBind>().eq(PlayerBind::getUserId, userId));
        if (bind == null) {
            throw new RuntimeException("请先绑定MAID");
        }

        double rating = bind.getRating() != null ? bind.getRating() : 10000;
        double weight = Math.round((rating / 10000.0) * 100.0) / 100.0;

        // 删除该用户对该谱面的旧投票
        remove(new LambdaQueryWrapper<SongDifficultyVote>()
                .eq(SongDifficultyVote::getUserId, userId)
                .eq(SongDifficultyVote::getDifficultyId, difficultyId));

        // 写入新投票
        for (String tagName : tagNames) {
            SongDifficultyVote vote = new SongDifficultyVote();
            vote.setUserId(userId);
            vote.setDifficultyId(difficultyId);
            vote.setTagName(tagName);
            vote.setVoteWeight(BigDecimal.valueOf(weight).setScale(2, RoundingMode.HALF_UP));
            save(vote);
        }

        log.info("用户{}对谱面{}提交投票: {}", userId, difficultyId, tagNames);
    }

    @Override
    public List<SongDifficultyVote> getUserVotes(Long userId, Long difficultyId) {
        return list(new LambdaQueryWrapper<SongDifficultyVote>()
                .eq(SongDifficultyVote::getUserId, userId)
                .eq(SongDifficultyVote::getDifficultyId, difficultyId));
    }

    @Override
    public List<TagVoteStat> getVoteStats(Long difficultyId) {
        List<SongDifficultyVote> votes = list(new LambdaQueryWrapper<SongDifficultyVote>()
                .eq(SongDifficultyVote::getDifficultyId, difficultyId));

        // 按标签聚合
        Map<String, TagVoteStat> statMap = new LinkedHashMap<>();
        for (SongDifficultyVote vote : votes) {
            String tag = vote.getTagName();
            TagVoteStat stat = statMap.computeIfAbsent(tag, k -> new TagVoteStat(tag, 0, 0));
            statMap.put(tag, new TagVoteStat(tag,
                    stat.totalWeight() + vote.getVoteWeight().doubleValue(),
                    stat.voteCount() + 1));
        }

        return new ArrayList<>(statMap.values());
    }
}
