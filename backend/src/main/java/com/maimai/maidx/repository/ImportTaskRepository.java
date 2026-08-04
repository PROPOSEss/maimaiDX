package com.maimai.maidx.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maimai.maidx.entity.ImportTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImportTaskRepository extends BaseMapper<ImportTask> {
}
