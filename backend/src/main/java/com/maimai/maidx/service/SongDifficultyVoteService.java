package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.SongDifficultyVote;

import java.util.List;

public interface SongDifficultyVoteService extends IService<SongDifficultyVote> {

    /**
     * 提交投票（最多选3个标签）
     * @param userId 用户ID
     * @param difficultyId 谱面ID
     * @param tagNames 标签列表（最多3个）
     */
    void submitVote(Long userId, Long difficultyId, List<String> tagNames);

    /**
     * 获取某用户对某谱面的投票
     */
    List<SongDifficultyVote> getUserVotes(Long userId, Long difficultyId);

    /**
     * 获取某谱面的投票统计（按标签聚合权重）
     */
    List<TagVoteStat> getVoteStats(Long difficultyId);

    /**
     * 投票统计DTO
     */
    record TagVoteStat(String tagName, double totalWeight, int voteCount) {}
}
