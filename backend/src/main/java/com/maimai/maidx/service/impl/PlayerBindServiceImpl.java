package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.repository.PlayerBindRepository;
import com.maimai.maidx.service.PlayerBindService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maimai.maidx.service.ScoreRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 玩家绑定服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerBindServiceImpl extends ServiceImpl<PlayerBindRepository, PlayerBind> implements PlayerBindService {

    private final ScoreRecordService scoreRecordService;

    @Override
    @Transactional
    public PlayerBind bindMaId(Long userId, String maId) {
        // 检查是否已绑定
        PlayerBind existing = getByUserId(userId);
        if (existing != null) {
            throw new RuntimeException("您已绑定MAID，请先解绑后再重新绑定");
        }

        // 检查MAID是否已被其他用户绑定
        PlayerBind maIdExists = getByMaId(maId);
        if (maIdExists != null) {
            throw new RuntimeException("该MAID已被其他账户绑定");
        }

        PlayerBind bind = new PlayerBind();
        bind.setUserId(userId);
        bind.setMaId(maId);
        bind.setSyncStatus(0);
        save(bind);

        log.info("用户{}绑定MAID: {}", userId, maId);
        return bind;
    }

    @Override
    @Transactional
    public void unbindMaId(Long userId) {
        PlayerBind bind = getByUserId(userId);
        if (bind == null) {
            throw new RuntimeException("未找到绑定信息");
        }
        removeById(bind.getId());
        log.info("用户{}解绑MAID: {}", userId, bind.getMaId());
    }

    @Override
    public PlayerBind getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<PlayerBind>().eq(PlayerBind::getUserId, userId));
    }

    @Override
    public PlayerBind getByMaId(String maId) {
        return getOne(new LambdaQueryWrapper<PlayerBind>().eq(PlayerBind::getMaId, maId));
    }

    @Override
    @Transactional
    public boolean syncScores(Long playerId) {
        PlayerBind bind = getById(playerId);
        if (bind == null) {
            throw new RuntimeException("未找到玩家绑定信息");
        }

        try {
            bind.setSyncStatus(1); // 同步中
            updateById(bind);

            // TODO: 调用maimai DX成绩API获取成绩数据
            // 目前为占位实现，后续接入实际API

            bind.setSyncStatus(2); // 已同步
            bind.setLastSyncTime(LocalDateTime.now());
            updateById(bind);

            log.info("玩家{}成绩同步成功", playerId);
            return true;
        } catch (Exception e) {
            bind.setSyncStatus(-1); // 同步失败
            updateById(bind);
            log.error("玩家{}成绩同步失败: {}", playerId, e.getMessage());
            return false;
        }
    }
}
