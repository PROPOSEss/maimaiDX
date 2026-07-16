package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.PlayerBind;

public interface PlayerBindService extends IService<PlayerBind> {

    /**
     * 绑定MAID
     * @param userId 用户ID
     * @param maId 舞萌MAID
     * @return 绑定信息
     */
    PlayerBind bindMaId(Long userId, String maId);

    /**
     * 解绑MAID
     */
    void unbindMaId(Long userId);

    /**
     * 根据userId获取绑定信息
     */
    PlayerBind getByUserId(Long userId);

    /**
     * 根据maId获取绑定信息
     */
    PlayerBind getByMaId(String maId);

    /**
     * 同步玩家成绩（从外部API获取）
     * @param playerId 绑定ID
     * @return 是否同步成功
     */
    boolean syncScores(Long playerId);
}
