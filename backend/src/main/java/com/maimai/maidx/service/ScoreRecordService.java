package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.ScoreRecord;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

public interface ScoreRecordService extends IService<ScoreRecord> {

    /**
     * 获取玩家所有成绩
     */
    Page<ScoreRecord> getPlayerScores(Long playerId, int page, int size);

    /**
     * 获取玩家B50成绩
     * @param playerId 绑定ID
     * @return B50成绩列表
     */
    List<ScoreRecord> getPlayerB50(Long playerId);

    /**
     * 保存或更新成绩记录
     */
    void saveOrUpdateScore(ScoreRecord score);

    /**
     * 批量导入成绩
     */
    void batchImportScores(Long playerId, List<ScoreRecord> scores);
}
