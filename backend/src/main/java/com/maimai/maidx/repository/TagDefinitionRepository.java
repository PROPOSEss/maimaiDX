package com.maimai.maidx.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maimai.maidx.entity.TagDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagDefinitionRepository extends BaseMapper<TagDefinition> {
}
