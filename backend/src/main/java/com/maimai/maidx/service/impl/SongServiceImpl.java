package com.maimai.maidx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maimai.maidx.entity.Song;
import com.maimai.maidx.repository.SongRepository;
import com.maimai.maidx.service.SongService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 歌曲服务实现
 */
@Service
public class SongServiceImpl extends ServiceImpl<SongRepository, Song> implements SongService {

    @Override
    public Page<Song> pageSongs(int page, int size, String keyword, Integer level, Integer difficulty) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Song::getTitle, keyword).or().like(Song::getArtist, keyword));
        }
        wrapper.orderByDesc(Song::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Song getBySongId(String songId) {
        return getOne(new LambdaQueryWrapper<Song>().eq(Song::getSongId, songId));
    }

    @Override
    public Page<Song> searchSongs(String keyword, int page, int size) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.like(Song::getTitle, keyword)
                .or().like(Song::getTitleEn, keyword)
                .or().like(Song::getArtist, keyword));
        wrapper.orderByDesc(Song::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }
}
