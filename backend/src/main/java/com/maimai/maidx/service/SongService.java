package com.maimai.maidx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maimai.maidx.entity.Song;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface SongService extends IService<Song> {

    /**
     * 分页查询歌曲列表
     */
    Page<Song> pageSongs(int page, int size, String keyword, Integer level, Integer difficulty);

    /**
     * 根据songId查询歌曲
     */
    Song getBySongId(String songId);

    /**
     * 搜索歌曲（标题/艺术家模糊匹配）
     */
    Page<Song> searchSongs(String keyword, int page, int size);
}
