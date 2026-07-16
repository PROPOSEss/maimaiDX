package com.maimai.maidx.utils;

import com.maimai.maidx.dto.LoginResponse;
import com.maimai.maidx.dto.ScoreResponse;
import com.maimai.maidx.entity.PlayerBind;
import com.maimai.maidx.entity.ScoreRecord;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.entity.SongDifficulty;
import com.maimai.maidx.entity.User;

/**
 * Bean 转换工具类
 */
public class BeanUtil {

    /**
     * 将 User 和绑定信息转换为 LoginResponse
     *
     * @param user  用户实体
     * @param token JWT
     * @param bind  玩家绑定信息，可为 null
     * @return LoginResponse
     */
    public static LoginResponse toLoginResponse(User user, String token, PlayerBind bind) {

        LoginResponse response = new LoginResponse();

        response.setToken(token);
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatar());

        if (bind != null) {

            LoginResponse.BindInfo bindInfo = new LoginResponse.BindInfo();

            // PlayerBind里面没有playerId字段，用id替代
            bindInfo.setPlayerId(bind.getId());

            bindInfo.setMaId(bind.getMaId());
            bindInfo.setPlayerName(bind.getPlayerName());
            bindInfo.setRating(bind.getRating());

            response.setBindInfo(bindInfo);
            response.setBound(true);

        } else {
            response.setBound(false);
        }

        return response;
    }

    /**
     * 根据难度数值返回难度名称
     */
    public static String getDifficultyName(Integer difficulty) {
        if (difficulty == null) {
            return "UNKNOWN";
        }

        return switch (difficulty) {
            case 0 -> "BASIC";
            case 1 -> "ADVANCED";
            case 2 -> "EXPERT";
            case 3 -> "MASTER";
            case 4 -> "RE:MASTER";
            default -> "UNKNOWN";
        };
    }

    /**
     * 将 ScoreRecord + Song + SongDifficulty 转换为 ScoreResponse
     */
    public static ScoreResponse toScoreResponse(ScoreRecord record, Song song, SongDifficulty difficulty) {

        ScoreResponse response = new ScoreResponse();

        response.setId(record.getId());
        response.setDifficultyId(record.getDifficultyId());
        response.setScore(record.getScore());
        response.setRank(record.getRank());
        response.setFc(record.getFc());
        response.setFs(record.getFs());
        response.setPlayCount(record.getPlayCount());
        response.setBestPlayTime(record.getBestPlayTime() != null ? record.getBestPlayTime().toString() : null);

        if (song != null) {
            response.setSongId(song.getSongId());
            response.setTitle(song.getTitle());
            response.setArtist(song.getArtist());
        }

        if (difficulty != null) {
            response.setDifficulty(difficulty.getDifficulty());
            response.setDifficultyName(getDifficultyName(difficulty.getDifficulty()));
            response.setLevel(difficulty.getLevel() != null ? difficulty.getLevel() : 0);
        }

        return response;
    }
}