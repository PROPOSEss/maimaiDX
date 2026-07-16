package com.maimai.maidx.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maimai.maidx.entity.Song;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SongRepository extends BaseMapper<Song> {
}
